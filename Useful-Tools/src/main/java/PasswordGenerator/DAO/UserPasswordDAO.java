package passwordgenerator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import common.DatabaseUtils;
import common.UnifiedLogger;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.DecryptionUtils;

/**
 * FIX 1 (package): Renamed from PasswordGenerator.DAO to passwordgenerator.dao.
 *
 * FIX 2 (JDBC indices): All pst.setXxx(0, ...) corrected to start at 1.
 *
 * FIX 3 (saveGeneratedPasswordDetails SQL): The original SQL listed 7 column
 *   names but only had 6 '?' placeholders. Added the missing 7th placeholder.
 *
 * FIX 4 (CRITICAL — encryption_table primary key):
 *   The original saveEncryptionDetails used ON CONFLICT (username), meaning
 *   there was ONE encryption row per user. Every time a new platform password
 *   was saved, a new RSA key pair was generated and the old private key was
 *   overwritten. This made every previously-saved platform's password permanently
 *   undecryptable — the private key needed to decrypt it no longer existed.
 *
 *   Fix: encryption_table now uses a composite primary key (username, platform).
 *   Each (user, platform) pair has its own RSA key pair. Re-saving a platform
 *   updates only that platform's key, leaving all others intact.
 *
 *   SCHEMA CHANGE REQUIRED — run this SQL once to recreate the table:
 *     DROP TABLE IF EXISTS encryption_table;
 *     CREATE TABLE encryption_table (
 *       username         TEXT NOT NULL,
 *       platform         TEXT NOT NULL,
 *       encrypted_password TEXT,
 *       private_key      TEXT,
 *       created_date     TEXT,
 *       PRIMARY KEY (username, platform)
 *     );
 *
 * FIX 5 (CRITICAL — wrong column in fetch queries):
 *   Both fetchUserPasswords and fetchUserPlatformPassword selected
 *   et.encrypted_password (from encryption_table — always the last-saved row)
 *   instead of pt.encrypted_password (from password_table — per-platform).
 *   Fixed to use pt.encrypted_password and et.private_key with the corrected
 *   join condition ON (et.username = pt.username AND et.platform = pt.platform).
 *
 * FIX 6 (fetchUserPasswords SQL column alias): "pt.platorm" typo corrected.
 *
 * FIX 7 (resource management): Connection/PreparedStatement/ResultSet always
 *   closed in finally blocks to prevent connection leaks.
 *
 * FIX 8 (logger): Single class-level logger instance instead of per-method.
 */
public class UserPasswordDAO {

    private static final Logger logger = new UnifiedLogger().writeLogs("dao");

