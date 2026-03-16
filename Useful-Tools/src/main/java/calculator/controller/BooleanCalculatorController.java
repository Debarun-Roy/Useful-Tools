package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.utilities.BooleanUtils;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates a boolean/logical expression using the full operator set
 * (AND, OR, XOR, NAND, NOR, implication, etc.) and persists the result.
 *
 * POST /api/calculator/boolean
 * Body: { "expression": "1&(0|1)" }
 */
@WebServlet("/api/calculator/boolean")
public class BooleanCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected double evaluate(String expression) throws Exception {
        double result = BooleanUtils.evaluateBooleanExpression(expression);
        ComputeDAO.storeExpressionResult(expression, Double.toString(result));
        return result;
    }
}