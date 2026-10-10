package backendsupport;

import com.google.gson.*;
import java.util.*;
import static backendsupport.SchemaGeneration.*;

/** Conservative full-plan diff. A single unsupported difference prevents SQL export. */
public final class MigrationGeneration {
    public record Change(String category,String code,String path) {}
    private final JsonObject request;
    private final Contracts.Findings out;
    private final SchemaDialect d;
    private final List<Change> changes=new ArrayList<>();
    private final List<String> up=new ArrayList<>(),down=new ArrayList<>();
    private final Set<String> usedMaps=new HashSet<>();
    private boolean addedColumns;
    private MigrationGeneration(JsonObject request,Contracts.Findings out){this.request=request;this.out=out;d=B3Generation.dialect(request);}
    private void block(String category,String code,String path){changes.add(new Change(category,code,path));out.add(code,path);}
    private void operation(String code,String path,String sql,String reverse){changes.add(new Change(code.startsWith("ADD")?"Added":"Changed",code,path));up.add(sql);if(reverse!=null)down.add(reverse);}
    public static B3Generation.Result process(JsonObject request,Contracts.Findings out) {
        var before=B3Generation.schema(request,"before",out);var after=B3Generation.schema(request,"after",out);
        if(!out.valid())return new B3Generation.Result(out,null,Map.of());
        MigrationGeneration p=new MigrationGeneration(request,out);
        int columns=0;for(var m:List.of(before,after))for(var t:m.tables())columns+=t.getAsJsonArray("columns").size();
        if(columns>1000)out.add("COMBINED_COLUMN_LIMIT","/after/tables");
        // The runner's temporary guard must never shadow a submitted object during DDL.
        for(String field:List.of("before","after")) {
            JsonArray tables=request.getAsJsonObject(field).getAsJsonArray("tables");
            for(int i=0;i<tables.size();i++)if(str(tables.get(i).getAsJsonObject(),"name").equalsIgnoreCase("ut_b3_guard"))out.add("MIGRATION_INTERNAL_NAME_COLLISION","/"+field+"/tables/"+i+"/name");
        }
        p.plan(before,after);
        if(!request.get("externalDependenciesReviewed").getAsBoolean())out.add("EXTERNAL_REVIEW_REQUIRED","/externalDependenciesReviewed");
        Map<String,Object> details=Map.of("changes",p.changes,"noOp",p.up.isEmpty()&&p.changes.isEmpty(),"executableSql",out.valid(),"automaticDown",!p.addedColumns&&!p.down.isEmpty(),"maintenanceWindowRequired",true);
        if(!out.valid())return new B3Generation.Result(out,null,details);
        ArtifactBundle files=new ArtifactBundle();
        files.add("before.schema.json",ArtifactBundle.canonical(request.get("before"))+"\n");
        files.add("after.schema.json",ArtifactBundle.canonical(request.get("after"))+"\n");
        files.add("migration.spec.json",ArtifactBundle.canonical(request)+"\n");
        files.add("up.sql",p.up.isEmpty()?"-- No-op: before and after models are identical.\n":String.join("\n",p.up)+"\n");
        if(!p.addedColumns&&!p.down.isEmpty()){Collections.reverse(p.down);files.add("down.sql",String.join("\n",p.down)+"\n");}
        files.add("prechecks.sql",p.checks(before));files.add("postchecks.sql",p.checks(after));
        files.add("migrate.py",B3Generation.resource("migrate.py"));files.add("verify.py",B3Generation.resource("verify.py"));
        String policy="Run only during a maintenance window after backup and review of the exact before snapshot and external dependencies. Use migrate.py; do not run up.sql alone. SQLite confirms foreign_keys before BEGIN IMMEDIATE; PostgreSQL takes ACCESS EXCLUSIVE locks in a transaction. Guard CHECK failures abort before mutation; postcheck or DDL failures roll back. Existing external views/triggers and PostgreSQL inheritance/partition objects are rejected. Checks compare modeled table count, ordered column names/rendered types/nullability and exact explicit index count, names, ordered columns and uniqueness. They do not prove CHECK/default/FK/collation equivalence; review these manually before use. No live connection is made by UsefulTools.\n\n";
        String reversal=p.addedColumns?"No down.sql: dropping an added column could destroy later writes. Recovery requires a reviewed backup/restore plan; an inverse DDL is not data recovery.":p.down.isEmpty()?"No down.sql for a no-op.":"down.sql reverses only names and added ordinary indexes, in reverse order. Use migrate.py --reverse, which checks the after shape first. It does not restore data from a backup or undo application writes.";
        files.add("README.md","# Safe-subset migration\n\n"+B3Generation.commonReadme(request)+policy+reversal+"\n\nUsage: python migrate.py --sqlite OWNED_DATABASE or python migrate.py --database OWNED_DATABASE --psql PATH_TO_PSQL. No database is created automatically. python verify.py checks artifact integrity only.\n");
        ArtifactBundle.Text report=new ArtifactBundle.Text();report.add("# Migration report\n\n"+policy+reversal+"\n\n");
        if(p.changes.isEmpty())report.add("No-op: no changes. Preconditions still run.\n");
        for(var c:p.changes)report.add("- "+c.category()+": "+c.code()+" at `"+c.path()+"`\n");
        files.add("migration_report.md",report.value());
        return new B3Generation.Result(out,B3Generation.bundle(request,files,true,List.of("Maintenance window, backup and external dependency review required","Shape checks do not establish full default/FK/CHECK/collation equivalence",reversal)),details);
    }
    private boolean rename(String group,String tableId,String columnId,String from,String to,String path,Set<String> occupied) {
        JsonArray maps=request.getAsJsonObject("renames").getAsJsonArray(group);int matches=0;
        for(int i=0;i<maps.size();i++) {
            JsonObject map=maps.get(i).getAsJsonObject();
            if(str(map,"tableId").equals(tableId)&&(columnId==null||str(map,"columnId").equals(columnId))) {
                matches++;usedMaps.add(group+"/"+i);
                if(!str(map,"fromName").equals(from)||!str(map,"toName").equals(to))block("Manual","CONFLICTING_RENAME_MAP","/renames/"+group+"/"+i);
            }
        }
        if(matches!=1)block("Manual","EXPLICIT_RENAME_REQUIRED",path);
        if(occupied.contains(to.toLowerCase(Locale.ROOT)))block("Manual","RENAME_TARGET_OCCUPIED",path);
        return out.valid();
    }
    private void plan(Model before,Model after) {
        Set<String> tableNames=new HashSet<>();for(var t:before.tables())tableNames.add(str(t,"name").toLowerCase(Locale.ROOT));
        for(var t:before.tables())if(!after.byId().containsKey(str(t,"id")))block("Removed","DESTRUCTIVE_TABLE_DROP","/before/tables/"+before.request().getAsJsonArray("tables").asList().indexOf(t));
        JsonArray afterTables=after.request().getAsJsonArray("tables");
        // All table renames precede column operations, including referenced tables.
        for(int n=0;n<afterTables.size();n++) {
            JsonObject a=afterTables.get(n).getAsJsonObject(),b=before.byId().get(str(a,"id"));String path="/after/tables/"+n;
            if(b==null){block("Manual","TABLE_ADD_UNSUPPORTED",path);continue;}
            if(!str(b,"name").equals(str(a,"name"))) {
                rename("tables",str(a,"id"),null,str(b,"name"),str(a,"name"),path+"/name",tableNames);
                operation("RENAME_TABLE",path+"/name","ALTER TABLE "+q(b)+" RENAME TO "+q(a)+";","ALTER TABLE "+q(a)+" RENAME TO "+q(b)+";");
            }
        }
        for(int n=0;n<afterTables.size();n++) {
            JsonObject a=afterTables.get(n).getAsJsonObject(),b=before.byId().get(str(a,"id"));String path="/after/tables/"+n;if(b==null)continue;
            for(String key:List.of("primaryKey","unique","foreignKeys","checks"))if(!Objects.equals(a.get(key),b.get(key)))block("Manual","CONSTRAINT_CHANGE_UNSUPPORTED",path+"/"+key);
            Map<String,JsonObject> bc=B3Generation.indexed(b.getAsJsonArray("columns")),ac=B3Generation.indexed(a.getAsJsonArray("columns"));
            Set<String> colNames=new HashSet<>();for(var c:bc.values())colNames.add(str(c,"name").toLowerCase(Locale.ROOT));
            for(String id:bc.keySet())if(!ac.containsKey(id))block("Removed","DESTRUCTIVE_COLUMN_DROP",path+"/columns");
            List<String> retained=ac.keySet().stream().filter(bc::containsKey).toList();
            if(!retained.equals(bc.keySet().stream().filter(ac::containsKey).toList()))block("Manual","COLUMN_ORDER_UNSUPPORTED",path+"/columns");
            boolean newSeen=false;int pos=0;
            for(var c:ac.values()) {
                String cp=path+"/columns/"+pos++;JsonObject old=bc.get(str(c,"id"));
                if(old==null) {
                    newSeen=true;addedColumns=true;JsonObject def=c.has("default")?c.getAsJsonObject("default"):null;
                    if(def!=null&&!str(def,"kind").equals("literal")||!c.get("nullable").getAsBoolean()&&(def==null||def.get("value")==null||def.get("value").isJsonNull())){block("Manual","COLUMN_DEFAULT_PRECONDITION_UNSUPPORTED",cp);continue;}
                    operation("ADD_COLUMN",cp,"ALTER TABLE "+q(a)+" ADD COLUMN "+definition(c)+";",null);
                } else {
                    if(newSeen)block("Manual","COLUMN_ORDER_UNSUPPORTED",cp);
                    if(!str(old,"name").equals(str(c,"name"))) {
                        rename("columns",str(a,"id"),str(c,"id"),str(old,"name"),str(c,"name"),cp+"/name",colNames);
                        operation("RENAME_COLUMN",cp+"/name","ALTER TABLE "+q(a)+" RENAME COLUMN "+q(old)+" TO "+q(c)+";","ALTER TABLE "+q(a)+" RENAME COLUMN "+q(c)+" TO "+q(old)+";");
                    }
                    for(String key:List.of("type","precision","scale","length","nullable","default"))if(!Objects.equals(c.get(key),old.get(key)))block("Manual",key.equals("nullable")?"NULLABILITY_BACKFILL_REQUIRED":"COLUMN_CHANGE_UNSUPPORTED",cp+"/"+key);
                }
            }
            Map<String,JsonObject> bi=B3Generation.indexed(b.getAsJsonArray("indexes")),ai=B3Generation.indexed(a.getAsJsonArray("indexes"));
            for(String id:bi.keySet())if(!ai.containsKey(id))block("Manual","INDEX_DROP_UNSUPPORTED",path+"/indexes");
            int ip=0;for(var index:ai.values()) {
                String at=path+"/indexes/"+ip++;JsonObject old=bi.get(str(index,"id"));
                if(old!=null){if(!old.equals(index))block("Manual","INDEX_CHANGE_UNSUPPORTED",at);continue;}
                if(index.get("unique").getAsBoolean()){block("Manual","UNIQUE_INDEX_PRECHECK_UNSUPPORTED",at);continue;}
                operation("ADD_INDEX",at,"CREATE INDEX "+q(index)+" ON "+q(a)+" ("+d.columns(a,index.getAsJsonArray("columns"))+");","DROP INDEX "+q(index)+";");
            }
        }
        for(String group:List.of("tables","columns"))for(int i=0;i<request.getAsJsonObject("renames").getAsJsonArray(group).size();i++)if(!usedMaps.contains(group+"/"+i))block("Manual","STALE_RENAME_MAP","/renames/"+group+"/"+i);
    }
    private String q(JsonObject o){return d.quote(str(o,"name"));}
    private String literal(String s){return d.literal(new JsonPrimitive(s));}
    private String definition(JsonObject c) {
        String sql=q(c)+" "+d.type(c);if(!c.get("nullable").getAsBoolean())sql+=" NOT NULL";
        if(c.has("default"))sql+=" DEFAULT "+d.literal(c.getAsJsonObject("default").get("value"));
        if(d instanceof SchemaDialect.SQLite)switch(str(c,"type")) {
            case "varchar" -> sql+=" CHECK (length("+q(c)+") <= "+c.get("length").getAsInt()+")";
            case "boolean" -> sql+=" CHECK ("+q(c)+" IN (0, 1))";
            case "json" -> sql+=" CHECK ("+q(c)+" IS NULL OR json_valid("+q(c)+"))";
            default -> { }
        }
        return sql;
    }
    private String checks(Model model) {
        ArtifactBundle.Text sql=new ArtifactBundle.Text();boolean sqlite=d instanceof SchemaDialect.SQLite;
        sql.add("-- Failing INSERT aborts the transaction; run only through migrate.py.\nCREATE TEMP TABLE ut_b3_guard (ok INT NOT NULL CHECK (ok=1));\n");
        if(sqlite) {
            guard(sql,"(SELECT count(*) FROM main.sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%')="+model.tables().size());
            guard(sql,"NOT EXISTS (SELECT 1 FROM main.sqlite_schema WHERE type IN ('view','trigger'))");
        } else {
            guard(sql,"(SELECT count(*) FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p'))="+model.tables().size());
            guard(sql,"NOT EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND (c.relkind IN ('v','m','p') OR c.relispartition))");
            guard(sql,"NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits)");
            guard(sql,"NOT EXISTS (SELECT 1 FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal)");
        }
        for(var t:model.tables()) {
            String table=literal(str(t,"name"));JsonArray columns=t.getAsJsonArray("columns");
            String cols=sqlite?"pragma_table_info("+table+", 'main')":"information_schema.columns WHERE table_schema='public' AND table_name="+table;
            guard(sql,"(SELECT count(*) FROM "+cols+")="+columns.size());
            int pos=0;for(JsonElement e:columns) {
                JsonObject c=e.getAsJsonObject();boolean nullable=c.get("nullable").getAsBoolean();
                if(sqlite)guard(sql,"EXISTS (SELECT 1 FROM "+cols+" WHERE cid="+pos+" AND name="+literal(str(c,"name"))+" AND upper(type)="+literal(d.type(c))+" AND \"notnull\"="+(nullable?0:1)+")");
                else {
                    String type=switch(str(c,"type")){case "varchar"->"character varying("+c.get("length").getAsInt()+")";case "timestamp"->"timestamp with time zone";case "decimal"->"numeric("+c.get("precision").getAsInt()+","+c.get("scale").getAsInt()+")";default->d.type(c).toLowerCase(Locale.ROOT);};
                    guard(sql,"EXISTS (SELECT 1 FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname="+table+" AND a.attnum="+(pos+1)+" AND NOT a.attisdropped AND a.attname="+literal(str(c,"name"))+" AND pg_catalog.format_type(a.atttypid,a.atttypmod)="+literal(type)+" AND a.attnotnull="+(nullable?"FALSE":"TRUE")+")");
                }
                pos++;
            }
            JsonArray indexes=t.getAsJsonArray("indexes");
            String ix=sqlite?"pragma_index_list("+table+", 'main') WHERE origin='c'":"pg_catalog.pg_indexes WHERE schemaname='public' AND tablename="+table+" AND indexname NOT IN (SELECT conname FROM pg_catalog.pg_constraint WHERE conrelid=('public.' || "+literal(d.quote(str(t,"name")))+")::regclass AND contype IN ('p','u'))";
            guard(sql,"(SELECT count(*) FROM "+ix+")="+indexes.size());
            for(JsonElement e:indexes) {
                JsonObject i=e.getAsJsonObject();String iname=literal(str(i,"name"));
                if(sqlite) {
                    guard(sql,"EXISTS (SELECT 1 FROM "+ix+" AND name="+iname+" AND \"unique\"="+(i.get("unique").getAsBoolean()?1:0)+" AND partial=0)");
                    guard(sql,"(SELECT count(*) FROM pragma_index_info("+iname+", 'main'))="+i.getAsJsonArray("columns").size());
                    int k=0;for(String id:ids(i.getAsJsonArray("columns")))guard(sql,"EXISTS (SELECT 1 FROM pragma_index_info("+iname+", 'main') WHERE seqno="+k+++" AND name="+literal(str(column(t,id),"name"))+")");
                } else {
                    String columnsSql="ARRAY["+String.join(",",ids(i.getAsJsonArray("columns")).stream().map(id->literal(str(column(t,id),"name"))).toList())+"]::text[]";
                    guard(sql,"EXISTS (SELECT 1 FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class ci ON ci.oid=x.indexrelid JOIN pg_catalog.pg_class ct ON ct.oid=x.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=ct.relnamespace WHERE n.nspname='public' AND ct.relname="+table+" AND ci.relname="+iname+" AND x.indisunique="+i.get("unique")+" AND x.indisvalid AND x.indpred IS NULL AND x.indexprs IS NULL AND ARRAY(SELECT a.attname::text FROM unnest(x.indkey) WITH ORDINALITY k(num,ord) JOIN pg_catalog.pg_attribute a ON a.attrelid=ct.oid AND a.attnum=k.num ORDER BY k.ord)="+columnsSql+")");
                }
            }
        }
        sql.add("DROP TABLE ut_b3_guard;\n");return sql.value();
    }
    private void guard(ArtifactBundle.Text sql,String predicate){sql.add("INSERT INTO ut_b3_guard(ok) SELECT CASE WHEN ("+predicate+") THEN 1 ELSE 0 END;\n");}
}
