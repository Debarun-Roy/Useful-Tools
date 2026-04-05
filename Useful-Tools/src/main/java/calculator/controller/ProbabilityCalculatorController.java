package calculator.controller;

import calculator.service.ProbabilityCalculatorService;
import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calculates probability distributions.
 *
 * POST /api/calculator/probability
 * Body: { "distribution": "normal", "params": { "mean": 0, "std": 1, "x": 0 } }
 */
@WebServlet("/api/calculator/probability")
public class ProbabilityCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ProbabilityCalculatorService service = new ProbabilityCalculatorService();
    private final Gson gson = new Gson();

    private static class ProbabilityRequest {
        String distribution;
        Map<String, Double> params;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // Parse JSON body
            ProbabilityRequest body = gson.fromJson(request.getReader(), ProbabilityRequest.class);

            if (body == null || body.distribution == null || body.distribution.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'distribution' field.",
                        "MISSING_DISTRIBUTION")));
                return;
            }

            if (body.params == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a 'params' object.",
                        "MISSING_PARAMS")));
                return;
            }

            String distribution = body.distribution.trim();
            Map<String, Double> params = body.params;

            // Calculate probability
            double result = service.calculateProbability(distribution, params);

            // Return result
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("distribution", distribution);
            data.put("params", params);
            data.put("result", result);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? "Probability calculation failed: " + e.getMessage()
                    : "The probability could not be calculated. Please check the parameters.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(message, "CALCULATION_ERROR")));
        }
    }
}