package emotes;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Explicit lifecycle only: never invoked by a read request or deployment listener. */
public final class EmoteMigration {
    @FunctionalInterface interface Checkpoint { void hit(String point) throws SQLException; }
    static final Checkpoint NONE = point -> { };
    private EmoteMigration() { }
    public static void migrate(Connection c) throws SQLException { migrate(c, NONE); }
    static void migrate(Connection c, Checkpoint check) throws SQLException {
        EmoteDb.configure(c);
        String sql = new String(CatalogSeed.resource("/emotes/001-catalog.sql"), StandardCharsets.UTF_8);
        String checksum = CatalogSeed.sha(sql.getBytes(StandardCharsets.UTF_8));
        EmoteDb.transaction(c, true, () -> {
            try (Statement s=c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS user_table(username VARCHAR(500) COLLATE BINARY NOT NULL UNIQUE, hashed_password VARCHAR(5000) NOT NULL, failed_attempts INTEGER DEFAULT 0, locked_until TEXT, created_date TEXT, recovery_code_hash TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS emote_schema_migration(migration_id TEXT PRIMARY KEY NOT NULL, checksum TEXT NOT NULL CHECK(length(checksum)=64), applied_at TEXT NOT NULL)");
            }
            String keyChecksum = keyChecksum();
            boolean keyDone = applied(c,"001-account-key",keyChecksum);
            AccountKey.ensure(c); check.hit("account-key");
            if(!keyDone) record(c,"001-account-key",keyChecksum);
            if(!applied(c,"002-catalog",checksum)) {
                for(String part:sql.split("-- statement")) {
                    try(Statement s=c.createStatement()) { s.execute(part); }
                    check.hit("ddl");
                }
                check.hit("before-ledger"); record(c,"002-catalog",checksum);
            }
            verifyDefinitions(c,sql);
            try(Statement s=c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS tool_toggles(tool_path TEXT PRIMARY KEY,enabled INTEGER NOT NULL DEFAULT 1,updated_at TEXT NOT NULL)");
                s.execute("INSERT OR IGNORE INTO tool_toggles VALUES('/emote-utils',0,strftime('%Y-%m-%dT%H:%M:%fZ','now'))");
                try(ResultSet rs=s.executeQuery("PRAGMA foreign_key_check")) { if(rs.next()) throw new SQLException("Foreign key validation failed"); }
            }
            check.hit("before-commit"); return null;
        });
    }
    static void verifyInstalled(Connection c) throws SQLException {
        String sql=new String(CatalogSeed.resource("/emotes/001-catalog.sql"),StandardCharsets.UTF_8);
        if(!applied(c,"001-account-key",keyChecksum()) || !applied(c,"002-catalog",CatalogSeed.sha(sql.getBytes(StandardCharsets.UTF_8))) || !AccountKey.compatible(c))
            throw new SQLException("Required migration missing");
        verifyDefinitions(c,sql);
    }
    private static String keyChecksum() {
        return CatalogSeed.sha("binary-username-key-v1:no-repair;exact-utf8-text;no-control;reserved-guest;sqlite-parent-probe".getBytes(StandardCharsets.UTF_8));
    }
    private static void verifyDefinitions(Connection c,String sql) throws SQLException {
        for(String definition:sql.split("-- statement")) {
            String expected=definition.trim().replaceFirst(";$","");
            var name=java.util.regex.Pattern.compile("CREATE (?:TABLE|INDEX|TRIGGER) ([a-z_]+)").matcher(expected);
            if(!name.find())throw new SQLException("Invalid packaged migration definition");
            try(PreparedStatement p=c.prepareStatement("SELECT sql FROM sqlite_master WHERE name=?")) {
                p.setString(1,name.group(1));try(ResultSet rs=p.executeQuery()) {
                    if(!rs.next() || !expected.equals(rs.getString(1)))throw new SQLException("Installed catalog schema differs from migration");
                }
            }
        }
    }
    static boolean applied(Connection c,String id,String hash) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT checksum FROM emote_schema_migration WHERE migration_id=?")) {
            p.setString(1,id);try(ResultSet rs=p.executeQuery()) {
                if(!rs.next()) return false;
                if(!hash.equals(rs.getString(1))) throw new SQLException("Migration checksum conflict"); return true;
            }
        }
    }
    private static void record(Connection c,String id,String hash) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO emote_schema_migration VALUES(?,?,?)")) {
            p.setString(1,id);p.setString(2,hash);p.setString(3,Instant.now().toString());p.executeUpdate();
        }
    }
}
