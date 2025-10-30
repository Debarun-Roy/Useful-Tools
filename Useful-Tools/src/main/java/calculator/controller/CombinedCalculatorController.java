package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;

import calculator.dao.ComputeDAO;
import calculator.utilities.CombinedUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/CombinedCalculator")
public class CombinedCalculatorController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession();
		StringBuilder expr = (StringBuilder) session.getAttribute("expression");
		String jsonResponse = "";
		Gson gson = new Gson();
		
		try {
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			if(expr == null) {
				expr = new StringBuilder();
				session.setAttribute("expression", expr);
			}

			String input = request.getParameter("input");

			if("C".equals(input)) {
				expr.setLength(0);
				jsonResponse = gson.toJson(expr);
				try (PrintWriter out = response.getWriter()){
					out.print(jsonResponse);
					out.flush();
				}
				return;
			}
			
			if("=".equals(input)) {
				double result = CombinedUtils.evaluateCombinedExpression(expr.toString());
				ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
				expr.setLength(0);
				expr.append(result);
				session.setAttribute("expression", expr);
				jsonResponse = gson.toJson(expr);
				try (PrintWriter out = response.getWriter()) {
					out.print(jsonResponse);
					out.flush();
				}
			}
			
			expr.append(input);
			session.setAttribute("expression", expr);
			try (PrintWriter out = response.getWriter()){
				out.print(expr);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}