package backendsupport;

import com.google.gson.JsonObject;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B5Test {
    static JsonObject sample(){return Contracts.resource("contracts/v0.5.0/rest-sample.json");}
    @Test void supportedProjectsAndFrozenTemplates() {
        for(String database:List.of("sqlite","postgresql"))for(String captcha:List.of("off","recaptcha-v3"))for(boolean profile:List.of(false,true)) {
            JsonObject spec=sample();spec.addProperty("database",database);spec.getAsJsonObject("captcha").addProperty("mode",captcha);
            if(!profile)spec.getAsJsonArray("modules").remove(1);
            var result=AuthGeneration.process(spec);assertTrue(result.validation().valid(),()->result.validation().items.toString());assertNotNull(result.bundle());
            assertArrayEquals(result.bundle().zip(),AuthGeneration.process(spec).bundle().zip());assertTrue(result.bundle().files().size()<100);
            assertEquals(profile,result.bundle().files().get("openapi.json").contains("/api/user/profile"));
            assertEquals(com.google.gson.JsonParser.parseString("[\"id\",\"username\",\"role\",\"displayName\",\"preferences\"]"),com.google.gson.JsonParser.parseString(result.bundle().files().get("openapi.json")).getAsJsonObject().getAsJsonObject("components").getAsJsonObject("schemas").getAsJsonObject("Profile").get("required"));
        }
    }
    @Test void invalidIdentifiersModulesOriginsAndSecrets() {
        for(String key:List.of("packageName","project","target")) {
            JsonObject spec=sample();spec.addProperty(key,"../injected");assertFalse(AuthGeneration.process(spec).validation().valid());
        }
        JsonObject spec=sample();spec.addProperty("packageName","example.class");assertFalse(AuthGeneration.process(spec).validation().valid());
        spec=sample();spec.getAsJsonArray("modules").remove(0);assertFalse(AuthGeneration.process(spec).validation().valid());
        spec=sample();spec.getAsJsonArray("origins").set(0,new com.google.gson.JsonPrimitive("http://example.com"));assertFalse(AuthGeneration.process(spec).validation().valid());
        spec=sample();spec.addProperty("secret","never-accepted");assertFalse(AuthGeneration.process(spec).validation().valid());
    }
    @Test void nestedPathsRejectExtractionHazards() {
        for(String path:List.of("/root/a.txt","C:/a.txt","a\\b.txt","../x","a/../x","a/./b","a//b","a/","a\0x","a\nx","con.txt","a/AUX","a/lpt1.java","a/b.","a/b "))
            assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safeProjectPath(path,new HashSet<>()),path);
        for(List<String> pair:List.of(List.of("a/b.txt","A/B.TXT"),List.of("a","a/b"),List.of("a/b","A"))) {
            Set<String> seen=new HashSet<>();ArtifactBundle.safeProjectPath(pair.get(0),seen);
            assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safeProjectPath(pair.get(1),seen));
        }
        Set<String> seen=new HashSet<>();for(String path:List.of("pom.xml","src/main/java/example/auth/App.java","src/main/resources/config.json",".env.example"))ArtifactBundle.safeProjectPath(path,seen);
    }
    @Test void flatPolicyIsStillFlat() {
        assertThrows(IllegalArgumentException.class,()->new ArtifactBundle().add("src/main/a.java","x\n"));
        assertThrows(IllegalArgumentException.class,()->ArtifactBundle.safePath(".env.example",new HashSet<>()));
    }
    @Test void projectAssemblyIsDeterministicAndImmutable() {
        ArtifactBundle a=ArtifactBundle.project(),b=ArtifactBundle.project();
        a.add("src/main/A.java","a\n");a.add("pom.xml","b\n");b.add("pom.xml","b\n");b.add("src/main/A.java","a\n");
        var one=a.finish(new JsonObject());var two=b.finish(new JsonObject());assertArrayEquals(one.zip(),two.zip());
        a.add("extra.txt","extra\n");assertFalse(one.files().containsKey("extra.txt"));
        assertEquals(3,one.files().size());assertThrows(UnsupportedOperationException.class,()->one.files().put("x.txt","x"));
    }
    @Test void projectLimitsIncludeManifest() {
        ArtifactBundle files=ArtifactBundle.project();for(int i=0;i<100;i++)files.add("a/"+i+".txt","");
        assertThrows(ArtifactBundle.Limit.class,()->files.finish(new JsonObject()));
        ArtifactBundle bytes=ArtifactBundle.project();bytes.add("a/x.txt","x".repeat(ArtifactBundle.MAX_BYTES));
        assertThrows(ArtifactBundle.Limit.class,()->bytes.finish(new JsonObject()));
    }
}
