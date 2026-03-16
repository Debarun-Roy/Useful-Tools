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
 * Validates whether an expression string is parseable without evaluating it.
 * Intended for live "as-you-type" feedback in the React calculator UI.
 *
 * ── CHANGE 5: HTTP status codes ──────────────────────────────────────────
 * 400 for missing expr parameter; 200 in all other cases including invalid
 * expressions (validation is not an error — the endpoint always succeeds,
 * it just reports whether the expression is valid or not).
 *
 * ── CHANGE 6: Path rename ────────────────────────────────────────────────
 * /ValidateExpression → /api/calculator/validate
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * GET /api/calculator/validate?expr=sin(3.14)+cos(0)
 *
 * ── Response ──────────────────────────────────────────────────────────────
 * 200: { "success": true, "data": { "valid": true,  "expr": "sin(3.14)+cos(0)" } }
 * 200: { "success": true, "data": { "valid": false, "expr": "sin(3.14)+("       } }
 * 400: { "success": false, "errorCode": "MISSING_PARAMETER", "error": "..." }
 *
 * React usage (debounced, called on every keypress):
 *   const res = await fetch(`/api/calculator/validate?expr=${encodeURIComponent(expr)}`,
 *                           { credentials: 'include' });
 *   const json = await res.json();
 *   setIsValid(json.data.valid);
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

        try (PrintWriter out = response.getWriter()) {

            String expr = request.getParameter("expr");

            if (expr == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Query parameter 'expr' is required.",
                        "MISSING_PARAMETER")));
                return;
            }

            boolean valid = service.validateExpression(expr);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("valid", valid);
            data.put("expr",  expr);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Validation check failed.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}