package backendsupport;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static backendsupport.SchemaGeneration.str;

/** B5 renders a separate operator-run Java project. No generated class is executed here. */
public final class AuthGeneration {
    public static final String VERSION="0.5.0",TEMPLATE="java-auth-0.5.0-b5";
    private static final JsonObject CONTRACT=Contracts.resource("contracts/v0.5.0/models.schema.json");
    private static final Set<String> RESERVED=Set.of(("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null _ var yield record sealed permits module open opens requires exports to uses provides with transitive").split(" "));
    private AuthGeneration() {}
    public static B3Generation.Result process(JsonElement input) {
        Contracts.Findings findings=Contracts.structural(input,CONTRACT);if(!findings.valid())return new B3Generation.Result(findings,null,Map.of());
        JsonObject spec=input.getAsJsonObject().deepCopy();
        for(String segment:str(spec,"packageName").split("\\."))if(RESERVED.contains(segment)||segment.length()>40)found(findings,"JAVA_PACKAGE","/packageName");
        List<String> modules=spec.getAsJsonArray("modules").asList().stream().map(JsonElement::getAsString).toList();
        if(!modules.contains("core")||new HashSet<>(modules).size()!=modules.size())found(findings,"AUTH_CORE_REQUIRED","/modules");
        if(spec.getAsJsonObject("session").get("absoluteSeconds").getAsInt()<spec.getAsJsonObject("session").get("idleSeconds").getAsInt())found(findings,"SESSION_LIFETIME","/session");
        if(spec.getAsJsonObject("policy").get("passwordMax").getAsInt()<spec.getAsJsonObject("policy").get("passwordMin").getAsInt())found(findings,"PASSWORD_BOUNDS","/policy");
        Set<String> origins=new HashSet<>();
        for(JsonElement element:spec.getAsJsonArray("origins")) {
            try {
                String origin=element.getAsString();URI uri=URI.create(origin);String scheme=uri.getScheme();
                if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getRawPath().isEmpty()||uri.getPort()>65535||uri.getPort()==0
                    ||!origin.equals(uri.toASCIIString())||!origins.add(origin)||!("https".equals(scheme)||str(spec,"mode").equals("development")&&"http".equals(scheme)&&Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost())))throw new IllegalArgumentException();
            }catch(RuntimeException invalid){found(findings,"TRUSTED_ORIGIN","/origins");}
        }
        Set<String> names=new HashSet<>();for(var entry:spec.getAsJsonObject("runtime").entrySet())if(!names.add(entry.getValue().getAsString()))found(findings,"DISTINCT_ENV_NAMES","/runtime");
        if(!names.add(str(spec.getAsJsonObject("captcha"),"secretEnv")))found(findings,"DISTINCT_ENV_NAMES","/captcha/secretEnv");
        // The selected package becomes directory segments and must obey the exact archive policy too.
        try{ArtifactBundle.safeProjectPath("src/main/java/"+str(spec,"packageName").replace('.','/')+"/Api.java",new HashSet<>());ArtifactBundle.safeProjectPath(str(spec,"project")+".war",new HashSet<>());}
        catch(IllegalArgumentException error){found(findings,"PROJECT_PATH","/packageName");}
        if(!findings.valid())return new B3Generation.Result(findings,null,Map.of());
        ArtifactBundle out=ArtifactBundle.project();String packageName=str(spec,"packageName"),project=str(spec,"project");
        for(String type:List.of("Api","Config","Passwords","Limiter","Captcha","Users","Sessions","AuthService","Bootstrap","SecurityFilter","AuthServlet"))
            out.add("src/main/java/"+packageName.replace('.','/')+"/"+type+".java",resource(type+".java.txt").replace("@PACKAGE@",packageName));
        out.add("src/test/java/"+packageName.replace('.','/')+"/RuntimeTest.java",resource("RuntimeTest.java.txt").replace("@PACKAGE@",packageName));
        boolean postgres=str(spec,"database").equals("postgresql");String jdbc=postgres?"<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.13</version></dependency>":"<dependency><groupId>org.xerial</groupId><artifactId>sqlite-jdbc</artifactId><version>3.53.4.0</version></dependency>";
        out.add("pom.xml",resource("pom.xml.txt").replace("@PACKAGE@",packageName).replace("@PROJECT@",project).replace("@JDBC@",jdbc));
        out.add("src/main/webapp/WEB-INF/web.xml",resource("web.xml.txt").replace("@PACKAGE@",packageName));
        out.add("src/main/webapp/META-INF/context.xml",resource("context.xml"));
        out.add("src/main/resources/V001__auth.sql",resource("V001__auth.sql"));
        out.add("src/main/resources/auth-spec.json",ArtifactBundle.canonical(spec)+"\n");out.add("auth-spec.json",ArtifactBundle.canonical(spec)+"\n");
        for(String name:List.of("README.md","SECURITY.md"))out.add(name,resource(name));
        JsonObject example=new JsonObject();for(var entry:spec.getAsJsonObject("runtime").entrySet())example.addProperty(entry.getValue().getAsString(),entry.getKey().equals("initializeEnv")?"false":"REPLACE_WITH_OPERATOR_VALUE");
        if(str(spec.getAsJsonObject("captcha"),"mode").equals("recaptcha-v3"))example.addProperty(str(spec.getAsJsonObject("captcha"),"secretEnv"),"REPLACE_WITH_PROVIDER_SECRET");
        out.add("environment.example.json",ArtifactBundle.canonical(example)+"\n");out.add("openapi.json",ArtifactBundle.canonical(AuthOpenApi.document(spec))+"\n");
        out.add("dependencies.json",ArtifactBundle.canonical(new Gson().toJsonTree(Map.of("servlet-provided","6.1.0","password4j","1.8.4","gson","2.10.1","jdbc",postgres?"org.postgresql:postgresql:42.7.13":"org.xerial:sqlite-jdbc:3.53.4.0","junit-test","5.11.4","container","Tomcat 11.0.26","compilerRelease","17")))+"\n");
        JsonObject core=new JsonObject();core.addProperty("manifestVersion","1.3.0");core.addProperty("schemaVersion",VERSION);core.addProperty("templateVersion",TEMPLATE);core.addProperty("module","rest");core.addProperty("target","java");core.addProperty("database",str(spec,"database"));core.addProperty("specificationDigest",ArtifactBundle.sha(ArtifactBundle.canonical(spec)));
        return new B3Generation.Result(findings,out.finish(core),Map.of("operatorRun",true,"coreDependencies",List.of("user persistence","password verification","session authentication","Origin and CSRF","bounded throttling"),"pythonAvailable",false));
    }
    private static void found(Contracts.Findings findings,String rule,String path){findings.add(rule,path);}
    private static String resource(String name) {
        try(InputStream in=AuthGeneration.class.getResourceAsStream("/backendsupport/b5/"+name)) {
            if(in==null)throw new IllegalStateException("MISSING_AUTH_TEMPLATE");return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }catch(IOException error){throw new IllegalStateException("MISSING_AUTH_TEMPLATE");}
    }
}
