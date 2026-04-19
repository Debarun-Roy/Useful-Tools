package common.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import common.DatabaseUtils;

/**
 * DAO for user-pinned / favorite tools (Sprint 15).
 *
 * ── What this is ──────────────────────────────────────────────────────────
 * Lets each user mark any Dashboard tool as a favorite, and reorder the
 * favorites list. Favorited tools are rendered in a dedicated section at
 * the top of the Dashboard, ordered by display_order ASC.
 *
 * ── Schema ────────────────────────────────────────────────────────────────
 *   user_favorites (
 *     id            INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username      TEXT NOT NULL,
 *     tool_path     TEXT NOT NULL,   -- '/vault', '/calculator', '/dev-utils', etc.
 *     display_order INTEGER NOT NULL, -- 0-based, lower = higher on page
 *     created_at    TEXT NOT NULL,
 *     UNIQUE(username, tool_path)
 *   )
 *
 *   Index: (username, display_order) for fast ordered retrieval.
 *
 * ── tool_path allow-list ──────────────────────────────────────────────────
 * Valid paths are the React Router routes for each tool. Any path not on
 * the allow-list is rejected by the controller to avoid junk rows being
 * inserted by a malicious client.
 */
public class FavoritesDAO {

    public static final java.util.Set<String> VALID_TOOL_PATHS = java.util.Set.of(
            "/calculator",
            "/analyser",
            "/vault",
            "/converter",
            "/text-utils",
            "/encoding",
            "/code-utils",
            "/web-dev",
            "/image-tools",
            "/dev-utils"
    );

    // Hard cap — a user pinning every tool still shouldn't have unlimited rows.
    public static final int MAX_FAVORITES_PER_USER = 20;

    // ── Schema ──────────────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS user_favorites ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL, "
                + "tool_path TEXT NOT NULL, "
                + "display_order INTEGER NOT NULL, "
                + "created_at TEXT NOT NULL, "
                + "UNIQUE(username, tool_path)"
                + ");"
            );
            st.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_user_favorites_user_order "
                + "ON user_favorites(username, display_order);"
            );
        }
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    /**
     * Returns the user's favorites in display order (lowest first).
     * Each element is a Map with keys: id, toolPath, displayOrder, createdAt.
     */
    public static List<Map<String, Object>> listForUser(String username) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (username == null || username.isBlank()) return rows;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT id, tool_path, display_order, created_at "
                    + "FROM user_favorites "
                    + "WHERE username = ? "
                    + "ORDER BY display_order ASC, id ASC;")) {

                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id",           rs.getLong  ("id"));
                        row.put("toolPath",     rs.getString("tool_path"));
                        row.put("displayOrder", rs.getInt   ("display_order"));
                        row.put("createdAt",    rs.getString("created_at"));
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
     * Returns how many favorites the user currently has. Used for the
     * cap-enforcement check before inserting.
     */
    public static int countForUser(String username) {
        if (username == null || username.isBlank()) return 0;
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT COUNT(*) FROM user_favorites WHERE username = ?;")) {
                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ── Writes ──────────────────────────────────────────────────────────────

    /**
     * Adds a favorite row if it doesn't already exist. New rows are placed
     * at the bottom of the user's list (highest display_order + 1).
     *
     * @return true if a new row was inserted, false if the row already existed
     *         or the insert failed or the user hit the cap.
     */
    public static boolean add(String username, String toolPath) {
        if (username == null || username.isBlank()) return false;
        if (toolPath == null || !VALID_TOOL_PATHS.contains(toolPath)) return false;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);

            // Already exists? Fast-path out.
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT 1 FROM user_favorites WHERE username = ? AND tool_path = ? LIMIT 1;")) {
                pst.setString(1, username);
                pst.setString(2, toolPath);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) return false;
                }
            }

            // Cap check.
            if (countForUser(username) >= MAX_FAVORITES_PER_USER) return false;

            // Find next display_order slot.
            int nextOrder = 0;
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT COALESCE(MAX(display_order), -1) + 1 FROM user_favorites WHERE username = ?;")) {
                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) nextOrder = rs.getInt(1);
                }
            }

            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO user_favorites (username, tool_path, display_order, created_at) "
                    + "VALUES (?, ?, ?, ?);")) {
                pst.setString(1, username);
                pst.setString(2, toolPath);
                pst.setInt   (3, nextOrder);
                pst.setString(4, Instant.now().toString());
                return pst.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes a single favorite row for the given user + tool path.
     * Does NOT re-compact the remaining rows' display_order values —
     * the client-side rendering tolerates gaps and re-compaction happens
     * implicitly on the next reorder() call.
     *
     * @return true if a row was removed, false otherwise.
     */
    public static boolean remove(String username, String toolPath) {
        if (username == null || username.isBlank()) return false;
        if (toolPath == null || toolPath.isBlank()) return false;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "DELETE FROM user_favorites WHERE username = ? AND tool_path = ?;")) {
                pst.setString(1, username);
                pst.setString(2, toolPath);
                return pst.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Replaces the display_order of every row in {@code orderedPaths} by the
     * index of that path. Paths not on the allow-list are silently skipped.
     * Paths that don't exist as favorites for this user are skipped (no-op).
     *
     * The reorder runs inside a single transaction so the list never lands
     * in a half-reordered state.
     *
     * @return true on success, false on any SQL error.
     */
    public static boolean reorder(String username, List<String> orderedPaths) {
        if (username == null || username.isBlank()) return false;
        if (orderedPaths == null)                   return false;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement pst = conn.prepareStatement(
                    "UPDATE user_favorites SET display_order = ? "
                    + "WHERE username = ? AND tool_path = ?;")) {
                for (int i = 0; i < orderedPaths.size(); i++) {
                    String path = orderedPaths.get(i);
                    if (path == null || !VALID_TOOL_PATHS.contains(path)) continue;
                    pst.setInt   (1, i);
                    pst.setString(2, username);
                    pst.setString(3, path);
                    pst.addBatch();
                }
                pst.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(oldAuto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
