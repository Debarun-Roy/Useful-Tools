package calculator.controller;

import calculator.service.CalculatorService;
import jakarta.servlet.annotation.WebServlet;

@WebServlet("/api/calculator/emi")
public class EMICalculatorController extends AbstractCalculatorController{

	private static final long serialVersionUID = 1L;
	private final CalculatorService service = new CalculatorService();

	@Override
	protected double evaluate(String expression) throws Exception {
		// TODO Auto-generated method stub
		return service.evaluateAndStore(expression);
	}
	
}
