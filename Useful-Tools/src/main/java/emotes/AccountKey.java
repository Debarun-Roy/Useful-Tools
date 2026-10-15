package emotes;

import java.sql.*;
import java.util.*;

/** BINARY identity only. No account repair, trimming, merging or case folding. */
public final class AccountKey {
    private AccountKey() { }
    public static void ensure(Connection c) throws SQLException {
        int invalid = 0;
        java.nio.charset.Charset databaseEncoding;
        try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("PRAGMA encoding")) {
            if(!rs.next())throw new SQLException("Database encoding unavailable");
            databaseEncoding=java.nio.charset.Charset.forName(rs.getString(1));
        }
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT username, typeof(username), CAST(username AS BLOB) FROM user_table")) {
            while (rs.next()) {
                String name = rs.getString(1);
                boolean wellFormed="text".equals(rs.getString(2)) && valid(name);
                if(wellFormed) {
                    try {
                        String exact=databaseEncoding.newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(rs.getBytes(3))).toString();
                        wellFormed=exact.equals(name);
                    } catch(java.nio.charset.CharacterCodingException malformed) {wellFormed=false;}
                }
                if(!wellFormed) invalid++;
            }
        }
        long duplicates;
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT count(*) FROM (SELECT username COLLATE BINARY FROM user_table GROUP BY username COLLATE BINARY HAVING count(*)>1)")) {
            rs.next(); duplicates = rs.getLong(1);
        }
        if (invalid != 0 || duplicates != 0) throw new SQLException("Account prerequisite rejected: invalid=" + invalid + ", duplicateGroups=" + duplicates);
        if (!compatible(c)) {
            try (Statement s = c.createStatement()) { s.execute("CREATE UNIQUE INDEX emote_account_username_binary ON user_table(username COLLATE BINARY)"); }
        }
        if (!compatible(c)) throw new SQLException("Account parent key incompatible");
        // SQLite itself verifies the parent's declared collation, not only index metadata.
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE emote_parent_probe(owner TEXT REFERENCES user_table(username))");
            try { s.execute("INSERT INTO emote_parent_probe VALUES(NULL)"); }
            finally { s.execute("DROP TABLE emote_parent_probe"); }
        }
    }
    public static boolean valid(String s) {
        if (s == null || s.isBlank() || s.equals("Guest User")) return false;
        for (int i=0;i<s.length();i++) {
            char ch=s.charAt(i);
            if (Character.isHighSurrogate(ch)) { if (++i>=s.length() || !Character.isLowSurrogate(s.charAt(i))) return false; }
            else if (Character.isLowSurrogate(ch) || Character.isISOControl(ch)) return false;
        }
        return true;
    }
    public static boolean compatible(Connection c) throws SQLException {
        List<String> indexes = new ArrayList<>();
        try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("PRAGMA index_list(user_table)")) {
            while(rs.next()) if(rs.getInt("unique")==1 && rs.getInt("partial")==0) indexes.add(rs.getString("name"));
        }
        for(String name:indexes) {
            int keys=0; boolean good=true;
            try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("PRAGMA index_xinfo('"+name.replace("'","''")+"')")) {
                while(rs.next()) if(rs.getInt("key")==1) { keys++; good &= "username".equals(rs.getString("name")) && "BINARY".equalsIgnoreCase(rs.getString("coll")); }
            }
            if(good && keys==1) return true;
        }
        return false;
    }
}
