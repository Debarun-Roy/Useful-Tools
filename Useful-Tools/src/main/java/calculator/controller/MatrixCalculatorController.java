package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import calculator.service.MatrixCalculatorService;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Matrix calculator for 2×2 and 3×3 matrices.
 *
 * POST /api/calculator/matrix
 * Content-Type: application/json
 *
 * Request body:
 *   {
 *     "operation": "determinant" | "transpose" | "inverse" | "multiply",
 *     "size": 2 | 3,
 *     "matrix1": [[r0c0, r0c1], [r1c0, r1c1]],
 *     "matrix2": [[...]]   // required only for "multiply"
 *   }
 *
 * Response 200 — scalar (determinant):
 *   { "success": true, "data": { "operation": "determinant", "size": 2, "result": -2.0 } }
 *
 * Response 200 — matrix (transpose / inverse / multiply):
 *   { "success": true, "data": { "operation": "inverse", "size": 2,
 *                                "result": [[-2.0, 1.0], [1.5, -0.5]] } }
 *
 * Response 400: { "success": false, "errorCode": "...", "error": "..." }
 */
@WebServlet("/api/calculator/matrix")
public class MatrixCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final MatrixCalculatorService service = new MatrixCalculatorService();

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

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.", "MISSING_BODY")));
                return;
            }

            // ── 1. Parse and validate fields ──────────────────────────────
            String operation = body.has("operation") && !body.get("operation").isJsonNull()
                    ? body.get("operation").getAsString().trim() : null;

            if (operation == null || operation.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Field 'operation' is required. "
                        + "Valid values: determinant, transpose, inverse, multiply.",
                        "MISSING_OPERATION")));
                return;
            }

            int size = -1;
            try {
                if (body.has("size") && !body.get("size").isJsonNull()) {
                    size = body.get("size").getAsInt();
                }
            } catch (Exception ignored) { /* size stays -1 */ }

            if (size != 2 && size != 3) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Field 'size' must be 2 or 3.", "INVALID_SIZE")));
                return;
            }

            if (!body.has("matrix1") || body.get("matrix1").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Field 'matrix1' is required.", "MISSING_MATRIX1")));
                return;
            }

            double[][] matrix1 = parseMatrix(body.get("matrix1").getAsJsonArray(), size, "matrix1");

            // ── 2. Dispatch ───────────────────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("operation", operation);
            data.put("size", size);

            switch (operation.toLowerCase()) {

                case "determinant": {
                    double det = MatrixCalculatorService.round(service.determinant(matrix1, size));
                    data.put("result", det);
                    break;
                }

                case "transpose": {
                    double[][] result = MatrixCalculatorService.roundMatrix(
                            service.transpose(matrix1, size));
                    data.put("result", result);
                    break;
                }

                case "inverse": {
                    double[][] result = MatrixCalculatorService.roundMatrix(
                            service.inverse(matrix1, size));
                    data.put("result", result);
                    break;
                }

                case "multiply": {
                    if (!body.has("matrix2") || body.get("matrix2").isJsonNull()) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print(gson.toJson(ApiResponse.fail(
                                "Field 'matrix2' is required for the multiply operation.",
                                "MISSING_MATRIX2")));
                        return;
                    }
                    double[][] matrix2 = parseMatrix(
                            body.get("matrix2").getAsJsonArray(), size, "matrix2");
                    double[][] result = MatrixCalculatorService.roundMatrix(
                            service.multiply(matrix1, matrix2, size));
                    data.put("result", result);
                    break;
                }

                default: {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Unknown operation '" + operation + "'. "
                            + "Valid values: determinant, transpose, inverse, multiply.",
                            "UNKNOWN_OPERATION")));
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (IllegalArgumentException iae) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(iae.getMessage(), "INVALID_INPUT")));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Matrix calculation failed. Please check your inputs.",
                    "INTERNAL_ERROR")));
        }
    }

    private double[][] parseMatrix(JsonArray arr, int size, String label) {
        if (arr.size() != size) {
            throw new IllegalArgumentException(
                    label + " must have exactly " + size + " rows. Received: " + arr.size());
        }
        double[][] m = new double[size][size];
        for (int i = 0; i < size; i++) {
            JsonArray row = arr.get(i).getAsJsonArray();
            if (row.size() != size) {
                throw new IllegalArgumentException(
                        label + " row " + i + " must have " + size
                        + " elements. Received: " + row.size());
            }
            for (int j = 0; j < size; j++) {
                try {
                    m[i][j] = row.get(j).getAsDouble();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            label + " [" + i + "][" + j + "] is not a valid number: "
                            + row.get(j));
                }
            }
        }
        return m;
    }
}