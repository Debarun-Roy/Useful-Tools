package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.expression.ExpressionBuilderFactory;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates trigonometric expressions using all registered trig functions
 * (sin, cos, tan, sec, cosec, cot, sind, cosd, tand, secd, cosecd, cotd,
 * acosec, asec, acot, atan2). Persists the result.
 *
 * POST /api/calculator/trig
 * Body: { "expression": "sind(45)+cosd(45)" }
 *
 * Note: Unlike the original implementation which had a 13-branch if-else
 * chain that could only evaluate one trig function per expression,
 * ExpressionBuilderFactory registers ALL functions simultaneously, so
 * expressions combining multiple trig functions work correctly.
 */
@WebServlet("/api/calculator/trig")
public class SimpleTrigonometricCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected double evaluate(String expression) throws Exception {
        double result = ExpressionBuilderFactory.create(expression).build().evaluate();
        ComputeDAO.storeExpressionResult(expression, Double.toString(result));
        return result;
    }
}