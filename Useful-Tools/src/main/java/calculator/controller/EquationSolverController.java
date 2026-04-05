package calculator.controller;

import calculator.service.EquationSolverService;
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
 * Solves equations of the form f(x) = 0.
 *
 * POST /api/calculator/solve
 * Body: { "equation": "x^2 - 4" }
 */
@WebServlet("/api/calculator/solve")
public class EquationSolverController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final EquationSolverService service = new EquationSolverService();
    private final Gson gson = new Gson();

    private static class SolveRequest {
        String equation;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // Parse JSON body
            SolveRequest body = gson.fromJson(request.getReader(), SolveRequest.class);

            if (body == null || body.equation == null || body.equation.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'equation' field.",
                        "MISSING_EQUATION")));
                return;
            }

            String equation = body.equation.trim();

            // Solve equation
            double[] roots = service.solveEquation(equation);

            // Return result
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("equation", equation);
            data.put("result", roots);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? "Solving failed: " + e.getMessage()
                    : "The equation could not be solved. Please check the syntax.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(message, "SOLVING_ERROR")));
        }
    }
}