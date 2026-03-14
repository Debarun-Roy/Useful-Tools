package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.dao.ComputeDAO;
import calculator.expression.ExpressionBuilderFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.objecthunter.exp4j.Expression;

/**
 * COMPLETE REWRITE — was ~440 lines, now ~80 lines.
 *
 * PROBLEM with the original:
 *   The controller had 13 nearly identical if-else branches, each checking
 *   whether the expression string CONTAINED a function name as a substring,
 *   then building an ExpressionBuilder with only that one function registered.
 *   This design had three serious flaws:
 *
 *   1. FRAGILE ORDERING: Function names are substrings of each other.
 *      "cosecd" contains "cosec", "secd", and "sec".
 *      "acosec" contains "cosec" and "sec".
 *      A single wrong check order causes the wrong function to be dispatched.
 *
 *   2. SINGLE FUNCTION ONLY: Registering only one function per evaluation
 *      means an expression combining two trig functions (e.g. "sin(x)+cos(x)")
 *      would always fail — the second function would be unrecognised.
 *
 *   3. MISSING RETURN: The "=" branch wrote a response but did not return,
 *      so execution fell through to expr.append("=") after every evaluation.
 *
 * SOLUTION:
 *   Use ExpressionBuilderFactory.create(), which draws from FunctionRegistry
 *   and OperatorRegistry (populated by AppInitializer at startup). Every
 *   function is registered, expressions combining multiple trig functions work
 *   correctly, and there are no substring-ordering concerns.
 *
 * IMPROVEMENT: Uses a unique session key "trig_expression" to avoid colliding
 *   with the "expression" key used by other calculator controllers when the
 *   user has multiple calculators open in the same browser session.
 */
@WebServlet("/TrigonometricCalculator")
public class SimpleTrigonometricCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "trig_expression";

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
                Expression exp = ExpressionBuilderFactory.create(expr.toString()).build();
                double result = exp.evaluate();
                ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
                expr.setLength(0);
                expr.append(result);
                session.setAttribute(SESSION_KEY, expr);
                out.print(gson.toJson(expr.toString()));
                return;   // FIX: original was missing this return
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