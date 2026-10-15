package emotes;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.Gson;

public final class CatalogImport {
    private static final Gson JSON=new Gson();
    record Category(String kind,String id,String parent,String label,int order) { }
    private CatalogImport() { }
    public static void install(Connection c) throws SQLException { install(c,CatalogSeed.load(),CatalogSeed.VERSION,EmoteMigration.NONE); }
    static void install(Connection c,CatalogSeed seed,String version,EmoteMigration.Checkpoint check) throws SQLException {
        EmoteDb.configure(c);
        EmoteDb.transaction(c,true,()-> {
            EmoteMigration.verifyInstalled(c);
            boolean exists=false;
            try(PreparedStatement p=c.prepareStatement("SELECT source_manifest_hash FROM emote_catalog_version WHERE version=?")) {
                p.setString(1,version);try(ResultSet rs=p.executeQuery()) { if(rs.next()) { exists=true; if(!CatalogSeed.MANIFEST.equals(rs.getString(1))) throw new SQLException("Catalog version content conflict"); } }
            }
            if(!exists) {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO emote_catalog_version VALUES(?,?,?,'staged')")) {
                    p.setString(1,version);p.setString(2,CatalogSeed.MANIFEST);p.setString(3,Instant.now().toString());p.executeUpdate();
                }
                check.hit("before-rows");
                try(PreparedStatement p=c.prepareStatement("INSERT INTO emote_category VALUES(?,?,?,?,?,?)")) {
                    for(Category cat:categories(seed)) {p.setString(1,version);p.setString(2,cat.kind);p.setString(3,cat.id);p.setString(4,cat.parent);p.setString(5,cat.label);p.setInt(6,cat.order);p.executeUpdate();}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO emote_catalog_entry VALUES(?,?,?,?,?,?,?,?,?,?,?)");
                    PreparedStatement tag=c.prepareStatement("INSERT INTO emote_catalog_tag VALUES(?,?,?,?)")) {
                    for(CatalogSeed.Entry e:seed.entries()) {
                        p.setString(1,version);p.setString(2,e.id());p.setString(3,e.kind());p.setString(4,e.categoryId());p.setString(5,e.subgroup());p.setString(6,e.name());p.setString(7,e.value());p.setString(8,JSON.toJson(e.codepoints()));p.setString(9,e.qualification());p.setString(10,e.emojiVersion());p.setInt(11,e.sortOrder());
                        p.executeUpdate();
                        for(int i=0;i<e.tags().size();i++) {tag.setString(1,version);tag.setString(2,e.id());tag.setInt(3,i);tag.setString(4,e.tags().get(i));tag.executeUpdate();}
                        check.hit("row");
                    }
                }
            }
            check.hit("validation");validate(c,seed,version);
            try(PreparedStatement p=c.prepareStatement("UPDATE emote_catalog_version SET state='validated' WHERE version=? AND state<>'validated'")) {p.setString(1,version);p.executeUpdate();}
            return null;
        });
        check.hit("staged-commit");
        activate(c,seed,version,check);
    }
    public static void activate(Connection c,String version) throws SQLException { activate(c,CatalogSeed.load(),version,EmoteMigration.NONE); }
    static void activate(Connection c,CatalogSeed seed,String version,EmoteMigration.Checkpoint check) throws SQLException {
        EmoteDb.configure(c);
        EmoteDb.transaction(c,true,()-> {
            EmoteMigration.verifyInstalled(c);
            validate(c,seed,version);
            try(PreparedStatement p=c.prepareStatement("SELECT state,source_manifest_hash FROM emote_catalog_version WHERE version=?")) {
                p.setString(1,version);try(ResultSet rs=p.executeQuery()) {if(!rs.next() || !"validated".equals(rs.getString(1)) || !CatalogSeed.MANIFEST.equals(rs.getString(2))) throw new SQLException("Catalog not validated");}
            }
            check.hit("activation");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO emote_catalog_active VALUES(1,?) ON CONFLICT(singleton_key) DO UPDATE SET version=excluded.version WHERE version<>excluded.version")) {p.setString(1,version);p.executeUpdate();}
            check.hit("after-pointer");return null;
        });
    }
    static List<Category> categories(CatalogSeed seed) {
        Map<String,Category> result=new LinkedHashMap<>();
        for(CatalogSeed.Entry e:seed.entries()) {
            String key=e.kind()+":"+e.categoryId();
            result.putIfAbsent(key,new Category(e.kind(),e.categoryId(),null,e.categoryId(),result.size()));
            if(e.subgroup()!=null) {String sub=e.categoryId()+"/"+e.subgroup();result.putIfAbsent(e.kind()+":"+sub,new Category(e.kind(),sub,e.categoryId(),e.subgroup(),result.size()));}
        }return List.copyOf(result.values());
    }
    static void validate(Connection c,CatalogSeed seed,String version) throws SQLException {
        Map<String,CatalogSeed.Entry> expected=new HashMap<>();for(var e:seed.entries()) expected.put(e.id(),e);
        List<CatalogSeed.Entry> actual=CatalogDao.all(c,version);
        if(actual.size()!=expected.size()) throw new SQLException("Catalog persisted set mismatch");
        for(var e:actual) if(!e.equals(expected.remove(e.id()))) throw new SQLException("Catalog persisted field mismatch");
        if(!expected.isEmpty()) throw new SQLException("Catalog persisted IDs mismatch");
        List<Category> cats=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT kind,category_id,parent_id,label,sort_order FROM emote_category WHERE version=? ORDER BY sort_order")) {
            p.setString(1,version);try(ResultSet rs=p.executeQuery()) {while(rs.next()) cats.add(new Category(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)));}
        }
        if(!cats.equals(categories(seed))) throw new SQLException("Catalog taxonomy mismatch");
    }
}
