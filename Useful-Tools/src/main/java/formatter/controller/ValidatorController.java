package formatter.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.dao.FormatterDAO;
import formatter.service.ValidatorService;
import formatter.service.ValidatorService.ValidationReport;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ValidatorController — Provides JSON validation endpoints.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────
 * POST /api/validator/schema       — Validate against JSON schema
 * POST /api/validator/required     — Check required fields
 * POST /api/validator/structure    — Validate structure/keys
 * GET  /api/validator/templates    — List available schema templates
 * GET  /api/validator/template/{name} — Get specific template
 */
@WebServlet({"/api/validator/schema", "/api/validator/required", "/api/validator/structure",
        "/api/validator/templates", "/api/validator/template/*"})
public class ValidatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final ValidatorService validatorService = new ValidatorService();
    private final FormatterDAO formatterDAO = new FormatterDAO();

    private static class ValidateRequest {
        String json;
        String schema;
        List<String> fields;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            ValidateRequest body = gson.fromJson(request.getReader(), ValidateRequest.class);

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.",
                        "MISSING_BODY")));
                return;
            }

            // Route to appropriate handler
            if (servletPath.equals("/api/validator/schema")) {
                handleSchema(body, out, response);
            } else if (servletPath.equals("/api/validator/required")) {
                handleRequired(body, out, response);
            } else if (servletPath.equals("/api/validator/structure")) {
                handleStructure(body, out, response);
            }

        } catch (JsonSyntaxException jse) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Request body must be valid JSON.",
                    "INVALID_REQUEST_JSON")));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Server error: " + e.getMessage(),
                    "INTERNAL_ERROR")));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            if (servletPath.equals("/api/validator/templates")) {
                handleListTemplates(request, out, response);
            } else if (servletPath.startsWith("/api/validator/template/")) {
                handleGetTemplate(servletPath, out, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Server error: " + e.getMessage(),
                    "INTERNAL_ERROR")));
        }
    }

    /**
     * Handler for /api/validator/schema — Validate JSON against schema.
     */
    private void handleSchema(ValidateRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.json == null || body.json.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'json' is required.",
                    "MISSING_JSON")));
            return;
        }

        if (body.schema == null || body.schema.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'schema' is required.",
                    "MISSING_SCHEMA")));
            return;
        }

        ValidationReport report = validatorService.validateAgainstSchema(body.json, body.schema);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(report.toMap())));
    }

    /**
     * Handler for /api/validator/required — Check required fields.
     */
    private void handleRequired(ValidateRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.json == null || body.json.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'json' is required.",
                    "MISSING_JSON")));
            return;
        }

        if (body.fields == null || body.fields.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'fields' (array of required field names) is required.",
                    "MISSING_FIELDS")));
            return;
        }

        ValidationReport report = validatorService.validateRequiredFields(body.json, body.fields);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(report.toMap())));
    }

    /**
     * Handler for /api/validator/structure — Validate structure.
     */
    private void handleStructure(ValidateRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.json == null || body.json.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'json' is required.",
                    "MISSING_JSON")));
            return;
        }

        if (body.fields == null || body.fields.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'fields' (array of expected keys) is required.",
                    "MISSING_FIELDS")));
            return;
        }

        ValidationReport report = validatorService.validateStructure(body.json, body.fields);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(report.toMap())));
    }

    /**
     * Handler for GET /api/validator/templates — List schema templates.
     */
    private void handleListTemplates(HttpServletRequest request, PrintWriter out,
                                    HttpServletResponse response) throws Exception {
        String category = request.getParameter("category");

        var templates = formatterDAO.listSchemaTemplates(category);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("templates", templates);
        data.put("count", templates.size());

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for GET /api/validator/template/{name} — Get specific template.
     */
    private void handleGetTemplate(String servletPath, PrintWriter out, HttpServletResponse response)
            throws Exception {
        String templateName = servletPath.substring("/api/validator/template/".length());

        if (templateName.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Template name is required.",
                    "MISSING_NAME")));
            return;
        }

        var template = formatterDAO.getSchemaTemplate(templateName);

        if (template.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print(gson.toJson(ApiResponse.fail(
                    "Template not found: " + templateName,
                    "NOT_FOUND")));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(template)));
    }
}
