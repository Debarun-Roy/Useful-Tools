package common.controller.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import common.ApiResponse;
import common.dao.MetricsDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AdminAnalyticsController — Sprint 18.
 *
 * Aggregates tool_metrics rows for the admin analytics dashboard. Protected
 * by AdminFilter (requires role=admin in session).
 *
 * ── Endpoint ──────────────────────────────────────────────────────────────
 *
 * GET /api/admin/analytics?window={24h|7d|30d|all}
 *   Default window is "7d" when the parameter is omitted or invalid.
 *
 *   Response 200 shape:
 *     {
 *       "success": true,
 *       "data": {
 *         "window": "7d",
 *         "overall": {
 *           "totalInvocations": 1234,
 *           "avgExecutionMs":   42.7,
 *           "avgLatencyMs":     45.2,
 *           "avgMemoryBytes":   1048576.0,
 *           "successRatePct":   98.4
 *         },
 *         "topSlow":       [ {toolName, invocations, avgExecutionMs, maxExecutionMs, ...}, ... ],
 *         "mostFailing":   [ {toolName, invocations, failures, failureRatePct, ...}, ... ],
 *         "mostPopular":   [ {toolName, invocations, avgExecutionMs, successRatePct}, ... ],
 *         "perTool":       [ full breakdown, one row per tool_name in the window ]
 *       }
 *     }
 *
 * Each list is capped at {@link #RANKING_LIMIT} entries.
 *
 * ── Guest behaviour ───────────────────────────────────────────────────────
 * Guests cannot reach this endpoint — AdminFilter rejects them with 403
 * before we see the request. We don't need a separate guest check here.
 */
@WebServlet("/api/admin/analytics")
public class AdminAnalyticsController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /** How many entries each ranking list returns. */
    private static final int RANKING_LIMIT = 5;

    /** Allowed window tokens. Unknown values fall back to the default. */
    private static final Set<String> VALID_WINDOWS = Set.of("24h", "7d", "30d", "all");
    private static final String DEFAULT_WINDOW = "7d";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String window = request.getParameter("window");
            if (window == null || !VALID_WINDOWS.contains(window)) {
                window = DEFAULT_WINDOW;
            }

            Map<String, Object>                overall     = MetricsDAO.getOverallStats(window);
            List<Map<String, Object>>          topSlow     = MetricsDAO.getTopSlowTools(window, RANKING_LIMIT);
            List<Map<String, Object>>          mostFailing = MetricsDAO.getMostFailingTools(window, RANKING_LIMIT);
            List<Map<String, Object>>          mostPopular = MetricsDAO.getMostPopularTools(window, RANKING_LIMIT);
            List<Map<String, Object>>          perTool     = MetricsDAO.getPerToolBreakdown(window);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("window",      window);
            data.put("overall",     overall);
            data.put("topSlow",     topSlow);
            data.put("mostFailing", mostFailing);
            data.put("mostPopular", mostPopular);
            data.put("perTool",     perTool);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to load analytics.", "INTERNAL_ERROR")));
            }
        }
    }
}
