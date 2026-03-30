package calculator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import common.DatabaseUtils;
import common.UserContext;

/**
 * DAO for standard calculator expression results.
 *
 * Sprint 5 addition — calc_history table:
 * storeExpressionResult() now also writes to calc_history (with username
 * and timestamp) whenever a username is available via UserContext. The legacy
 * expr_table write is preserved for backward compatibility.
 *
 * Required schema (run once before Sprint 5 deployment):
 *   CREATE TABLE IF NOT EXISTS calc_history (
 *     id            INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username      TEXT    NOT NULL,
 *     expression    TEXT    NOT NULL,
 *     result        TEXT    NOT NULL,
 *     calculated_at TEXT    NOT NULL
 *   );
 */
public class ComputeDAO {

    public static void storeExpressionResult(String expr, String ans) {
        // Legacy write — preserves backward compatibility with expr_table.
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                     "INSERT INTO expr_table VALUES(?, ?);")) {
            pst.setString(1, expr);
            pst.setString(2, ans);
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // History write — only when an authenticated username is available.
        // UserContext.get() returns null for requests that bypass AuthFilter
        // (e.g. called from tests or non-authenticated paths), and null is
        // handled gracefully here.
        String username = UserContext.get();
        if (username != null && !username.isBlank()) {
            try (Connection conn = DatabaseUtils.getSQLite3Connection();
                 PreparedStatement pst = conn.prepareStatement(
                         "INSERT INTO calc_history (username, expression, result, calculated_at) "
                         + "VALUES (?, ?, ?, ?);")) {
                pst.setString(1, username);
                pst.setString(2, expr);
                pst.setString(3, ans);
                pst.setString(4, Instant.now().toString());
                pst.executeUpdate();
            } catch (SQLException e) {
                // Silently swallow — if calc_history table does not exist yet
                // (pre-migration), history is simply not recorded.
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a paginated slice of calculation history for the given user,
     * most recent first.
     *
     * @param username  The authenticated user.
     * @param page      Zero-indexed page number.
     * @param size      Number of entries per page.
     * @return Map containing: total (long), page (int), size (int),
     *         entries (List of {id, expression, result, calculatedAt}).
     */
    public static LinkedHashMap<String, Object> fetchHistory(
            String username, int page, int size) {

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int offset = page * size;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {

            // Total count for pagination controls.
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT COUNT(*) FROM calc_history WHERE username = ?")) {
                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    result.put("total", rs.next() ? rs.getLong(1) : 0L);
                }
            }

            // Page of entries.
            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT id, expression, result, calculated_at "
                    + "FROM calc_history WHERE username = ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id",           rs.getLong("id"));
                        entry.put("expression",   rs.getString("expression"));
                        entry.put("result",       rs.getString("result"));
                        entry.put("calculatedAt", rs.getString("calculated_at"));
                        entries.add(entry);
                    }
                }
            }
            result.put("entries", entries);

        } catch (SQLException e) {
            e.printStackTrace();
            result.put("total",   0L);
            result.put("entries", new ArrayList<>());
        }

        result.put("page", page);
        result.put("size", size);
        return result;
    }
}