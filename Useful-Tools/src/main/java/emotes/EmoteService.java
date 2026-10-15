package emotes;

import java.sql.*;
import java.util.*;

public final class EmoteService {
    public static final class Failure extends RuntimeException {
        public final int status; public final String code;
        Failure(int status,String code){super("Emote request could not be completed");this.status=status;this.code=code;}
    }
    private final EmoteDb.Factory factory;
    public EmoteService(){this(EmoteDb::open);}
    public EmoteService(EmoteDb.Factory factory){this.factory=factory;}
    public Object read(String username,String role,String raw,boolean categories) {
        try(Connection c=factory.open()) {
            EmoteDb.configure(c);
            return EmoteDb.transaction(c,false,()-> {
                access(c,username,role);
                EmoteQuery q=EmoteQuery.parse(raw,categories);
                String version;
                try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT a.version FROM emote_catalog_active a JOIN emote_catalog_version v ON a.version=v.version WHERE v.state='validated'")) {
                    if(!rs.next())throw new Failure(503,"CATALOG_UNAVAILABLE");version=rs.getString(1);
                }
                if(q.version()!=null && !q.version().equals(version))throw new Failure(409,"CATALOG_VERSION_CHANGED");
                List<Object> args=new ArrayList<>(List.of(version));String where="version=?";
                if(!q.type().equals("all")){where+=" AND kind=?";args.add(q.type());}
                if(!q.alternates())where+=" AND (kind<>'emoji' OR qualification IN ('fully-qualified','component'))";
                if(categories)return categories(c,version,where,args);
                if(q.category()!=null) {
                    String taxonomy="SELECT 1 FROM emote_category WHERE version=? AND parent_id IS NULL AND category_id=?"+(q.type().equals("all")?"":" AND kind=?");
                    try(PreparedStatement p=c.prepareStatement(taxonomy)){p.setString(1,version);p.setString(2,q.category());if(!q.type().equals("all"))p.setString(3,q.type());try(ResultSet rs=p.executeQuery()){if(!rs.next())throw EmoteQuery.invalid();}}
                    where+=" AND category_id=?";args.add(q.category());
                }
                if(q.subgroup()!=null){
                    try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM emote_category WHERE version=? AND kind='emoji' AND parent_id=? AND label=?")){p.setString(1,version);p.setString(2,q.category());p.setString(3,q.subgroup());try(ResultSet rs=p.executeQuery()){if(!rs.next())throw EmoteQuery.invalid();}}
                    where+=" AND subgroup=?";args.add(q.subgroup());
                }
                if(!q.q().isEmpty()) {where+=" AND (name LIKE ? ESCAPE '\\' OR instr(value,?)>0)";args.add("%"+q.q().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");args.add(q.q());}
                long total;
                try(PreparedStatement p=c.prepareStatement("SELECT count(*) FROM emote_catalog_entry WHERE "+where)){CatalogDao.bind(p,args);try(ResultSet rs=p.executeQuery()){rs.next();total=rs.getLong(1);}}
                return Map.of("items",CatalogDao.rows(c,where,args,q),"total",total,"limit",q.limit(),"offset",q.offset(),"hasMore",(long)q.offset()+q.limit()<total,"catalogVersion",version);
            });
        } catch(Failure e){throw e;}
        catch(SQLException e){throw new Failure(503,"DB_UNAVAILABLE");}
        catch(IllegalArgumentException e){throw new Failure(400,"INVALID_QUERY");}
        catch(RuntimeException e){throw new Failure(503,"DB_UNAVAILABLE");}
    }
    public void access(String username,String role) {
        try(Connection c=factory.open()){EmoteDb.configure(c);access(c,username,role);}
        catch(Failure e){throw e;}
        catch(SQLException | RuntimeException e){throw new Failure(503,"DB_UNAVAILABLE");}
    }
    static void access(Connection c,String username,String role) throws SQLException {
        if(username==null || username.isBlank())throw new Failure(401,"UNAUTHENTICATED");
        if(!"guest".equals(role) && !"Guest User".equals(username)) {
            if(!Set.of("user","admin").contains(role==null?"":role))throw new Failure(401,"UNAUTHENTICATED");
            try(PreparedStatement p=c.prepareStatement("SELECT count(*) FROM user_table WHERE username=? COLLATE BINARY")){p.setString(1,username);try(ResultSet rs=p.executeQuery()){rs.next();if(rs.getInt(1)!=1)throw new Failure(401,"UNAUTHENTICATED");}}
        }
        try(PreparedStatement p=c.prepareStatement("SELECT enabled FROM tool_toggles WHERE tool_path='/emote-utils'");ResultSet rs=p.executeQuery()) {
            if(!rs.next())throw new Failure(403,"TOOL_DISABLED");String value=rs.getString(1);
            if("0".equals(value))throw new Failure(403,"TOOL_DISABLED");if(!"1".equals(value))throw new Failure(503,"DB_UNAVAILABLE");
        }
    }
    private static Object categories(Connection c,String version,String where,List<Object> args) throws SQLException {
        List<Map<String,Object>> cats=new ArrayList<>();Map<String,Map<String,Object>> byKey=new LinkedHashMap<>();
        String sql="SELECT kind,category_id,subgroup,count(*),min(sort_order) FROM emote_catalog_entry WHERE "+where+" GROUP BY kind,category_id,subgroup ORDER BY CASE kind WHEN 'emoji' THEN 0 WHEN 'ascii' THEN 1 WHEN 'kaomoji' THEN 2 ELSE 3 END,min(sort_order)";
        try(PreparedStatement p=c.prepareStatement(sql)){CatalogDao.bind(p,args);try(ResultSet rs=p.executeQuery()){while(rs.next()){
            String kind=rs.getString(1),id=rs.getString(2),sub=rs.getString(3);long count=rs.getLong(4);String key=kind+":"+id;
            Map<String,Object> cat=byKey.get(key);
            if(cat==null){cat=new LinkedHashMap<>();cat.put("type",kind);cat.put("id",id);cat.put("label",id);cat.put("count",0L);cat.put("subgroups",new ArrayList<Map<String,Object>>());byKey.put(key,cat);cats.add(cat);}
            cat.put("count",(Long)cat.get("count")+count);
            if(sub!=null){@SuppressWarnings("unchecked") var subs=(List<Map<String,Object>>)cat.get("subgroups");subs.add(Map.of("id",sub,"label",sub,"count",count));}
        }}}
        return Map.of("catalogVersion",version,"categories",cats);
    }
}
