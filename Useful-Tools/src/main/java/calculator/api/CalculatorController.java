package calculator.api;

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

@WebServlet("/Calculator")
public class CalculatorController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final CalculatorService service = new CalculatorService();

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        Gson gson = new Gson();
        String jsonResponse;

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
                jsonResponse = gson.toJson("");
                out.print(jsonResponse);
                return;
            }

            if ("=".equals(input)) {

                double result = service.evaluateAndStore(expr.toString());

                expr.setLength(0);
                expr.append(result);

                jsonResponse = gson.toJson(expr);
                out.print(jsonResponse);
                return;
            }

            expr.append(input);
            jsonResponse = gson.toJson(expr);
            out.print(jsonResponse);

        } catch (Exception e) {

            jsonResponse = gson.toJson(e.getMessage());

            try (PrintWriter out = response.getWriter()) {
                out.print(jsonResponse);
            }
        }
    }
}

