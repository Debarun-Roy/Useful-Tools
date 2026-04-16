package passwordgenerator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import common.DatabaseUtils;

/**
 * DAO for password change history — used to prevent reuse of recent passwords.
 *
 * Required schema (run once before Sprint 6 deployment):
 *   CREATE TABLE IF NOT EXISTS password_history (
 *     id              INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username        TEXT    NOT NULL,
 *     hashed_password TEXT    NOT NULL,
 *     created_at      TEXT    NOT NULL
 *   );
 *
 * Why BCrypt hashes (not plaintext) are stored:
 * Storing plaintext would be a critical security vulnerability. BCrypt uses
 * per-hash random salts, so each call to HashingUtils.generateHashedPassword()
 * produces a different hash string for the same password. However, BCrypt.checkpw()
 * correctly compares a plaintext candidate against any of these hashes.
 * This means checking for password reuse is done via checkpw(), not string equality.
 *
 * Seeding strategy:
 * The initial password hash is added when the user registers, so the user
 * cannot immediately change their password back to the registration password.
 * Subsequent changes are added by UpdatePasswordController.
 */
public class PasswordHistoryDAO {

    public static void ensurePasswordHistorySchema(Connection conn) throws SQLException {
        try {
             PreparedStatement pst = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS password_history ("
                     + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                     + "username TEXT NOT NULL, "
                     + "hashed_password TEXT NOT NULL, "
                     + "created_at TEXT NOT NULL);");
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the most recent {@code limit} hashed passwords for the given
     * user, ordered most-recent-first (highest id first).
     *
     * @param username The user's username.
     * @param limit    Maximum number of history entries to return (e.g. 5).
     * @return List of BCrypt hash strings, newest first. Empty if none exist
     *         or if the table does not yet exist.
     */
    public static List<String> getRecentHashes(String username, int limit) {
        List<String> hashes = new ArrayList<>();
        try {
            Connection conn = DatabaseUtils.getSQLite3Connection();
            ensurePasswordHistorySchema(conn);
            PreparedStatement pst = conn.prepareStatement(
                     "SELECT hashed_password FROM password_history "
                     + "WHERE username = ? ORDER BY id DESC LIMIT ?"); 
            pst.setString(1, username);
            pst.setInt(2, limit);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    hashes.add(rs.getString("hashed_password"));
                }
            }
        } catch (SQLException e) {
            // Return empty list if table doesn't exist yet (pre-migration).
            e.printStackTrace();
        }
        return hashes;
    }

    /**
     * Records a new hashed password in the history for the given user.
     * Called by RegistrationController (initial seed) and
     * UpdatePasswordController (on every successful update).
     *
     * @param username       The user's username.
     * @param hashedPassword A BCrypt hash of the new password.
     */
    public static void addToHistory(String username, String hashedPassword) {
        try {
            Connection conn = DatabaseUtils.getSQLite3Connection();
            ensurePasswordHistorySchema(conn);
            PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO password_history (username, hashed_password, created_at) "
                    + "VALUES (?, ?, ?);");
            pst.setString(1, username);
            pst.setString(2, hashedPassword);
            pst.setString(3, Instant.now().toString());
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}