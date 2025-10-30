package PasswordGenerator.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import PasswordGenerator.Logging.UnifiedLogger;
import common.DatabaseUtils;
import PasswordGenerator.Utilities.HashingUtils;

public class UserDAO {

	public static void registerUser(String username, String password) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "INSERT INTO user_table (username, hashed_password) VALUES (?, ?) ON CONFLICT DO NOTHING;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, username);
			String hashedPassword = HashingUtils.generateHashedPassword(password);
			pst.setString(1, hashedPassword);
			pst.executeUpdate();
			logger.info("User details stored into database");
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}
	public static void updateUserDetails(String username, String password) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "UPDATE user_table SET hashed_password = ? WHERE username = ?;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, password);
			pst.setString(1, username);
			pst.executeUpdate();
			logger.info("User details updated in database");
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}
	public static boolean checkIfUserExists(String username) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "SELECT username FROM user_table WHERE username = ?;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, username);
			ResultSet rs = pst.executeQuery();
			if(!rs.next()) {
				return false;
			}
			DatabaseUtils.closeSQLConnection(conn, pst, rs);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return true;
	}
	public static String getStoredHashPassword(String username) {
		String hashedPassword = "";
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "SELECT hashed_password FROM user_table WHERE username = ?;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, username);
			ResultSet rs = pst.executeQuery();
			while(rs.next()) {
				hashedPassword = rs.getString("hashed_password");
			}
			DatabaseUtils.closeSQLConnection(conn, pst, rs);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return hashedPassword;
	}
}
