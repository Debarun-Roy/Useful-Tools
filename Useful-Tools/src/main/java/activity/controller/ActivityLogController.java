package activity.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.UserContext;
import common.dao.ActivityDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /api/activity/log — records a single activity entry for the
 * authenticated user.
 *
 * ── Why this endpoint exists ──────────────────────────────────────────────
 * Most useful tools in UsefulTools are pure client-side transformations
 * (unit conversions, hash identification, API key generation, text utilities,
 * encoding / decoding) — they never hit the server today. To surface a
 * unified recent-activity timeline on the Dashboard, the frontend fires
 * a fire-and-forget request to this endpoint after each successful
 * operation. Server-side tools can call it too but they're not required
 * to; their tool-specific tables remain the source of truth.
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * POST /api/activity/log
 * Content-Type: application/json
 * Body: {
 *   "toolName": "password.generate",   // must be on ActivityDAO.VALID_TOOL_NAMES
 *   "summary":  "Generated 16-char password for github.com",
 *   "payload":  { ... optional ... }   // any JSON-serialisable object
 * }
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: { success: true,  data: { id: 123 } }
 * 400: invalid JSON, missing toolName / summary, unknown toolName
 * 401: authenticated-only (AuthFilter rejects)
 *
 * ── Notes on failure modes ────────────────────────────────────────────────
 * Since callers are fire-and-forget, a 500 here is non-fatal — the user
 * doesn't see it. We still return a structured envelope so developers can
 * debug via the network tab.
 */
@WebServlet("/api/activity/log")
public class ActivityLogController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /** DTO matching the expected JSON body shape. */
    private static class LogRequest {
        String toolName;
        String summary;
        // payload is preserved as a raw JsonElement so arbitrary shapes pass through.
        com.google.gson.JsonElement payload;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = UserContext.get();
            if (username == null || username.isBlank()) {
                // AuthFilter should have blocked this, but defense in depth.
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in.", "UNAUTHENTICATED")));
                return;
            }

            // Parse body.
            LogRequest body;
            try {
                body = gson.fromJson(request.getReader(), LogRequest.class);
            } catch (JsonSyntaxException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is not valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null || body.toolName == null || body.toolName.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "toolName is required.", "MISSING_TOOL_NAME")));
                return;
            }
            if (body.summary == null || body.summary.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "summary is required.", "MISSING_SUMMARY")));
                return;
            }
            if (!ActivityDAO.VALID_TOOL_NAMES.contains(body.toolName)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Unknown toolName. See ActivityDAO.VALID_TOOL_NAMES.",
                        "UNKNOWN_TOOL_NAME")));
                return;
            }

            // Serialize the optional payload back to a string. Keeps the
            // DAO signature simple and gives us a stable TEXT column.
            String payloadJson = body.payload != null
                    ? gson.toJson(body.payload) : null;

            long id = ActivityDAO.log(username, body.toolName, body.summary, payloadJson);
            if (id < 0) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to record activity.", "INTERNAL_ERROR")));
                return;
            }

            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("id", id);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Activity log failed.", "INTERNAL_ERROR")));
            }
        }
    }
}
