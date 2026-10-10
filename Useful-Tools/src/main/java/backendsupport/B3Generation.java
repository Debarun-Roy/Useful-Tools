package backendsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static backendsupport.SchemaGeneration.str;

/** B3 module dispatch inside the existing authenticated, bounded generation boundary. */
public final class B3Generation {
    public static final String VERSION="0.3.0",RULESET="schema-rules-0.3.0";
    public static final Set<String> MODULES=Set.of("migration","view","evaluator");
    static final JsonObject CONTRACT=Contracts.resource("contracts/v0.3.0/models.schema.json");
    public record Result(Contracts.Findings validation,ArtifactBundle.Bundle bundle,Map<String,Object> details) {}
    private B3Generation() {}
    public static Result process(JsonElement input) {
        Contracts.Findings out=Contracts.structural(input,CONTRACT);
        if(!out.valid())
            return new Result(out,null,Map.of());
        JsonObject request=input.getAsJsonObject().deepCopy();
        if(!boundedNumbers(request)){
            out.add("NUMERIC_COMPLEXITY","/");
            return new Result(out,null,Map.of());
        }
        return switch(str(request,"module")) {
            case "migration" -> MigrationGeneration.process(request,out);
            case "view" -> ViewGeneration.process(request,out);
            case "evaluator" -> SchemaEvaluation.process(request,out);
            default -> throw new IllegalStateException("Unsupported bundled module");
        };
    }
    private static boolean boundedNumbers(JsonElement e) {
        if(e.isJsonObject())
            return e.getAsJsonObject().entrySet().stream().allMatch(p->boundedNumbers(p.getValue()));
        if(e.isJsonArray())
            return e.getAsJsonArray().asList().stream().allMatch(B3Generation::boundedNumbers);
        if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()) {
            var n=e.getAsBigDecimal();
            return n.precision()<=128&&Math.abs((long)n.scale())<=100;
        }
        return true;
    }
    static SchemaGeneration.Model schema(JsonObject request,String field,Contracts.Findings out) {
        var validated=SchemaGeneration.validate(request.get(field));
        for(var f:validated.findings().items)
            if(f.blocking())
                out.add(f.ruleId(),"/"+field+f.path());
        if(validated.model()!=null) {
            if(!str(request,"target").equals(str(validated.model().request(),"target")))
                out.add("DIALECT_MISMATCH","/"+field+"/target");
            request.add(field,validated.model().request());
        }
        return validated.model();
    }
    static SchemaDialect dialect(JsonObject request){
        return str(request,"target").equals("sqlite")?new SchemaDialect.SQLite():new SchemaDialect.PostgreSQL();
    }
    static Map<String,JsonObject> indexed(JsonArray array){
        Map<String,JsonObject> map=new LinkedHashMap<>();
        for(JsonElement e:array)
            map.put(str(e.getAsJsonObject(),"id"),e.getAsJsonObject());
        return map;
    }
    static String resource(String name) {
        try(InputStream stream=B3Generation.class.getResourceAsStream("/backendsupport/b3/"+name)) {
            if(stream==null)
                throw new IllegalStateException("Missing bundled template");
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }
        catch(IOException e){
            throw new IllegalStateException("Missing bundled template");}
    }
    static ArtifactBundle.Bundle bundle(JsonObject request,ArtifactBundle artifacts,boolean executable,List<String> limitations) {
        JsonObject core=new JsonObject();core.addProperty("manifestVersion","1.1.0");core.addProperty("schemaVersion",VERSION);
        core.addProperty("module",str(request,"module"));core.addProperty("templateVersion",str(request,"templateVersion"));core.addProperty("target",str(request,"target"));
        if(request.has("rulesetVersion"))core.addProperty("rulesetVersion",str(request,"rulesetVersion"));
        core.addProperty("specificationDigest",ArtifactBundle.sha(ArtifactBundle.canonical(request)));
        core.addProperty("executableSql",executable);core.addProperty("verificationRuntime",str(request,"target").equals("sqlite")?"SQLite 3.50.4":"PostgreSQL 17.11");
        core.add("verificationDependencies",new Gson().toJsonTree(List.of("Python 3.14.3",str(request,"target").equals("sqlite")?"stdlib sqlite3":"psql 17.11")));
        core.add("limitations",new Gson().toJsonTree(limitations));return artifacts.finish(core);
    }
    static String commonReadme(JsonObject request) {
        return "Template: "+str(request,"templateVersion")+"; contract 0.3.0; target "+str(request,"target")+".\n\n"
            +"Artifacts are UTF-8/LF. Manifest payload hashes include exact file bytes; manifest.json is excluded from its own inventory. Manifest digest hashes canonical core, input digest hashes canonical full request, both without trailing LF. Canonical JSON sorts object keys, preserves arrays and emits plain minimal numbers. ZIP paths are sorted, flat, fixed and use STORED entries with 1980-01-02 DOS metadata and no timezone extra fields. Limits: 100 files / 5 MiB including manifest.\n\n"
            +"Verify integrity before use with python verify.py. No specification, credentials or customer connection is retained by UsefulTools. SQL runs only in your own environment.\n\n";
    }
}
