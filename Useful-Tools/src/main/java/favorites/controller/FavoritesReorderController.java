package favorites.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
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
 * PUT /api/favorites/reorder — replaces the display_order of the user's
 * favorites with the order supplied in the request body.
 *
 * ── Why PUT (not POST) ────────────────────────────────────────────────────
 * This endpoint is idempotent: sending the same ordered list twice leaves
 * the database in the same state. PUT is the standard verb for "replace
 * this resource with the supplied representation", which is exactly what
 * reorder does to the user's favorite list.
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * PUT /api/favorites/reorder
 * Content-Type: application/json
 * Body: {
 *   "orderedPaths": ["/vault", "/calculator", "/dev-utils"]
 * }
 *
 * Each string must be a valid tool_path on FavoritesDAO.VALID_TOOL_PATHS.
 * Paths the user hasn't favorited are silently ignored by the DAO, so the
 * caller can safely send the current full list without first removing
 * stale entries.
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: { success: true, data: { reordered: N } }    // N = paths processed
 * 400: { success: false, errorCode: "INVALID_JSON" | "MISSING_ORDERED_PATHS" | "PAYLOAD_TOO_LARGE" }
 * 401: AuthFilter rejection
 * 500: { success: false, errorCode: "INTERNAL_ERROR" }
 *
 * ── Size cap ──────────────────────────────────────────────────────────────
 * A malicious client could pass a huge list. We cap the accepted size at
 * MAX_FAVORITES_PER_USER; anything larger is rejected outright rather than
 * silently truncated (fails loudly — the frontend should never send more).
 */
@WebServlet("/api/favorites/reorder")
public class FavoritesReorderController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /** DTO matching the expected JSON body shape. */
    private static class ReorderRequest {
        List<String> orderedPaths;
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
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

            ReorderRequest body;
            try {
                // Gson infers the parametric List<String> from the field
                // declaration on ReorderRequest; no TypeToken needed.
                body = gson.fromJson(request.getReader(), ReorderRequest.class);
            } catch (JsonSyntaxException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is not valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null || body.orderedPaths == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "orderedPaths is required.", "MISSING_ORDERED_PATHS")));
                return;
            }

            if (body.orderedPaths.size() > FavoritesDAO.MAX_FAVORITES_PER_USER) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Too many paths. Limit is "
                                + FavoritesDAO.MAX_FAVORITES_PER_USER + ".",
                        "PAYLOAD_TOO_LARGE")));
                return;
            }

            boolean ok = FavoritesDAO.reorder(username, body.orderedPaths);
            if (!ok) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not save new favorites order.", "INTERNAL_ERROR")));
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reordered", body.orderedPaths.size());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not reorder favorites.", "INTERNAL_ERROR")));
            }
        }
    }
}
