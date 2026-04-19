package favorites.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UserContext;
import common.dao.FavoritesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /api/favorites/list — returns the authenticated user's pinned tools
 * in their chosen display order.
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: {
 *   success: true,
 *   data: {
 *     favorites: [
 *       { id, toolPath, displayOrder, createdAt },
 *       ...
 *     ],
 *     max: 20
 *   }
 * }
 * 401: authenticated-only (AuthFilter rejects)
 *
 * ── Why return `max` ──────────────────────────────────────────────────────
 * The client can then render a "You can pin up to 20 tools" hint without
 * hard-coding the limit in two places.
 */
@WebServlet("/api/favorites/list")
public class FavoritesListController extends HttpServlet {

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

            List<Map<String, Object>> favorites = FavoritesDAO.listForUser(username);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("favorites", favorites);
            data.put("max",       FavoritesDAO.MAX_FAVORITES_PER_USER);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not load favorites.", "INTERNAL_ERROR")));
            }
        }
    }
}
