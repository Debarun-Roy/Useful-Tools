package backendsupport;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class B3Test {
    static JsonObject sample(String module){JsonObject s=Contracts.resource("contracts/v0.3.0/"+module+"-sample.json");if(module.equals("migration"))s.addProperty("externalDependenciesReviewed",true);return s;}
    static JsonObject table(JsonObject s,String field){return s.getAsJsonObject(field).getAsJsonArray("tables").get(0).getAsJsonObject();}
    static JsonObject col(JsonObject t,int n){return t.getAsJsonArray("columns").get(n).getAsJsonObject();}
    static B3Generation.Result valid(JsonObject s){var r=B3Generation.process(s);assertTrue(r.validation().valid(),()->new Gson().toJson(r.validation().items));assertNotNull(r.bundle());return r;}
    static void invalid(JsonObject s,String code){var r=B3Generation.process(s);assertNull(r.bundle());assertTrue(r.validation().items.stream().anyMatch(f->f.ruleId().equals(code)),()->new Gson().toJson(r.validation().items));}
    @Test void samplesBothDialectsDeterministic(){for(String module:List.of("migration","view","evaluator"))for(String target:List.of("sqlite","postgresql")){var s=sample(module);s.addProperty("target",target);for(String f:List.of("before","after","schema"))if(s.has(f)&&s.getAsJsonObject(f).has("target"))s.getAsJsonObject(f).addProperty("target",target);var a=valid(s);assertArrayEquals(a.bundle().zip(),valid(s).bundle().zip());assertEquals(a.bundle().digest(),valid(s).bundle().digest());}}
    @Test void migrationAddDefaultsIndexesNoopAndRollback(){var s=sample("migration");var a=table(s,"after");JsonObject c=col(a,3);c.addProperty("nullable",false);c.add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":\"O'Reilly 世界\"}"));a.getAsJsonArray("indexes").add(JsonParser.parseString("{\"id\":\"email_index\",\"name\":\"email_index\",\"unique\":false,\"columns\":[\"email\"]}"));var r=valid(s);assertTrue(r.bundle().files().get("up.sql").contains("O''Reilly 世界"));assertFalse(r.bundle().files().containsKey("down.sql"));s.add("after",s.get("before").deepCopy());assertEquals(true,valid(s).details().get("noOp"));}
    @Test void migrationEveryUnsupportedDifferenceBlocksMixedPlan(){for(String field:List.of("type","length","nullable","default")){var s=sample("migration");var c=col(table(s,"after"),1);switch(field){case "type" -> {c.addProperty("type","text");c.remove("length");}case "length"->c.addProperty("length",81);case "nullable"->c.addProperty("nullable",true);case "default"->c.add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":\"x\"}"));}invalid(s,field.equals("nullable")?"NULLABILITY_BACKFILL_REQUIRED":"COLUMN_CHANGE_UNSUPPORTED");}var s=sample("migration");table(s,"after").getAsJsonArray("columns").remove(2);s.addProperty("acknowledgeDestructive",true);invalid(s,"DESTRUCTIVE_COLUMN_DROP");s=sample("migration");table(s,"after").getAsJsonArray("indexes").add(JsonParser.parseString("{\"id\":\"u\",\"name\":\"u\",\"unique\":true,\"columns\":[\"email\"]}"));invalid(s,"UNIQUE_INDEX_PRECHECK_UNSUPPORTED");s=sample("migration");s.addProperty("externalDependenciesReviewed",false);invalid(s,"EXTERNAL_REVIEW_REQUIRED");}
    @Test void explicitRenameIdentityAndReverse(){var s=sample("migration");s.add("after",s.get("before").deepCopy());table(s,"after").addProperty("name","clients");invalid(s,"EXPLICIT_RENAME_REQUIRED");s.getAsJsonObject("renames").getAsJsonArray("tables").add(JsonParser.parseString("{\"tableId\":\"customers\",\"fromName\":\"customers\",\"toName\":\"clients\"}"));assertTrue(valid(s).bundle().files().get("down.sql").contains("RENAME TO \"customers\""));s.getAsJsonObject("renames").getAsJsonArray("tables").add(s.getAsJsonObject("renames").getAsJsonArray("tables").get(0).deepCopy());invalid(s,"EXPLICIT_RENAME_REQUIRED");}
    @Test void viewRejectsUnknownAliasesNullEqualityAndGrouping(){var s=sample("view");s.getAsJsonObject("source").addProperty("tableId","missing");invalid(s,"UNKNOWN_VIEW_SOURCE");s=sample("view");s.getAsJsonArray("groupBy").remove(0);invalid(s,"UNGROUPED_PROJECTION");s=sample("view");s.add("where",JsonParser.parseString("{\"op\":\"eq\",\"left\":{\"kind\":\"column\",\"sourceAlias\":\"c\",\"columnId\":\"name\"},\"right\":{\"kind\":\"literal\",\"value\":null}}"));invalid(s,"USE_NULL_PREDICATE");}
    @Test void evaluationErrorsAreReportOnly(){var s=sample("evaluator");var t=table(s,"schema");t.add("primaryKey",new JsonArray());var r=valid(s);assertEquals(true,r.details().get("evaluationCompleted"));assertEquals(true,r.details().get("hasErrors"));assertEquals(false,r.details().get("executableSql"));assertTrue(r.bundle().files().containsKey("report.md"));assertFalse(r.bundle().files().keySet().stream().anyMatch(f->f.endsWith(".sql")));}
    @Test void evaluatorRulePositiveNegativeAndNotApplicable(){
        for(String rule:List.of("MISSING_PRIMARY_KEY","INVALID_FOREIGN_KEY_TARGET","INCONSISTENT_FOREIGN_KEY_TYPE","REDUNDANT_IDENTICAL_INDEX","NULLABLE_BUSINESS_KEY","UNSAFE_DEFAULT_EXPRESSION","UNSUPPORTED_TYPE","NAMING_INCONSISTENCY")) {
            var s=sample("evaluator"); // Replace intentionally defective sample with a known good B2 diagnostic model.
            s.add("schema",sample("migration").get("before").deepCopy());s.getAsJsonObject("schema").keySet().removeIf(k->!k.equals("tables"));
            for(var e:s.getAsJsonObject("schema").getAsJsonArray("tables"))e.getAsJsonObject().add("businessKeys",new JsonArray());
            assertFalse(has(valid(s),rule),rule+" negative");var t=table(s,"schema");
            switch(rule) {
                case "MISSING_PRIMARY_KEY" -> t.add("primaryKey",new JsonArray());
                case "INVALID_FOREIGN_KEY_TARGET" -> s.getAsJsonObject("schema").getAsJsonArray("tables").get(1).getAsJsonObject().getAsJsonArray("foreignKeys").get(0).getAsJsonObject().addProperty("tableId","missing");
                case "INCONSISTENT_FOREIGN_KEY_TYPE" -> col(s.getAsJsonObject("schema").getAsJsonArray("tables").get(1).getAsJsonObject(),1).addProperty("type","bigint");
                case "REDUNDANT_IDENTICAL_INDEX" -> {var ix=JsonParser.parseString("{\"id\":\"a\",\"name\":\"a\",\"unique\":false,\"columns\":[\"name\"]}");t.getAsJsonArray("indexes").add(ix);t.getAsJsonArray("indexes").add(ix.deepCopy());}
                case "NULLABLE_BUSINESS_KEY" -> col(t,1).addProperty("nullable",true);
                case "UNSAFE_DEFAULT_EXPRESSION" -> col(t,1).add("default",JsonParser.parseString("{\"kind\":\"expression\",\"value\":\"DROP TABLE secret;\"}"));
                case "UNSUPPORTED_TYPE" -> col(t,0).addProperty("type","unknown");
                case "NAMING_INCONSISTENCY" -> t.addProperty("name","Bad Name");
            }
            assertTrue(has(valid(s),rule),rule+" positive");s.getAsJsonObject("schema").add("tables",new JsonArray());assertFalse(has(valid(s),rule),rule+" N/A");
        }
    }
    static boolean has(B3Generation.Result r,String rule){return ((List<SchemaEvaluation.Diagnostic>)r.details().get("findings")).stream().anyMatch(f->f.ruleId().equals(rule));}
    @Test void boundsVersionsAndUnknownFields(){var s=sample("view");s.addProperty("rawSql","DROP TABLE anything");assertFalse(B3Generation.process(s).validation().valid());s=sample("evaluator");s.addProperty("rulesetVersion","future");assertFalse(B3Generation.process(s).validation().valid());s=sample("migration");s.getAsJsonObject("after").addProperty("target","postgresql");invalid(s,"DIALECT_MISMATCH");}
    @Test void runnerGuardCannotShadowSchemaObject(){var s=sample("migration");for(String field:List.of("before","after"))table(s,field).addProperty("name","ut_b3_guard");invalid(s,"MIGRATION_INTERNAL_NAME_COLLISION");}
    @Test void diagnosticTruncationSkipsAndAmbiguousIdentity(){
        var s=sample("evaluator");JsonObject t=table(s,"schema");t.add("primaryKey",new JsonArray());t.addProperty("name","Bad Name");JsonArray many=new JsonArray();
        for(int i=0;i<100;i++){var next=t.deepCopy();next.addProperty("id","t"+i);many.add(next);}s.getAsJsonObject("schema").add("tables",many);
        var r=valid(s);assertEquals(true,r.details().get("truncated"));assertEquals(100,((List<?>)r.details().get("findings")).size());assertTrue(((List<?>)r.details().get("checksNotRun")).size()<=100);
        many.get(1).getAsJsonObject().addProperty("id","t0");invalid(s,"AMBIGUOUS_DIAGNOSTIC_ID");
        s=sample("evaluator");var fk=s.getAsJsonObject("schema").getAsJsonArray("tables").get(1).getAsJsonObject().getAsJsonArray("foreignKeys").get(0).getAsJsonObject();fk.addProperty("tableId","missing");r=valid(s);assertTrue(has(r,"INVALID_FOREIGN_KEY_TARGET"));assertFalse(((List<SchemaEvaluation.Diagnostic>)r.details().get("findings")).stream().anyMatch(f->f.ruleId().equals("INCONSISTENT_FOREIGN_KEY_TYPE")&&f.path().startsWith("/schema/tables/1/foreignKeys/0")));assertFalse(((List<?>)r.details().get("checksNotRun")).isEmpty());
    }
    @Test void combinedProcessingBoundAndNumericComplexity(){
        var s=sample("migration");for(String field:List.of("before","after")){JsonArray tables=new JsonArray();for(int j=0;j<6;j++){JsonObject t=table(s,field).deepCopy();t.addProperty("id","t"+j);t.addProperty("name","t"+j);JsonArray columns=t.getAsJsonArray("columns");for(int i=0;i<82;i++)columns.add(JsonParser.parseString("{\"id\":\"c"+i+"\",\"name\":\"c"+i+"\",\"type\":\"text\",\"nullable\":true}"));tables.add(t);}s.getAsJsonObject(field).add("tables",tables);}invalid(s,"COMBINED_COLUMN_LIMIT");
        s=sample("evaluator");col(table(s,"schema"),0).add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":1e999999999}"));invalid(s,"NUMERIC_COMPLEXITY");
    }
    @Test void everyB3BundleIsTimezoneIndependentAndBounded(){
        TimeZone original=TimeZone.getDefault();try {for(String module:List.of("migration","view","evaluator")){byte[] prior=null;for(String zone:List.of("UTC","Asia/Kolkata","America/Los_Angeles")){TimeZone.setDefault(TimeZone.getTimeZone(zone));var bundle=valid(sample(module)).bundle();byte[] bytes=bundle.zip();if(prior!=null)assertArrayEquals(prior,bytes);prior=bytes;assertTrue(bytes.length<ArtifactBundle.MAX_BYTES);}}}finally{TimeZone.setDefault(original);}
        ArtifactBundle files=new ArtifactBundle();assertThrows(ArtifactBundle.Limit.class,()->files.add("large.sql","x".repeat(ArtifactBundle.MAX_BYTES+1)));assertThrows(ArtifactBundle.ResponseLimit.class,()->ArtifactBundle.previewBytes(Map.of("data","x".repeat(1024)),128));
        assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safePath("before..schema.json",new HashSet<>()));
    }
}
