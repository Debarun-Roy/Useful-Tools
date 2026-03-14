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
 * IMPROVEMENT: Unique session key "simple_expression" prevents state collision
 *   when the user has multiple calculator types open in the same browser session.
 *   Previously all calculators shared the key "expression", so evaluating on
 *   one calculator would corrupt the display of another.
 */
@WebServlet("/SimpleCalculator")
public class SimpleCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "simple_expression";

    private final CalculatorService service = new CalculatorService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Gson gson = new Gson();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession();
        StringBuilder expr = (StringBuilder) session.getAttribute(SESSION_KEY);
        if (expr == null) {
            expr = new StringBuilder();
            session.setAttribute(SESSION_KEY, expr);
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
                session.setAttribute(SESSION_KEY, expr);
                out.print(gson.toJson(expr.toString()));
                return;
            }

            expr.append(input);
            session.setAttribute(SESSION_KEY, expr);
            out.print(gson.toJson(expr.toString()));

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}