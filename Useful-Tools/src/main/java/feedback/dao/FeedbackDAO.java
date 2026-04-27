package feedback.dao;

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

    // ── Read methods (admin-only) ─────────────────────────────────────────────

    /**
     * Lists feedback rows newest first, with each row's per-feature breakdown
     * embedded under "features". Capped via limit/offset for pagination.
     *
     * @param limit   Page size; clamped to [1, 200].
     * @param offset  Row offset; clamped to >= 0.
     * @return Ordered list of feedback rows. Each row contains:
     *           id, username, overallRating, generalComment, submittedAt,
     *           features = [ { featureName, rating, comment }, ... ]
     */
    public static List<Map<String, Object>> listAll(int limit, int offset) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int safeLimit  = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);

            // First pass — fetch the parent feedback rows in the page.
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT id, username, overall_rating, general_comment, submitted_at "
                    + "FROM feedback "
                    + "ORDER BY id DESC "
                    + "LIMIT ? OFFSET ?;")) {

                pst.setInt(1, safeLimit);
                pst.setInt(2, safeOffset);

                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id",             rs.getLong("id"));
                        row.put("username",       rs.getString("username"));
                        row.put("overallRating",  rs.getInt("overall_rating"));
                        row.put("generalComment", rs.getString("general_comment"));
                        row.put("submittedAt",    rs.getString("submitted_at"));
                        row.put("features",       new ArrayList<Map<String, Object>>());
                        rows.add(row);
                    }
                }
            }

            if (rows.isEmpty()) return rows;

            // Second pass — fetch all per-feature rows for the page in one query
            // and merge them into the parent rows by id. Keeps the round-trips
            // bounded regardless of how many features each feedback row has.
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql =
                "SELECT feedback_id, feature_name, feature_rating, feature_comment "
                + "FROM feedback_features "
                + "WHERE feedback_id IN (" + placeholders + ") "
                + "ORDER BY id ASC;";

            Map<Long, List<Map<String, Object>>> byId = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> feats = (List<Map<String, Object>>) row.get("features");
                byId.put((Long) row.get("id"), feats);
            }

            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                int idx = 1;
                for (Map<String, Object> row : rows) {
                    pst.setLong(idx++, (Long) row.get("id"));
                }
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        long fid = rs.getLong("feedback_id");
                        Map<String, Object> feat = new LinkedHashMap<>();
                        feat.put("featureName", rs.getString("feature_name"));
                        int rating = rs.getInt("feature_rating");
                        feat.put("rating", rs.wasNull() ? null : rating);
                        feat.put("comment", rs.getString("feature_comment"));
                        List<Map<String, Object>> bucket = byId.get(fid);
                        if (bucket != null) bucket.add(feat);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return rows;
    }

    /** Total feedback row count (used for pagination metadata). */
    public static long count() {
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT COUNT(*) FROM feedback;");
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Aggregate roll-up for the admin dashboard — total count, average overall
     * rating, and a 1–5 distribution histogram. Cheap single-query pass.
     */
    public static Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        long total = 0;
        double avg = 0;
        int[] hist = new int[5]; // index 0 = rating 1, index 4 = rating 5

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT overall_rating, COUNT(*) AS c "
                    + "FROM feedback GROUP BY overall_rating;");
                 ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    int r = rs.getInt(1);
                    int c = rs.getInt(2);
                    if (r >= 1 && r <= 5) hist[r - 1] = c;
                    total += c;
                    avg   += (double) r * c;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        out.put("total", total);
        out.put("avgOverallRating", total > 0 ? avg / total : 0.0);
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(String.valueOf(i), hist[i - 1]);
        out.put("distribution", dist);
        return out;
    }
}
