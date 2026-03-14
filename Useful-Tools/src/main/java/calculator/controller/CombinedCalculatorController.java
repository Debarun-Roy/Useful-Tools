package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.utilities.CombinedUtils;
import jakarta.servlet.annotation.WebServlet;

/**
 * REFACTORED: Now extends AbstractCalculatorController.
 *
 * Was ~77 lines (with the missing-return bug). Now 30 lines.
 * The missing-return bug is gone — AbstractCalculatorController's
 * doPost() always returns after the "=" branch.
 *
 * The only logic unique to this controller is:
 *   - Its session key ("combined_expression")
 *   - Its evaluation call (CombinedUtils.evaluateCombinedExpression)
 *   - Persisting result via ComputeDAO directly
 */
@WebServlet("/CombinedCalculator")
public class CombinedCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected String getSessionKey() {
        return "combined_expression";
    }

    @Override
    protected double evaluate(String expr) throws Exception {
        double result = CombinedUtils.evaluateCombinedExpression(expr);
        ComputeDAO.storeExpressionResult(expr, Double.toString(result));
        return result;
    }
}