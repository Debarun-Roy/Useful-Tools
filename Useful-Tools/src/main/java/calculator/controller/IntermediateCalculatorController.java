package calculator.controller;

import calculator.service.CalculatorService;
import jakarta.servlet.annotation.WebServlet;

/**
 * REFACTORED: Now extends AbstractCalculatorController.
 *
 * Was ~115 lines (including a large commented-out dead code block). Now 25 lines.
 * The only logic unique to this controller is:
 *   - Its session key ("intermediate_expression")
 *   - Its evaluation call (service.evaluateAndStore — persists to DB)
 *
 * The distinction between this and SimpleCalculatorController is that
 * evaluateAndStore() also writes the expression and result to expr_table.
 */
@WebServlet("/IntermediateCalculator")
public class IntermediateCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    private final CalculatorService service = new CalculatorService();

    @Override
    protected String getSessionKey() {
        return "intermediate_expression";
    }

    @Override
    protected double evaluate(String expr) throws Exception {
        return service.evaluateAndStore(expr);
    }
}