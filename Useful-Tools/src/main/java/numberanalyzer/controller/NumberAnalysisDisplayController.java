package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.service.NumberAnalyzerService;

/**
 * Classifies a number across all mathematical categories.
 *
 * ── CHANGE 5: HTTP status codes + ApiResponse ─────────────────────────────
 * All responses are now wrapped in ApiResponse. Proper status codes applied:
 *   400 for invalid number format, 500 for internal errors.
 *
 * ── CHANGE 6: Path rename ────────────────────────────────────────────────
 * /NumberAnalyzer → /api/analyzer/classify
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * POST /api/analyzer/classify
 * Content-Type: application/x-www-form-urlencoded
 * Body: number=153
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: {
 *   "success": true,
 *   "data": {
 *     "number": 153,
 *     "analysis": {
 *       "Number Theory": { "1": "153 is an Odd number.", ... },
 *       "Primes":        { ... },
 *       "Recreational":  { "1": "153 is an Armstrong number.", ... },
 *       ...
 *     }
 *   }
 * }
 * 400: { "success": false, "errorCode": "INVALID_NUMBER", "error": "..." }
 * 500: { "success": false, "errorCode": "INTERNAL_ERROR", "error": "..." }
 */
@WebServlet("/api/analyzer/classify")
public class NumberAnalysisDisplayController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final NumberAnalyzerService analyzerService = new NumberAnalyzerService();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            HttpSession session = request.getSession();

            // Use boxed Long — unboxing null to long primitive throws NPE.
            Long number = (Long) session.getAttribute("number");
            if (number == null) number = 0L;

            String numberParam = request.getParameter("number");
            if (numberParam != null && !numberParam.isBlank()) {
                try {
                    number = Long.parseLong(numberParam.trim());
                    session.setAttribute("number", number);
                } catch (NumberFormatException nfe) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "'" + numberParam + "' is not a valid integer.",
                            "INVALID_NUMBER")));
                    return;
                }
            }

            LinkedHashMap<String, LinkedHashMap<Integer, String>> analysis =
                    analyzerService.analyzeNumber(number);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("number",   number);
            data.put("analysis", analysis);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Analysis failed. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}