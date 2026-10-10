package backendsupport;

import com.google.gson.*;
import java.util.*;
import static backendsupport.SchemaGeneration.*;

/** Structured portable view compiler; every expression form is allowlisted. */
public final class ViewGeneration {
    private final JsonObject request;private final Contracts.Findings out;private final Model schema;private final SchemaDialect dialect;
    private final Map<String,JsonObject> sources=new LinkedHashMap<>();private final Set<String> aliases=new HashSet<>();private int predicates;
    private ViewGeneration(JsonObject request,Contracts.Findings out,Model schema){this.request=request;this.out=out;this.schema=schema;this.dialect=B3Generation.dialect(request);}
    public static B3Generation.Result process(JsonObject request,Contracts.Findings out) {
        Model model=B3Generation.schema(request,"schema",out);if(!out.valid())return new B3Generation.Result(out,null,Map.of());
        ViewGeneration compiler=new ViewGeneration(request,out,model);String sql=compiler.render();
        if(!out.valid())return new B3Generation.Result(out,null,Map.of("executableSql",false));
        ArtifactBundle files=new ArtifactBundle();files.add("view.sql",sql);files.add("view.spec.json",ArtifactBundle.canonical(request)+"\n");
        files.add("verify.py",B3Generation.resource("verify.py"));
        files.add("README.md","# Structured view bundle\n\n"+B3Generation.commonReadme(request)
            +"The referenced schema must already exist and match the supplied snapshot. This bundle does not create or modify its tables. Apply view.sql only after review in an owned database, using a transaction. SQLite: enable and confirm PRAGMA foreign_keys=ON before the transaction; PostgreSQL: psql -X -v ON_ERROR_STOP=1 -d YOUR_DATABASE -f view.sql. Views are ordinary persisted queries with embedded typed literals, never request parameters.\n\n"
            +"Run python verify.py --sqlite YOUR_DISPOSABLE_DB or --database YOUR_DISPOSABLE_DB --psql PATH. The optional SQL smoke procedure creates/probes the view then rolls back; it requires the referenced schema.\n\n"
            +"INNER/LEFT equijoins, aliases/self-joins, typed predicates, grouping and COUNT/SUM/AVG/MIN/MAX form the supported subset. COUNT(*) includes unmatched LEFT rows; COUNT(column) excludes NULL. Every projected nonaggregate column is explicitly grouped when aggregates/grouping are used. SQL NULL predicates are IS NULL/IS NOT NULL, not equality. SQLite numeric aggregates and text timestamp ordering retain its affinity/convention limitations; collation depends on the database. No materialized views, subqueries, raw SQL or user functions.\n");
        return new B3Generation.Result(out,B3Generation.bundle(request,files,true,List.of("Referenced schema must already exist","Portable grouping; SQLite affinity and database collation differences remain")),Map.of("executableSql",true,"viewReady",true));
    }
    private String source(JsonObject source,String path) {
        String alias=str(source,"alias"),id=str(source,"tableId");JsonObject table=schema.byId().get(id);
        if(!aliases.add(alias.toLowerCase(Locale.ROOT)))out.add("DUPLICATE_SOURCE_ALIAS",path+"/alias");
        if(table==null){out.add("UNKNOWN_VIEW_SOURCE",path+"/tableId");return "";}
        sources.put(alias,table);return dialect.quote(str(table,"name"))+" AS "+dialect.quote(alias);
    }
    private JsonObject resolve(JsonObject ref,String path) {
        JsonObject table=sources.get(str(ref,"sourceAlias"));
        if(table==null){out.add("UNKNOWN_SOURCE_ALIAS",path+"/sourceAlias");return null;}
        JsonObject c=column(table,str(ref,"columnId"));if(c==null)out.add("UNKNOWN_VIEW_COLUMN",path+"/columnId");return c;
    }
    private String reference(JsonObject ref,String path) {
        JsonObject c=resolve(ref,path);return c==null?"":dialect.quote(str(ref,"sourceAlias"))+"."+dialect.quote(str(c,"name"));
    }
    private boolean compatible(JsonObject a,JsonObject b) {
        if(a==null||b==null)return false;
        for(String key:List.of("type","precision","scale","length"))if(!Objects.equals(a.get(key),b.get(key)))return false;
        return !str(a,"type").equals("json");
    }
    private String predicate(JsonObject p,String path,int depth) {
        if(depth>8||++predicates>100){out.add("PREDICATE_COMPLEXITY",path);return "";}
        String op=str(p,"op");
        if(op.equals("and")||op.equals("or")) {
            List<String> parts=new ArrayList<>();JsonArray terms=p.getAsJsonArray("terms");
            for(int i=0;i<terms.size();i++)parts.add(predicate(terms.get(i).getAsJsonObject(),path+"/terms/"+i,depth+1));
            return "("+String.join(op.equals("and")?" AND ":" OR ",parts)+")";
        }
        if(op.equals("not"))return "(NOT "+predicate(p.getAsJsonObject("term"),path+"/term",depth+1)+")";
        if(op.equals("isNull")||op.equals("isNotNull"))return "("+reference(p.getAsJsonObject("column"),path+"/column")+(op.equals("isNull")?" IS NULL)":" IS NOT NULL)");
        JsonObject left=p.getAsJsonObject("left"),right=p.getAsJsonObject("right"),col=resolve(left,path+"/left");
        String lhs=reference(left,path+"/left"),rhs;
        if(str(right,"kind").equals("column")) {
            if(!compatible(col,resolve(right,path+"/right")))out.add("INCOMPATIBLE_COMPARISON",path);
            rhs=reference(right,path+"/right");
        } else {
            JsonElement value=right.get("value");
            if(value.isJsonNull())out.add("USE_NULL_PREDICATE",path+"/right/value");
            else if(col!=null&&!SchemaGeneration.validLiteral(col,value,false))out.add("INVALID_TYPED_LITERAL",path+"/right/value");
            rhs=dialect.literal(value);
        }
        if(col!=null&&(str(col,"type").equals("json")||str(col,"type").equals("boolean")&&!Set.of("eq","ne").contains(op)))out.add("UNSUPPORTED_COMPARISON",path);
        String sqlOp=switch(op){case "eq"->"=";case "ne"->"<>";case "lt"->"<";case "le"->"<=";case "gt"->">";case "ge"->">=";default->throw new IllegalStateException();};
        return "("+lhs+" "+sqlOp+" "+rhs+")";
    }
    private String render() {
        Set<String> names=new HashSet<>();
        for(JsonObject table:schema.tables()) {
            names.add(str(table,"name").toLowerCase(Locale.ROOT));names.add(("ut_pk_"+str(table,"id")).toLowerCase(Locale.ROOT));
            for(int i=0;i<table.getAsJsonArray("unique").size();i++)names.add(("ut_uq_"+str(table,"id")+"_"+i).toLowerCase(Locale.ROOT));
            for(JsonElement index:table.getAsJsonArray("indexes"))names.add(str(index.getAsJsonObject(),"name").toLowerCase(Locale.ROOT));
        }
        SchemaGeneration.validateName(str(request,"name"),"/name",names,out);
        String from=source(request.getAsJsonObject("source"),"/source");List<String> joins=new ArrayList<>();JsonArray joinSpecs=request.getAsJsonArray("joins");
        for(int i=0;i<joinSpecs.size();i++) {
            JsonObject join=joinSpecs.get(i).getAsJsonObject();String path="/joins/"+i;Set<String> prior=Set.copyOf(sources.keySet());
            String alias=str(join.getAsJsonObject("source"),"alias"),sql=source(join.getAsJsonObject("source"),path+"/source");
            JsonObject left=join.getAsJsonObject("left"),right=join.getAsJsonObject("right");String la=str(left,"sourceAlias"),ra=str(right,"sourceAlias");
            if(!(la.equals(alias)&&prior.contains(ra)||ra.equals(alias)&&prior.contains(la)))out.add("JOIN_MUST_CONNECT_NEW_SOURCE",path);
            if(!compatible(resolve(left,path+"/left"),resolve(right,path+"/right")))out.add("INCOMPATIBLE_JOIN",path);
            joins.add((str(join,"type").equals("inner")?"INNER":"LEFT")+" JOIN "+sql+" ON "+reference(left,path+"/left")+" = "+reference(right,path+"/right"));
        }
        List<String> groups=new ArrayList<>();Set<String> grouped=new HashSet<>();JsonArray groupSpecs=request.getAsJsonArray("groupBy");
        for(int i=0;i<groupSpecs.size();i++) {
            JsonObject ref=groupSpecs.get(i).getAsJsonObject();String key=ArtifactBundle.canonical(ref);
            if(!grouped.add(key))out.add("DUPLICATE_GROUP_COLUMN","/groupBy/"+i);
            JsonObject c=resolve(ref,"/groupBy/"+i);if(c!=null&&str(c,"type").equals("json"))out.add("UNSUPPORTED_GROUP_TYPE","/groupBy/"+i);
            groups.add(reference(ref,"/groupBy/"+i));
        }
        List<String> projections=new ArrayList<>();Set<String> outputs=new HashSet<>();boolean aggregate=false;JsonArray projectionSpecs=request.getAsJsonArray("projections");
        for(JsonElement projection:projectionSpecs)aggregate|=str(projection.getAsJsonObject().getAsJsonObject("expression"),"kind").equals("aggregate");
        for(int i=0;i<projectionSpecs.size();i++) {
            JsonObject projection=projectionSpecs.get(i).getAsJsonObject(),expression=projection.getAsJsonObject("expression");String path="/projections/"+i;
            SchemaGeneration.validateName(str(projection,"name"),path+"/name",outputs,out);String sql;
            if(str(expression,"kind").equals("column")) {
                sql=reference(expression,path+"/expression");
                if((aggregate||!groups.isEmpty())&&!grouped.contains(ArtifactBundle.canonical(expression)))out.add("UNGROUPED_PROJECTION",path+"/expression");
            } else {
                String function=str(expression,"function");JsonElement argument=expression.get("argument");String column;
                if(argument.isJsonNull()) {if(!function.equals("count"))out.add("AGGREGATE_ARGUMENT_REQUIRED",path+"/expression/argument");column="*";}
                else {
                    JsonObject ref=argument.getAsJsonObject(),c=resolve(ref,path+"/expression/argument");column=reference(ref,path+"/expression/argument");
                    if(c!=null) {
                        String type=str(c,"type");
                        if(type.equals("json")||type.equals("boolean")&&!function.equals("count")||Set.of("sum","avg").contains(function)&&!Set.of("integer","bigint","decimal").contains(type))out.add("UNSUPPORTED_AGGREGATE_TYPE",path+"/expression");
                    }
                }
                sql=function.toUpperCase(Locale.ROOT)+"("+column+")";
            }
            projections.add(sql+" AS "+dialect.quote(str(projection,"name")));
        }
        String where=request.get("where").isJsonNull()?"":"\nWHERE "+predicate(request.getAsJsonObject("where"),"/where",0);
        if(!out.valid())return "";
        ArtifactBundle.Text sql=new ArtifactBundle.Text();
        if(dialect instanceof SchemaDialect.PostgreSQL)sql.add("SET standard_conforming_strings=on;\nSET TIME ZONE 'UTC';\n");
        sql.add("CREATE VIEW "+dialect.quote(str(request,"name"))+" AS\nSELECT "+String.join(",\n       ",projections)+"\nFROM "+from);
        if(!joins.isEmpty())sql.add("\n"+String.join("\n",joins));sql.add(where);
        if(!groups.isEmpty())sql.add("\nGROUP BY "+String.join(", ",groups));sql.add(";\n");return sql.value();
    }
}
