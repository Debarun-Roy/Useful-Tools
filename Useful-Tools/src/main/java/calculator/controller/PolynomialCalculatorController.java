package calculator.controller;

import calculator.service.PolynomialCalculatorService;
import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

/**
 * Performs polynomial operations.
 *
 * POST /api/calculator/polynomial
 * Body: { "operation": "evaluate", "coefficients": [1, -3, 2], "x": 1 }
 */
@WebServlet("/api/calculator/polynomial")
public class PolynomialCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final PolynomialCalculatorService service = new PolynomialCalculatorService();
    private final Gson gson = new Gson();

    private static class PolynomialRequest {
        String operation;
        double[] coefficients;
        Double x;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // Parse JSON body
            PolynomialRequest body = gson.fromJson(request.getReader(), PolynomialRequest.class);

            if (body == null || body.operation == null || body.operation.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'operation' field.",
                        "MISSING_OPERATION")));
                return;
            }

            if (body.coefficients == null || body.coefficients.length == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'coefficients' array.",
                        "MISSING_COEFFICIENTS")));
                return;
            }

            String operation = body.operation.trim();
            double[] coefficients = body.coefficients;

            // Perform operation
            Object result = service.performOperation(operation, coefficients, body.x);

            // Return result
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("operation", operation);
            data.put("coefficients", coefficients);
            if (body.x != null) {
                data.put("x", body.x);
            }
            data.put("result", result);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? "Polynomial operation failed: " + e.getMessage()
                    : "The polynomial operation could not be performed. Please check the parameters.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(message, "OPERATION_ERROR")));
        }
    }
}