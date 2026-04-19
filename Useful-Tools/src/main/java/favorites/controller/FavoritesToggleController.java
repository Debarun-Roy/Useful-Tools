package favorites.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.UserContext;
import common.dao.FavoritesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Add / remove a favorite:
 *   POST   /api/favorites/toggle    JSON body: { "toolPath": "/vault" }
 *   DELETE /api/favorites/toggle    query param:  toolPath=/vault
 *
 * ── Why a single servlet ──────────────────────────────────────────────────
 * Add and remove are mirror operations against the same resource. Keeping
 * them in one servlet keeps the URL space small and makes it obvious at
 * the call site that the effect is the inverse.
 *
 * ── Responses ─────────────────────────────────────────────────────────────
 *   POST success (new row):       200 { success:true, data:{ added:true,  toolPath } }
 *   POST duplicate (already fav): 200 { success:true, data:{ added:false, toolPath, reason:"already-favorited" } }
 *   POST hit cap:                 409 { success:false, errorCode:"FAVORITES_FULL" }
 *   POST invalid path:            400 { success:false, errorCode:"INVALID_TOOL_PATH" }
 *
 *   DELETE success:               200 { success:true, data:{ removed:true,  toolPath } }
 *   DELETE not-found:             200 { success:true, data:{ removed:false, toolPath } }
 *   DELETE missing toolPath:      400 { success:false, errorCode:"MISSING_TOOL_PATH" }
 *
 * Unauth'd hits → 401 UNAUTHENTICATED (AuthFilter will have already rejected).
 *
 * ── Why POST returns 200 on duplicate ─────────────────────────────────────
 * Toggling "already-a-favorite" isn't an error — it's just a no-op. The
 * client can use the "added" flag to decide whether to animate the star or
 * not. Returning 409 only for the cap makes the cap case distinctively
 * recoverable ("You've pinned the maximum number of tools").
 */
@WebServlet("/api/favorites/toggle")
public class FavoritesToggleController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    private static class ToggleRequest {
        String toolPath;
    }

    // ── POST: add ───────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
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

            ToggleRequest body;
            try {
                body = gson.fromJson(request.getReader(), ToggleRequest.class);
            } catch (JsonSyntaxException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is not valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null || body.toolPath == null || body.toolPath.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "toolPath is required.", "MISSING_TOOL_PATH")));
                return;
            }

            if (!FavoritesDAO.VALID_TOOL_PATHS.contains(body.toolPath)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Unknown toolPath.", "INVALID_TOOL_PATH")));
                return;
            }

            // Pre-check the cap so we can return a distinct 409 rather than
            // letting add() silently fail — makes the UI message accurate.
            int currentCount = FavoritesDAO.countForUser(username);
            if (currentCount >= FavoritesDAO.MAX_FAVORITES_PER_USER) {
                // But only fail the cap if the path isn't ALREADY favorited —
                // an already-present path is a harmless no-op.
                boolean alreadyPresent = FavoritesDAO.listForUser(username).stream()
                        .anyMatch(row -> body.toolPath.equals(row.get("toolPath")));
                if (!alreadyPresent) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    out.print(gson.toJson(ApiResponse.fail(
                            "You've pinned the maximum number of tools ("
                                    + FavoritesDAO.MAX_FAVORITES_PER_USER + ").",
                            "FAVORITES_FULL")));
                    return;
                }
            }

            boolean added = FavoritesDAO.add(username, body.toolPath);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("added",    added);
            data.put("toolPath", body.toolPath);
            if (!added) data.put("reason", "already-favorited");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not add favorite.", "INTERNAL_ERROR")));
            }
        }
    }

    // ── DELETE: remove ──────────────────────────────────────────────────────

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

            String toolPath = request.getParameter("toolPath");
            if (toolPath == null || toolPath.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "toolPath query param is required.", "MISSING_TOOL_PATH")));
                return;
            }

            boolean removed = FavoritesDAO.remove(username, toolPath);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("removed",  removed);
            data.put("toolPath", toolPath);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not remove favorite.", "INTERNAL_ERROR")));
            }
        }
    }
}
