package backendsupport;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationTest {
    static JsonObject sample(){return Contracts.resource("contracts/v0.2.0/customers-orders.json");}
    static JsonObject table(JsonObject s){return s.getAsJsonArray("tables").get(0).getAsJsonObject();}
    static JsonObject col(JsonObject s){return table(s).getAsJsonArray("columns").get(0).getAsJsonObject();}
    static ArtifactBundle.Bundle bundle(JsonObject s){var v=SchemaGeneration.validate(s);assertTrue(v.findings().valid(),v.findings().items.toString());return ArtifactBundle.assemble(v.model());}
    static void invalid(JsonObject s,String code){var v=SchemaGeneration.validate(s);assertFalse(v.findings().valid());assertNull(v.model());assertTrue(v.findings().items.stream().anyMatch(f->f.ruleId().equals(code)),v.findings().items.toString());}
    @Test void contractsAndTypes(){
        var s=sample();assertTrue(SchemaGeneration.validate(s).findings().valid());
        s.addProperty("rawSql","DROP TABLE x");invalid(s,"UNKNOWN_FIELD");
        s=sample();col(s).addProperty("nullable",true);invalid(s,"PRIMARY_KEY_NULLABLE");
        s=sample();col(s).addProperty("length",50);invalid(s,"VARCHAR_LENGTH_REQUIRED_ONLY_FOR_VARCHAR");
        s=sample();col(s).add("default",JsonParser.parseString("{\"kind\":\"raw\",\"value\":\"now()\"}"));invalid(s,"INVALID_COMBINATION");
        s=sample();col(s).add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":2147483648}"));invalid(s,"INVALID_TYPED_LITERAL");
        s=sample();col(s).add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":1e999999999}"));invalid(s,"INVALID_TYPED_LITERAL");
    }
    @Test void namesAndNamespaces(){
        var s=sample();table(s).addProperty("name","select\"客户");assertTrue(bundle(s).files().get("schema.sql").contains("\"select\"\"客户\""));
        s=sample();table(s).addProperty("name","a\nDROP");invalid(s,"UNSUPPORTED_IDENTIFIER");
        s=sample();table(s).addProperty("name","客".repeat(22));invalid(s,"UNSUPPORTED_IDENTIFIER");
        s=sample();table(s).addProperty("name","ORDERS");invalid(s,"EMITTED_NAME_COLLISION");
        s=sample();table(s).addProperty("name","ut_pk_customers");invalid(s,"EMITTED_NAME_COLLISION");
    }
    @Test void relationshipsAndCycles(){
        var s=sample();var f=s.getAsJsonArray("tables").get(1).getAsJsonObject().getAsJsonArray("foreignKeys").get(0).getAsJsonObject();
        f.addProperty("onDelete","SET NULL");invalid(s,"SET_NULL_REQUIRES_NULLABLE");
        s=sample();col(s).addProperty("type","bigint");invalid(s,"FOREIGN_KEY_TYPE_MISMATCH");
        s=sample();table(s).getAsJsonArray("foreignKeys").add(JsonParser.parseString("{\"columns\":[\"id\"],\"tableId\":\"orders\",\"targetColumns\":[\"id\"],\"onDelete\":\"NO ACTION\",\"onUpdate\":\"NO ACTION\"}"));invalid(s,"CYCLIC_DEPENDENCY_UNSUPPORTED");
        s=sample();table(s).getAsJsonArray("foreignKeys").add(JsonParser.parseString("{\"columns\":[\"id\"],\"tableId\":\"customers\",\"targetColumns\":[\"id\"],\"onDelete\":\"NO ACTION\",\"onUpdate\":\"NO ACTION\"}"));assertTrue(SchemaGeneration.validate(s).findings().valid());
    }
    @Test void checksAndTypedDefaults(){
        var s=sample();table(s).getAsJsonArray("checks").add(JsonParser.parseString("{\"column\":\"id\",\"op\":\"sql\",\"value\":0}"));invalid(s,"UNSUPPORTED_VALUE");
        s=sample();col(s).add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":null}"));invalid(s,"NULL_LITERAL_CONFLICT");
        s=sample();col(s).add("default",JsonParser.parseString("{\"kind\":\"currentTimestamp\"}"));invalid(s,"TIMESTAMP_DEFAULT_TYPE");
        s=sample();table(s).getAsJsonArray("columns").get(1).getAsJsonObject().add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":\"O'Brien; -- \\\\ path\"}"));assertTrue(bundle(s).files().get("schema.sql").contains("O''Brien; -- \\ path"));
    }
    @Test void deterministicNormalizationAndArchives()throws Exception {
        var s=sample();var a=bundle(s);var t=s.getAsJsonArray("tables");JsonElement first=t.remove(0);t.add(first);var b=bundle(s);
        assertEquals(a.digest(),b.digest());assertArrayEquals(a.zip(),b.zip());
        JsonObject manifest=a.manifest();assertEquals(a.digest(),ArtifactBundle.sha(ArtifactBundle.canonical(manifest.get("core"))));
        var inventory=manifest.getAsJsonObject("core").getAsJsonArray("files");assertEquals(4,inventory.size());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(a.zip()),StandardCharsets.UTF_8)) {
            int count=0;ZipEntry entry;while((entry=zip.getNextEntry())!=null){count++;assertEquals(ZipEntry.STORED,entry.getMethod());assertArrayEquals(a.files().get(entry.getName()).getBytes(StandardCharsets.UTF_8),zip.readAllBytes());}assertEquals(5,count);
        }
        col(s).addProperty("name","renamed");assertNotEquals(a.digest(),bundle(s).digest());
    }
    @Test void pathsAndBounds(){
        for(String path:List.of("../x.sql","/x.sql","C:x.sql","a\\x.sql","a/x.sql","x\n.sql",".hidden.sql","x..sql"))assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safePath(path,new HashSet<>()));
        Set<String> paths=new HashSet<>();ArtifactBundle.safePath("A.sql",paths);assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safePath("a.sql",paths));
        ArtifactBundle files=new ArtifactBundle();files.add("max.txt","x".repeat(ArtifactBundle.MAX_BYTES));assertThrows(ArtifactBundle.Limit.class,()->files.add("more.txt","x"));
        ArtifactBundle many=new ArtifactBundle();for(int i=0;i<100;i++)many.add("a"+i+".txt","");assertThrows(ArtifactBundle.Limit.class,()->many.add("extra.txt",""));
        ArtifactBundle.Text text=new ArtifactBundle.Text();text.add("x".repeat(ArtifactBundle.MAX_BYTES));assertThrows(ArtifactBundle.Limit.class,()->text.add("x"));
    }
    @Test void concurrencyBound(){assertTrue(GenerationController.SLOTS.tryAcquire(2));try{assertFalse(GenerationController.SLOTS.tryAcquire());}finally{GenerationController.SLOTS.release(2);}assertEquals(2,GenerationController.SLOTS.availablePermits());}
    @Test void zipIsIndependentOfHostTimezone()throws Exception {
        var bundle=bundle(sample());TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));byte[] a=bundle.zip();
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));byte[] b=bundle.zip();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));assertArrayEquals(a,b);assertArrayEquals(a,bundle.zip());
            try(var zip=new ZipInputStream(new ByteArrayInputStream(a))) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                    assertEquals(java.time.LocalDateTime.of(1980,1,2,0,0),entry.getTimeLocal());
                    assertNull(entry.getExtra());
                }
            }
        } finally {TimeZone.setDefault(original);}
    }
    @Test void escapedResponseBudgetAndJsonLiteralBounds(){
        assertThrows(ArtifactBundle.ResponseLimit.class,()->ArtifactBundle.previewBytes("<".repeat(100),128));
        assertTrue(ArtifactBundle.previewBytes("<".repeat(100),1024).length>500);
        for(String value:List.of("{\"n\":1e999999999}","{\"s\":\"\\u0000\"}","{\"s\":\"\\ud800\"}")) {
            var s=sample();JsonObject c=s.getAsJsonArray("tables").get(1).getAsJsonObject().getAsJsonArray("columns").get(4).getAsJsonObject();
            c.getAsJsonObject("default").addProperty("value",value);invalid(s,"INVALID_TYPED_LITERAL");
        }
    }
    @Test void dialectsAndExportFixture()throws Exception {
        for(String target:List.of("sqlite","postgresql")) {
            var s=sample();s.addProperty("target",target);var b=bundle(s);String sql=b.files().get("schema.sql");assertFalse(sql.contains("DROP "));assertFalse(sql.contains("IF NOT EXISTS"));
            assertTrue(sql.contains(target.equals("sqlite")?"PRAGMA foreign_keys = ON":"TIMESTAMPTZ"));
            Path dir=Path.of("target/b2-fixtures");Files.createDirectories(dir);Files.write(dir.resolve(target+".zip"),b.zip());
        }
    }
}
