package backendsupport;

import com.google.gson.*;
import common.ApiResponse;
import common.dao.ToolToggleDAO;
import common.dao.ToolToggleDAO.Availability;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

@WebServlet(urlPatterns={"/api/backend-support/generate","/api/backend-support/export"})
public final class GenerationController extends HttpServlet {
    // Includes decoding, rendering, JSON serialization and response writes; no waiting queue.
    static final Semaphore SLOTS=new Semaphore(2);
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {Responses.fail(response,405,"METHOD_NOT_ALLOWED");}
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException {
        if(request.getAttribute("username")==null){Responses.fail(response,401,"UNAUTHENTICATED");return;}
        Availability state=ToolToggleDAO.backendSupportAvailability();
        if(state==Availability.FAILED){Responses.fail(response,503,"TOOL_UNAVAILABLE");return;}
        if(state==Availability.ABSENT){Responses.fail(response,403,"TOOL_UNCONFIGURED");return;}
        if(state==Availability.DISABLED&&!"admin".equals(request.getAttribute("role"))){Responses.fail(response,403,"TOOL_DISABLED");return;}
        if("Guest User".equals(request.getAttribute("username"))){Responses.fail(response,403,"GUEST_GENERATION_DENIED");return;}
        String media=request.getContentType();
        if(media==null||!media.split(";",2)[0].trim().equalsIgnoreCase("application/json")){Responses.fail(response,415,"UNSUPPORTED_MEDIA_TYPE");return;}
        if(request.getContentLengthLong()>BoundedJson.MAX_BYTES){Responses.fail(response,413,"BODY_TOO_LARGE");return;}
        if(!SLOTS.tryAcquire()){response.setHeader("Retry-After","1");Responses.fail(response,503,"GENERATION_BUSY");return;}
        try {
            JsonElement decoded=BoundedJson.read(request.getInputStream());boolean export=request.getServletPath().endsWith("/export");
            if(!decoded.isJsonObject()){Responses.fail(response,400,"INVALID_TRANSPORT");return;}
            JsonObject payload=decoded.getAsJsonObject();
            if(!payload.keySet().equals(export?Set.of("request","expectedDigest"):Set.of("request")) || export&&(!payload.get("expectedDigest").isJsonPrimitive()||!payload.getAsJsonPrimitive("expectedDigest").isString()||!payload.get("expectedDigest").getAsString().matches("[a-f0-9]{64}"))){Responses.fail(response,400,"INVALID_TRANSPORT");return;}
            JsonElement spec=payload.get("request");
            String module=spec.isJsonObject()&&spec.getAsJsonObject().get("module") instanceof JsonPrimitive p&&p.isString()?p.getAsString():"schema";
            ArtifactBundle.Bundle bundle;Contracts.Findings findings;Map<String,Object> details=Map.of();
            if(module.equals("rest")) {
                var result=spec.isJsonObject()&&new JsonPrimitive("python").equals(spec.getAsJsonObject().get("target"))?PythonAuthGeneration.process(spec):AuthGeneration.process(spec);findings=result.validation();bundle=result.bundle();details=result.details();
            } else if(module.equals("etl")) {
                var result=EtlGeneration.process(spec);findings=result.validation();bundle=result.bundle();details=result.details();
            } else if(B3Generation.MODULES.contains(module)) {
                var result=B3Generation.process(spec);findings=result.validation();bundle=result.bundle();details=result.details();
            } else {
                if(spec.isJsonObject()&&spec.getAsJsonObject().has("templateVersion")&&!new JsonPrimitive(SchemaGeneration.TEMPLATE).equals(spec.getAsJsonObject().get("templateVersion"))){Responses.fail(response,422,"UNAVAILABLE_TEMPLATE");return;}
                var validation=SchemaGeneration.validate(spec);findings=validation.findings();bundle=findings.valid()?ArtifactBundle.assemble(validation.model()):null;
            }
            if(!findings.valid()) {
                Map<String,Object> failure=new LinkedHashMap<>();failure.put("findings",findings.items);failure.put("truncated",findings.truncated);if(!details.isEmpty())failure.put("details",details);
                Responses.send(response,422,ApiResponse.failWithData(failure,"Specification cannot be processed.","INVALID_GENERATION_SPECIFICATION"));return;
            }
            if(export) {
                if(!bundle.digest().equals(payload.get("expectedDigest").getAsString())){Responses.fail(response,409,"PREVIEW_DIGEST_MISMATCH");return;}
                byte[] zip=bundle.zip();response.setStatus(200);response.setContentType("application/zip");response.setHeader("Cache-Control","no-store");response.setHeader("Content-Disposition","attachment; filename=\"usefultools-"+(B3Generation.MODULES.contains(module)||Set.of("etl","rest").contains(module)?module:"schema")+".zip\"");response.setContentLength(zip.length);response.getOutputStream().write(zip);
            } else {
                // Worst-case escaping is six bytes per character. B1's 256 KiB limit is unchanged.
                Map<String,Object> preview=new LinkedHashMap<>(bundle.preview(findings.items,state==Availability.DISABLED));if(!details.isEmpty())preview.put("details",details);
                byte[] bytes=ArtifactBundle.previewBytes(ApiResponse.ok(preview));
                response.setStatus(200);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");response.setContentLength(bytes.length);response.getOutputStream().write(bytes);
            }
            // No activity persistence: specifications, SQL, identifiers and digests never enter analytics.
        } catch(BoundedJson.Rejected e){Responses.fail(response,e.status,e.code);}
        catch(ArtifactBundle.Limit e){Responses.fail(response,413,"OUTPUT_LIMIT");}
        catch(ArtifactBundle.ResponseLimit e){Responses.fail(response,413,"GENERATION_RESPONSE_LIMIT");}
        finally {SLOTS.release();}
    }
}
