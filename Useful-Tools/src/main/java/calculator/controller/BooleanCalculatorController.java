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

@WebServlet("/BooleanCalculator")
public class BooleanCalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession();
        StringBuilder expr = (StringBuilder) session.getAttribute("expression");
        Gson gson = new Gson();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            if (expr == null) {
                expr = new StringBuilder();
                session.setAttribute("expression", expr);
            }

            String input = request.getParameter("input");

            if ("C".equals(input)) {
                expr.setLength(0);
                session.setAttribute("expression", expr);
                out.print(gson.toJson(""));
                return;
            }

            if ("=".equals(input)) {
                double result = BooleanUtils.evaluateBooleanExpression(expr.toString());
                ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
                expr.setLength(0);
                expr.append(result);
                session.setAttribute("expression", expr);
                out.print(gson.toJson(expr.toString()));
                // FIX: The original was missing this return statement.
                // Without it, execution fell through to expr.append(input)
                // which appended the literal "=" character to the result.
                return;
            }

            expr.append(input);
            session.setAttribute("expression", expr);
            out.print(gson.toJson(expr.toString()));

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print(new Gson().toJson(e.getMessage()));
            }
        }
    }
}
