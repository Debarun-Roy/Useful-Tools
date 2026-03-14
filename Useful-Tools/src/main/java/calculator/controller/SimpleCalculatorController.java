package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.service.CalculatorService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * FIX: Removed the large commented-out block of stale code (~35 lines) that
 *   was left over from an earlier implementation. Dead commented code adds
 *   noise and makes the intent of the live code harder to follow.
 *
 * FIX: The original had a nested try-catch structure where the outer catch
 *   tried to obtain a second PrintWriter after the inner try-with-resources
 *   had already closed the first one. Collapsed into a single clean structure.
 *
 * NOTE: The duplicate CalculatorController in calculator.api (mapped to
 *   /Calculator) has been removed — this servlet at /SimpleCalculator is the
 *   canonical implementation. Update any front-end references from /Calculator
 *   to /SimpleCalculator accordingly.
 */
@WebServlet("/SimpleCalculator")
public class SimpleCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final CalculatorService service = new CalculatorService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Gson gson = new Gson();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession();
        StringBuilder expr = (StringBuilder) session.getAttribute("expression");
        if (expr == null) {
            expr = new StringBuilder();
            session.setAttribute("expression", expr);
        }

        String input = request.getParameter("input");

        try (PrintWriter out = response.getWriter()) {
            if ("C".equals(input)) {
                expr.setLength(0);
                out.print(gson.toJson(""));
                return;
            }

            if ("=".equals(input)) {
                double result = service.evaluate(expr.toString());
                expr.setLength(0);
                expr.append(result);
                out.print(gson.toJson(expr.toString()));
                return;
            }

            expr.append(input);
            out.print(gson.toJson(expr.toString()));

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(e.getMessage()));
            }
        }
    }
}
