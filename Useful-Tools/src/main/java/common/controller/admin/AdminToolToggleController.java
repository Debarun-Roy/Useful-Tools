package common.controller.admin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.ApiResponse;
import common.dao.ToolToggleDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AdminToolToggleController — Sprint 17 RBAC.
 *
 * Manages global tool enable/disable toggles for admin users.
 * Protected by AdminFilter.
 *
 * ── Endpoints ─────────────────────────────────────────────────────────────
 *
 * GET /api/admin/tool-toggles
 *   Returns all tool paths with their enabled states.
 *   Response 200: { "success": true, "data": { "toggles": { "/calculator": true, ... } } }
 *
 * PUT /api/admin/tool-toggles
 *   Toggles a single tool on or off.
 *   Body (JSON): { "toolPath": "/calculator", "enabled": false }
 *   Response 200: { "success": true, "data": { "message": "..." } }
 *   Response 400: unknown toolPath
 */
@WebServlet("/api/admin/tool-toggles")
public class AdminToolToggleController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // ── GET — list all toggles ─────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            Map<String, Boolean> statuses = ToolToggleDAO.getAllStatuses();

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("toggles", statuses);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            response.getWriter().print(gson.toJson(
                ApiResponse.fail("Failed to fetch toggles.", "INTERNAL_ERROR")));
        }
    }

    // ── PUT — update a single toggle ───────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            JsonObject body = parseJsonBody(request);
            String toolPath = getString(body, "toolPath");
            Boolean enabled = getBoolean(body, "enabled");

            if (toolPath == null || toolPath.isBlank()) {
                response.setStatus(400);
                out.print(gson.toJson(
                    ApiResponse.fail("Field 'toolPath' is required.", "MISSING_FIELD")));
                return;
            }
            if (!ToolToggleDAO.KNOWN_TOOLS.contains(toolPath)) {
                response.setStatus(400);
                out.print(gson.toJson(
                    ApiResponse.fail("Unknown tool path: " + toolPath, "UNKNOWN_TOOL")));
                return;
            }
            if (enabled == null) {
                response.setStatus(400);
                out.print(gson.toJson(
                    ApiResponse.fail("Field 'enabled' (boolean) is required.", "MISSING_FIELD")));
                return;
            }

            boolean updated = ToolToggleDAO.setEnabled(toolPath, enabled);
            if (!updated) {
                response.setStatus(500);
                out.print(gson.toJson(
                    ApiResponse.fail("Failed to update toggle.", "INTERNAL_ERROR")));
                return;
            }

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("toolPath", toolPath);
            data.put("enabled",  enabled);
            data.put("message",  toolPath + " is now " + (enabled ? "enabled" : "disabled"));

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            response.getWriter().print(gson.toJson(
                ApiResponse.fail("Failed to update toggle.", "INTERNAL_ERROR")));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private JsonObject parseJsonBody(HttpServletRequest req) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                req.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsString() : null; }
        catch (Exception e) { return null; }
    }

    private Boolean getBoolean(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsBoolean() : null; }
        catch (Exception e) { return null; }
    }
}
