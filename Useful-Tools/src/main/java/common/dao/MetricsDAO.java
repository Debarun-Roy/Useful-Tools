package common.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import common.DatabaseUtils;

/**
 * DAO for per-invocation tool metrics (Sprint 18).
 *
 * ── What this is ──────────────────────────────────────────────────────────
 * A row is appended every time a user invokes a tool — one row per invocation.
 * Rows carry wall-clock execution time, a best-effort memory delta, a
 * latency figure, a success/failure flag, and an optional error code.
 *
 * Server-side tools are auto-instrumented by {@code common.filter.MetricsFilter}
 * (registered last in the filter chain, after CsrfFilter). Client-side tools
 * (text utilities, encoding, code utilities, etc.) report their own metrics
 * via POST /api/metrics/log — same fire-and-forget pattern as activity logs.
 *
 * ── Relation to ActivityDAO ───────────────────────────────────────────────
 * ActivityDAO is a human-readable timeline: "Generated 16-char password for
 * github.com". MetricsDAO is the numeric counterpart: "password.generate took
 * 142 ms, succeeded". The two tables are orthogonal — a tool can log to both,
 * neither, or either.
 *
 * ── Schema ────────────────────────────────────────────────────────────────
 *   tool_metrics (
 *     id                INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username          TEXT    NOT NULL,
 *     tool_name         TEXT    NOT NULL,     -- stable id from VALID_TOOL_NAMES
 *     execution_time_ms INTEGER NOT NULL,     -- wall-clock duration of the operation
 *     memory_bytes      INTEGER,              -- best-effort delta; may be null / negative
 *     latency_ms        INTEGER,              -- round-trip latency inc. network (server-side only)
 *     success           INTEGER NOT NULL DEFAULT 1,  -- 0 = failure, 1 = success
 *     error_code        TEXT,                 -- machine-readable failure code (nullable)
 *     created_at        TEXT    NOT NULL      -- ISO-8601 instant
 *   )
 *
 *   Indexes: (tool_name, created_at DESC) and (created_at DESC).
 *
 * ── A note on memory_bytes ────────────────────────────────────────────────
 * There is no reliable way to measure the memory footprint of a single
 * operation in a shared JVM; between start and end, other threads can
 * allocate and free heap memory. The value recorded here is
 *
 *     (totalMemory - freeMemory)_after − (totalMemory - freeMemory)_before
 *
 * sampled once on either side of the operation. On the client, it is
 * performance.memory.usedJSHeapSize deltas (Chromium-only; falls back to
 * null elsewhere). Both are coarse proxies, meaningful only in aggregate
 * across many samples — not per-invocation. The admin UI treats the average
 * memory figure as indicative, not precise.
 *
 * ── Retention ────────────────────────────────────────────────────────────
 * Rows older than {@link #RETENTION_DAYS} are pruned opportunistically on
 * ~1% of writes. This keeps the table bounded over long uptimes without
 * requiring a scheduled job.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────
 * All public methods open, use, and close their own connection. No shared
 * mutable state.
 */
public class MetricsDAO {

    // ── Tool-name allow-list ────────────────────────────────────────────────
    //
    // Anything not in this set is rejected by both the filter and the log
    // controller with no row written. Keep in sync with the frontend
    // VALID_TOOL_NAMES in utils/logMetric.js and with the PATH_TO_TOOL map
    // in MetricsFilter.
    //
    // Naming convention: <category>.<verb> — matches ActivityDAO where the
    // tools overlap.
    public static final Set<String> VALID_TOOL_NAMES = Set.of(
            // ── Client-side tools (mirror ActivityDAO + key.generate) ──────
            "analyzer.classify",
            "analyzer.base",
            "analyzer.series",
            "converter.convert",
            "text.transform",
            "encoding.transform",
            "code.format",
            "webdev.generate",
            "webdev.headers",
            "image.process",
            "hash.identify",
            "key.generate",
            "qrcode.generate",
            "cron.build",
            "time.convert",
            "time.timestamp",

            // ── Server-side tools (auto-instrumented by MetricsFilter) ─────
            "password.generate",
            "password.save",
            "password.fetch",
            "calculator.standard",
            "calculator.financial",
            "calculator.probability"
    );

