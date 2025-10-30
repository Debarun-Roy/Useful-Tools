package common;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseUtils {
	public static Connection getSQLite3Connection(){
		Connection conn = null;
		try{
			Properties properties = new Properties();
			String root_path = Thread.currentThread().getContextClassLoader().getResource("").getPath();
			String properties_path = root_path + "config.properties";
			FileInputStream fis = new FileInputStream(properties_path);
			properties.load(fis);
			Class.forName(properties.getProperty("sqlite3_driver"));
			conn = DriverManager.getConnection(properties.getProperty("sqlite3_url"));
			System.out.println("Connection to SQLite3 database successful");
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		catch(FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		}
		catch(IOException ioe) {
			ioe.printStackTrace();
		}
		catch(ClassNotFoundException cnfe) {
			cnfe.printStackTrace();
		}
		return conn;
	}
	public static Connection getMySQLConnection(String url, String user, String password) throws SQLException {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
			System.out.println("Connection to MySQL database successful");
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return conn;
	}
	public static Connection getPostgreSQLConnection(String url, String user, String password, String host, int port) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
			System.out.println("Connection to PostgreSQL dadtabase successful");
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
		return conn;
	}
	public static void closeSQLConnection(Connection conn, PreparedStatement pst, ResultSet rs) {
		try {
			if(rs != null) {
				rs.close();
			}
			if(pst != null) {
				pst.close();
			}
			if(conn != null) {
				conn.close();
			}
		}
		catch(SQLException sqle) {
			sqle.printStackTrace();
		}
	}
}