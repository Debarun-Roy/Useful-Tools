package calculator.controller;

import calculator.service.CalculatorService;
import jakarta.servlet.annotation.WebServlet;

/**
 * REFACTORED: Now extends AbstractCalculatorController.
 *
 * Was ~70 lines. Now 25 lines.
 * The only logic unique to this controller is:
 *   - Its session key ("simple_expression")
 *   - Its evaluation call (service.evaluate — does NOT persist to DB)
 *
 * All session management, JSON serialisation, error handling, and
 * input routing is handled once in AbstractCalculatorController.
 */
@WebServlet("/SimpleCalculator")
public class SimpleCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    private final CalculatorService service = new CalculatorService();

    @Override
    protected String getSessionKey() {
        return "simple_expression";
    }

    @Override
    protected double evaluate(String expr) throws Exception {
        return service.evaluate(expr);
    }
}