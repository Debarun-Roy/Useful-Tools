package backendsupport;

import com.google.gson.*;
import java.util.*;

/** The emitted schema is framework neutral; no Java type names enter the API. */
final class AuthOpenApi {
    private static final Gson JSON=new Gson();
    private static Map<String,Object> object(Map<String,Object> properties,List<String> required){return Map.of("type","object","additionalProperties",false,"properties",properties,"required",required);}
    private static Map<String,Object> ref(String name){return Map.of("$ref","#/components/schemas/"+name);}
    static JsonObject document(JsonObject spec) {
        Map<String,Object> schemas=new LinkedHashMap<>();Map<String,Object> text=Map.of("type","string");
        schemas.put("Error",object(Map.of("error",text,"requestId",text),List.of("error","requestId")));
        schemas.put("Identity",object(Map.of("id",Map.of("type","string","format","uuid"),"username",Map.of("type","string","pattern","^[a-z0-9_]{3,32}$"),"role",Map.of("type","string","enum",List.of("user"))),List.of("id","username","role")));
        schemas.put("Csrf",object(Map.of("csrfToken",Map.of("type","string","pattern","^[A-Za-z0-9_-]{43}$")),List.of("csrfToken")));
        schemas.put("Registered",object(Map.of("registered",Map.of("type","boolean","enum",List.of(true))),List.of("registered")));
        schemas.put("Authenticated",object(Map.of("authenticated",Map.of("type","boolean","enum",List.of(true)),"user",ref("Identity")),List.of("authenticated","user")));
        schemas.put("Session",Map.of("oneOf",List.of(ref("Authenticated"),object(Map.of("authenticated",Map.of("type","boolean","enum",List.of(false))),List.of("authenticated")))));
        Map<String,Object> credentials=new LinkedHashMap<>();credentials.put("username",Map.of("type","string","pattern","^[A-Za-z0-9_]{3,32}$"));credentials.put("password",Map.of("type","string","minLength",spec.getAsJsonObject("policy").get("passwordMin").getAsInt(),"maxLength",spec.getAsJsonObject("policy").get("passwordMax").getAsInt(),"description","Unchanged; additionally at most 512 UTF-8 bytes"));
        if(spec.getAsJsonObject("captcha").get("mode").getAsString().equals("recaptcha-v3"))credentials.put("captchaToken",Map.of("type","string","minLength",1,"maxLength",4096,"description","reCAPTCHA v3 action register or login matching the endpoint"));
        schemas.put("Credentials",object(credentials,new ArrayList<>(credentials.keySet())));
        JsonObject profile=spec.getAsJsonObject("profile");Map<String,Object> mutable=Map.of("displayName",Map.of("type","string","maxLength",profile.get("displayNameMax").getAsInt(),"description","No control characters"),"preferences",Map.of("type","object","maxProperties",profile.get("preferenceCount").getAsInt(),"additionalProperties",Map.of("type","string","maxLength",profile.get("preferenceValueMax").getAsInt()),"description","Keys match [a-z][a-z0-9_]{0,31}; string values without controls; complete map replacement"));
        Map<String,Object> patch=new LinkedHashMap<>(object(mutable,List.of()));patch.remove("required");patch.put("minProperties",1);schemas.put("ProfilePatch",patch);
        Map<String,Object> all=new LinkedHashMap<>(mutable);all.put("id",Map.of("type","string","format","uuid"));all.put("username",text);all.put("role",Map.of("type","string","enum",List.of("user")));schemas.put("Profile",object(all,List.of("id","username","role","displayName","preferences")));
        schemas.put("Empty",Map.of("type","object","additionalProperties",false,"properties",Map.of()));
        Map<String,Object> paths=new LinkedHashMap<>();
        operation(paths,"/api/auth/register","post","Credentials","Registered",201,false);
        operation(paths,"/api/auth/login","post","Credentials","Authenticated",200,false);
        operation(paths,"/api/auth/logout","post","Empty",null,204,false);
        operation(paths,"/api/auth/session","get",null,"Session",200,false);
        operation(paths,"/api/auth/csrf-token","get",null,"Csrf",200,false);
        if(spec.getAsJsonArray("modules").asList().stream().anyMatch(x->x.getAsString().equals("profile"))) {
            operation(paths,"/api/user/profile","get",null,"Profile",200,true);operation(paths,"/api/user/profile","patch","ProfilePatch","Profile",200,true);
        }
        return JSON.toJsonTree(Map.of("openapi","3.0.3","info",Map.of("title","Auth starter","version","0.5.0","description","Same-origin browser API. Bootstrap anonymous CSRF, register (no login), login (rotates cookie/token), refresh token, session/profile, logout. Secure HttpOnly SameSite=Lax context-path cookie in production. No CORS. Absent-session logout still requires trusted Origin."),"paths",paths,"components",Map.of("schemas",schemas,"securitySchemes",Map.of("session",Map.of("type","apiKey","in","cookie","name","JSESSIONID"),"csrf",Map.of("type","apiKey","in","header","name","X-CSRF-Token"))))).getAsJsonObject();
    }
    @SuppressWarnings("unchecked") private static void operation(Map<String,Object> paths,String path,String method,String input,String output,int status,boolean authenticated) {
        Map<String,Object> operation=new LinkedHashMap<>(),responses=new LinkedHashMap<>();
        responses.put(Integer.toString(status),output==null?Map.of("description","Session invalidated; matching session cookie cleared"):Map.of("description","Success","content",Map.of("application/json",Map.of("schema",ref(output)))));
        for(int code:List.of(400,401,403,404,405,409,413,415,429,503))responses.put(Integer.toString(code),Map.of("description","Safe error; 429/503 may carry Retry-After","headers",Map.of("Retry-After",Map.of("schema",Map.of("type","integer","minimum",1))),"content",Map.of("application/json",Map.of("schema",ref("Error")))));
        operation.put("responses",responses);operation.put("operationId",method+path.replace('/','_'));
        if(input!=null) {
            operation.put("requestBody",Map.of("required",true,"content",Map.of("application/json",Map.of("schema",ref(input)))));
            operation.put("parameters",List.of(Map.of("name","Origin","in","header","required",true,"schema",Map.of("type","string"),"description","Exact configured trusted origin")));
            operation.put("security",List.of(Map.of("session",List.of(),"csrf",List.of())));
        }else if(authenticated)operation.put("security",List.of(Map.of("session",List.of())));
        if(path.endsWith("logout"))operation.put("description","Existing session requires CSRF. With absent/expired session, trusted Origin and {} suffice for idempotent 204.");
        ((Map<String,Object>)paths.computeIfAbsent(path,k->new LinkedHashMap<>())).put(method,operation);
    }
}
