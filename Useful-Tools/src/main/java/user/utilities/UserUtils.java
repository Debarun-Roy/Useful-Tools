package user.utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import common.DatabaseUtils;
import common.UnifiedLogger;

/**
 * UserUtils — cascading deletion of per-user data.
 *
 * FIX: deleteUserData() previously always returned false and never touched
 * the database — "Delete My Data" had no real backend behind it. This class
 * is now the single source of truth for "which tables hold a row per user",
 * used by both:
 *
 *   - DeleteUserDataController ("Delete My Data" — keeps the account, wipes
 *     usage data)
 *   - RemoveAccountController  ("Remove My Account" — wipes usage data AND
 *     the user_table row itself)
 *
 * RoleDAO.deleteUser() (the admin "delete this user" action) also delegates
 * here now, instead of keeping its own separate — and, on audit, incomplete —
 * list of tables. Previously that list was missing ci_calculations,
 * emi_calculations, salary_calculations, tax_calculations, regex_patterns,
 * tool_metrics, and tool_recommendations, all added in later sprints after
 * that list was last touched. Having one list instead of three prevents that
 * kind of drift going forward.
 *
 * Tables intentionally NOT included:
 *   - expr_table       — legacy cache keyed by expression text, not username.
 *   - schema_templates — global/admin-seeded content, not per-user.
 *   - tool_toggles      — global admin configuration, not per-user.
 *   - user_table        — the account row itself; only deleteAccountAndData()
 *                          touches this, never deleteUserData().
 */
public class UserUtils {

    private static final Logger logger = new UnifiedLogger().writeLogs("dao");

    /**
     * Tables holding a row per username (simple "DELETE ... WHERE username = ?").
     * feedback/feedback_features are handled separately below because
     * feedback_features is keyed by feedback_id, not username.
     */
    private static final String[] USER_DATA_TABLES = {
        "calc_history",
        "emi_calculations",
        "tax_calculations",
        "ci_calculations",
        "salary_calculations",
        "password_table",
        "encryption_table",
        "generator_table",
        "password_history",
        "user_activity",
        "user_favorites",
        "tool_metrics",
        "regex_patterns",
        "tool_recommendations",
    };

    private UserUtils() { }

    /**
     * Deletes every row of usage data associated with {@code username} —
     * calculation history, vault entries + encryption keys, generated-password
     * history, password-change history, activity log, favorites, metrics,
     * saved regex patterns, tool recommendations, and submitted feedback.
     *
     * The user_table row (login credentials) is left untouched, so the
     * account keeps working afterwards with a clean slate. This is what
     * backs "Delete My Data".
     *
     * Missing legacy tables are detected before deletion. Any actual SQL
     * failure rolls back the complete wipe; account removal shares the transaction.
     *
     * @return true only after commit; false on unavailable connection or SQL failure.
     */
    public static boolean deleteUserData(String username) {
        return deleteWithConnection(username, false);
    }

    public static boolean deleteAccountAndData(String username) {
        return deleteWithConnection(username, true);
    }

    private static boolean deleteWithConnection(String username, boolean removeAccount) {
        if (username == null || username.isBlank()) return false;
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            if (conn == null) return false;
            deleteAtomically(conn, username, removeAccount);
            return true;
        } catch (SQLException | RuntimeException e) {
            // No account identifiers, SQL text or personal records in the failure log.
            logger.warning("Account data deletion did not complete normally; no success acknowledged.");
            return false;
        }
    }

    /** Connection is owned by the caller; all existing data and account deletes share one transaction. */
    static void deleteAtomically(Connection conn, String username, boolean removeAccount) throws SQLException {
        if (conn == null || username == null || username.isBlank()) throw new SQLException("Connection and nonblank username required");
        if (!conn.getAutoCommit()) throw new SQLException("Deletion requires an unused transaction");
        conn.setAutoCommit(false);
        boolean transactionEnded = false;
        Throwable primaryFailure = null;
        try {
            verifyAccount(conn, username, removeAccount);
            for (String table : USER_DATA_TABLES) {
                if (!tableExists(conn, table)) continue;
                try (PreparedStatement statement = conn.prepareStatement("DELETE FROM " + table + " WHERE username = ?")) {
                    statement.setString(1, username);
                    statement.executeUpdate();
                }
            }
            if (tableExists(conn, "feedback")) {
                if (tableExists(conn, "feedback_features")) {
                    try (PreparedStatement statement = conn.prepareStatement(
                            "DELETE FROM feedback_features WHERE feedback_id IN (SELECT id FROM feedback WHERE username = ?)")) {
                        statement.setString(1, username);
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = conn.prepareStatement("DELETE FROM feedback WHERE username = ?")) {
                    statement.setString(1, username);
                    statement.executeUpdate();
                }
            }
            if (removeAccount) {
                try (PreparedStatement statement = conn.prepareStatement("DELETE FROM user_table WHERE username = ?")) {
                    statement.setString(1, username);
                    if (statement.executeUpdate() != 1) throw new SQLException("Account identity is missing or ambiguous");
                }
            }
            conn.commit();
            transactionEnded = true;
        } catch (SQLException | RuntimeException failure) {
            primaryFailure = failure;
            try { conn.rollback(); transactionEnded = true; } catch (SQLException | RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        } finally {
            // Never implicitly commit a failed transaction if rollback itself failed.
            if (transactionEnded) {
                try { conn.setAutoCommit(true); }
                catch (SQLException | RuntimeException cleanupFailure) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                    else throw cleanupFailure;
                }
            }
        }
    }

    // Recheck identity and the existing last-admin policy in the deletion transaction.
    // Earlier controller checks provide friendly errors but cannot serialize races.
    private static void verifyAccount(Connection conn, String username, boolean removeAccount) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("SELECT COUNT(*) FROM user_table WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) throw new SQLException("Account identity is missing or ambiguous");
            }
        }
        if (!removeAccount) return;
        boolean hasRole = false;
        try (PreparedStatement statement = conn.prepareStatement("PRAGMA table_info(user_table)");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) if ("role".equalsIgnoreCase(rows.getString("name"))) hasRole = true;
        }
        if (hasRole) {
            try (PreparedStatement statement = conn.prepareStatement(
                    "SELECT 1 FROM user_table WHERE username = ? AND role = 'admin' AND (SELECT COUNT(*) FROM user_table WHERE role = 'admin') <= 1")) {
                statement.setString(1, username);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) throw new SQLException("Last admin removal is not permitted");
                }
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? COLLATE NOCASE")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }
}
