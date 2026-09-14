package user.utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
     * Every DELETE is best-effort: a table that doesn't exist yet on this
     * deployment (pre-migration) is skipped rather than aborting the whole
     * wipe, matching the existing tolerance pattern in RoleDAO.
     *
     * @return true if the wipe ran to completion; false only if no database
     *         connection could be obtained at all.
     */
    public static boolean deleteUserData(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            if (conn == null) {
                logger.warning("deleteUserData: no DB connection available for " + username);
                return false;
            }

            for (String table : USER_DATA_TABLES) {
                try (PreparedStatement pst = conn.prepareStatement(
                        "DELETE FROM " + table + " WHERE username = ?;")) {
                    pst.setString(1, username);
                    pst.executeUpdate();
                } catch (SQLException e) {
                    // Table may not exist yet on this deployment — skip, don't abort.
                    logger.info("deleteUserData: skipped " + table + " for " + username
                            + " (" + e.getMessage() + ")");
                }
            }

            deleteFeedback(conn, username);

            logger.info("Deleted platform usage data for " + username);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes {@code username}'s feedback submissions. feedback_features rows
     * are children of feedback.id (not username directly), so the matching
     * feedback ids are looked up first and their children deleted before the
     * parent rows.
     */
    private static void deleteFeedback(Connection conn, String username) {
        List<Long> feedbackIds = new ArrayList<>();

        try (PreparedStatement pst = conn.prepareStatement(
                "SELECT id FROM feedback WHERE username = ?;")) {
            pst.setString(1, username);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    feedbackIds.add(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            return; // feedback table may not exist yet on this deployment.
        }

        for (Long feedbackId : feedbackIds) {
            try (PreparedStatement pst = conn.prepareStatement(
                    "DELETE FROM feedback_features WHERE feedback_id = ?;")) {
                pst.setLong(1, feedbackId);
                pst.executeUpdate();
            } catch (SQLException ignored) {
                // Best-effort — a missing child row is not an error here.
            }
        }

        try (PreparedStatement pst = conn.prepareStatement(
                "DELETE FROM feedback WHERE username = ?;")) {
            pst.setString(1, username);
            pst.executeUpdate();
        } catch (SQLException ignored) {
            // feedback table may not exist yet on this deployment.
        }
    }

    /**
     * Full account removal: wipes all usage data (see deleteUserData) and
     * then deletes the user_table row itself, so the account can no longer
     * log in. This is what backs "Remove My Account" and the admin-triggered
     * user deletion in RoleDAO.
     *
     * @return true if both the data wipe and the account-row deletion
     *         completed without a connection-level failure.
     */
    public static boolean deleteAccountAndData(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        boolean dataDeleted = deleteUserData(username);

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                     "DELETE FROM user_table WHERE username = ?;")) {
            pst.setString(1, username);
            pst.executeUpdate();
            logger.info("Deleted account row for " + username);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        return dataDeleted;
    }
}
