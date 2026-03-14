package passwordgenerator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import common.DatabaseUtils;
import common.UnifiedLogger;
import passwordgenerator.utilities.HashingUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.DAO to passwordgenerator.dao
 *   to follow Java's all-lowercase package naming convention.
 *
 * FIX (JDBC): All PreparedStatement parameter indices corrected from 0-based
 *   to 1-based. pst.setString(0, ...) throws SQLException; must start at 1.
 *
 * FIX (logger): Logger is now a class-level field instantiated once rather than
 *   creating a new UnifiedLogger instance on every single method call.
 *   Repeated calls to addHandler() on the same Logger would have attached
 *   duplicate handlers, printing each log line N times.
 *
 * FIX (import): Now imports common.UnifiedLogger — the duplicate class at
 *   PasswordGenerator.Logging.UnifiedLogger has been removed.
 */
public class UserDAO {

    private static final Logger logger = new UnifiedLogger().writeLogs("dao");

    public static void registerUser(String username, String password) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "INSERT INTO user_table (username, hashed_password) VALUES (?, ?) ON CONFLICT DO NOTHING;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            String hashedPassword = HashingUtils.generateHashedPassword(password);
            pst.setString(2, hashedPassword);
            pst.executeUpdate();
            logger.info("User details stored into database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }

    public static void updateUserDetails(String username, String password) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "UPDATE user_table SET hashed_password = ? WHERE username = ?;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, password);
            pst.setString(2, username);
            pst.executeUpdate();
            logger.info("User details updated in database");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }

    public static boolean checkIfUserExists(String username) {
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "SELECT username FROM user_table WHERE username = ?;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            rs = pst.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return false;
    }

    public static String getStoredHashPassword(String username) {
        String hashedPassword = "";
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            logger.info("SQLite3 connection successful");
            String sql = "SELECT hashed_password FROM user_table WHERE username = ?;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            rs = pst.executeQuery();
            if (rs.next()) {
                hashedPassword = rs.getString("hashed_password");
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return hashedPassword;
    }
}
