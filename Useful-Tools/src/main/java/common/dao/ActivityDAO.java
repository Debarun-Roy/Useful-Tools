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

import common.DatabaseUtils;

/**
 * DAO for the unified user-activity log (Sprint 15).
 *
 * ── What this is ──────────────────────────────────────────────────────────
 * A per-user timeline of meaningful tool usage: every generated password,
 * every unit conversion, every hash identified, and so on. Rendered by the
 * Dashboard's "Recent Activity" widget and by per-tool "Recent …" sections.
 *
 * ── Relation to existing tool-specific tables ─────────────────────────────
 * Existing tables (calc_history, generator_table, emi_calculations, …)
 * remain the source of truth for their respective tools' own UIs. This
 * table is a THIN INDEX on top — writing here never replaces a write to
 * the tool-specific table. Tools that are client-side only (unit converter,
 * hash identifier, API key generator) have no tool-specific table, so this
 * table IS their only record of activity.
 *
 * ── Who writes rows ───────────────────────────────────────────────────────
 * The frontend calls POST /api/activity/log in a fire-and-forget fashion
 * after a successful operation. This avoids touching any of the existing
 * server-side DAOs — zero regression risk to the Sprint 14 work.
 *
 * ── Schema ────────────────────────────────────────────────────────────────
 *   user_activity (
 *     id         INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username   TEXT NOT NULL,
 *     tool_name  TEXT NOT NULL,   -- stable machine id, e.g. "password.generate"
 *     summary    TEXT NOT NULL,   -- one-line human-readable ("Generated password for github.com")
 *     payload    TEXT,            -- optional JSON blob for future drill-down views
 *     created_at TEXT NOT NULL    -- ISO-8601 instant
 *   )
 *
 *   Index: (username, created_at DESC) for fast "recent N" queries.
 */
public class ActivityDAO {

    // ── Tool-name allow-list ─────────────────────────────────────────────────
    //
    // Only tools WITHOUT a dedicated history table belong here. Tools that
    // already persist their own history (Calculator → calc_history,
    // Password Vault Generate → generator_table, Password Vault Save →
    // password_table, Calculator Financial → emi_calculations /
    // tax_calculations / ci_calculations / salary_calculations) are
    // intentionally excluded so we don't double-log. Their history is
    // viewed inside each tool's own UI (e.g. Calculator → History tab),
    // and the unified activity widget on the Dashboard complements —
    // rather than duplicates — that.
    //
    // Adding a new tool? Just add its stable machine id here and wire the
    // frontend to call logActivity(). Rejecting an unknown tool at the
    // controller layer keeps the table free of junk rows.

    /*
     * ActivityDAO patch — Sprint 16
     *
     * Add these tool names to the allow-list comment in ActivityDAO.java.
     * The allow-list is enforced in the servlet/controller layer, not in the DAO itself,
     * but the comment in ActivityDAO is the canonical reference.
     *
     * New tool IDs (add alongside the existing ones):
     *
     *   "qrcode.generate"  — QR Code Generator (DevUtilsPage)
     *   "cron.build"       — Cron Expression Builder (DevUtilsPage)
     *   "time.convert"     — Timezone Converter (TimeUtilsPage)
     *   "time.timestamp"   — Timestamp ↔ Date (TimeUtilsPage)
     *
     * If ActivityDAO has a hard-coded Set<String> ALLOWED_TOOL_NAMES (check the
     * full file), add those four strings to it.
     *
     * Example (if using a static Set):
     *
     *   private static final Set<String> ALLOWED_TOOL_NAMES = Set.of(
     *       // ... existing entries ...
     *       "hash.identify",
     *       "key.generate",
     *       // Sprint 16 additions:
     *       "qrcode.generate",
     *       "cron.build",
     *       "time.convert",
     *       "time.timestamp"
     *   );
     */
    
    public static final java.util.Set<String> VALID_TOOL_NAMES = java.util.Set.of(
            "analyzer.classify",   // Number Analyser
            "converter.convert",   // Unit Converter
            "text.transform",      // Text Utilities
            "encoding.transform",  // Encoding & Decoding
            "code.format",         // Code Utilities
            "webdev.generate",     // Web Dev Helpers
            "image.process",       // Image Tools  (log params only, never file content)
            "hash.identify",       // Dev Utilities – Hash Identifier (new)
            // Sprint 16 additions:
            "qrcode.generate",
            "cron.build",
            "time.convert",
            "time.timestamp"
    );

    // ── Maximum payload size (TEXT column, keep sane to avoid abuse) ────────
    public static final int MAX_SUMMARY_LEN = 200;
    public static final int MAX_PAYLOAD_LEN = 4_000;

    // ── Retention cap (per user) ────────────────────────────────────────────
    // Prevents unbounded growth; oldest rows past this cap are pruned
    // opportunistically on each insert.
    public static final int MAX_ROWS_PER_USER = 500;

