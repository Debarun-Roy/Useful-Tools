package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import calculator.dao.ComputeDAO;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Returns paginated standard calculation history for the logged-in user.
 *
 * GET /api/calculator/history?page=0&size=20
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "total": 145, "page": 0, "size": 20,
 *       "entries": [
 *         { "id": 145, "expression": "sin(0.5)+cos(0)", "result": "1.479...",
 *           "calculatedAt": "2025-06-01T12:34:56Z" },
 *         ...
 *       ]
 *   }}
 *
 * History is ordered most-recent first (highest id first).
 * Only expressions evaluated while authenticated are stored.
 */
@WebServlet("/api/calculator/history")
public class CalculationHistoryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE     = 100;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username") : null;
            if (username == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Not authenticated.", "UNAUTHENTICATED")));
                return;
            }

            int page = parseIntParam(request.getParameter("page"), 0);
            int size = parseIntParam(request.getParameter("size"), DEFAULT_PAGE_SIZE);
            page = Math.max(0, page);
            size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

            LinkedHashMap<String, Object> data =
                    ComputeDAO.fetchHistory(username, page, size);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to fetch history.", "INTERNAL_ERROR")));
        }
    }

    private int parseIntParam(String val, int defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}