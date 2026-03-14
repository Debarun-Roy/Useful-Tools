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

/**
 * NEW FILE — ValidateExpressionController
 *
 * Exposes expression validation as a lightweight GET endpoint that the
 * front-end can call on keypress (debounced) or on button-hover to give
 * instant feedback about whether the expression typed so far is parseable.
 *
 * URL: GET /ValidateExpression?expr=sin(3.14)+cos(0)
 *
 * Response (JSON):
 *   { "valid": true }   — expression is parseable
 *   { "valid": false }  — expression is null, blank, or has a parse error
 *
 * This endpoint is intentionally read-only and stateless — it never modifies
 * the session and never writes to the database.
 */
@WebServlet("/ValidateExpression")
public class ValidateExpressionController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final CalculatorService service = new CalculatorService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String expr = request.getParameter("expr");
        boolean valid = service.validateExpression(expr);

        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(java.util.Collections.singletonMap("valid", valid)));
        }
    }
}