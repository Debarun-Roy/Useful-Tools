package backendsupport;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Versioned, offline schema compiler. No database, filesystem or session access. */
public final class SchemaGeneration {
    public static final String VERSION = "0.2.0", TEMPLATE = "schema-0.2.0-b2";
    private static final JsonObject CONTRACT = Contracts.resource("contracts/v0.2.0/models.schema.json");
    public record Model(JsonObject request, List<JsonObject> tables, Map<String,JsonObject> byId) {}
    public record Validation(Contracts.Findings findings, Model model) {}
    private SchemaGeneration() {}
    /** Shared typed-literal predicate; diagnostic consumers do not weaken generation validation. */
    public static boolean validLiteral(JsonObject column,JsonElement value,boolean nullable) {
        Contracts.Findings findings=new Contracts.Findings();literal(column,value,"/",findings,nullable);return findings.valid();
    }
    public static void validateName(String value,String path,Set<String> namespace,Contracts.Findings findings) {
        name(value,path,namespace,findings);
    }
    public static String str(JsonObject o, String key) { return o.get(key).getAsString(); }
    public static List<String> ids(JsonArray a) { return a.asList().stream().map(JsonElement::getAsString).toList(); }
    public static JsonObject column(JsonObject t, String id) {
        for (JsonElement e:t.getAsJsonArray("columns")) if(str(e.getAsJsonObject(),"id").equals(id)) return e.getAsJsonObject();
        return null;
    }
    public static Validation validate(JsonElement input) {
        Contracts.Findings out=Contracts.structural(input, CONTRACT);
        if(!out.valid()) return new Validation(out,null);
        JsonObject req=input.getAsJsonObject().deepCopy();
        boolean sqlite=str(req,"target").equals("sqlite");
        Map<String,JsonObject> tables=new TreeMap<>();Set<String> objects=new HashSet<>();int total=0;
        JsonArray all=req.getAsJsonArray("tables");
        for(int i=0;i<all.size();i++) {
            JsonObject t=all.get(i).getAsJsonObject();String p="/tables/"+i;
            if(tables.put(str(t,"id"),t)!=null)out.add("DUPLICATE_ID",p+"/id");
            name(str(t,"name"),p+"/name",objects,out);
            // Explicit deterministic names avoid PostgreSQL's automatically truncated names.
            name("ut_pk_"+str(t,"id"),p+"/primaryKey",objects,out);
            for(int u=0;u<t.getAsJsonArray("unique").size();u++)name("ut_uq_"+str(t,"id")+"_"+u,p+"/unique/"+u,objects,out);
            Set<String> colIds=new HashSet<>(),colNames=new HashSet<>(),indexIds=new HashSet<>();
            JsonArray cols=t.getAsJsonArray("columns");total+=cols.size();
            for(int c=0;c<cols.size();c++) {
                JsonObject col=cols.get(c).getAsJsonObject();String cp=p+"/columns/"+c;String type=str(col,"type");
                if(!colIds.add(str(col,"id")))out.add("DUPLICATE_ID",cp+"/id");
                name(str(col,"name"),cp+"/name",colNames,out);
                if(type.equals("decimal")) {
                    if(!col.has("precision")||!col.has("scale"))out.add("DECIMAL_OPTIONS_REQUIRED",cp);
                    else if(col.get("scale").getAsInt()>col.get("precision").getAsInt())out.add("INVALID_SCALE",cp+"/scale");
                } else if(col.has("precision")||col.has("scale"))out.add("UNSUPPORTED_TYPE_OPTION",cp);
                if(type.equals("varchar")!=col.has("length"))out.add("VARCHAR_LENGTH_REQUIRED_ONLY_FOR_VARCHAR",cp);
                if(col.has("default")) {
                    JsonObject def=col.getAsJsonObject("default");
                    if(str(def,"kind").equals("currentTimestamp")) {
                        if(!type.equals("timestamp"))out.add("TIMESTAMP_DEFAULT_TYPE",cp+"/default");
                    } else literal(col,def.get("value"),cp+"/default/value",out,true);
                }
            }
            refs(t,t.getAsJsonArray("primaryKey"),p+"/primaryKey",out);
            for(String id:ids(t.getAsJsonArray("primaryKey"))) {
                JsonObject c=column(t,id);if(c!=null && c.get("nullable").getAsBoolean())out.add("PRIMARY_KEY_NULLABLE",p+"/primaryKey");
            }
            Set<List<String>> keys=new HashSet<>();keys.add(ids(t.getAsJsonArray("primaryKey")));
            for(JsonElement u:t.getAsJsonArray("unique")) {
                refs(t,u.getAsJsonArray(),p+"/unique",out);
                if(!keys.add(ids(u.getAsJsonArray())))out.add("DUPLICATE_KEY",p+"/unique");
            }
            for(JsonElement e:t.getAsJsonArray("indexes")) {
                JsonObject index=e.getAsJsonObject();name(str(index,"name"),p+"/indexes",objects,out);
                if(!indexIds.add(str(index,"id")))out.add("DUPLICATE_ID",p+"/indexes");
                refs(t,index.getAsJsonArray("columns"),p+"/indexes",out);
            }
            int n=0;
            for(JsonElement e:t.getAsJsonArray("checks")) {
                JsonObject check=e.getAsJsonObject();String cp=p+"/checks/"+n++;JsonObject col=column(t,str(check,"column"));
                if(col==null)out.add("UNKNOWN_COLUMN",cp+"/column");
                else {
                    literal(col,check.get("value"),cp+"/value",out,false);
                    String type=str(col,"type");
                    if(type.equals("json") || type.equals("boolean")&&!Set.of("eq","ne").contains(str(check,"op")))out.add("UNSUPPORTED_CHECK",cp);
                }
            }
        }
        if(total>1000)out.add("COLUMN_LIMIT","/tables");
        for(int i=0;i<all.size();i++) {
            JsonObject t=all.get(i).getAsJsonObject();int n=0;
            for(JsonElement e:t.getAsJsonArray("foreignKeys")) {
                JsonObject fk=e.getAsJsonObject();String p="/tables/"+i+"/foreignKeys/"+n++;
                refs(t,fk.getAsJsonArray("columns"),p+"/columns",out);
                JsonObject target=tables.get(str(fk,"tableId"));
                if(target==null) {out.add("UNKNOWN_TABLE",p+"/tableId");continue;}
                refs(target,fk.getAsJsonArray("targetColumns"),p+"/targetColumns",out);
                List<String> from=ids(fk.getAsJsonArray("columns")),to=ids(fk.getAsJsonArray("targetColumns"));
                boolean unique=to.equals(ids(target.getAsJsonArray("primaryKey")));
                for(JsonElement u:target.getAsJsonArray("unique"))unique|=to.equals(ids(u.getAsJsonArray()));
                if(!unique)out.add("REFERENCE_NOT_UNIQUE",p+"/targetColumns");
                if(from.size()!=to.size()) {out.add("FOREIGN_KEY_ARITY",p);continue;}
                for(int c=0;c<from.size();c++) {
                    JsonObject a=column(t,from.get(c)),b=column(target,to.get(c));
                    if(a==null||b==null)continue;
                    for(String key:List.of("type","precision","scale","length"))
                        if(!Objects.equals(a.get(key),b.get(key)))out.add("FOREIGN_KEY_TYPE_MISMATCH",p+"/columns/"+c);
                    if((str(fk,"onDelete").equals("SET NULL")||str(fk,"onUpdate").equals("SET NULL"))&&!a.get("nullable").getAsBoolean())out.add("SET_NULL_REQUIRES_NULLABLE",p);
                }
            }
        }
        if(!out.valid())return new Validation(out,null);
        List<JsonObject> ordered=new ArrayList<>();Set<String> done=new HashSet<>();
        while(done.size()<tables.size()) {
            boolean progress=false;
            for(var entry:tables.entrySet()) {
                String id=entry.getKey();if(done.contains(id))continue;boolean ready=true;
                for(JsonElement f:entry.getValue().getAsJsonArray("foreignKeys")) {
                    String dependency=str(f.getAsJsonObject(),"tableId");if(!dependency.equals(id)&&!done.contains(dependency))ready=false;
                }
                if(ready){ordered.add(entry.getValue());done.add(id);progress=true;}
            }
            if(!progress){out.add("CYCLIC_DEPENDENCY_UNSUPPORTED","/tables");return new Validation(out,null);}
        }
        JsonArray normalized=new JsonArray();ordered.forEach(normalized::add);req.add("tables",normalized);
        if(sqlite)out.add("SQLITE_AFFINITY_AND_DECIMAL_LIMITATIONS","/target",false);
        out.add("INITIAL_SCHEMA_ONLY_EMPTY_DATABASE","/tables",false);
        return new Validation(out,new Model(req,List.copyOf(ordered),Map.copyOf(tables)));
    }
    private static void name(String name,String path,Set<String> seen,Contracts.Findings out) {
        if(name.getBytes(StandardCharsets.UTF_8).length>63 || name.codePoints().anyMatch(c->Character.isISOControl(c)||c>=0xD800&&c<=0xDFFF)
                || name.toLowerCase(Locale.ROOT).startsWith("sqlite_") || name.toLowerCase(Locale.ROOT).startsWith("pg_"))out.add("UNSUPPORTED_IDENTIFIER",path);
        // Conservative case-folding across both dialects; no identifier normalization or truncation.
        if(!seen.add(name.toLowerCase(Locale.ROOT)))out.add("EMITTED_NAME_COLLISION",path);
    }
    private static void refs(JsonObject t,JsonArray a,String path,Contracts.Findings out) {
        Set<String> seen=new HashSet<>();for(String id:ids(a)) {
            if(column(t,id)==null)out.add("UNKNOWN_COLUMN",path);
            if(!seen.add(id))out.add("DUPLICATE_COLUMN_REFERENCE",path);
        }
    }
    private static void literal(JsonObject col,JsonElement value,String path,Contracts.Findings out,boolean nullable) {
        if(value.isJsonNull()){if(!nullable||!col.get("nullable").getAsBoolean())out.add("NULL_LITERAL_CONFLICT",path);return;}
        JsonPrimitive v=value.getAsJsonPrimitive();String type=str(col,"type");boolean valid=true;
        try {
            switch(type) {
                case "integer","bigint","decimal" -> {
                    valid=v.isNumber();if(!valid)break;
                    BigDecimal n=v.getAsBigDecimal();
                    if(type.equals("decimal")) {
                        if(col.has("precision")&&col.has("scale")) {
                            int scale=col.get("scale").getAsInt(),precision=col.get("precision").getAsInt();
                            valid=n.stripTrailingZeros().scale()<=scale && n.abs().compareTo(BigDecimal.TEN.pow(Math.max(0,precision-scale)))<0;
                        }
                    } else {long l=n.longValueExact();valid=!type.equals("integer")||(l>=Integer.MIN_VALUE&&l<=Integer.MAX_VALUE);}
                }
                case "boolean" -> valid=v.isBoolean();
                case "text","varchar","date","timestamp","json" -> {
                    valid=v.isString();if(!valid)break;String s=v.getAsString();
                    valid=s.codePoints().noneMatch(c->c==0 || c==13 || c>=0xD800&&c<=0xDFFF);
                    if(type.equals("varchar")&&col.has("length"))valid &=s.codePointCount(0,s.length())<=col.get("length").getAsInt();
                    if(type.equals("date"))valid &=LocalDate.parse(s).getYear()>=1&&s.length()==10;
                    if(type.equals("timestamp"))valid &=s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9](\\.[0-9]{1,6})?Z")&&Instant.parse(s).atOffset(ZoneOffset.UTC).getYear()>=1;
                    if(type.equals("json")) {
                        // Same strict duplicate-key/depth/string bounds as requests, including nested JSON literals.
                        JsonElement json=BoundedJson.read(new java.io.ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
                        valid &=safeJsonLiteral(json);
                    }
                }
                default -> valid=false;
            }
        } catch(Exception e){valid=false;}
        if(!valid)out.add("INVALID_TYPED_LITERAL",path);
    }
    private static boolean safeJsonLiteral(JsonElement json) {
        if(json.isJsonObject()) {
            for(var entry:json.getAsJsonObject().entrySet())
                if(!safeJsonLiteral(new JsonPrimitive(entry.getKey()))||!safeJsonLiteral(entry.getValue()))return false;
        } else if(json.isJsonArray()) {
            for(JsonElement item:json.getAsJsonArray())if(!safeJsonLiteral(item))return false;
        } else if(json.isJsonPrimitive()) {
            JsonPrimitive p=json.getAsJsonPrimitive();
            if(p.isString())return p.getAsString().codePoints().noneMatch(c->c==0||c>=0xD800&&c<=0xDFFF);
            if(p.isNumber()) {BigDecimal n=p.getAsBigDecimal().stripTrailingZeros();return n.scale()<=18&&n.abs().compareTo(BigDecimal.TEN.pow(38))<0;}
        }
        return true;
    }
}
