package backendsupport;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static backendsupport.SchemaGeneration.*;

/** B4 compiles inert specifications into fixed, operator-run Python artifacts. */
public final class EtlGeneration {
    public static final String VERSION="0.4.0", TEMPLATE="etl-0.4.0-b4";
    private static final JsonObject CONTRACT=Contracts.resource("contracts/v0.4.0/models.schema.json");
    private EtlGeneration() {}
    public static B3Generation.Result process(JsonElement input) {
        Contracts.Findings out=Contracts.structural(input,CONTRACT);
        if(!out.valid())return new B3Generation.Result(out,null,Map.of());
        JsonObject request=input.getAsJsonObject().deepCopy();
        if(!boundedNumbers(request)){out.add("NUMERIC_COMPLEXITY","/");return new B3Generation.Result(out,null,Map.of());}
        Model model=B3Generation.schema(request,"schema",out);
        if(!out.valid())return new B3Generation.Result(out,null,Map.of());
        JsonObject table=model.byId().get(str(request,"tableId"));
        if(table==null){out.add("UNKNOWN_ETL_TABLE","/tableId");return new B3Generation.Result(out,null,Map.of());}
        Map<String,JsonObject> mapped=new LinkedHashMap<>();Set<String> sources=new HashSet<>();
        JsonArray mappings=request.getAsJsonArray("mappings");
        for(int i=0;i<mappings.size();i++) {
            JsonObject m=mappings.get(i).getAsJsonObject();String p="/mappings/"+i,id=str(m,"columnId"),source=str(m,"source");
            if(!sources.add(source))out.add("DUPLICATE_SOURCE_FIELD",p+"/source");
            if(source.codePoints().anyMatch(c->Character.isISOControl(c)||c>=0xd800&&c<=0xdfff))out.add("INVALID_SOURCE_FIELD",p+"/source");
            if(mapped.put(id,m)!=null)out.add("DUPLICATE_ETL_COLUMN",p+"/columnId");
            JsonObject c=column(table,id);if(c==null){out.add("UNKNOWN_ETL_COLUMN",p+"/columnId");continue;}
            String type=str(c,"type");List<String> transforms=ids(m.getAsJsonArray("transforms"));
            String last=transforms.get(transforms.size()-1);
            boolean text=Set.of("text","varchar").contains(type);
            Set<String> finals=type.equals("date")?Set.of("date_iso","date_dmy"):type.equals("timestamp")?Set.of("timestamp_iso"):Set.of("cast");
            if(!finals.contains(last))out.add("ETL_FINAL_CONVERSION",p+"/transforms");
            for(String transform:transforms.subList(0,transforms.size()-1))
                if(!Set.of("trim","lower","upper").contains(transform)||!text&&!transform.equals("trim"))out.add("ETL_TRANSFORM_ORDER",p+"/transforms");
            for(String policy:List.of("missing","null","empty")) {
                String value=str(m,policy);
                if(value.equals("null")&&!c.get("nullable").getAsBoolean())out.add("ETL_NULL_POLICY",p+"/"+policy);
                if(value.equals("default")&&!c.has("default"))out.add("ETL_DEFAULT_REQUIRED",p+"/"+policy);
                if(value.equals("keep")&&!text)out.add("ETL_EMPTY_KEEP_TEXT_ONLY",p+"/"+policy);
            }
        }
        for(JsonElement element:table.getAsJsonArray("columns")) {
            JsonObject c=element.getAsJsonObject();
            if(!mapped.containsKey(str(c,"id"))&&!c.get("nullable").getAsBoolean()&&!c.has("default"))out.add("ETL_REQUIRED_COLUMN_UNMAPPED","/mappings");
        }
        List<String> key=ids(request.getAsJsonArray("conflictKey")),updates=ids(request.getAsJsonArray("updateColumns"));
        boolean upsert=str(request,"mode").equals("upsert");
        if(!str(request,"resumePolicy").equals(upsert?"upsert_replay":"manual_insert"))out.add("ETL_RESUME_POLICY","/resumePolicy");
        if(!upsert&&(!key.isEmpty()||!updates.isEmpty()))out.add("ETL_INSERT_HAS_CONFLICT_OPTIONS","/conflictKey");
        if(upsert) {
            Set<List<String>> keys=new HashSet<>();keys.add(ids(table.getAsJsonArray("primaryKey")));
            for(JsonElement u:table.getAsJsonArray("unique"))keys.add(ids(u.getAsJsonArray()));
            if(key.isEmpty()||new HashSet<>(key).size()!=key.size()||!keys.contains(key))out.add("ETL_EXPLICIT_UNIQUE_KEY","/conflictKey");
            for(String id:key) {
                JsonObject c=column(table,id),m=mapped.get(id);
                if(c==null||c.get("nullable").getAsBoolean()||m==null)out.add("ETL_REQUIRED_MAPPED_KEY","/conflictKey");
                else if(!str(m,"missing").equals("reject")||!str(m,"null").equals("reject")||!str(m,"empty").equals("reject"))out.add("ETL_KEY_MUST_BE_EXPLICIT","/conflictKey");
            }
            if(updates.isEmpty()||new HashSet<>(updates).size()!=updates.size())out.add("ETL_UPDATE_COLUMNS_REQUIRED","/updateColumns");
            for(String id:updates)if(!mapped.containsKey(id)||key.contains(id))out.add("ETL_UPDATE_COLUMN","/updateColumns");
        }
        Set<String> envs=new HashSet<>();
        for(var entry:request.getAsJsonObject("runtime").entrySet())if(!envs.add(entry.getValue().getAsString()))out.add("ETL_DISTINCT_ENV_NAMES","/runtime");
        if(!out.valid())return new B3Generation.Result(out,null,Map.of());
        ArtifactBundle files=new ArtifactBundle();
        for(String name:List.of("etl.py","verify.py","README.md"))files.add(name,resource(name));
        files.add("etl.spec.json",ArtifactBundle.canonical(request)+"\n");
        files.add("configuration.example.json",ArtifactBundle.canonical(request.getAsJsonObject("runtime"))+"\n");
        boolean postgres=str(request,"target").equals("postgresql");
        files.add("requirements.txt",postgres?"psycopg==3.3.6\npsycopg-binary==3.3.6\ntzdata==2026.4\n":"# Python 3.14.3 standard library only.\n");
        // Empty fixtures are deliberate: arbitrary destination checks cannot imply valid synthetic rows.
        files.add("example.json","[]\n");
        String delimiter=str(request.getAsJsonObject("source"),"delimiter");
        files.add("example.csv",String.join(delimiter,sourcesInOrder(mappings))+"\n");
        JsonObject core=new JsonObject();core.addProperty("manifestVersion","1.2.0");core.addProperty("schemaVersion",VERSION);
        core.addProperty("module","etl");core.addProperty("templateVersion",TEMPLATE);core.addProperty("target",str(request,"target"));
        core.addProperty("specificationDigest",ArtifactBundle.sha(ArtifactBundle.canonical(request)));
        core.add("verificationDependencies",new Gson().toJsonTree(postgres?List.of("Python 3.14.3","psycopg 3.3.6","psycopg-binary 3.3.6","tzdata 2026.4","PostgreSQL 17.11"):List.of("Python 3.14.3","SQLite 3.50.4")));
        core.add("limitations",new Gson().toJsonTree(List.of("Existing destination schema required; finite shape checks only","External post-commit checkpoints can replay; insert resume requires explicit acknowledgement","SQLite NUMERIC affinity is approximate; PostgreSQL NUMERIC preserves exact validated values","Finite local files only; no production service execution")));
        return new B3Generation.Result(out,files.finish(core),Map.of("operatorRun",true,"validationOnlyDryRun",true,"checkpointPolicy","external-post-commit"));
    }
    private static boolean boundedNumbers(JsonElement value) {
        if(value.isJsonObject())return value.getAsJsonObject().entrySet().stream().allMatch(e->boundedNumbers(e.getValue()));
        if(value.isJsonArray())return value.getAsJsonArray().asList().stream().allMatch(EtlGeneration::boundedNumbers);
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()){var n=value.getAsBigDecimal();return n.precision()<=128&&Math.abs((long)n.scale())<=100;}
        return true;
    }
    private static List<String> sourcesInOrder(JsonArray mappings) {
        List<String> fields=new ArrayList<>();
        for(JsonElement e:mappings)fields.add("\""+str(e.getAsJsonObject(),"source").replace("\"","\"\"")+"\"");
        return fields;
    }
    private static String resource(String name) {
        try(InputStream stream=EtlGeneration.class.getResourceAsStream("/backendsupport/b4/"+name)) {
            if(stream==null)throw new IllegalStateException("Missing ETL template");
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }catch(IOException e){throw new IllegalStateException("Missing ETL template");}
    }
}
