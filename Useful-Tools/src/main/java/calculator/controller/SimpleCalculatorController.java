package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import calculator.dao.ComputeDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/SimpleCalculator")
public class SimpleCalculatorController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String jsonResponse = "";
		Gson gson = new Gson();
		try {
			HttpSession session = request.getSession();
			StringBuilder expr = (StringBuilder) session.getAttribute("expression");
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			if(expr == null) {
				expr = new StringBuilder();
				session.setAttribute("expression", expr);
			}

			String input = request.getParameter("input");

			if("C".equals(input)) {
				expr.setLength(0);
				jsonResponse = "";
				try (PrintWriter out = response.getWriter()){
					out.print(jsonResponse);
					out.flush();
				}
				return;
			}

			if("=".equals(input)) {
				try {
					Expression exp = new ExpressionBuilder(expr.toString()).build();
					double result = exp.evaluate();
					ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
					expr.setLength(0);
					expr.append(result);
					jsonResponse = gson.toJson(expr);
					try (PrintWriter out = response.getWriter()){
						out.print(jsonResponse);
						out.flush();
					}
				}
				catch(Exception ex) {
					ex.printStackTrace();
					jsonResponse = gson.toJson(ex.getMessage());
					try (PrintWriter out = response.getWriter()){
						out.print(jsonResponse);
						out.flush();
					}
				}
				return;
			}
			
			expr.append(input);
			jsonResponse = gson.toJson(expr);
			try (PrintWriter out = response.getWriter()){
				out.print(jsonResponse);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			jsonResponse = gson.toJson(e.getMessage());
			try (PrintWriter out = response.getWriter()){
				out.print(jsonResponse);
				out.flush();
			}

		}
	}
}
