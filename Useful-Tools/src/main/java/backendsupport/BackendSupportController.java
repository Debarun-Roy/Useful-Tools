package backendsupport;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import common.ApiResponse;
import common.dao.ActivityDAO;
import common.dao.ToolToggleDAO;
import common.dao.ToolToggleDAO.Availability;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns={"/api/backend-support/catalog", "/api/backend-support/validate"})
public final class BackendSupportController extends HttpServlet {
    private static boolean admin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("role"));
    }
    private static boolean guest(HttpServletRequest request) {
        return "Guest User".equals(request.getAttribute("username"));
    }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!request.getServletPath().endsWith("/catalog")) {
            Responses.fail(response,405,"METHOD_NOT_ALLOWED");
            return;
        }
        Availability state = ToolToggleDAO.backendSupportAvailability();
        if (state == Availability.FAILED) {
            Responses.fail(response,503,"TOOL_UNAVAILABLE");
            return;
        }
        boolean enabled = state == Availability.ENABLED;
        boolean preview = state == Availability.DISABLED && admin(request);
        boolean allowed = enabled || preview;
        JsonObject catalog = Contracts.catalog();
        JsonObject access = new JsonObject();
        access.addProperty("enabled",enabled);
        access.addProperty("configured",state != Availability.ABSENT);
        access.addProperty("adminPreview",preview);
        access.addProperty("sampleOnly",guest(request));
        access.addProperty("canValidate",allowed);
        access.addProperty("canCustom",allowed && !guest(request));
        catalog.add("access",access);
        JsonObject generation = new JsonObject();
        generation.addProperty("schemaVersion",SchemaGeneration.VERSION);
        generation.addProperty("templateVersion",SchemaGeneration.TEMPLATE);
        generation.addProperty("module","schema");
        generation.addProperty("canGenerate",allowed && !guest(request));
        generation.addProperty("canExport",allowed && !guest(request));
        generation.add("targets",new Gson().toJsonTree(List.of("sqlite","postgresql")));
        catalog.add("generation",generation);
        JsonArray modules=new JsonArray();
        for(String module:List.of("migration","view","evaluator")) {
            JsonObject capability=new JsonObject();
            capability.addProperty("module",module);
            capability.addProperty("schemaVersion",B3Generation.VERSION);
            capability.addProperty("templateVersion",module+"-0.3.0-b3");
            capability.addProperty("canGenerate",allowed&&!guest(request));
            capability.addProperty("canExport",allowed&&!guest(request));
            capability.add("targets",new Gson().toJsonTree(List.of("sqlite","postgresql")));
            if(module.equals("evaluator"))
                capability.addProperty("rulesetVersion",B3Generation.RULESET);
            capability.add("supported",new Gson().toJsonTree(switch(module) {
                case "migration" -> List.of("append nullable/constant-default column","ordinary index","explicit same-ID rename","transactional shape prechecks");
                case "view" -> List.of("INNER/LEFT/self joins","typed predicates","portable grouping","COUNT/SUM/AVG/MIN/MAX");
                default -> List.of("eight deterministic diagnostic rules","report export despite schema errors");
            }));
            modules.add(capability);
        }
        JsonObject etl=new JsonObject();
        etl.addProperty("module","etl");
        etl.addProperty("schemaVersion",EtlGeneration.VERSION);
        etl.addProperty("templateVersion",EtlGeneration.TEMPLATE);
        etl.addProperty("canGenerate",allowed&&!guest(request));etl.addProperty("canExport",allowed&&!guest(request));
        etl.add("targets",new Gson().toJsonTree(List.of("sqlite","postgresql")));
        etl.add("supported",new Gson().toJsonTree(List.of("local CSV and JSON arrays","typed mappings","insert and explicit-key upsert","batch transactions and rejects","validation-only dry run","external post-commit checkpoint and replay policy")));
        modules.add(etl);
        JsonObject auth=new JsonObject();
        auth.addProperty("module","rest");auth.addProperty("schemaVersion",AuthGeneration.VERSION);auth.addProperty("templateVersion",AuthGeneration.TEMPLATE);
        auth.addProperty("canGenerate",allowed&&!guest(request));auth.addProperty("canExport",allowed&&!guest(request));
        auth.add("targets",new Gson().toJsonTree(List.of("java","python")));auth.add("databases",new Gson().toJsonTree(List.of("sqlite","postgresql")));
        auth.add("supported",new Gson().toJsonTree(List.of("required core auth","optional profile","reCAPTCHA v3 or explicit off","standalone Maven WAR")));
        auth.addProperty("pythonAvailable",true);
        auth.add("python",new Gson().toJsonTree(Map.of("schemaVersion",PythonAuthGeneration.VERSION,"templateVersion",PythonAuthGeneration.TEMPLATE,"target","python","framework","FastAPI","pythonVersion","3.14","workers",1,"sessionStore","Redis")));modules.add(auth);
        catalog.add("generationModules",modules);
        catalog.getAsJsonObject("operations").addProperty("validate",allowed);
        catalog.getAsJsonObject("operations").addProperty("preview",allowed);
        Responses.send(response,200,ApiResponse.ok(catalog));
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!request.getServletPath().endsWith("/validate")) {
            Responses.fail(response,405,"METHOD_NOT_ALLOWED");
            return;
        }
        Availability state = ToolToggleDAO.backendSupportAvailability();
        if (state == Availability.FAILED) {
            Responses.fail(response,503,"TOOL_UNAVAILABLE");
            return;
        }
        if (state == Availability.ABSENT) {
            Responses.fail(response,403,"TOOL_UNCONFIGURED");
            return;
        }
        if (state == Availability.DISABLED && !admin(request)) {
            Responses.fail(response,403,"TOOL_DISABLED");
            return;
        }
        String media = request.getContentType();
        if (media == null || !media.split(";",2)[0].trim().equalsIgnoreCase("application/json")) {
            Responses.fail(response,415,"UNSUPPORTED_MEDIA_TYPE");
            return;
        }
        if (request.getContentLengthLong() > BoundedJson.MAX_BYTES) {
            Responses.fail(response,413,"BODY_TOO_LARGE");
            return;
        }
        try {
            JsonElement decoded = BoundedJson.read(request.getInputStream());
            if (!decoded.isJsonObject()) {
                Responses.fail(response,400,"INVALID_TRANSPORT");
                return;
            }
            JsonObject payload = decoded.getAsJsonObject();
            boolean sampleForm = payload.size() == 1 && payload.has("sampleId")
                    && payload.get("sampleId").isJsonPrimitive() && payload.getAsJsonPrimitive("sampleId").isString();
            if (guest(request) && !sampleForm) {
                Responses.fail(response,403,"GUEST_SAMPLE_ONLY");
                return;
            }
            JsonElement spec;
            if (sampleForm) {
                spec = Contracts.sample(payload.get("sampleId").getAsString());
                if (spec == null) {
                    Responses.fail(response,400,"UNKNOWN_SAMPLE");
                    return;
                }
            }
            else if (payload.size() == 1 && payload.has("request"))
                spec = payload.get("request");
            else {
                Responses.fail(response,400,"INVALID_TRANSPORT");
                return;
            }
            Contracts.Findings findings = SpecificationValidator.validate(spec);
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("result",findings.result()); data.put("truncated",findings.truncated);
            data.put("adminPreview",state == Availability.DISABLED);
            if (sampleForm)
                data.put("sampleRequest",spec);
            if (findings.valid()) {
                ActivityDAO.log((String)request.getAttribute("username"),"backend-support.validate","Validated a backend specification",null);
                Responses.send(response,200,ApiResponse.ok(data));
            }
            else {
                // Preserve the failure envelope semantics and include actionable bounded findings.
                Responses.send(response,422,ApiResponse.failWithData(data,"Specification is invalid.","INVALID_SPECIFICATION"));
            }
        }
        catch (BoundedJson.Rejected e) {
            Responses.fail(response,e.status,e.code);
        }
    }
}
