package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.dao.ComputeDAO;
import calculator.utilities.CombinedUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * FIX: Missing return after the "=" branch — identical bug to
 *   BooleanCalculatorController. Without the return, execution fell through
 *   to expr.append(input), appending the literal "=" to the expression after
 *   every evaluation, corrupting the session state for the next request.
 *
 * IMPROVEMENT: Unique session key "combined_expression" instead of "expression"
 *   to avoid state collision with other open calculator tabs in the same session.
 *
 * IMPROVEMENT: Collapsed multiple nested try-with-resources PrintWriter blocks
 *   into one clean outer try-with-resources.
 */
@WebServlet("/CombinedCalculator")
public class CombinedCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "combined_expression";

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
                double result = CombinedUtils.evaluateCombinedExpression(expr.toString());
                ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
                expr.setLength(0);
                expr.append(result);
                session.setAttribute(SESSION_KEY, expr);
                out.print(gson.toJson(expr.toString()));
                return;   // FIX: was missing — execution fell through to append below
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