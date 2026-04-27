package common.controller.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import common.ApiResponse;
import feedback.dao.FeedbackDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AdminFeedbackController — admin-only paginated read of all submitted
 * feedback. Protected by AdminFilter (requires role=admin).
 *
 * GET /api/admin/feedback?limit={int}&offset={int}
 *   Default limit 25, max 200. Default offset 0. Newest-first.
 *
 *   Response 200 shape:
 *     {
 *       "success": true,
 *       "data": {
 *         "summary": {
 *           "total": 42,
 *           "avgOverallRating": 4.28,
 *           "distribution": { "1":0, "2":1, "3":4, "4":15, "5":22 }
 *         },
 *         "limit": 25,
 *         "offset": 0,
 *         "total": 42,
 *         "entries": [
 *           {
 *             "id": 17, "username": "alice", "overallRating": 5,
 *             "generalComment": "Love it!", "submittedAt": "2026-04-21T13:45:01Z",
 *             "features": [ { featureName, rating, comment }, ... ]
 *           }, ...
 *         ]
 *       }
 *     }
 */
@WebServlet("/api/admin/feedback")
public class AdminFeedbackController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT     = 200;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            int limit  = parseIntOr(request.getParameter("limit"),  DEFAULT_LIMIT);
            int offset = parseIntOr(request.getParameter("offset"), 0);
            if (limit  < 1)         limit  = DEFAULT_LIMIT;
            if (limit  > MAX_LIMIT) limit  = MAX_LIMIT;
            if (offset < 0)         offset = 0;

            List<Map<String, Object>> entries = FeedbackDAO.listAll(limit, offset);
            long total = FeedbackDAO.count();
            Map<String, Object> summary = FeedbackDAO.summary();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", summary);
            data.put("limit",   limit);
            data.put("offset",  offset);
            data.put("total",   total);
            data.put("entries", entries);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to load feedback.", "INTERNAL_ERROR")));
            }
        }
    }

    private static int parseIntOr(String v, int fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}
