package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.service.CalculatorService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * IMPROVEMENT: Removed ~30 lines of commented-out stale code from an earlier
 *   implementation. The active logic below it was identical and correct.
 *
 * IMPROVEMENT: Unique session key "intermediate_expression" to prevent state
 *   collision when the user has multiple calculators open in the same session.
 *
 * IMPROVEMENT: Promoted service field to final (was non-final).
 *
 * IMPROVEMENT: Collapsed nested try-with-resources into a single clean
 *   try-with-resources block.
 */
@WebServlet("/IntermediateCalculator")
public class IntermediateCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "intermediate_expression";

    private final CalculatorService service = new CalculatorService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Gson gson = new Gson();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession();
        StringBuilder expr = (StringBuilder) session.getAttribute(SESSION_KEY);
        if (expr == null) {
            expr = new StringBuilder();
            session.setAttribute(SESSION_KEY, expr);
        }

        String input = request.getParameter("input");

        try (PrintWriter out = response.getWriter()) {

            if ("C".equals(input)) {
                expr.setLength(0);
                out.print(gson.toJson(""));
                return;
            }

            if ("=".equals(input)) {
                double result = service.evaluateAndStore(expr.toString());
                expr.setLength(0);
                expr.append(result);
                session.setAttribute(SESSION_KEY, expr);
                out.print(gson.toJson(expr.toString()));
                return;
            }

            expr.append(input);
            session.setAttribute(SESSION_KEY, expr);
            out.print(gson.toJson(expr.toString()));

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}