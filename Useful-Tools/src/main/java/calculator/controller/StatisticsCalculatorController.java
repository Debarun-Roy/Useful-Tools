package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import calculator.service.StatisticsCalculatorService;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Descriptive statistics endpoint.
 *
 * POST /api/calculator/stats
 * Content-Type: application/json
 *
 * Request — JSON array or comma/whitespace-separated string:
 *   { "data": [1, 2, 3, 4, 5] }
 *   { "data": "1, 2, 3, 4, 5" }
 *   { "data": "1\n2\n3\n4\n5" }
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "count": 5, "sum": 15.0, "mean": 3.0, "median": 3.0,
 *       "mode": "None", "min": 1.0, "max": 5.0, "range": 4.0,
 *       "variance": 2.0, "stdDev": 1.414214,
 *       "skewness": 0.0, "kurtosis": -1.3
 *   }}
 *
 * No DB persistence — statistics are computed on demand per request.
 */
@WebServlet("/api/calculator/stats")
public class StatisticsCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final StatisticsCalculatorService service = new StatisticsCalculatorService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            JsonObject body;
            try {
                body = gson.fromJson(request.getReader(), JsonObject.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null || !body.has("data") || body.get("data").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Field 'data' is required. "
                        + "Provide a JSON array or a comma-separated string of numbers.",
                        "MISSING_DATA")));
                return;
            }

            double[] dataset = parseDataset(body);

            if (dataset.length == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Dataset must contain at least one number.", "EMPTY_DATASET")));
                return;
            }

            LinkedHashMap<String, Object> data = service.calculate(dataset);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (IllegalArgumentException iae) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(iae.getMessage(), "INVALID_DATA")));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Statistics calculation failed. Please check your inputs.",
                    "INTERNAL_ERROR")));
        }
    }

    private double[] parseDataset(JsonObject body) {
        var dataElem = body.get("data");

        // JSON array: [1, 2, 3]
        if (dataElem.isJsonArray()) {
            JsonArray arr = dataElem.getAsJsonArray();
            if (arr.size() == 0) return new double[0];
            double[] result = new double[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                try {
                    result[i] = arr.get(i).getAsDouble();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Non-numeric value at index " + i + ": " + arr.get(i));
                }
            }
            return result;
        }

        // String: "1, 2, 3" or "1 2 3" or "1\n2\n3"
        if (dataElem.isJsonPrimitive()) {
            String raw = dataElem.getAsString().trim();
            if (raw.isEmpty()) return new double[0];
            String[] parts = raw.split("[,\\s]+");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String token = parts[i].trim();
                if (token.isEmpty()) continue;
                try {
                    result[i] = Double.parseDouble(token);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException(
                            "Non-numeric value in dataset: '" + token + "'");
                }
            }
            return result;
        }

        throw new IllegalArgumentException(
                "Field 'data' must be a JSON array or a comma-separated string of numbers.");
    }
}