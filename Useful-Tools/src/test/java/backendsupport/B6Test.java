package backendsupport;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class B6Test {
    static JsonObject sample(){return Contracts.resource("contracts/v0.6.0/rest-sample.json");}
    @Test void eightDeterministicProjects() {
        for(String db:List.of("sqlite","postgresql"))for(String captcha:List.of("off","recaptcha-v3"))for(boolean profile:List.of(false,true)) {
            JsonObject s=sample();s.addProperty("database",db);s.getAsJsonObject("captcha").addProperty("mode",captcha);if(!profile)s.getAsJsonArray("modules").remove(1);
            var r=PythonAuthGeneration.process(s);assertTrue(r.validation().valid(),()->r.validation().items.toString());
            assertArrayEquals(r.bundle().zip(),PythonAuthGeneration.process(s).bundle().zip());
            assertTrue(r.bundle().files().containsKey("auth_app/app.py"));assertFalse(r.bundle().files().containsKey("pom.xml"));
            assertEquals(profile,r.bundle().files().get("openapi.json").contains("/api/user/profile"));
        }
    }
    @Test void rejectsPythonIdentifiersAndJavaFields() {
        for(String value:List.of("class","json","datetime","dataclasses","typing_extensions","anyio","con","../escape","x.y","hello\nworld","import os","😀")) {
            JsonObject s=sample();s.addProperty("pythonModule",value);assertFalse(PythonAuthGeneration.process(s).validation().valid(),value);
        }
        JsonObject s=sample();s.addProperty("packageName","example.auth");assertFalse(PythonAuthGeneration.process(s).validation().valid());
        s=sample();s.getAsJsonObject("runtime").addProperty("redisEnv","AUTH_DATABASE");assertFalse(PythonAuthGeneration.process(s).validation().valid());
        s=sample();s.getAsJsonObject("runtime").addProperty("redisEnv","WEB_CONCURRENCY");assertFalse(PythonAuthGeneration.process(s).validation().valid());
        s=sample();s.getAsJsonArray("modules").remove(0);assertFalse(PythonAuthGeneration.process(s).validation().valid());
    }
    @Test void refusesInjectionAndOldVersions() {
        for(String key:List.of("project","pythonModule","schemaVersion","templateVersion","target")) {
            JsonObject s=sample();s.addProperty(key,"quote\"\\\nimport os");assertFalse(PythonAuthGeneration.process(s).validation().valid());
        }
        JsonObject s=sample();s.getAsJsonArray("origins").set(0,new JsonPrimitive("https://host.invalid/\ncode"));assertFalse(PythonAuthGeneration.process(s).validation().valid());
        s=sample();s.addProperty("secret","private-sentinel");assertFalse(PythonAuthGeneration.process(s).validation().valid());
    }
}
