package PasswordGenerator.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import PasswordGenerator.Models.PasswordModel;
import common.DatabaseUtils;
import PasswordGenerator.Utilities.DecryptionUtils;
import PasswordGenerator.Logging.UnifiedLogger;

public class UserPasswordDAO {
	
	public static void saveGeneratedPasswordDetails(PasswordModel pass) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "INSERT INTO generator_table (username, password, number_count, special_character_count, lowercase_count, uppercase_count, generated_timestamp) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, pass.getUsername());
			pst.setString(1, pass.getPassword());
			pst.setInt(2, pass.getNumberCount());
			pst.setInt(3, pass.getSpecialCharactercount());
			pst.setInt(4, pass.getLowercaseCount());
			pst.setInt(5, pass.getUppercaseCount());
			pst.setTimestamp(6, pass.getGeneratedTimestamp());
			pst.executeUpdate();
			logger.info("Generated password stored in database");
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}

	public static void saveUserPasswordDetails(PasswordModel pass) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "INSERT INTO password_table (username, platform, encrypted_password, hashed_password, created_date) VALUES(?, ?, ?, ?) ON CONFLICT (username, platform) DO UPDATE SET encrypted_password = excluded.encrypted_password, hashed_password = excluded.hashed_password, created_date = excluded.created_date;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, pass.getUsername());
			pst.setString(1, pass.getPlatform());
			pst.setNString(2, pass.getEncryptedPassword());
			pst.setNString(3, pass.getHashedPassword());
			pst.setTimestamp(4, Timestamp.from(pass.getCreatedDate()));
			pst.executeUpdate();
			logger.info("User password details stored in database");
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}
	public static LinkedHashMap<Integer, LinkedHashMap<String, String>> fetchUserPasswords(String username) {
		
		LinkedHashMap<Integer, LinkedHashMap<String, String>> lhmap = new LinkedHashMap<>();
		String encryptedPassword = "";
		String privateKey = "";
		String decryptedPassword = "";
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "SELECT pt.platorm, et.encrypted_password, et.private_key FROM password_table pt INNER JOIN encryption_table et ON et.username = pt.username WHERE pt.username = ?;";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, username);
			ResultSet rs = pst.executeQuery();
			logger.info("Fetching user password details..");
			int i=0;
			while(rs.next()) {
				i += 1;
				LinkedHashMap<String, String> map = new LinkedHashMap<>();
				map.put("platform", rs.getString("platform"));
				encryptedPassword = rs.getString("encrypted_password");
				privateKey = rs.getString("private_key");
				decryptedPassword = DecryptionUtils.decryptEncryptedPassword(encryptedPassword, privateKey);
				map.put("decrypted_password", decryptedPassword);
				lhmap.put(i, map);
			}
			logger.info("User password details fetched");
			DatabaseUtils.closeSQLConnection(conn, pst, rs);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return lhmap;
	}
	public static LinkedHashMap<String, String> fetchUserPlatformPassword(String username, String platform) {
		LinkedHashMap<String, String> lhmap = new LinkedHashMap<>();
		String encryptedPassword = "";
		String privateKey = "";
		String decryptedPassword = "";
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "SELECT et.encrypted_password, et.private_key FROM password_table pt INNER JOIN encryption_table et ON pt.username = et.username WHERE pt.username = ? AND pt.platform = ?";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, username);
			pst.setString(1, platform);
			ResultSet rs = pst.executeQuery();
			logger.info("Fetching user password details..");
			while(rs.next()) {
				encryptedPassword = rs.getString("encrypted_password");
				privateKey = rs.getString("private_key");
				decryptedPassword = DecryptionUtils.decryptEncryptedPassword(encryptedPassword, privateKey);
				lhmap.put("decrypted_password", decryptedPassword);
			}
			logger.info("User-platform password details fetched");
			DatabaseUtils.closeSQLConnection(conn, pst, rs);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return lhmap;
	}
	public static void saveEncryptionDetails(PasswordModel pass) {
		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("dao");
			Connection conn = DatabaseUtils.getSQLite3Connection();
			logger.info("SQLite3 connection successful");
			String sql = "INSERT INTO encryption_table (username, encrypted_password, private_key, created_date) VALUES (?, ?, ?, ?) ON CONLICT ();";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(0, pass.getUsername());
			pst.setString(1, pass.getEncryptedPassword());
			pst.setString(2, pass.getPrivateKey());
			pst.setTimestamp(3, Timestamp.from(pass.getCreatedDate()));
			pst.executeUpdate();
			logger.info("Encryption details stored in database");
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}
}