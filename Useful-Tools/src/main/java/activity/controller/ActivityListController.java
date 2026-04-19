package activity.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UserContext;
import common.dao.ActivityDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /api/activity/list — returns paginated recent activity for the
 * authenticated user.
 *
 * ── Query params ──────────────────────────────────────────────────────────
 *   tool    (optional) Restrict to one tool_name on the allow-list.
 *   limit   (optional, default 10, max 100) Page size.
 *   offset  (optional, default 0) Row offset for pagination.
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: {
 *   success: true,
 *   data: {
 *     entries: [
 *       { id, toolName, summary, payload, createdAt },
 *       ...
 *     ],
 *     total:  120,
 *     limit:  10,
 *     offset: 0
 *   }
 * }
 * 401: authenticated-only (AuthFilter rejects)
 */
@WebServlet("/api/activity/list")
public class ActivityListController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = UserContext.get();
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in.", "UNAUTHENTICATED")));
                return;
            }

            // ── Params ───────────────────────────────────────────────────
            String tool   = request.getParameter("tool");
            // Treat blank/"all"/"null" as "no filter" so the client can
            // safely pass an empty value to mean "unfiltered".
            if (tool != null && (tool.isBlank() || "all".equalsIgnoreCase(tool))) {
                tool = null;
            }

            int limit  = parseIntOr(request.getParameter("limit"),  10);
            int offset = parseIntOr(request.getParameter("offset"), 0);

            // ── Query ────────────────────────────────────────────────────
            List<Map<String, Object>> entries = ActivityDAO.listRecent(
                    username, tool, limit, offset);
            long total = ActivityDAO.count(username, tool);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entries", entries);
            data.put("total",   total);
            data.put("limit",   limit);
            data.put("offset",  offset);
            // Echo the filter so the client can confirm what it got.
            data.put("tool",    tool);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not load activity.", "INTERNAL_ERROR")));
            }
        }
    }

    /** Parses an int param; on any parse failure returns the default. */
    private static int parseIntOr(String v, int fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}
