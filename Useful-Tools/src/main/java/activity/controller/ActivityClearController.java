package activity.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
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
 * DELETE /api/activity/clear — clears all activity rows for the authenticated
 * user, optionally narrowed by tool.
 *
 * ── Privacy rationale ─────────────────────────────────────────────────────
 * The activity log contains enough signal to reconstruct what a user has
 * been doing (platforms they've generated passwords for, units they've
 * converted, etc.). Users should always be able to erase this trail.
 *
 * ── Query params ──────────────────────────────────────────────────────────
 *   tool  (optional) Restrict the delete to one tool_name. Omit to clear
 *                    the entire log for the current user.
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: { success: true,  data: { deleted: 42 } }
 * 401: authenticated-only (AuthFilter rejects)
 */
@WebServlet("/api/activity/clear")
public class ActivityClearController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
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

            String tool = request.getParameter("tool");
            if (tool != null && (tool.isBlank() || "all".equalsIgnoreCase(tool))) {
                tool = null;
            }

            int deleted = ActivityDAO.clear(username, tool);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deleted", deleted);
            data.put("tool",    tool);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not clear activity.", "INTERNAL_ERROR")));
            }
        }
    }
}
