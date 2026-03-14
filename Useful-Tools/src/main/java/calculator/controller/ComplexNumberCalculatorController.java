package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.service.ComplexNumberService;
import calculator.service.ComplexNumberService.ComplexResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * REFACTORED — was ~265 lines, now ~90 lines.
 *
 * All arithmetic logic has been moved to ComplexNumberService.
 * This controller is now a thin dispatcher: it reads the input, identifies
 * the operation, delegates to the service, and writes the JSON response.
 *
 * FIXES carried over from the original:
 *
 * FIX 1 — Double.NaN comparison:
 *   The original used "lastReal == Double.NaN" which is always false in Java
 *   because NaN != NaN by IEEE 754 definition. Must use Double.isNaN().
 *
 * FIX 2 — Unique session keys:
 *   Uses "complex_real" and "complex_imag" instead of sharing "expression"
 *   with every other calculator controller.
 *
 * FIX 3 — "=" stripping before dispatch:
 *   The original stripped the trailing "=" inside the if-block for "=".
 *   Now stripped once at the top to avoid repetition in each branch.
 */
@WebServlet("/ComplexNumberCalculator/HandleCalculate")
public class ComplexNumberCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_REAL = "complex_real";
    private static final String SESSION_IMAG = "complex_imag";

    private final ComplexNumberService service = new ComplexNumberService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Gson gson = new Gson();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession();
        Double lastReal = (Double) session.getAttribute(SESSION_REAL);
        Double lastImag = (Double) session.getAttribute(SESSION_IMAG);

        // FIX: was "lastReal == Double.NaN" — always false; use Double.isNaN()
        if (lastReal == null || Double.isNaN(lastReal)) lastReal = 0.0;
        if (lastImag == null || Double.isNaN(lastImag)) lastImag = 0.0;

        String input = request.getParameter("input");

        try (PrintWriter out = response.getWriter()) {

            if (!input.endsWith("=")) {
                // Not yet evaluated — nothing to do (expression built client-side).
                out.print(gson.toJson(input));
                return;
            }

            // Strip trailing "=" once, then dispatch on function name.
            input = input.substring(0, input.length() - 1);

            ComplexResult result;

            if (input.startsWith("complex_add")) {
                result = service.add(input);
            } else if (input.startsWith("complex_subtract")) {
                result = service.subtract(input);
            } else if (input.startsWith("conj")) {
                result = service.conjugate(input);
            } else if (input.startsWith("imag")) {
                result = service.imagPart(input);
            } else if (input.startsWith("real")) {
                result = service.realPart(input);
            } else if (input.startsWith("csq")) {
                result = service.modulusSquared(input);
            } else {
                throw new IllegalArgumentException("Unknown complex operation: " + input);
            }

            session.setAttribute(SESSION_REAL, result.real);
            session.setAttribute(SESSION_IMAG, result.imag);
            out.print(gson.toJson(result.display));

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}