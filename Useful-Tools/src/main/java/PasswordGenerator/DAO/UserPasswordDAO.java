package passwordgenerator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import common.DatabaseUtils;
import common.UnifiedLogger;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.DecryptionUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.DAO to passwordgenerator.dao.
 *
 * FIX (JDBC indices): All pst.setXxx(0, ...) corrected to start at 1.
 *
 * FIX (saveGeneratedPasswordDetails SQL): The original SQL listed 7 column
 *   names but only had 6 '?' placeholders. Added the missing 7th placeholder.
 *
 * FIX (saveEncryptionDetails SQL): "ON CONLICT ()" was a typo causing a SQL
 *   syntax error. Corrected to a proper UPSERT clause.
 *
 * FIX (fetchUserPasswords SQL): Column alias "pt.platorm" corrected to
 *   "pt.platform".
 *
 * FIX (logger): Single class-level logger instance instead of per-method.
 *
 * FIX (resource management): Connection/PreparedStatement/ResultSet are now
 *   always closed in a finally block to prevent connection leaks.
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

    public static LinkedHashMap<Integer, LinkedHashMap<String, String>> fetchUserPasswords(String username) {
        LinkedHashMap<Integer, LinkedHashMap<String, String>> lhmap = new LinkedHashMap<>();
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            // FIX: "pt.platorm" typo corrected to "pt.platform"
            String sql = "SELECT pt.platform, et.encrypted_password, et.private_key "
                    + "FROM password_table pt "
                    + "INNER JOIN encryption_table et ON et.username = pt.username "
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

    public static LinkedHashMap<String, String> fetchUserPlatformPassword(String username, String platform) {
        LinkedHashMap<String, String> lhmap = new LinkedHashMap<>();
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "SELECT et.encrypted_password, et.private_key "
                    + "FROM password_table pt "
                    + "INNER JOIN encryption_table et ON pt.username = et.username "
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

    public static void saveEncryptionDetails(PasswordModel pass) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            // FIX: "ON CONLICT ()" was a typo and invalid SQL — replaced with
            // a proper UPSERT so re-saving encryption details doesn't fail.
            String sql = "INSERT INTO encryption_table "
                    + "(username, encrypted_password, private_key, created_date) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (username) DO UPDATE SET "
                    + "encrypted_password = excluded.encrypted_password, "
                    + "private_key = excluded.private_key, "
                    + "created_date = excluded.created_date;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, pass.getUsername());
            pst.setString(2, pass.getEncryptedPassword());
            pst.setString(3, pass.getPrivateKey());
            pst.setTimestamp(4, Timestamp.from(pass.getCreatedDate()));
            pst.executeUpdate();
            logger.info("Encryption details stored in database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }
}