    // ── Schema creation ─────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS user_activity ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL, "
                + "tool_name TEXT NOT NULL, "
                + "summary TEXT NOT NULL, "
                + "payload TEXT, "
                + "created_at TEXT NOT NULL"
                + ");"
            );
            st.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_user_activity_user_created "
                + "ON user_activity(username, created_at DESC);"
            );
        }
    }

    // ── Writes ──────────────────────────────────────────────────────────────

    /**
     * Appends a new activity entry and opportunistically prunes any rows
     * above the per-user retention cap.
     *
     * @param username    Authenticated username from UserContext. Must be non-null / non-blank.
     * @param toolName    Machine id from VALID_TOOL_NAMES. Rejected at controller
     *                    if not on the allow-list, but defensively re-checked here.
     * @param summary     Human-readable one-liner. Truncated to MAX_SUMMARY_LEN.
     * @param payloadJson Optional JSON blob. May be null. Truncated to MAX_PAYLOAD_LEN.
     * @return Generated row id, or -1 on failure.
     */
    public static long log(String username, String toolName, String summary, String payloadJson) {
        if (username == null || username.isBlank()) return -1;
        if (toolName == null || !VALID_TOOL_NAMES.contains(toolName)) return -1;
        if (summary == null || summary.isBlank()) return -1;

        String safeSummary = summary.length() > MAX_SUMMARY_LEN
                ? summary.substring(0, MAX_SUMMARY_LEN) : summary;
        String safePayload = null;
        if (payloadJson != null) {
            safePayload = payloadJson.length() > MAX_PAYLOAD_LEN
                    ? payloadJson.substring(0, MAX_PAYLOAD_LEN) : payloadJson;
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            long id;
            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO user_activity (username, tool_name, summary, payload, created_at) "
                    + "VALUES (?, ?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS)) {

                pst.setString(1, username);
                pst.setString(2, toolName);
                pst.setString(3, safeSummary);
                if (safePayload != null) {
                    pst.setString(4, safePayload);
                } else {
                    pst.setNull(4, Types.VARCHAR);
                }
                pst.setString(5, Instant.now().toString());
                pst.executeUpdate();

                try (ResultSet rs = pst.getGeneratedKeys()) {
                    id = rs.next() ? rs.getLong(1) : -1;
                }
            }

            // Prune: drop rows beyond retention cap for this user.
            // Done inside the same connection to keep the operation cheap.
            pruneOld(conn, username);

            return id;

        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Deletes rows older than the retention cap for the given user.
     * Called after each insert so the table never grows unboundedly.
     */
    private static void pruneOld(Connection conn, String username) throws SQLException {
        try (PreparedStatement pst = conn.prepareStatement(
                "DELETE FROM user_activity "
                + "WHERE username = ? AND id NOT IN ("
                + "  SELECT id FROM user_activity "
                + "  WHERE username = ? "
                + "  ORDER BY created_at DESC, id DESC "
                + "  LIMIT ? "
                + ");")) {
            pst.setString(1, username);
            pst.setString(2, username);
            pst.setInt(3, MAX_ROWS_PER_USER);
            pst.executeUpdate();
        }
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    /**
     * Returns the most recent activity rows for the given user, newest first.
     *
     * @param username    Authenticated username. Required.
     * @param toolName    Optional tool filter — when non-null, restricts the
     *                    result to rows matching this single tool.
     * @param limit       Maximum rows to return. Clamped to [1, 100].
     * @param offset      Row offset for pagination. Clamped to >= 0.
     * @return Ordered list of activity rows, each a Map of column-name → value
     *         (id, toolName, summary, payload, createdAt). Empty list on error.
     */
    public static List<Map<String, Object>> listRecent(
            String username, String toolName, int limit, int offset) {

        List<Map<String, Object>> rows = new ArrayList<>();
        if (username == null || username.isBlank()) return rows;

        int safeLimit  = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        String sql = toolName != null && !toolName.isBlank()
                ? "SELECT id, tool_name, summary, payload, created_at "
                + "FROM user_activity "
                + "WHERE username = ? AND tool_name = ? "
                + "ORDER BY created_at DESC, id DESC "
                + "LIMIT ? OFFSET ?;"
                : "SELECT id, tool_name, summary, payload, created_at "
                + "FROM user_activity "
                + "WHERE username = ? "
                + "ORDER BY created_at DESC, id DESC "
                + "LIMIT ? OFFSET ?;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                int idx = 1;
                pst.setString(idx++, username);
                if (toolName != null && !toolName.isBlank()) pst.setString(idx++, toolName);
                pst.setInt(idx++, safeLimit);
                pst.setInt(idx,   safeOffset);

                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id",        rs.getLong  ("id"));
                        row.put("toolName",  rs.getString("tool_name"));
                        row.put("summary",   rs.getString("summary"));
                        row.put("payload",   rs.getString("payload")); // may be null
                        row.put("createdAt", rs.getString("created_at"));
                        rows.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    /**
     * Total row count for the given user, optionally filtered by tool.
     * Used for pagination metadata.
     */
    public static long count(String username, String toolName) {
        if (username == null || username.isBlank()) return 0;

        String sql = toolName != null && !toolName.isBlank()
                ? "SELECT COUNT(*) FROM user_activity WHERE username = ? AND tool_name = ?;"
                : "SELECT COUNT(*) FROM user_activity WHERE username = ?;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setString(1, username);
                if (toolName != null && !toolName.isBlank()) pst.setString(2, toolName);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ── Deletes ─────────────────────────────────────────────────────────────

    /**
     * Deletes activity rows for the given user, optionally narrowed to a
     * specific tool. Used by the "Clear history" controls.
     *
     * @return Number of rows deleted, or 0 on error.
     */
    public static int clear(String username, String toolName) {
        if (username == null || username.isBlank()) return 0;

        String sql = toolName != null && !toolName.isBlank()
                ? "DELETE FROM user_activity WHERE username = ? AND tool_name = ?;"
                : "DELETE FROM user_activity WHERE username = ?;";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setString(1, username);
                if (toolName != null && !toolName.isBlank()) pst.setString(2, toolName);
                return pst.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
