package metrics.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.UserContext;
import common.dao.MetricsDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /api/metrics/log — records a single tool-metric row for the
 * authenticated user. Called by client-side tools (text utilities, encoding,
 * code utilities, dev utilities, image tools, time utilities, web dev
 * helpers) because they never hit a server-side endpoint when they compute.
 *
 * Counterpart of {@code common.filter.MetricsFilter} (which handles
 * server-side tools).
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * POST /api/metrics/log
 * Content-Type: application/json
 * Body: {
 *   "toolName":       "text.transform",        // required, must be in VALID_TOOL_NAMES
 *   "executionTimeMs": 12,                     // required, wall-clock ms
 *   "memoryBytes":     34816,                  // optional; null on non-Chromium browsers
 *   "latencyMs":       null,                   // optional; usually null for client-side
 *   "success":         true,                   // optional, defaults to true
 *   "errorCode":       "PARSE_ERROR"           // optional; only when success=false
 * }
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: { success: true, data: { id: 123 } }
 * 400: invalid JSON / missing toolName or executionTimeMs / unknown toolName
 * 401: AuthFilter will already have blocked unauthenticated requests
 *
 * ── Failure semantics ─────────────────────────────────────────────────────
 * Callers are fire-and-forget, so a 500 here is non-fatal — the user doesn't
 * see it. We still return a structured envelope for debugging in the network
 * tab.
 *
 * ── Why this is NOT exempt from CSRF ──────────────────────────────────────
 * It's a POST that writes to the database; same rule applies as every other
 * mutating endpoint. Client-side callers already have a CSRF token because
 * they've logged in (guest or registered). Making this endpoint CSRF-exempt
 * would let a malicious site fire metric writes cross-origin — not
 * catastrophic on its own, but pointless noise and inconsistent with the
 * rest of the stack.
 */
@WebServlet("/api/metrics/log")
public class MetricsLogController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /** DTO matching the expected JSON body shape. */
    private static class LogRequest {
        String  toolName;
        Long    executionTimeMs;
        Long    memoryBytes;     // nullable
        Long    latencyMs;       // nullable
        Boolean success;         // nullable; defaults to true
        String  errorCode;       // nullable
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = UserContext.get();
            if (username == null || username.isBlank()) {
                // AuthFilter should have blocked this, but defence in depth.
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in.", "UNAUTHENTICATED")));
                return;
            }

            LogRequest body;
            try {
                body = gson.fromJson(request.getReader(), LogRequest.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is not valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null
                    || body.toolName == null
                    || body.toolName.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "toolName is required.", "MISSING_TOOL_NAME")));
                return;
            }
            if (!MetricsDAO.VALID_TOOL_NAMES.contains(body.toolName)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Unknown toolName. See MetricsDAO.VALID_TOOL_NAMES.",
                        "UNKNOWN_TOOL_NAME")));
                return;
            }
            if (body.executionTimeMs == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "executionTimeMs is required (numeric).",
                        "MISSING_EXEC_TIME")));
                return;
            }

            boolean success = body.success == null ? true : body.success;

            long id = MetricsDAO.log(
                    username,
                    body.toolName,
                    body.executionTimeMs,
                    body.memoryBytes,
                    body.latencyMs,
                    success,
                    body.errorCode);

            if (id < 0) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to record metric.", "INTERNAL_ERROR")));
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Metric log failed.", "INTERNAL_ERROR")));
            }
        }
    }
}
