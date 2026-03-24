package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import calculator.service.CalculatorService;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates whether an expression is parseable for a given calculator mode.
 *
 * FIX — Now reads an optional 'mode' query parameter and delegates to
 * CalculatorService.validateForMode(expr, mode) instead of the mode-blind
 * validateExpression(). This allows boolean and combined expressions containing
 * unicode operators (&, |, ⊕, →, etc.) to be validated correctly using the
 * same ExpressionBuilder configuration that actually evaluates them.
 *
 * Request:
 *   GET /api/calculator/validate?expr=1%261&mode=boolean
 *   GET /api/calculator/validate?expr=sin(0.5)&mode=trig
 *   GET /api/calculator/validate?expr=max(2,3)     (mode defaults to "simple")
 *
 * Response:
 *   200: { "success": true, "data": { "valid": true,  "expr": "...", "mode": "..." } }
 *   200: { "success": true, "data": { "valid": false, "expr": "...", "mode": "..." } }
 *   400: { "success": false, "errorCode": "MISSING_PARAMETER", "error": "..." }
 */
@WebServlet("/api/calculator/validate")
public class ValidateExpressionController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final CalculatorService service = new CalculatorService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            String expr = request.getParameter("expr");

            if (expr == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Query parameter 'expr' is required.",
                        "MISSING_PARAMETER")));
                return;
            }

            // mode defaults to "simple" if not provided — safe for all
            // arithmetic-only calculators that do not send a mode param.
            String mode = request.getParameter("mode");
            if (mode == null || mode.isBlank()) {
                mode = "simple";
            }

            boolean valid = service.validateForMode(expr, mode);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("valid", valid);
            data.put("expr",  expr);
            data.put("mode",  mode);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Validation check failed.",
                    "INTERNAL_ERROR")));
        }
    }
}