package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.utilities.BooleanUtils;
import jakarta.servlet.annotation.WebServlet;

/**
 * REFACTORED: Now extends AbstractCalculatorController.
 *
 * Was ~80 lines. Now 30 lines.
 * The only logic unique to this controller is:
 *   - Its session key ("boolean_expression")
 *   - Its evaluation call (BooleanUtils.evaluateBooleanExpression)
 *   - Persisting to the database via ComputeDAO directly (BooleanUtils
 *     does not go through CalculatorService, so we store the result here)
 */
@WebServlet("/BooleanCalculator")
public class BooleanCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected String getSessionKey() {
        return "boolean_expression";
    }

    @Override
    protected double evaluate(String expr) throws Exception {
        double result = BooleanUtils.evaluateBooleanExpression(expr);
        ComputeDAO.storeExpressionResult(expr, Double.toString(result));
        return result;
    }
}