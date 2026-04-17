package common;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * FIX: Environment-variable override for the SQLite database path.
 *
 * WHY: The config.properties hardcodes the path as
 *   sqlite3_url=jdbc:sqlite:/databases/UsefulTools.db
 * This works fine for the Docker/Railway deployment but makes it impossible
 * to override the path via Railway's environment variables panel without
 * rebuilding the image.
 *
 * The resolution order is now:
 *   1. SQLITE_DB_URL environment variable (highest priority — Railway secrets)
 *   2. SQLITE_DB_PATH environment variable (convenience — path only, jdbc: prefix added)
 *   3. config.properties sqlite3_url       (existing default — unchanged behaviour)
 *
 * Examples for Railway environment variable panel:
 *   SQLITE_DB_URL  = jdbc:sqlite:/databases/UsefulTools.db
 *   SQLITE_DB_PATH = /databases/UsefulTools.db
 *
 * The file-creation and parent-directory logic is unchanged.
 */
public class DatabaseUtils {

    /**
     * Returns a connection to the SQLite3 database.
     * Resolution order: SQLITE_DB_URL env → SQLITE_DB_PATH env → config.properties.
     */
    public static Connection getSQLite3Connection() {
        Connection conn = null;
        try {
            Class.forName(AppConfig.getRequired("sqlite3_driver"));

            String url = resolveJdbcUrl();

            // Create parent directory and file if they don't exist yet.
            String pathPart = url.replaceFirst("^jdbc:sqlite:", "");
            File dbFile = new File(pathPart);
            if (!dbFile.exists()) {
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                System.out.println("Database file not found. Creating new database at: " + pathPart);
            } else {
                System.out.println("Database file already exists at: " + pathPart);
            }

            conn = DriverManager.getConnection(url);

            if (conn != null) {
                System.out.println("Connection to SQLite3 database successful");
            }

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
     * Determines the JDBC URL using the environment-variable override chain.
     *
     * Resolution order:
     *   1. SQLITE_DB_URL  — full JDBC URL
     *   2. SQLITE_DB_PATH — file path only; jdbc:sqlite: prefix is prepended
     *   3. config.properties sqlite3_url
     */
    private static String resolveJdbcUrl() {
        // Tier 1: full JDBC URL from env
        String envUrl = System.getenv("SQLITE_DB_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            System.out.println("[DatabaseUtils] Using SQLITE_DB_URL env var: " + envUrl);
            return envUrl.trim();
        }

        // Tier 2: file path from env — add the jdbc: prefix
        String envPath = System.getenv("SQLITE_DB_PATH");
        if (envPath != null && !envPath.isBlank()) {
            String jdbcUrl = "jdbc:sqlite:" + envPath.trim();
            System.out.println("[DatabaseUtils] Using SQLITE_DB_PATH env var: " + jdbcUrl);
            return jdbcUrl;
        }

        // Tier 3: config.properties (existing behaviour — unchanged)
        return AppConfig.getRequired("sqlite3_url");
    }

    /**
     * Returns a connection to a MySQL database.
     *
     * BUG FIX: The original called DriverManager.getConnection(url), passing
     * only the URL and ignoring the user and password parameters entirely.
     * Fixed to pass user and password as separate arguments.
     */
    public static Connection getMySQLConnection(String url, String user, String password)
            throws SQLException {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connection to MySQL database successful");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return conn;
    }

    /**
     * Returns a connection to a PostgreSQL database.
     *
     * BUG FIX 1: credentials were never forwarded to DriverManager.
     * BUG FIX 2: typo "dadtabase" → "database" in log message.
     */
    public static Connection getPostgreSQLConnection(
            String url, String user, String password, String host, int port) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connection to PostgreSQL database successful");
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return conn;
    }

    /**
     * Closes a JDBC Connection, PreparedStatement, and ResultSet safely.
     * Null values are silently skipped.
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
