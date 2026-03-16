package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import common.ApiResponse;
import calculator.service.ComplexNumberService;
import calculator.service.ComplexNumberService.ComplexResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Evaluates complex number operations.
 *
 * Does NOT extend AbstractCalculatorController because complex arithmetic
 * returns two values (real and imaginary parts), not a single double.
 *
 * ── CHANGE 4: Stateless ───────────────────────────────────────────────────
 * The previous design stored lastReal and lastImag in the HTTP session.
 * React now holds these values in component state. The server receives a
 * complete operation string and returns the computed real/imag pair.
 * No session reads or writes occur here.
 *
 * ComplexNumberCalculatorHandleResetController has been DELETED. React
 * resets lastReal/lastImag by setting its own state to {real:0, imag:0}.
 *
 * ── CHANGE 6: Path rename ────────────────────────────────────────────────
 * /ComplexNumberCalculator/HandleCalculate → /api/calculator/complex
 *
 * ── CHANGE 5: HTTP status codes ──────────────────────────────────────────
 * 200 on success, 400 on invalid/malformed input, 500 on server error.
 *
 * ── Request format ────────────────────────────────────────────────────────
 * POST /api/calculator/complex
 * Content-Type: application/json
 * Body: { "operation": "complex_add(3+4i,1+2i)" }
 *
 * Supported operation prefixes:
 *   complex_add(a+bi,c+di)      Addition
 *   complex_subtract(a+bi,c+di) Subtraction
 *   conj(a+bi)                  Conjugate
 *   imag(a+bi)                  Extract imaginary part
 *   real(a+bi)                  Extract real part
 *   csq(a+bi)                   Modulus squared |z|²
 *
 * ── Response format ───────────────────────────────────────────────────────
 * 200: { "success": true,  "data": { "real": 4.0, "imag": 6.0, "display": "4.0+6.0i" } }
 * 400: { "success": false, "errorCode": "MISSING_OPERATION", "error": "..." }
 * 400: { "success": false, "errorCode": "UNKNOWN_OPERATION", "error": "..." }
 * 500: { "success": false, "errorCode": "INTERNAL_ERROR",    "error": "..." }
 */
@WebServlet("/api/calculator/complex")
public class ComplexNumberCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ComplexNumberService service = new ComplexNumberService();
    private final Gson gson = new Gson();

    /** Simple inner class for deserialising the JSON request body. */
    private static class ComplexRequest {
        String operation;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── 1. Parse JSON body ──────────────────────────────────────────
            ComplexRequest body;
            try {
                body = gson.fromJson(request.getReader(), ComplexRequest.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON: { \"operation\": \"...\" }",
                        "INVALID_JSON")));
                return;
            }

            // ── 2. Validate operation field ─────────────────────────────────
            if (body == null || body.operation == null || body.operation.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'operation' field.",
                        "MISSING_OPERATION")));
                return;
            }

            String operation = body.operation.trim();

            // ── 3. Dispatch to service ──────────────────────────────────────
            // Dispatch based on the operation name prefix (case-sensitive,
            // matching the strings the React client will send).
            ComplexResult result;

            if (operation.startsWith("complex_add")) {
                result = service.add(operation);
            } else if (operation.startsWith("complex_subtract")) {
                result = service.subtract(operation);
            } else if (operation.startsWith("conj")) {
                result = service.conjugate(operation);
            } else if (operation.startsWith("imag")) {
                result = service.imagPart(operation);
            } else if (operation.startsWith("real")) {
                result = service.realPart(operation);
            } else if (operation.startsWith("csq")) {
                result = service.modulusSquared(operation);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Unknown operation: '" + operation + "'. Supported operations: "
                        + "complex_add, complex_subtract, conj, imag, real, csq.",
                        "UNKNOWN_OPERATION")));
                return;
            }

            // ── 4. Build response payload ───────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("real",      result.real);
            data.put("imag",      result.imag);
            data.put("display",   result.display);
            data.put("operation", operation);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (IllegalArgumentException iae) {
            // Service throws this for malformed operation strings (e.g. bad regex match).
            iae.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Malformed operation string: " + iae.getMessage(),
                        "MALFORMED_OPERATION")));
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Complex number evaluation failed. Please check the expression format.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}