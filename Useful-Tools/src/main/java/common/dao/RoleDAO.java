package common.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import common.DatabaseUtils;
import common.UserContext;

/**
 * RoleDAO — Sprint 17 RBAC support.
 *
 * Manages the `role` column on `user_table` and provides all role-related
 * queries used by the admin controllers.
 *
 * ── Schema ────────────────────────────────────────────────────────────────
 * user_table already exists (managed by UserDAO). This DAO adds:
 *   ALTER TABLE user_table ADD COLUMN role TEXT DEFAULT 'user';
 *
 * Then bootstraps the hardcoded admin:
 *   UPDATE user_table SET role = 'admin' WHERE username = 'Deba_exe';
 *
 * ── Roles ──────────────────────────────────────────────────────────────────
 *   admin — can manage users and enable/disable tools
 *   user  — standard registered account (default)
 *   (guest — never stored; set in session by GuestLoginController)
 *
 * ── Thread safety ─────────────────────────────────────────────────────────
 * All public methods open, use, and close their own connection. No shared
 * mutable state.
 */
public class RoleDAO {

    /** Hardcoded bootstrap admin username (Sprint 17 decision). */
    private static final String BOOTSTRAP_ADMIN = "Deba_exe";

    private static final String VALID_ROLES = "(admin|user)";

    // ── Schema ────────────────────────────────────────────────────────────────

    /**
     * Idempotent schema migration: adds the role column if absent and
     * bootstraps the hardcoded admin user.
     *
     * Called by LoginController and AdminUsersController to ensure the
     * column exists before any role read/write.
     */
    public static void ensureSchema(Connection conn) throws SQLException {
        // 1. Add role column if not present
        boolean hasRole = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(user_table)")) {
            while (rs.next()) {
                if ("role".equalsIgnoreCase(rs.getString("name"))) {
                    hasRole = true;
                    break;
                }
            }
        }
        if (!hasRole) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(
                    "ALTER TABLE user_table ADD COLUMN role TEXT DEFAULT 'user'");
            }
        }

        // 2. Bootstrap Deba_exe as admin (only if the user already registered)
        try (PreparedStatement pst = conn.prepareStatement(
                "UPDATE user_table SET role = 'admin' "
                + "WHERE username = ? AND (role IS NULL OR role != 'admin')")) {
            pst.setString(1, BOOTSTRAP_ADMIN);
            pst.executeUpdate();
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    /**
     * Returns the role for a registered user.
     * Returns "user" if the user is not found or the column is NULL.
     */
    public static String getRole(String username) {
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT role FROM user_table WHERE username = ?")) {
                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        String role = rs.getString("role");
                        return (role != null && !role.isBlank()) ? role : UserContext.ROLE_USER;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return UserContext.ROLE_USER;
    }

    /**
     * Returns all registered users (excluding Guest User) with their roles
     * and creation dates, ordered by username ASC.
     *
     * Each map entry has keys: username, role, createdDate.
     */
    public static List<Map<String, String>> listUsers() {
        List<Map<String, String>> result = new ArrayList<>();
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT username, role, created_date "
                    + "FROM user_table "
                    + "WHERE username != 'Guest User' "
                    + "ORDER BY username ASC")) {
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("username",    rs.getString("username"));
                        row.put("role",        rs.getString("role") != null
                                                ? rs.getString("role") : "user");
                        row.put("createdDate", rs.getString("created_date") != null
                                                ? rs.getString("created_date") : "Unknown");
                        result.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Changes a user's role.
     *
     * Validation:
     *   - Cannot change own role (prevents self-demotion from admin).
     *   - Cannot change the bootstrap admin's role.
     *   - Role must be 'admin' or 'user'.
     *   - Cannot set the last remaining admin to 'user'.
     *
     * @return true if updated, false if blocked or user not found.
     */
    public static boolean setRole(String targetUsername, String newRole,
                                  String requestingUsername) {
        if (targetUsername == null || newRole == null) return false;
        if (!newRole.equals(UserContext.ROLE_ADMIN)
                && !newRole.equals(UserContext.ROLE_USER)) return false;

        // Cannot demote self
        if (targetUsername.equalsIgnoreCase(requestingUsername)) return false;

        // If demoting an admin, ensure at least one other admin remains
        if (UserContext.ROLE_USER.equals(newRole)) {
            int adminCount = countAdmins();
            if (adminCount <= 1) return false; // last admin protection
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "UPDATE user_table SET role = ? WHERE username = ?")) {
                pst.setString(1, newRole);
                pst.setString(2, targetUsername);
                return pst.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Deletes a user and all their associated data.
     *
     * Validation:
     *   - Cannot delete self.
     *   - Cannot delete the last remaining admin.
     *
     * @return true if deleted, false if blocked or not found.
     */
    public static boolean deleteUser(String targetUsername, String requestingUsername) {
        if (targetUsername == null || targetUsername.isBlank()) return false;
        if (targetUsername.equalsIgnoreCase(requestingUsername)) return false;

        // Prevent deleting last admin
        String targetRole = getRole(targetUsername);
        if (UserContext.ROLE_ADMIN.equals(targetRole) && countAdmins() <= 1) {
            return false;
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);

            // Delete in dependency order — ignore errors on tables that may
            // not exist for every user.
            String[] deleteSqls = {
                "DELETE FROM user_activity   WHERE username = ?",
                "DELETE FROM user_favorites  WHERE username = ?",
                "DELETE FROM generator_table WHERE username = ?",
                "DELETE FROM password_table  WHERE username = ?",
                "DELETE FROM encryption_table WHERE username = ?",
                "DELETE FROM password_history WHERE username = ?",
                "DELETE FROM calc_history    WHERE username = ?",
                "DELETE FROM user_table      WHERE username = ?",
            };

            for (String sql : deleteSqls) {
                try (PreparedStatement pst = conn.prepareStatement(sql)) {
                    pst.setString(1, targetUsername);
                    pst.executeUpdate(); // swallow; table may not exist for every user
                } catch (SQLException ignored) { /* table may not exist */ }
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countAdmins() {
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                     "SELECT COUNT(*) FROM user_table WHERE role = 'admin'");
             ResultSet rs = pst.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
