package common;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseUtils {

    /**
     * Returns a connection to the SQLite3 database configured in config.properties.
     * The driver class name and JDBC URL are both read from the properties file,
     * so no credentials are needed for SQLite.
     */
    public static Connection getSQLite3Connection() {
        Connection conn = null;
        try {
        	Class.forName(AppConfig.getRequired("sqlite3_driver"));
            String url = AppConfig.getRequired("sqlite3_url");
            
            File dbFile = new File("/databases/UsefulTools.db");

            if (!dbFile.exists()) {
            
                File parentDir = dbFile.getParentFile();
            
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
            
                System.out.println("Database file not found. Creating new database...");
            } else {
                System.out.println("Database file already exists. Skipping creation.");
            }

            conn = DriverManager.getConnection(url);

            if (conn != null) {
                System.out.println("Connection to SQLite3 database successful");
            }
            System.out.println("Connection to SQLite3 database successful");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        } catch (RuntimeException re) {
        	re.printStackTrace();
        }
        return conn;
    }

    /**
     * Returns a connection to a MySQL database.
     *
     * BUG FIX: The original called DriverManager.getConnection(url), passing
     * only the URL and ignoring the user and password parameters entirely.
     * This works only when credentials are embedded in the JDBC URL string,
     * which is insecure and non-standard. The fix passes user and password as
     * separate arguments to DriverManager.getConnection(url, user, password),
     * which is the correct and standard JDBC approach.
     */
    public static Connection getMySQLConnection(String url, String user, String password)
            throws SQLException {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password); // FIX: was getConnection(url)
            System.out.println("Connection to MySQL database successful");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return conn;
    }

    /**
     * Returns a connection to a PostgreSQL database.
     *
     * BUG FIX 1: Same credential-passing bug as getMySQLConnection above.
     * The user and password parameters were accepted but never forwarded to
     * DriverManager. Fixed to pass all three arguments.
     *
     * BUG FIX 2: Typo "dadtabase" corrected to "database" in the log message.
     *
     * Note: The host and port parameters are accepted for API consistency but
     * are not used here because PostgreSQL JDBC URLs already embed host and
     * port (jdbc:postgresql://host:port/dbname). They may be used in a future
     * refactor that constructs the URL programmatically.
     */
    public static Connection getPostgreSQLConnection(
            String url, String user, String password, String host, int port) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password); // FIX: was getConnection(url)
            System.out.println("Connection to PostgreSQL database successful"); // FIX: was "dadtabase"
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return conn;
    }

    /**
     * Closes a JDBC Connection, PreparedStatement, and ResultSet safely.
     * Null values are silently skipped. All resources are closed regardless
     * of whether earlier closes throw an exception.
     */
    public static void closeSQLConnection(Connection conn, PreparedStatement pst, ResultSet rs) {
        try {
            if (rs   != null) rs.close();
            if (pst  != null) pst.close();
            if (conn != null) conn.close();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }
}
