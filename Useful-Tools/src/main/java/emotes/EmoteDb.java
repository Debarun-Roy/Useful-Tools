package emotes;

import java.sql.*;
import common.DatabaseUtils;

/** Connections are owned by callers; never changes the database journal mode. */
public final class EmoteDb {
    private EmoteDb() { }
    @FunctionalInterface public interface Factory { Connection open() throws SQLException; }
    @FunctionalInterface public interface Work<T> { T run() throws SQLException; }
    public static Connection open() throws SQLException {
        Connection c = DatabaseUtils.getSQLite3Connection(false);
        if (c == null) throw new SQLException("Database unavailable");
        try { configure(c); return c; }
        catch (SQLException e) { try { c.close(); } catch (SQLException close) { e.addSuppressed(close); } throw e; }
    }
    public static void configure(Connection c) throws SQLException {
        if (c == null || !c.getAutoCommit()) throw new SQLException("Connection unavailable or transaction already owned");
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout=3000"); s.execute("PRAGMA foreign_keys=ON");
            try (ResultSet rs = s.executeQuery("PRAGMA foreign_keys")) {
                if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("Foreign keys unavailable");
            }
        }
    }
    public static <T> T transaction(Connection c, boolean write, Work<T> work) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Caller transaction is not supported");
        try (Statement s = c.createStatement()) {
            s.execute(write ? "BEGIN IMMEDIATE" : "BEGIN");
            try {
                T result = work.run(); s.execute("COMMIT"); return result;
            } catch (SQLException | RuntimeException | Error e) {
                try { s.execute("ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
