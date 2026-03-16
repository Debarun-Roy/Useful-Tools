package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.utilities.CombinedUtils;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates expressions that mix arithmetic, trigonometric, and logical
 * operators in a single expression string. Persists the result.
 *
 * POST /api/calculator/combined
 * Body: { "expression": "sin(3.14)+max(3,7)" }
 */
@WebServlet("/api/calculator/combined")
public class CombinedCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected double evaluate(String expression) throws Exception {
        double result = CombinedUtils.evaluateCombinedExpression(expression);
        ComputeDAO.storeExpressionResult(expression, Double.toString(result));
        return result;
    }
}