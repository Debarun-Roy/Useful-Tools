package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.service.NumberAnalyzerService;

/**
 * REFACTORED — was ~327 lines of inline analysis, now ~60 lines.
 *
 * All analysis logic has been moved to NumberAnalyzerService.
 *
 * FIX 1 — NullPointerException on first request:
 *   The original did:
 *     long number = (long) session.getAttribute("number");
 *   On the very first request, "number" is not in the session, so
 *   getAttribute() returns null. Unboxing null to long throws NPE.
 *   Fixed by using Long (boxed) and defaulting to 0 if not yet set.
 *
 * FIX 2 — Response was not valid JSON:
 *   The original called out.print(responseJSONMap) which invokes Map.toString(),
 *   producing Java's default "{key=value}" format — not JSON. The front-end
 *   would receive unparseable text. Fixed with Gson.toJson().
 *
 * FIX 3 — Duplicate analysis checks (Real, Rational, Sad, Composite):
 *   Moved to NumberAnalyzerService where they are documented and removed.
 */
@WebServlet("/NumberAnalyzer")
public class NumberAnalysisDisplayController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final NumberAnalyzerService analyzerService = new NumberAnalyzerService();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            HttpSession session = request.getSession();

            // FIX: Use boxed Long so null-check works; default to 0 on first request.
            Long number = (Long) session.getAttribute("number");
            if (number == null) number = 0L;

            String numberParam = request.getParameter("number");
            if (numberParam != null && !numberParam.isBlank()) {
                number = Long.parseLong(numberParam.trim());
                session.setAttribute("number", number);
            }

            LinkedHashMap<String, LinkedHashMap<Integer, String>> result =
                    analyzerService.analyzeNumber(number);

            // FIX: Use Gson — Map.toString() produces "{key=value}", not JSON.
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(result));
                out.flush();
            }

        } catch (NumberFormatException nfe) {
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: '" + request.getParameter("number")
                        + "' is not a valid integer."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}