    public static void saveGeneratedPasswordDetails(PasswordModel pass) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            // FIX: Was 6 placeholders for 7 columns — added the 7th '?'
            String sql = "INSERT INTO generator_table "
                    + "(username, password, number_count, special_character_count, "
                    + "lowercase_count, uppercase_count, generated_timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, pass.getUsername());
            pst.setString(2, pass.getPassword());
            pst.setInt(3, pass.getNumberCount());
            pst.setInt(4, pass.getSpecialCharacterCount());
            pst.setInt(5, pass.getLowercaseCount());
            pst.setInt(6, pass.getUppercaseCount());
            pst.setTimestamp(7, pass.getGeneratedTimestamp());
            pst.executeUpdate();
            logger.info("Generated password stored in database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }

    public static void saveUserPasswordDetails(PasswordModel pass) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "INSERT INTO password_table "
                    + "(username, platform, encrypted_password, hashed_password, created_date) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (username, platform) DO UPDATE SET "
                    + "encrypted_password = excluded.encrypted_password, "
                    + "hashed_password = excluded.hashed_password, "
                    + "created_date = excluded.created_date;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, pass.getUsername());
            pst.setString(2, pass.getPlatform());
            pst.setString(3, pass.getEncryptedPassword());
            pst.setString(4, pass.getHashedPassword());
            pst.setTimestamp(5, Timestamp.from(pass.getCreatedDate()));
            pst.executeUpdate();
            logger.info("User password details stored in database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }

    /**
     * FIX 5: Fetch now uses:
     *   - pt.encrypted_password (per-platform ciphertext, not the last-saved one)
     *   - et.private_key joined on BOTH username AND platform so each platform
     *     gets its own private key
     */
    public static LinkedHashMap<Integer, LinkedHashMap<String, String>> fetchUserPasswords(String username) {
        LinkedHashMap<Integer, LinkedHashMap<String, String>> lhmap = new LinkedHashMap<>();
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "SELECT pt.platform, pt.encrypted_password, et.private_key "
                    + "FROM password_table pt "
                    + "INNER JOIN encryption_table et "
                    + "    ON et.username = pt.username AND et.platform = pt.platform "
                    + "WHERE pt.username = ?;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            rs = pst.executeQuery();
            logger.info("Fetching user password details...");
            int i = 0;
            while (rs.next()) {
                i++;
                LinkedHashMap<String, String> map = new LinkedHashMap<>();
                map.put("platform", rs.getString("platform"));
                String encryptedPassword = rs.getString("encrypted_password");
                String privateKey = rs.getString("private_key");
                String decryptedPassword = DecryptionUtils.decryptEncryptedPassword(encryptedPassword, privateKey);
                map.put("decrypted_password", decryptedPassword);
                lhmap.put(i, map);
            }
            logger.info("User password details fetched");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return lhmap;
    }

    /**
     * FIX 5: Uses pt.encrypted_password and joins encryption_table on both
     * username AND platform to get the correct per-platform private key.
     */
    public static LinkedHashMap<String, String> fetchUserPlatformPassword(String username, String platform) {
        LinkedHashMap<String, String> lhmap = new LinkedHashMap<>();
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "SELECT pt.encrypted_password, et.private_key "
                    + "FROM password_table pt "
                    + "INNER JOIN encryption_table et "
                    + "    ON et.username = pt.username AND et.platform = pt.platform "
                    + "WHERE pt.username = ? AND pt.platform = ?;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            pst.setString(2, platform);
            rs = pst.executeQuery();
            logger.info("Fetching user-platform password details...");
            if (rs.next()) {
                String encryptedPassword = rs.getString("encrypted_password");
                String privateKey = rs.getString("private_key");
                String decryptedPassword = DecryptionUtils.decryptEncryptedPassword(encryptedPassword, privateKey);
                lhmap.put("decrypted_password", decryptedPassword);
            }
            logger.info("User-platform password details fetched");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return lhmap;
    }

    /**
     * FIX 4: ON CONFLICT now targets (username, platform) — each platform gets
     * its own key pair. Re-saving a platform replaces only that platform's key.
     * All other platforms' keys remain intact and their passwords remain decryptable.
     */
    public static void saveEncryptionDetails(PasswordModel pass) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "INSERT INTO encryption_table "
                    + "(username, platform, encrypted_password, private_key, created_date) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (username, platform) DO UPDATE SET "
                    + "encrypted_password = excluded.encrypted_password, "
                    + "private_key = excluded.private_key, "
                    + "created_date = excluded.created_date;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, pass.getUsername());
            pst.setString(2, pass.getPlatform());
            pst.setString(3, pass.getEncryptedPassword());
            pst.setString(4, pass.getPrivateKey());
            pst.setTimestamp(5, Timestamp.from(pass.getCreatedDate()));
            pst.executeUpdate();
            logger.info("Encryption details stored in database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }

    /**
     * Returns paginated generated-password history from generator_table.
     *
     * Expected schema (already used by saveGeneratedPasswordDetails):
     *   CREATE TABLE IF NOT EXISTS generator_table (
     *     id                       INTEGER PRIMARY KEY AUTOINCREMENT,
     *     username                 TEXT NOT NULL,
     *     password                 TEXT NOT NULL,
     *     number_count             INTEGER,
     *     special_character_count  INTEGER,
     *     lowercase_count          INTEGER,
     *     uppercase_count          INTEGER,
     *     generated_timestamp      TEXT NOT NULL
     *   );
     */
    public static LinkedHashMap<String, Object> fetchGeneratedPasswordHistory(
            String username, int page, int size) {

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int offset = page * size;

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {

            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT COUNT(*) FROM generator_table WHERE username = ?")) {
                pst.setString(1, username);
                try (ResultSet rs = pst.executeQuery()) {
                    result.put("total", rs.next() ? rs.getLong(1) : 0L);
                }
            }

            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT id, password, number_count, special_character_count, "
                    + "lowercase_count, uppercase_count, generated_timestamp "
                    + "FROM generator_table WHERE username = ? "
                    + "ORDER BY generated_timestamp DESC, id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id",                    rs.getLong("id"));
                        entry.put("password",              rs.getString("password"));
                        entry.put("numberCount",           rs.getInt("number_count"));
                        entry.put("specialCharacterCount", rs.getInt("special_character_count"));
                        entry.put("lowercaseCount",        rs.getInt("lowercase_count"));
                        entry.put("uppercaseCount",        rs.getInt("uppercase_count"));
                        entry.put("generatedAt",           rs.getString("generated_timestamp"));
                        entries.add(entry);
                    }
                }
            }

