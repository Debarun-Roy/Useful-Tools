package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import calculator.dao.ComputeDAO;
import calculator.utilities.BooleanUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * IMPROVEMENT: Unique session key "boolean_expression" to prevent state
 *   collision across calculator tabs in the same browser session.
 *
 * (Missing return after "=" was fixed in the previous review batch.)
 */
@WebServlet("/BooleanCalculator")
public class BooleanCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "boolean_expression";

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
                session.setAttribute(SESSION_KEY, expr);
                out.print(gson.toJson(""));
                return;
            }

            if ("=".equals(input)) {
                double result = BooleanUtils.evaluateBooleanExpression(expr.toString());
                ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
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