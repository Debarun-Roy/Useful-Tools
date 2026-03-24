package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless base class for all standard calculator controllers.
 *
 * FIX 1 — PrintWriter try-with-resources bug:
 *   The original used try-with-resources for the outer block:
 *     try (PrintWriter out = response.getWriter()) { ... evaluate ... }
 *     catch (Exception e) {
 *       try (PrintWriter out = response.getWriter()) { ... error ... }
 *     }
 *   When an exception was thrown during evaluate(), the try-with-resources
 *   automatically CLOSED the first PrintWriter before the catch block ran.
 *   On Tomcat 11, calling response.getWriter() a second time after the first
 *   writer has been closed causes the response to be committed in a broken
 *   state — an empty or partial body. The frontend's response.json() then
 *   throws because there is no valid JSON, which was incorrectly showing
 *   "Could not reach the server" for things like 1/0.
 *
 *   Fix: obtain the PrintWriter ONCE before the try block and share it across
 *   both the success path and the catch block. The servlet container closes
 *   the response automatically when the request cycle ends — we do not close it
 *   manually.
 *
 * FIX 2 — NaN and Infinity are not valid JSON:
 *   Gson throws IllegalArgumentException when serialising Double.NaN or
 *   Double.POSITIVE_INFINITY / Double.NEGATIVE_INFINITY because JSON has no
 *   representation for these values. Operations like sqrt(-1), asin(2),
 *   fact(-1), 1/0 (now returns NaN from CalculatorService) all produce these
 *   special values.
 *
 *   Fix: after evaluate() returns, check the result with Double.isNaN() and
 *   Double.isInfinite(). If special, put the result into the data map as a
 *   String ("NaN", "Infinity", "-Infinity") rather than as a double. Gson
 *   serialises strings without any issue. The frontend formatResult() already
 *   handles string values gracefully.
 */
public abstract class AbstractCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    protected abstract double evaluate(String expression) throws Exception;

    private static class EvalRequest {
        String expression;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // FIX 1: Obtain the writer once, before the try/catch, and reuse it
        // in both the success path and the error path. This guarantees the
        // response body is always written to the same open writer.
        PrintWriter out = response.getWriter();

        try {
            // ── 1. Parse JSON body ──────────────────────────────────────────
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
            double result = evaluate(expression);

            // ── 4. Handle NaN and Infinity ──────────────────────────────────
            // FIX 2: JSON has no representation for NaN or Infinity.
            // Return them as strings so Gson can serialise the response.
            // The frontend formatResult() displays them as-is.
            //
            // When does this occur?
            //   NaN      — 1/0 (caught in CalculatorService), sqrt(-1), asin(2),
            //              fact(-1), fact(0.5), nCr with invalid args, 0/0, etc.
            //   Infinity — fact(171+), very large exponentials that overflow double
            final Object resultValue;
            if (Double.isNaN(result)) {
                resultValue = "NaN";
            } else if (Double.isInfinite(result)) {
                resultValue = result > 0 ? "Infinity" : "-Infinity";
            } else {
                resultValue = result;
            }

            // ── 5. Return result ────────────────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("expression", expression);
            data.put("result",     resultValue);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            // This catches parse errors, unknown function names, etc.
            // ArithmeticException (division by zero) is caught in
            // CalculatorService.evaluate() and returned as NaN — it does
            // not reach here.
            e.printStackTrace();
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? "Evaluation failed: " + e.getMessage()
                    : "The expression could not be evaluated. Please check the syntax.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // FIX 1: Reuse the same 'out' writer — do NOT call getWriter() again.
            out.print(gson.toJson(ApiResponse.fail(message, "EVALUATION_ERROR")));
        }
    }
}