            result.put("entries", entries);

        } catch (SQLException e) {
            e.printStackTrace();
            result.put("total", 0L);
            result.put("entries", new ArrayList<>());
        }

        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * Returns decrypted vault entries for export.
     *
     * The export payload contains the user's platform names and plaintext
     * passwords before the controller encrypts the JSON document for download.
     */
    public static ArrayList<LinkedHashMap<String, String>> fetchVaultEntriesForExport(String username) {
        ArrayList<LinkedHashMap<String, String>> entries = new ArrayList<>();

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                     "SELECT pt.platform, pt.encrypted_password, et.private_key "
                     + "FROM password_table pt "
                     + "INNER JOIN encryption_table et "
                     + "    ON et.username = pt.username AND et.platform = pt.platform "
                     + "WHERE pt.username = ? "
                     + "ORDER BY pt.platform COLLATE NOCASE ASC")) {

            pst.setString(1, username);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    LinkedHashMap<String, String> entry = new LinkedHashMap<>();
                    entry.put("platform", rs.getString("platform"));
                    entry.put(
                            "password",
                            DecryptionUtils.decryptEncryptedPassword(
                                    rs.getString("encrypted_password"),
                                    rs.getString("private_key")));
                    entries.add(entry);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return entries;
    }

    /**
     * Deletes the vault entry for the given (username, platform) pair from both
     * encryption_table and password_table.
     *
     * Deletion order: encryption_table first, then password_table.
     * SQLite does not enforce FK constraints by default, but this order is
     * semantically correct — remove the key before the ciphertext.
     *
     * @return true if at least one row was deleted from password_table, false if
     *         no matching entry was found.
     */
    public static boolean deleteVaultEntry(String username, String platform) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("Deleting vault entry for user=" + username + " platform=" + platform);

            pst = conn.prepareStatement(
                    "DELETE FROM encryption_table WHERE username = ? AND platform = ?;");
            pst.setString(1, username);
            pst.setString(2, platform);
            pst.executeUpdate();
            pst.close();

            pst = conn.prepareStatement(
                    "DELETE FROM password_table WHERE username = ? AND platform = ?;");
            pst.setString(1, username);
            pst.setString(2, platform);
            int rowsAffected = pst.executeUpdate();

            logger.info("Vault entry deletion result: " + rowsAffected + " row(s) affected");
            return rowsAffected > 0;
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            return false;
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }
}
