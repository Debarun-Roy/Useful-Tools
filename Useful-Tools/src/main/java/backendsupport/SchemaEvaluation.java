package backendsupport;

import com.google.gson.*;
import java.util.*;
import static backendsupport.SchemaGeneration.*;

/** Diagnostic rules intentionally accept defective models. Never renders or executes SQL. */
public final class SchemaEvaluation {
    public record Diagnostic(String ruleId,String severity,String path,String message,String suggestion,boolean blocking,List<String> blocks) {}
    public record Skipped(String ruleId,String path,String reasonCode) {}
    static final Set<String> TYPES=Set.of("integer","bigint","decimal","text","varchar","boolean","date","timestamp","json");
    private final List<Diagnostic> findings=new ArrayList<>();private final List<Skipped> skipped=new ArrayList<>();private boolean truncated;private boolean errors;
    private void add(String id,String severity,String path,String message,String correction) {
        boolean blocking=severity.equals("error");errors|=blocking;
        if(findings.size()>=100){truncated=true;return;}
        findings.add(new Diagnostic(id,severity,path,message,correction,blocking,blocking?List.of("schema","migration","view"):List.of()));
    }
    private void skip(String rule,String path,String reason){if(skipped.size()<100)skipped.add(new Skipped(rule,path,reason));else truncated=true;}
    static boolean supportedType(JsonObject c) {
        String t=str(c,"type");if(!TYPES.contains(t))return false;
        if(t.equals("decimal")) {if(!c.has("precision")||!c.has("scale")||c.get("precision").getAsInt()<c.get("scale").getAsInt())return false;}
        else if(c.has("precision")||c.has("scale"))return false;
        return t.equals("varchar")==c.has("length");
    }
    public static B3Generation.Result process(JsonObject request,Contracts.Findings validation) {
        SchemaEvaluation engine=new SchemaEvaluation();JsonArray tables=request.getAsJsonObject("schema").getAsJsonArray("tables");
        Map<String,JsonObject> byId=new HashMap<>();int columns=0;
        for(int i=0;i<tables.size();i++) {
            JsonObject t=tables.get(i).getAsJsonObject();String path="/schema/tables/"+i;
            if(byId.put(str(t,"id"),t)!=null)validation.add("AMBIGUOUS_DIAGNOSTIC_ID",path+"/id");
            Set<String> ids=new HashSet<>();for(JsonElement c:t.getAsJsonArray("columns")){columns++;if(!ids.add(str(c.getAsJsonObject(),"id")))validation.add("AMBIGUOUS_DIAGNOSTIC_ID",path+"/columns");}
        }
        if(columns>1000)validation.add("COLUMN_LIMIT","/schema/tables");
        if(!validation.valid())return new B3Generation.Result(validation,null,Map.of());
        for(int i=0;i<tables.size();i++)engine.table(tables.get(i).getAsJsonObject(),"/schema/tables/"+i,byId,str(request,"namingConvention"));
        engine.findings.sort(Comparator.comparing(Diagnostic::path).thenComparing(Diagnostic::ruleId));
        engine.skipped.sort(Comparator.comparing(Skipped::path).thenComparing(Skipped::ruleId));
        Map<String,Object> details=new LinkedHashMap<>();details.put("evaluationCompleted",true);details.put("executableSql",false);
        // No claim that this finite rule set proves all generation conditions.
        details.put("generationEligibility","NOT_DETERMINED_RUN_GENERATOR_VALIDATION");details.put("hasErrors",engine.errors);
        details.put("rulesetVersion",B3Generation.RULESET);details.put("findings",engine.findings);details.put("checksNotRun",engine.skipped);details.put("truncated",engine.truncated);
        ArtifactBundle files=new ArtifactBundle();files.add("findings.json",ArtifactBundle.canonical(new Gson().toJsonTree(details))+"\n");
        ArtifactBundle.Text report=new ArtifactBundle.Text();report.add("# Schema evaluation report\n\nEvaluation completed. Findings may contain errors; this report is not executable SQL or proof of schema validity. No quality score is assigned.\n\n");
        for(Diagnostic f:engine.findings)report.add("- "+f.severity()+" "+f.ruleId()+" at `"+f.path()+"`: "+f.message()+" "+f.suggestion()+"\n");
        report.add("\nChecks not run: "+engine.skipped.size()+". Truncated: "+engine.truncated+". Inspect findings.json for prerequisite reasons and affected generation operations.\n");
        files.add("report.md",report.value());files.add("README.md","# Diagnostic report bundle\n\n"+B3Generation.commonReadme(request)+"This report can be exported despite schema errors. It never executes SQL. Unsupported default text was inspected as inert data. Unique/business-key metadata is explicit; naming is advisory. Run a generator's independent validation before producing SQL.\n");
        files.add("verify.py",B3Generation.resource("verify.py"));
        return new B3Generation.Result(validation,B3Generation.bundle(request,files,false,List.of("Diagnostic report only; errors do not prevent report export","No universal quality or workload-performance score")),details);
    }
    private void table(JsonObject t,String p,Map<String,JsonObject> tables,String convention) {
        if(t.getAsJsonArray("primaryKey").isEmpty())add("MISSING_PRIMARY_KEY","error",p+"/primaryKey","Table has no declared primary key.","Declare an explicit nonnullable primary key before schema generation.");
        if(convention.equals("snake_case"))naming(str(t,"name"),p+"/name");
        JsonArray cols=t.getAsJsonArray("columns");
        for(int i=0;i<cols.size();i++) {
            JsonObject c=cols.get(i).getAsJsonObject();String cp=p+"/columns/"+i;
            if(convention.equals("snake_case"))naming(str(c,"name"),cp+"/name");
            boolean type=supportedType(c);
            if(!type)add("UNSUPPORTED_TYPE","error",cp+"/type","Logical type or its options are unsupported.","Select a supported logical type with consistent precision, scale or length options.");
            if(c.has("default")) {
                JsonObject d=c.getAsJsonObject("default");String kind=str(d,"kind");
                if(kind.equals("expression"))add("UNSAFE_DEFAULT_EXPRESSION","error",cp+"/default","Default expression is inert diagnostic data and cannot be generated.","Use a supported typed literal or timestamp preset.");
                else if(!type)skip("UNSAFE_DEFAULT_EXPRESSION",cp+"/default","UNSUPPORTED_TYPE");
                else if(kind.equals("currentTimestamp")&&!str(c,"type").equals("timestamp") || kind.equals("literal")&&!SchemaGeneration.validLiteral(c,d.get("value"),true))
                    add("UNSAFE_DEFAULT_EXPRESSION","error",cp+"/default","Default is incompatible with the declared type or nullability.","Correct the typed value before generating SQL.");
            }
        }
        JsonArray fks=t.getAsJsonArray("foreignKeys");
        for(int i=0;i<fks.size();i++) {
            JsonObject f=fks.get(i).getAsJsonObject();String fp=p+"/foreignKeys/"+i;JsonObject target=tables.get(str(f,"tableId"));
            List<String> from=ids(f.getAsJsonArray("columns")),to=ids(f.getAsJsonArray("targetColumns"));
            boolean valid=target!=null&&from.size()==to.size()&&new HashSet<>(from).size()==from.size()&&new HashSet<>(to).size()==to.size();
            if(valid) {
                boolean key=to.equals(ids(target.getAsJsonArray("primaryKey")));for(JsonElement u:target.getAsJsonArray("unique"))key|=to.equals(ids(u.getAsJsonArray()));
                valid=key&&from.stream().allMatch(c->column(t,c)!=null)&&to.stream().allMatch(c->column(target,c)!=null);
            }
            if(!valid) {add("INVALID_FOREIGN_KEY_TARGET","error",fp,"Foreign key does not resolve to an ordered declared key with matching arity.","Correct source/target IDs and the referenced primary or unique key.");skip("INCONSISTENT_FOREIGN_KEY_TYPE",fp,"INVALID_REFERENCE");continue;}
            for(int n=0;n<from.size();n++) {
                JsonObject a=column(t,from.get(n)),b=column(target,to.get(n));
                if(!supportedType(a)||!supportedType(b)){skip("INCONSISTENT_FOREIGN_KEY_TYPE",fp+"/columns/"+n,"UNSUPPORTED_TYPE");continue;}
                boolean equal=true;for(String key:List.of("type","precision","scale","length"))equal&=Objects.equals(a.get(key),b.get(key));
                if(!equal)add("INCONSISTENT_FOREIGN_KEY_TYPE","error",fp+"/columns/"+n,"Foreign-key column types or options differ.","Use compatible logical types and options on both sides.");
            }
        }
        Set<String> signatures=new HashSet<>();JsonArray indexes=t.getAsJsonArray("indexes");
        for(int i=0;i<indexes.size();i++) {
            JsonObject index=indexes.get(i).getAsJsonObject();String ip=p+"/indexes/"+i;
            if(ids(index.getAsJsonArray("columns")).stream().anyMatch(c->column(t,c)==null)){skip("REDUNDANT_IDENTICAL_INDEX",ip,"INVALID_REFERENCE");continue;}
            String signature=ArtifactBundle.canonical(index.get("columns"))+":"+index.get("unique");
            if(!signatures.add(signature))add("REDUNDANT_IDENTICAL_INDEX","warning",ip,"An earlier index has identical ordered columns and uniqueness.","Review duplicate maintenance cost; this is not a workload-performance claim.");
        }
        for(String group:List.of("unique","businessKeys")) {
            JsonArray keys=t.getAsJsonArray(group);
            for(int i=0;i<keys.size();i++)for(String id:ids(keys.get(i).getAsJsonArray())) {
                JsonObject c=column(t,id);String kp=p+"/"+group+"/"+i;
                if(c==null)skip("NULLABLE_BUSINESS_KEY",kp,"INVALID_REFERENCE");
                else if(c.get("nullable").getAsBoolean()) {add("NULLABLE_BUSINESS_KEY","warning",kp,"An explicit unique or business key includes a nullable column.","Confirm NULL semantics or plan validated nonnull data and a separate migration.");break;}
            }
        }
    }
    private void naming(String name,String path) {if(!name.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))add("NAMING_INCONSISTENCY","warning",path,"Database name differs from the selected snake_case convention.","Keep the existing name or choose an explicit reviewed rename; this advisory never guesses a rename.");}
}
