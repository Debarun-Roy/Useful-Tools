package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import calculator.dao.ComputeDAO;
import calculator.functions.cadd;
import calculator.functions.csq;
import calculator.functions.csub;
import calculator.functions.imag;
import calculator.functions.radd;
import calculator.functions.real;
import calculator.functions.rsub;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

@WebServlet("/ComplexNumberCalculator/HandleCalculate")
public class ComplexNumberCalculatorController extends HttpServlet {

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
			Double lastReal = (Double) session.getAttribute("lastReal");
			Double lastImag = (Double) session.getAttribute("lastImag");

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			if(lastReal == Double.NaN || lastImag == Double.NaN || lastReal == null || lastImag == null) {
				lastReal = 0.0;
				lastImag = 0.0;
				session.setAttribute("realPart", lastReal);
				session.setAttribute("imagPart", lastImag);
			}

			String input = request.getParameter("input");
			
			if(input.endsWith("=")) {
				input = input.substring(0,input.length()-1); //remove the =
				if(input.startsWith("complex_add")) {
					try {
						String pattern = "complex_add\\(([^\\+\\-]+)\\+([^\\+\\-]+)i,([^\\+\\-]+)\\+([^\\+\\-]+)i\\)";
						String replacement = "radd($1,$3) + cadd($2,$4)";
						
						String replacedExpr = input.replaceAll(pattern, replacement);
						
						String realStr = replacedExpr.replaceAll(".*radd\\(([^,]+),([^\\)]+)\\).*", "radd($1,$2)");
						String imagStr = replacedExpr.replaceAll(".*cadd\\(([^,]+),([^\\)]+)\\).*", "cadd($1,$2)");
						
						Expression realExp = new ExpressionBuilder(realStr)
								.function(new radd())
								.build();
						lastReal = realExp.evaluate();
						
						Expression imagExp = new ExpressionBuilder(imagStr)
								.function(new cadd())
								.build();
						lastImag = imagExp.evaluate();
						
						String resultExpr = lastReal+"+"+lastImag+"i";
						ComputeDAO.storeExpressionResult(input, resultExpr);
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
						
						jsonResponse = gson.toJson(resultExpr);
						
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
				else if(input.startsWith("complex_subtract")) {
					try {
						
						String pattern = "complex_subtract\\(([^\\+\\-]+)\\+([^\\+\\-]+)i,([^\\+\\-]+)\\+([^\\+\\-]+)i\\)";
						String replacement = "rsub($1,$3) + csub($2,$4)";
						
						String replacedExpr = input.replaceAll(pattern, replacement);
						
						String realStr = replacedExpr.replaceAll(".*rsub\\(([^,]+),([^\\)]+)\\).*", "rsub($1,$2)");
						String imagStr = replacedExpr.replaceAll(".*csub\\(([^,]+),([^\\)]+)\\).*", "csub($1,$2)");
						
						Expression realExp = new ExpressionBuilder(realStr)
								.function(new rsub())
								.build();
						lastReal = realExp.evaluate();
						
						Expression imagExp = new ExpressionBuilder(imagStr)
								.function(new csub())
								.build();
						lastImag = imagExp.evaluate();
						
						String resultExpr = lastReal+"+"+lastImag+"i";
						
						ComputeDAO.storeExpressionResult(input, resultExpr);
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
						
						jsonResponse = gson.toJson(resultExpr);
						
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
				else if(input.startsWith("conj")) {
					try {
						Pattern p = Pattern.compile("conj\\(([^\\+\\-]+)\\+([^\\+\\-]+)i\\)");
						Matcher m = p.matcher(input);
						double aReal = 0.0;
						double aImag = 0.0;
						if (m.find()) {
							String aRealExpr = m.group(1);
							String aImagExpr = m.group(2);

							aReal = new ExpressionBuilder(aRealExpr).build().evaluate();
							aImag = new ExpressionBuilder(aImagExpr).build().evaluate();
						}
						String resultExpr = aReal+"-"+aImag+"i";
						ComputeDAO.storeExpressionResult(input, resultExpr);
						lastReal=aReal;
						lastImag=aImag;
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
						jsonResponse = gson.toJson(resultExpr);
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
				else if(input.startsWith("imag")) {
					try {
						Expression expr = new ExpressionBuilder(input)
								.function(new imag())
								.build();
						lastImag = expr.evaluate();
						lastReal = 0.0;
						
						ComputeDAO.storeExpressionResult(input, Double.toString(lastImag));
						jsonResponse = gson.toJson(lastImag);
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
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
				else if(input.startsWith("real")) {
					try {
						Expression expr = new ExpressionBuilder(input)
								.function(new real())
								.build();
						lastImag = 0.0;
						lastReal = expr.evaluate();
						
						ComputeDAO.storeExpressionResult(input, Double.toString(lastReal));
						jsonResponse = gson.toJson(lastReal);
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
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
				else if(input.startsWith("csq")) {
					try {
						Expression expr = new ExpressionBuilder(input)
								.function(new csq())
								.build();
						lastImag = 0.0;
						lastReal = expr.evaluate();
						
						ComputeDAO.storeExpressionResult(input, Double.toString(lastReal));
						jsonResponse = gson.toJson(lastReal);
						session.setAttribute("lastReal", lastReal);
						session.setAttribute("lastImag", lastImag);
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
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}