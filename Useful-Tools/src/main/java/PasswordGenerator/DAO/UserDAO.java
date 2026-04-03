package passwordgenerator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.logging.Logger;

import common.DatabaseUtils;
import common.UnifiedLogger;
import passwordgenerator.utilities.HashingUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.DAO to passwordgenerator.dao.
 *
 * FIX (JDBC): All PreparedStatement parameter indices corrected from 0-based
 *   to 1-based.
 *
 * FIX (logger): Single class-level logger instance.
 *
 * Sprint 6 additions:
 *   - LockStatus inner class (value object for lockout state)
 *   - getLockStatus()      — read current failed_attempts and locked_until
 *   - recordFailedLogin()  — increment failed_attempts; lock if >= 5
 *   - resetFailedAttempts() — clear on successful login
 *
 * Required schema change (run once before Sprint 6):
 *   ALTER TABLE user_table ADD COLUMN failed_attempts INTEGER DEFAULT 0;
 *   ALTER TABLE user_table ADD COLUMN locked_until    TEXT;
 */
public class UserDAO {

    private static final Logger logger = new UnifiedLogger().writeLogs("dao");
    private static final String USER_CREATED_DATE_FALLBACK = "Unknown";

    private static void ensureUserProfileSchema(Connection conn) throws SQLException {
        boolean hasCreatedDate = false;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(user_table)")) {
            while (rs.next()) {
                if ("created_date".equalsIgnoreCase(rs.getString("name"))) {
                    hasCreatedDate = true;
                    break;
                }
            }
        }

        if (!hasCreatedDate) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE user_table ADD COLUMN created_date TEXT;");
                st.executeUpdate("UPDATE user_table SET created_date = CURRENT_TIMESTAMP "
                        + "WHERE created_date IS NULL OR TRIM(created_date) = '';");
            }
            logger.info("Added created_date column to user_table for profile support");
        }
    }
    
    // ── Lock status value object ──────────────────────────────────────────────

    /**
     * Snapshot of a user's account lock state at the time of the query.
     */
    public static final class LockStatus {
        /** True if the account is currently locked (lock has not expired). */
        public final boolean locked;
        /** Number of consecutive failed login attempts. */
        public final int failedAttempts;
        /**
         * ISO 8601 timestamp of when the lock expires, or null if not locked.
         * Only meaningful when {@code locked} is true.
         */
        public final String lockedUntil;

        public LockStatus(boolean locked, int failedAttempts, String lockedUntil) {
            this.locked        = locked;
            this.failedAttempts = failedAttempts;
            this.lockedUntil   = lockedUntil;
        }
    }

    // ── Existing methods (unchanged) ──────────────────────────────────────────

    public static void registerUser(String username, String password) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            ensureUserProfileSchema(conn);
            logger.info("SQLite3 connection successful");
            String sql = "INSERT INTO user_table (username, hashed_password, created_date) VALUES (?, ?, ?) "
                    + "ON CONFLICT DO NOTHING;";
            pst = conn.prepareStatement(sql);
            pst.setString(1, username);
            String hashedPassword = HashingUtils.generateHashedPassword(password);
            pst.setString(2, hashedPassword);
            pst.setString(3, Instant.now().toString());
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

    // ── Sprint 6: Account lockout methods ────────────────────────────────────

    /**
     * Returns the current lock status for the given user.
     *
     * Reads {@code failed_attempts} and {@code locked_until} from user_table.
     * If {@code locked_until} is in the future, returns {@code locked = true}.
     * If {@code locked_until} is in the past (expired), returns {@code locked = false}
     * — the caller (LoginController) clears the lock on the next successful login.
     *
     * Returns {@code LockStatus(false, 0, null)} safely if the columns do not
     * yet exist (pre-migration) — the COALESCE handles a missing column value,
     * and a caught SQLException handles a completely missing column.
     */
    public static LockStatus getLockStatus(String username) {
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            ensureUserProfileSchema(conn);
            pst = conn.prepareStatement(
                    "SELECT COALESCE(failed_attempts, 0), locked_until "
                    + "FROM user_table WHERE username = ?;");
            pst.setString(1, username);
            rs = pst.executeQuery();
            if (rs.next()) {
                int    failedAttempts = rs.getInt(1);
                String lockedUntil   = rs.getString(2);

                boolean locked = false;
                if (lockedUntil != null && !lockedUntil.isBlank()) {
                    try {
                        Instant lockEnd = Instant.parse(lockedUntil);
                        locked = Instant.now().isBefore(lockEnd);
                    } catch (Exception e) {
                        // Malformed ISO timestamp — treat as not locked.
                        locked = false;
                    }
                }
                return new LockStatus(locked, failedAttempts, locked ? lockedUntil : null);
            }
        } catch (SQLException e) {
            // Columns may not exist before schema migration.
            e.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return new LockStatus(false, 0, null);
    }

    /**
     * Records a failed login attempt for the given user.
     * Increments {@code failed_attempts}. If the count reaches 5, sets
     * {@code locked_until} to 15 minutes from now (ISO 8601 string).
     *
     * @param username The username that failed to authenticate.
     */
    public static void recordFailedLogin(String username) {
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            ensureUserProfileSchema(conn);
            
            // Read current count.
            pst = conn.prepareStatement(
                    "SELECT COALESCE(failed_attempts, 0) FROM user_table WHERE username = ?;");
            pst.setString(1, username);
            rs = pst.executeQuery();
            int currentCount = rs.next() ? rs.getInt(1) : 0;
            rs.close(); pst.close();

            int    newCount    = currentCount + 1;
            String lockedUntil = (newCount >= 5)
                    ? Instant.now().plusSeconds(900).toString() // 15 minutes
                    : null;

            pst = conn.prepareStatement(
                    "UPDATE user_table SET failed_attempts = ?, locked_until = ? "
                    + "WHERE username = ?;");
            pst.setInt(1, newCount);
            pst.setString(2, lockedUntil);
            pst.setString(3, username);
            pst.executeUpdate();

            logger.info("Failed login recorded for " + username
                    + " (attempt " + newCount + ")"
                    + (lockedUntil != null ? " — account locked until " + lockedUntil : ""));

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
    }

    /**
     * Clears the failed attempt counter and any active lock for the given user.
     * Called by LoginController after a successful BCrypt verification.
     *
     * @param username The username that authenticated successfully.
     */
    public static void resetFailedAttempts(String username) {
        Connection conn = null;
        PreparedStatement pst = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            ensureUserProfileSchema(conn);
            
            pst = conn.prepareStatement(
                    "UPDATE user_table SET failed_attempts = 0, locked_until = NULL "
                    + "WHERE username = ?;");
            pst.setString(1, username);
            pst.executeUpdate();
            logger.info("Failed attempt counter reset for " + username);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, null);
        }
    }
    
    public static String getAccountCreatedDate(String username) {
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            conn = DatabaseUtils.getSQLite3Connection();
            ensureUserProfileSchema(conn);
            pst = conn.prepareStatement(
                    "SELECT created_date FROM user_table WHERE username = ?;");
            pst.setString(1, username);
            rs = pst.executeQuery();
            if (rs.next()) {
                String createdDate = rs.getString("created_date");
                return (createdDate == null || createdDate.isBlank())
                        ? USER_CREATED_DATE_FALLBACK
                        : createdDate;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DatabaseUtils.closeSQLConnection(conn, pst, rs);
        }
        return USER_CREATED_DATE_FALLBACK;
    }
}
