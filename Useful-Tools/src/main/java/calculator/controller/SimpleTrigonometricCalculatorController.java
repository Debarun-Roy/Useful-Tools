package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.expression.ExpressionBuilderFactory;
import jakarta.servlet.annotation.WebServlet;

/**
 * REFACTORED: Now extends AbstractCalculatorController.
 *
 * Was 440 lines (the 13-branch if-else substring dispatcher). Now 30 lines.
 *
 * The entire dispatch chain has been replaced by ExpressionBuilderFactory,
 * which has all trig functions available simultaneously via the registry
 * populated by AppInitializer at startup. Expressions combining multiple
 * trig functions (e.g. "sin(0.5)+cos(0.5)") now work correctly.
 *
 * The only logic unique to this controller is:
 *   - Its session key ("trig_expression")
 *   - Its evaluation call (ExpressionBuilderFactory.create().build().evaluate())
 *   - Persisting result via ComputeDAO directly
 */
@WebServlet("/TrigonometricCalculator")
public class SimpleTrigonometricCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected String getSessionKey() {
        return "trig_expression";
    }

    @Override
    protected double evaluate(String expr) throws Exception {
        double result = ExpressionBuilderFactory.create(expr).build().evaluate();
        ComputeDAO.storeExpressionResult(expr, Double.toString(result));
        return result;
    }
}