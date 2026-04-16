package feedback.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;

import common.DatabaseUtils;

/**
 * DAO for persisting user feedback.
 *
 * Tables are created automatically on first use (CREATE TABLE IF NOT EXISTS)
 * so no manual schema migration is required.
 *
 * Schema:
 *
 *   feedback (
 *     id             INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username       TEXT    NOT NULL,
 *     overall_rating INTEGER NOT NULL CHECK(overall_rating BETWEEN 1 AND 5),
 *     general_comment TEXT,
 *     submitted_at   TEXT    NOT NULL
 *   )
 *
 *   feedback_features (
 *     id              INTEGER PRIMARY KEY AUTOINCREMENT,
 *     feedback_id     INTEGER NOT NULL,
 *     feature_name    TEXT    NOT NULL,
 *     feature_rating  INTEGER CHECK(feature_rating BETWEEN 1 AND 5),
 *     feature_comment TEXT
 *   )
 */
public class FeedbackDAO {

    // ── Schema creation ───────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS feedback ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL, "
                + "overall_rating INTEGER NOT NULL, "
                + "general_comment TEXT, "
                + "submitted_at TEXT NOT NULL"
                + ");"
            );
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS feedback_features ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "feedback_id INTEGER NOT NULL, "
                + "feature_name TEXT NOT NULL, "
                + "feature_rating INTEGER, "
                + "feature_comment TEXT"
                + ");"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── Write methods ─────────────────────────────────────────────────────────

    /**
     * Persists the top-level feedback row and returns its auto-generated id.
     * Returns -1 if the insert fails.
     */
    public static long saveFeedback(String username, int overallRating, String generalComment) {
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO feedback (username, overall_rating, general_comment, submitted_at) "
                    + "VALUES (?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS)) {

                pst.setString(1, username);
                pst.setInt(2, overallRating);
                if (generalComment != null && !generalComment.isBlank()) {
                    pst.setString(3, generalComment.trim());
                } else {
                    pst.setNull(3, Types.VARCHAR);
                }
                pst.setString(4, Instant.now().toString());
                pst.executeUpdate();

                try (ResultSet rs = pst.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1L;
    }

    /**
     * Persists a single per-feature feedback row linked to the parent feedback row.
     *
     * @param feedbackId   The id of the parent feedback row.
     * @param featureName  The feature name (e.g. "Calculator").
     * @param rating       Optional 1–5 rating; null if the user didn't rate this feature.
     * @param comment      Optional text; null or blank if the user didn't comment.
     */
    public static void saveFeatureFeedback(
            long feedbackId, String featureName, Integer rating, String comment) {

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                "INSERT INTO feedback_features "
                + "(feedback_id, feature_name, feature_rating, feature_comment) "
                + "VALUES (?, ?, ?, ?);")) {

            pst.setLong(1, feedbackId);
            pst.setString(2, featureName);
            if (rating != null) {
                pst.setInt(3, rating);
            } else {
                pst.setNull(3, Types.INTEGER);
            }
            if (comment != null && !comment.isBlank()) {
                pst.setString(4, comment.trim());
            } else {
                pst.setNull(4, Types.VARCHAR);
            }
            pst.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
