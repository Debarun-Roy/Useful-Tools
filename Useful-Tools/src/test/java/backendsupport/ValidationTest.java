package backendsupport;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    private JsonElement decode(String json) throws Exception { return BoundedJson.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))); }
    private JsonObject sample(String id) { return Contracts.sample(id).getAsJsonObject(); }
    private void code(JsonElement value, String code) { assertTrue(SpecificationValidator.validate(value).items.stream().anyMatch(f -> f.ruleId().equals(code)),code); }
    @Test void allImmutableSamplesValidateAndAreCopied() {
        for(String id:Contracts.sampleIds()) assertTrue(SpecificationValidator.validate(sample(id)).valid(),id + ": " + SpecificationValidator.validate(sample(id)).items);
        JsonObject copy=sample("customers-orders"); copy.remove("spec"); assertNotNull(sample("customers-orders").get("spec"));
    }
    @Test void decoderRejectsAmbiguityAndInvalidUtf8() {
        for(String json:new String[]{"{\"a\":1,\"a\":2}","{a:1}","{}{}","[1,]","NaN","/*x*/{}",""})
            assertThrows(BoundedJson.Rejected.class,()->decode(json),json);
        assertThrows(BoundedJson.Rejected.class,()->BoundedJson.read(new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf})));
    }
    @Test void decoderBoundsActualBytesDepthStringsCollectionsAndNodes() {
        assertEquals(413,assertThrows(BoundedJson.Rejected.class,()->decode(" ".repeat(1048577))).status);
        assertThrows(BoundedJson.Rejected.class,()->decode("[".repeat(34)+"0"+"]".repeat(34)));
        assertThrows(BoundedJson.Rejected.class,()->decode("\""+"x".repeat(4097)+"\""));
        assertThrows(BoundedJson.Rejected.class,()->decode("["+"0,".repeat(1000)+"0]"));
        String row="["+"0,".repeat(999)+"0]";
        assertThrows(BoundedJson.Rejected.class,()->decode("["+(row+",").repeat(20)+row+"]"));
    }
    @Test void structuralRejectsUnknownVersionTypeAndTargetsWithoutLeakingValues() {
        JsonObject s=sample("customers-orders"); s.addProperty("private-secret-field","sentinel-value");
        var findings=SpecificationValidator.validate(s); assertFalse(findings.valid());
        String json=new Gson().toJson(findings.items); assertFalse(json.contains("private-secret-field")); assertFalse(json.contains("sentinel-value"));
        s=sample("customers-orders");s.addProperty("schemaVersion","999");code(s,"UNSUPPORTED_VALUE");
        s=sample("customers-orders");s.getAsJsonObject("target").addProperty("framework","fastapi");code(s,"UNSUPPORTED_VALUE");
        s=sample("customers-orders");s.addProperty("spec",true);code(s,"INVALID_TYPE");
        s=sample("java-auth");s.getAsJsonObject("target").addProperty("language","python");code(s,"INVALID_COMBINATION");
    }
    @Test void duplicateAndUnknownReferencesFail() {
        JsonObject s=sample("customers-orders"); JsonArray tables=s.getAsJsonObject("spec").getAsJsonArray("tables");tables.add(tables.get(0).deepCopy());code(s,"DUPLICATE_IDENTIFIER");
        s=sample("customers-orders");s.getAsJsonObject("spec").getAsJsonArray("tables").get(0).getAsJsonObject().getAsJsonArray("primaryKey").add("missing");code(s,"UNKNOWN_REFERENCE");
        s=sample("valid-view");s.getAsJsonObject("spec").getAsJsonArray("sources").add("missing");code(s,"UNKNOWN_REFERENCE");
    }
    @Test void dependentOptionsFail() {
        JsonObject s=sample("java-auth");s.getAsJsonObject("spec").getAsJsonArray("modules").remove(new JsonPrimitive("csrf"));code(s,"CSRF_REQUIRED");
        s=sample("csv-etl");s.getAsJsonObject("spec").addProperty("mode","upsert");s.getAsJsonObject("spec").add("uniqueKey",new JsonArray());code(s,"UPSERT_KEY_REQUIRED");
        s=sample("csv-etl");s.getAsJsonObject("spec").getAsJsonObject("destination").addProperty("dialect","postgresql");
        s.getAsJsonObject("target").addProperty("dialect","sqlite");code(s,"DIALECT_MISMATCH");
    }
    @Test void findingsBoundedAndDeterministic() {
        JsonObject bad=new JsonObject();JsonArray a=new JsonArray();for(int i=0;i<1000;i++)a.add(new JsonObject());
        bad=sample("customers-orders");bad.getAsJsonObject("spec").add("tables",a);
        var one=SpecificationValidator.validate(bad);var two=SpecificationValidator.validate(bad);
        assertEquals(100,one.items.size());assertTrue(one.truncated);assertFalse(one.valid());assertEquals(one.items,two.items);
        assertTrue(new Gson().toJson(one.items).length()<262144);
    }
    @Test void aggregateColumnsAreBounded() {
        JsonObject s=sample("customers-orders");JsonArray tables=new JsonArray();
        for(int i=0;i<11;i++) {JsonObject t=s.getAsJsonObject("spec").getAsJsonArray("tables").get(0).getAsJsonObject().deepCopy();t.addProperty("id","t"+i);t.addProperty("name","t"+i);JsonArray cols=new JsonArray();
            for(int j=0;j<100;j++){JsonObject c=t.getAsJsonArray("columns").get(0).getAsJsonObject().deepCopy();c.addProperty("id","c"+j);c.addProperty("name","c"+j);cols.add(c);}t.add("columns",cols);tables.add(t);}
        s.getAsJsonObject("spec").add("tables",tables);code(s,"TOTAL_COLUMN_LIMIT");
    }
    @Test void frozenGenerationFixturesAgreeWithStructuralContract() throws Exception {
        JsonArray fixtures=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("../contracts/backend-support/v0.1.0/fixtures.json"))).getAsJsonArray();
        int count=0;
        for(JsonElement item:fixtures) {
            JsonObject fixture=item.getAsJsonObject();
            if(!fixture.get("model").getAsString().equals("GenerationRequest"))continue;
            assertEquals(fixture.getAsJsonObject("expected").get("structural").getAsBoolean(),Contracts.structural(fixture.get("value")).valid(),fixture.get("name").getAsString());count++;
        }
        assertTrue(count>=10);
    }
    @Test void destructiveMigrationAndCaptchaDependenciesAreExplicit() {
        JsonObject s=sample("safe-add-column");
        s.getAsJsonObject("spec").getAsJsonObject("after").getAsJsonArray("tables").get(0).getAsJsonObject().getAsJsonArray("columns").remove(1);
        s.getAsJsonObject("spec").addProperty("destructiveAcknowledged",false);code(s,"DESTRUCTIVE_MIGRATION");
        s=sample("java-auth");s.getAsJsonObject("spec").getAsJsonObject("captcha").addProperty("enabled",true);
        s.getAsJsonObject("spec").getAsJsonObject("captcha").addProperty("provider","none");code(s,"CAPTCHA_OPTIONS_MISMATCH");
    }
    @Test void catalogTargetsSamplesAndVocabularyStayInSync() {
        for(JsonElement m:Contracts.catalog().getAsJsonArray("modules")) {
            JsonObject module=m.getAsJsonObject();
            for(JsonElement id:module.getAsJsonArray("sampleIds")) {
                JsonObject s=sample(id.getAsString());assertEquals(module.get("id"),s.get("module"));
                assertTrue(module.getAsJsonArray("targets").contains(s.get("target")));
            }
        }
        vocabulary(Contracts.SCHEMA);
    }
    private void vocabulary(JsonObject schema) {
        var supported=java.util.Set.of("$schema","$id","$defs","$ref","type","enum","const","properties","required","additionalProperties","items","minItems","maxItems","minLength","maxLength","pattern","minimum","maximum","allOf","if","then","oneOf");
        for(String key:schema.keySet())assertTrue(supported.contains(key),"Interpreter must implement schema keyword: "+key);
        for(String key:java.util.List.of("$defs","properties"))if(schema.has(key)) for(var entry:schema.getAsJsonObject(key).entrySet())vocabulary(entry.getValue().getAsJsonObject());
        for(String key:java.util.List.of("items","if","then"))if(schema.has(key))vocabulary(schema.getAsJsonObject(key));
        for(String key:java.util.List.of("allOf","oneOf"))if(schema.has(key))for(JsonElement item:schema.getAsJsonArray(key))vocabulary(item.getAsJsonObject());
    }
}
