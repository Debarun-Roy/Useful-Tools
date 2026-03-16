package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless base class for all standard calculator controllers.
 *
 * ── CHANGE 4: From stateful session to stateless ─────────────────────────
 * The previous design stored the expression string in the HTTP session and
 * appended to it on every button press. This created a round-trip per
 * keypress, required sticky sessions for multi-server deployments, and
 * forced the server to maintain intermediate UI state.
 *
 * The new design:
 *   - React holds the expression string in a useState hook. Button presses
 *     update local state instantly with zero network latency.
 *   - Only the "=" button sends an HTTP request, containing the complete
 *     expression that React has accumulated locally.
 *   - "C" (clear) is a React-only operation — no server call at all.
 *   - This server becomes a pure function: expression in → result out.
 *
 * ── Request format ────────────────────────────────────────────────────────
 * POST /api/calculator/{type}
 * Content-Type: application/json
 * Body: { "expression": "3+4*sin(0.5)" }
 *
 * ── Response format ───────────────────────────────────────────────────────
 * 200 OK:  { "success": true,  "data": { "expression": "3+4*sin(0.5)", "result": 5.916... } }
 * 400:     { "success": false, "errorCode": "MISSING_EXPRESSION", "error": "..." }
 * 400:     { "success": false, "errorCode": "INVALID_JSON",       "error": "..." }
 * 500:     { "success": false, "errorCode": "EVALUATION_ERROR",   "error": "..." }
 *
 * ── Template Method pattern ───────────────────────────────────────────────
 * The evaluate() method is the only line that differs between the five
 * concrete subclasses. Everything else — JSON parsing, validation, response
 * serialisation, error handling — is implemented here once.
 *
 * ── Note on ComplexNumberCalculatorController ─────────────────────────────
 * Complex arithmetic returns two values (real and imaginary parts), not a
 * single double. It does not extend this class — it is a standalone servlet.
 */
public abstract class AbstractCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /**
     * Evaluates the given expression string and returns the numeric result.
     * Implementations may also persist the result to the database.
     *
     * @param expression The complete expression to evaluate (e.g. "3+4*sin(0.5)").
     * @return The numeric result.
     * @throws Exception if the expression cannot be parsed or evaluated.
     *                   The exception message is returned to the client as the
     *                   "error" field in the ApiResponse.
     */
    protected abstract double evaluate(String expression) throws Exception;

    /** Simple inner class for deserialising the JSON request body. */
    private static class EvalRequest {
        String expression;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── 1. Parse JSON body ──────────────────────────────────────────
            // gson.fromJson(reader, Class) returns null if the body is empty.
            // It throws JsonSyntaxException if the body is malformed JSON.
            EvalRequest body;
            try {
                body = gson.fromJson(request.getReader(), EvalRequest.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON: { \"expression\": \"...\" }",
                        "INVALID_JSON")));
                return;
            }

            // ── 2. Validate expression field ────────────────────────────────
            if (body == null || body.expression == null || body.expression.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'expression' field.",
                        "MISSING_EXPRESSION")));
                return;
            }

            String expression = body.expression.trim();

            // ── 3. Evaluate ─────────────────────────────────────────────────
            // Delegate to the concrete subclass. Any parse/evaluation errors
            // are caught below and returned as structured JSON.
            double result = evaluate(expression);

            // ── 4. Return result ────────────────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("expression", expression);
            data.put("result",     result);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            // Do not expose raw exception class names — use a clean message.
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? "Evaluation failed: " + e.getMessage()
                    : "The expression could not be evaluated. Please check the syntax.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(message, "EVALUATION_ERROR")));
            }
        }
    }
}