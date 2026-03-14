package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * NEW FILE — AbstractCalculatorController
 *
 * WHY THIS EXISTS:
 * All five standard calculator controllers (Simple, Boolean, Intermediate,
 * Combined, Trig) contained near-identical doPost() bodies implementing the
 * same session-expression pattern:
 *
 *   1. Read (or create) a StringBuilder from the HTTP session.
 *   2. If input is "C" → clear the expression, respond with "".
 *   3. If input is "=" → call an evaluation method, store result, respond.
 *   4. Otherwise      → append input to expression, respond with current value.
 *
 * The only line that differed between controllers was the evaluation call on "=".
 * Everything else — session management, JSON serialisation, error handling,
 * content-type headers — was duplicated verbatim five times.
 *
 * This base class implements the entire pattern once using the Template Method
 * design pattern. Each concrete subclass provides:
 *   (a) a unique SESSION_KEY string (so concurrent tabs don't share state)
 *   (b) an implementation of evaluate(String expr) (the single line that differed)
 *
 * RESULT:
 *   Each concrete controller is now ~25 lines instead of ~70 lines.
 *   Any future fix to the session pattern (error format, response encoding, etc.)
 *   needs to be made in exactly one place.
 *
 * NOTE: ComplexNumberCalculatorController is NOT a subclass — its input/output
 * pattern is fundamentally different (two-part complex results, no running
 * expression string). It remains standalone.
 */
public abstract class AbstractCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /**
     * Returns the HTTP session attribute key used to store this calculator's
     * expression string. Every concrete subclass must return a unique key to
     * prevent session state from leaking between different open calculator tabs.
     *
     * Examples: "simple_expression", "boolean_expression", etc.
     */
    protected abstract String getSessionKey();

    /**
     * Evaluates the given expression string and returns the numeric result.
     * Implementations may also persist the result to the database.
     *
     * @param expr The full expression string accumulated so far (e.g. "3+4*2").
     * @return The numeric result of evaluating the expression.
     * @throws Exception if the expression cannot be parsed or evaluated.
     */
    protected abstract double evaluate(String expr) throws Exception;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession();
        StringBuilder expr = (StringBuilder) session.getAttribute(getSessionKey());
        if (expr == null) {
            expr = new StringBuilder();
            session.setAttribute(getSessionKey(), expr);
        }

        String input = request.getParameter("input");

        try (PrintWriter out = response.getWriter()) {

            if ("C".equals(input)) {
                expr.setLength(0);
                session.setAttribute(getSessionKey(), expr);
                out.print(gson.toJson(""));
                return;
            }

            if ("=".equals(input)) {
                double result = evaluate(expr.toString());
                expr.setLength(0);
                expr.append(result);
                session.setAttribute(getSessionKey(), expr);
                out.print(gson.toJson(expr.toString()));
                return;
            }

            expr.append(input);
            session.setAttribute(getSessionKey(), expr);
            out.print(gson.toJson(expr.toString()));

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}