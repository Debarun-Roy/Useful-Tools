package calculator.controller;

import calculator.service.CalculatorService;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates a standard arithmetic/function expression.
 * Extends AbstractCalculatorController — see that class for full details.
 *
 * Does NOT persist the result to the database (use IntermediateCalculator for that).
 *
 * POST /api/calculator/simple
 * Body: { "expression": "3+4*sin(0.5)" }
 */
@WebServlet("/api/calculator/simple")
public class SimpleCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;
    private final CalculatorService service = new CalculatorService();

    @Override
    protected double evaluate(String expression) throws Exception {
        return service.evaluate(expression);
    }
}