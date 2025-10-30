package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;

import calculator.dao.ComputeDAO;
import calculator.functions.acosec;
import calculator.functions.acot;
import calculator.functions.asec;
import calculator.functions.atan2;
import calculator.functions.cosd;
import calculator.functions.cosec;
import calculator.functions.cosecd;
import calculator.functions.cot;
import calculator.functions.cotd;
import calculator.functions.sec;
import calculator.functions.secd;
import calculator.functions.sind;
import calculator.functions.tand;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

@WebServlet("/TrigonometricCalculator")
public class SimpleTrigonometricCalculatorController extends HttpServlet {

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
				jsonResponse = gson.toJson(expr);
				try (PrintWriter out = response.getWriter()){
					out.print(jsonResponse);
					out.flush();
				}
				return;
			}

			if("=".equals(input)) {
				/*
				 * input = input.substring(0, input.length()-1); //remove trailing =
				 */				
				if(expr.toString().contains("cosec")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new cosec())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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

				else if(expr.toString().contains("sec")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new sec())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("cot")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new cot())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("acosec")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new acosec())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("asec")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new asec())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("acot")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new acot())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("sind")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new sind())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("cosd")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new cosd())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("tand")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new tand())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("cosecd")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new cosecd())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("secd")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new secd())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("cotd")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new cotd())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else if(expr.toString().contains("atan2")) {
					try {
						Expression exp = new ExpressionBuilder(expr.toString())
								.function(new atan2())
								.build();
						double result = exp.evaluate();
						ComputeDAO.storeExpressionResult(expr.toString(), Double.toString(result));
						expr.setLength(0);
						expr.append(result);
						session.setAttribute("expression", expr);
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
				else {
					try {
						Expression exp = new ExpressionBuilder(expr.toString()).build();
						double result = exp.evaluate();
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
