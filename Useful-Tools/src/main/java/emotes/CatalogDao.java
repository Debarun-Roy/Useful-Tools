package emotes;

import java.sql.*;
import java.util.*;
import com.google.gson.*;

public final class CatalogDao {
    static final String ORDER="CASE kind WHEN 'emoji' THEN 0 WHEN 'ascii' THEN 1 WHEN 'kaomoji' THEN 2 ELSE 3 END,sort_order,entry_id COLLATE BINARY";
    private CatalogDao() { }
    static List<CatalogSeed.Entry> all(Connection c,String version) throws SQLException { return rows(c,"version=?",List.of(version),null); }
    static List<CatalogSeed.Entry> rows(Connection c,String where,List<Object> args,EmoteQuery query) throws SQLException {
        String sql="SELECT * FROM emote_catalog_entry WHERE "+where+" ORDER BY "+ORDER+(query==null?"":" LIMIT ? OFFSET ?");
        List<Object> bindings=new ArrayList<>(args);if(query!=null){bindings.add(query.limit());bindings.add(query.offset());}
        Map<String,List<String>> tags=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement("SELECT entry_id,tag FROM emote_catalog_tag WHERE version=? ORDER BY entry_id,ordinal")) {
            p.setObject(1,args.get(0));try(ResultSet rs=p.executeQuery()){while(rs.next())tags.computeIfAbsent(rs.getString(1),k->new ArrayList<>()).add(rs.getString(2));}
        }
        List<CatalogSeed.Entry> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(sql)) {
            bind(p,bindings);try(ResultSet rs=p.executeQuery()) {while(rs.next()) {
                List<String> cps=new ArrayList<>();JsonParser.parseString(rs.getString("codepoints_json")).getAsJsonArray().forEach(v->cps.add(v.getAsString()));
                result.add(new CatalogSeed.Entry(rs.getString("entry_id"),rs.getString("value"),rs.getString("name"),rs.getString("kind"),rs.getString("category_id"),rs.getString("subgroup"),rs.getString("qualification"),rs.getString("emoji_version"),List.copyOf(cps),rs.getInt("sort_order"),List.copyOf(tags.getOrDefault(rs.getString("entry_id"),List.of()))));
            }}
        }return result;
    }
    static void bind(PreparedStatement p,List<Object> args) throws SQLException { for(int i=0;i<args.size();i++)p.setObject(i+1,args.get(i)); }
}
