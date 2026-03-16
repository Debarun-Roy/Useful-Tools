package calculator.controller;

import calculator.service.CalculatorService;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates an expression AND persists it (expression + result) to expr_table.
 * Use this calculator when you want the result to appear in calculation history.
 *
 * POST /api/calculator/intermediate
 * Body: { "expression": "log(2,8)" }
 */
@WebServlet("/api/calculator/intermediate")
public class IntermediateCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;
    private final CalculatorService service = new CalculatorService();

    @Override
    protected double evaluate(String expression) throws Exception {
        return service.evaluateAndStore(expression);
    }
}