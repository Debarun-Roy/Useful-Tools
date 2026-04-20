package common.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import common.DatabaseUtils;

/**
 * ToolToggleDAO — Sprint 17 RBAC, global tool enable/disable.
 *
 * ── Schema ────────────────────────────────────────────────────────────────
 *   tool_toggles (
 *     tool_path  TEXT PRIMARY KEY,   -- "/calculator", "/vault", etc.
 *     enabled    INTEGER NOT NULL DEFAULT 1,   -- 1 = on, 0 = off (SQLite bool)
 *     updated_at TEXT NOT NULL                 -- ISO-8601 instant
 *   )
 *
 * ── Default state ─────────────────────────────────────────────────────────
 * All known tools are inserted as enabled=1 on first startup.  If a new
 * tool is added to KNOWN_TOOLS later, it is also inserted on next startup
 * via ensureSchema().
 *
 * ── Granularity ───────────────────────────────────────────────────────────
 * V1 is global-only: one toggle per tool path, affects all users.
 * Admins are never blocked by a disabled toggle on their own account —
 * this is enforced by the frontend (Dashboard shows "disabled" badge but
 * admins can still navigate).
 */
public class ToolToggleDAO {

    /** Canonical set of tool paths that can be toggled. */
    public static final Set<String> KNOWN_TOOLS = Set.of(
            "/calculator",
            "/analyser",
            "/vault",
            "/converter",
            "/text-utils",
            "/encoding",
            "/code-utils",
            "/web-dev",
            "/image-tools",
            "/dev-utils",
            "/time-utils"
    );

    // ── Schema ─────────────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tool_toggles ("
                + "tool_path  TEXT PRIMARY KEY, "
                + "enabled    INTEGER NOT NULL DEFAULT 1, "
                + "updated_at TEXT NOT NULL"
                + ")"
            );
        }

        // Insert any missing tools as enabled=1
        String now = Instant.now().toString();
        for (String path : KNOWN_TOOLS) {
            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT OR IGNORE INTO tool_toggles (tool_path, enabled, updated_at) "
                    + "VALUES (?, 1, ?)")) {
                pst.setString(1, path);
                pst.setString(2, now);
                pst.executeUpdate();
            }
        }
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    /**
     * Returns the enabled state of all tools.
     * Map key = tool_path, value = true (enabled) / false (disabled).
     */
    public static Map<String, Boolean> getAllStatuses() {
        Map<String, Boolean> result = new LinkedHashMap<>();

        // Pre-populate with defaults so we always return something even if
        // the DB call fails.
        for (String path : KNOWN_TOOLS) {
            result.put(path, true);
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT tool_path, enabled FROM tool_toggles ORDER BY tool_path")) {
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("tool_path"), rs.getInt("enabled") == 1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Returns true if the given tool_path is enabled (or unknown).
     */
    public static boolean isEnabled(String toolPath) {
        if (toolPath == null) return true;
        if (!KNOWN_TOOLS.contains(toolPath)) return true; // unknown → allow

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT enabled FROM tool_toggles WHERE tool_path = ?")) {
                pst.setString(1, toolPath);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) return rs.getInt("enabled") == 1;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true; // fail-open: if we can't read the DB, allow access
    }

    // ── Writes ──────────────────────────────────────────────────────────────

    /**
     * Updates the enabled state of a single tool.
     *
     * @return true if a row was updated (toolPath is in KNOWN_TOOLS).
     */
    public static boolean setEnabled(String toolPath, boolean enabled) {
        if (toolPath == null || !KNOWN_TOOLS.contains(toolPath)) return false;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO tool_toggles (tool_path, enabled, updated_at) "
                    + "VALUES (?, ?, ?) "
                    + "ON CONFLICT(tool_path) DO UPDATE SET "
                    + "enabled = excluded.enabled, updated_at = excluded.updated_at")) {
                pst.setString(1, toolPath);
                pst.setInt(2, enabled ? 1 : 0);
                pst.setString(3, Instant.now().toString());
                return pst.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