    /** Truncate error codes to this length to keep rows compact. */
    public static final int MAX_ERROR_CODE_LEN = 80;

    /** Rows older than this many days are eligible for pruning. */
    public static final long RETENTION_DAYS = 90L;

    /** Sample-size floor for top-slow / most-failing rankings. */
    public static final int MIN_SAMPLES_FOR_RANKING = 5;

    /** Probability of an opportunistic prune firing on a single insert. */
    private static final double PRUNE_SAMPLE_RATE = 0.01;

    // ── Schema ───────────────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tool_metrics ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL, "
                + "tool_name TEXT NOT NULL, "
                + "execution_time_ms INTEGER NOT NULL, "
                + "memory_bytes INTEGER, "
                + "latency_ms INTEGER, "
                + "success INTEGER NOT NULL DEFAULT 1, "
                + "error_code TEXT, "
                + "created_at TEXT NOT NULL"
                + ");"
            );
            st.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_tool_metrics_tool_time "
                + "ON tool_metrics(tool_name, created_at DESC);"
            );
            st.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_tool_metrics_time "
                + "ON tool_metrics(created_at DESC);"
            );
        }
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Appends a single metric row. Validates tool_name against the allow-list
     * and coerces negative/null durations to sensible defaults.
     *
     * @param username        Authenticated username (including "Guest User"). Required.
     * @param toolName        Stable id from {@link #VALID_TOOL_NAMES}. Required.
     * @param executionTimeMs Wall-clock duration. Clamped to >= 0.
     * @param memoryBytes     Optional memory delta. May be null or negative.
     * @param latencyMs       Optional round-trip latency. May be null (client-side tools).
     * @param success         true = ok, false = failure.
     * @param errorCode       Optional machine-readable failure code. Truncated to MAX_ERROR_CODE_LEN.
     * @return Generated row id, or -1 on failure.
     */
    public static long log(String username,
                           String toolName,
                           long executionTimeMs,
                           Long memoryBytes,
                           Long latencyMs,
                           boolean success,
                           String errorCode) {

        if (username == null || username.isBlank()) return -1;
        if (toolName == null || !VALID_TOOL_NAMES.contains(toolName)) return -1;

        long safeExec = Math.max(0L, executionTimeMs);
        String safeErr = null;
        if (errorCode != null && !errorCode.isBlank()) {
            safeErr = errorCode.length() > MAX_ERROR_CODE_LEN
                    ? errorCode.substring(0, MAX_ERROR_CODE_LEN)
                    : errorCode;
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);

            long id;
            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO tool_metrics "
                    + "(username, tool_name, execution_time_ms, memory_bytes, "
                    + " latency_ms, success, error_code, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS)) {

                pst.setString(1, username);
                pst.setString(2, toolName);
                pst.setLong  (3, safeExec);

                if (memoryBytes == null) pst.setNull(4, Types.INTEGER);
                else                     pst.setLong(4, memoryBytes);

                if (latencyMs == null)   pst.setNull(5, Types.INTEGER);
                else                     pst.setLong(5, Math.max(0L, latencyMs));

                pst.setInt   (6, success ? 1 : 0);

                if (safeErr == null)     pst.setNull(7, Types.VARCHAR);
                else                     pst.setString(7, safeErr);

                pst.setString(8, Instant.now().toString());

                pst.executeUpdate();

                try (ResultSet rs = pst.getGeneratedKeys()) {
                    id = rs.next() ? rs.getLong(1) : -1;
                }
            }

            // Opportunistic retention prune. We accept the small cost on the
            // request path rather than running a scheduled job.
            if (Math.random() < PRUNE_SAMPLE_RATE) {
                pruneOld(conn);
            }

            return id;

        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static void pruneOld(Connection conn) {
        String cutoff = Instant.now().minusSeconds(RETENTION_DAYS * 86400L).toString();
        try (PreparedStatement pst = conn.prepareStatement(
                "DELETE FROM tool_metrics WHERE created_at < ?;")) {
            pst.setString(1, cutoff);
            pst.executeUpdate();
        } catch (SQLException e) {
            // Non-fatal — the insert already succeeded.
            e.printStackTrace();
        }
    }

    // ── Read helpers ─────────────────────────────────────────────────────────

    /**
     * Turns a window token ("24h" / "7d" / "30d" / "all" / null) into an
     * ISO-8601 cutoff instant, or null for "all time". Unknown tokens map
     * to null (all time) rather than failing, which keeps the admin UI
     * robust to typos.
     */
    private static String windowCutoff(String window) {
        if (window == null) return null;
        return switch (window) {
            case "24h" -> Instant.now().minusSeconds(86_400L).toString();
            case "7d"  -> Instant.now().minusSeconds(7L  * 86_400L).toString();
            case "30d" -> Instant.now().minusSeconds(30L * 86_400L).toString();
            default    -> null;  // "all" or unrecognised
        };
    }

    /**
     * Overall stats across all tools within the window.
     *
     * Returns a map with:
     *   totalInvocations (long)     — row count
     *   avgExecutionMs   (double)   — AVG(execution_time_ms), 0.0 when empty
     *   avgLatencyMs     (double)   — AVG(latency_ms), 0.0 when empty
     *   avgMemoryBytes   (double)   — AVG(memory_bytes), 0.0 when empty
     *   successRatePct   (double)   — 100 × AVG(success), 100.0 when empty
     */
    public static Map<String, Object> getOverallStats(String window) {
        String cutoff = windowCutoff(window);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("totalInvocations", 0L);
        result.put("avgExecutionMs",   0.0);
        result.put("avgLatencyMs",     0.0);
        result.put("avgMemoryBytes",   0.0);
        result.put("successRatePct", 100.0);

        String sql = "SELECT COUNT(*)                 AS total, "
                   + "       AVG(execution_time_ms)   AS avg_exec, "
                   + "       AVG(latency_ms)          AS avg_lat, "
                   + "       AVG(memory_bytes)        AS avg_mem, "
                   + "       AVG(success)             AS success_ratio "
                   + "FROM   tool_metrics"
                   + (cutoff != null ? " WHERE created_at >= ?" : "") + ";";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                if (cutoff != null) pst.setString(1, cutoff);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        long total = rs.getLong("total");
                        result.put("totalInvocations", total);
                        if (total > 0) {
                            result.put("avgExecutionMs", rs.getDouble("avg_exec"));
                            result.put("avgLatencyMs",   rs.getDouble("avg_lat"));
                            result.put("avgMemoryBytes", rs.getDouble("avg_mem"));
                            result.put("successRatePct", rs.getDouble("success_ratio") * 100.0);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /** Top-N tools by average execution time, with min-sample floor. */
    public static List<Map<String, Object>> getTopSlowTools(String window, int limit) {
        return rankTools(window, limit,
                "avg_exec DESC",
                /* requireFailures = */ false);
    }

    /** Top-N tools by failure rate, with min-sample floor; excludes 0-failure rows. */
    public static List<Map<String, Object>> getMostFailingTools(String window, int limit) {
        return rankTools(window, limit,
                "failure_rate_pct DESC",
                /* requireFailures = */ true);
    }

    /**
     * Top-N tools by invocation count. No sample-size floor is applied here —
     * "popularity" is defined by raw volume, so even 1 invocation counts.
     */
    public static List<Map<String, Object>> getMostPopularTools(String window, int limit) {
        String cutoff = windowCutoff(window);
        int safeLimit = Math.max(1, Math.min(limit, 50));

        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT tool_name, "
                   + "       COUNT(*)                   AS invocations, "
                   + "       AVG(execution_time_ms)     AS avg_exec, "
                   + "       AVG(success) * 100.0       AS success_rate_pct "
                   + "FROM   tool_metrics"
                   + (cutoff != null ? " WHERE created_at >= ?" : "")
                   + " GROUP  BY tool_name "
                   + " ORDER  BY invocations DESC "
                   + " LIMIT  ?;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                int idx = 1;
                if (cutoff != null) pst.setString(idx++, cutoff);
                pst.setInt(idx, safeLimit);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) rows.add(extractRankRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    /**
     * Per-tool breakdown across every tool seen in the window. Rendered as
     * the main analytics table. No sample-size floor — every tool with at
     * least one invocation appears.
     */
    public static List<Map<String, Object>> getPerToolBreakdown(String window) {
        String cutoff = windowCutoff(window);
        List<Map<String, Object>> rows = new ArrayList<>();

        String sql = "SELECT tool_name, "
                   + "       COUNT(*)                                AS invocations, "
                   + "       AVG(execution_time_ms)                  AS avg_exec, "
                   + "       MAX(execution_time_ms)                  AS max_exec, "
                   + "       AVG(latency_ms)                         AS avg_lat, "
                   + "       AVG(memory_bytes)                       AS avg_mem, "
                   + "       AVG(success) * 100.0                    AS success_rate_pct, "
                   + "       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failures "
                   + "FROM   tool_metrics"
                   + (cutoff != null ? " WHERE created_at >= ?" : "")
                   + " GROUP  BY tool_name "
                   + " ORDER  BY invocations DESC;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                if (cutoff != null) pst.setString(1, cutoff);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                        row.put("toolName",       rs.getString("tool_name"));
                        row.put("invocations",    rs.getLong("invocations"));
                        row.put("avgExecutionMs", rs.getDouble("avg_exec"));
                        row.put("maxExecutionMs", rs.getLong("max_exec"));
                        row.put("avgLatencyMs",   rs.getDouble("avg_lat"));
                        row.put("avgMemoryBytes", rs.getDouble("avg_mem"));
                        row.put("successRatePct", rs.getDouble("success_rate_pct"));
                        row.put("failures",       rs.getLong("failures"));
                        rows.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static List<Map<String, Object>> rankTools(String window,
                                                       int limit,
                                                       String orderBy,
                                                       boolean requireFailures) {
        String cutoff = windowCutoff(window);
        int safeLimit = Math.max(1, Math.min(limit, 50));

        List<Map<String, Object>> rows = new ArrayList<>();

        // SECURITY: orderBy is a hard-coded constant from the caller, never
        // user-controlled. Parameterising ORDER BY clauses in JDBC isn't
        // possible directly; the alternative is a switch in the caller, which
        // is effectively what we have.
        String sql = "SELECT tool_name, "
                   + "       COUNT(*)                          AS invocations, "
                   + "       AVG(execution_time_ms)            AS avg_exec, "
                   + "       MAX(execution_time_ms)            AS max_exec, "
                   + "       AVG(latency_ms)                   AS avg_lat, "
                   + "       AVG(success) * 100.0              AS success_rate_pct, "
                   + "       (1.0 - AVG(success)) * 100.0      AS failure_rate_pct, "
                   + "       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failures "
                   + "FROM   tool_metrics"
                   + (cutoff != null ? " WHERE created_at >= ?" : "")
                   + " GROUP  BY tool_name "
                   + " HAVING COUNT(*) >= " + MIN_SAMPLES_FOR_RANKING
                   + (requireFailures ? " AND failures > 0" : "")
                   + " ORDER  BY " + orderBy
                   + " LIMIT  ?;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                int idx = 1;
                if (cutoff != null) pst.setString(idx++, cutoff);
                pst.setInt(idx, safeLimit);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) rows.add(extractRankRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    private static LinkedHashMap<String, Object> extractRankRow(ResultSet rs) throws SQLException {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("toolName",       rs.getString("tool_name"));
        row.put("invocations",    rs.getLong  ("invocations"));
        row.put("avgExecutionMs", rs.getDouble("avg_exec"));
        row.put("maxExecutionMs", rs.getLong  ("max_exec"));
        row.put("avgLatencyMs",   rs.getDouble("avg_lat"));
        // success_rate_pct is always present; failure_rate_pct and failures
        // only in ranking queries. We read defensively.
        try { row.put("successRatePct", rs.getDouble("success_rate_pct")); }
        catch (SQLException ignore) { /* absent */ }
        try { row.put("failureRatePct", rs.getDouble("failure_rate_pct")); }
        catch (SQLException ignore) { /* absent */ }
        try { row.put("failures", rs.getLong("failures")); }
        catch (SQLException ignore) { /* absent */ }
        return row;
    }

    private MetricsDAO() { }  // utility class — not instantiable
}
