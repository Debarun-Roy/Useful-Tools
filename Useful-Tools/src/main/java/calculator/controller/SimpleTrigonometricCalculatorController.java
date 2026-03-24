package calculator.controller;

import calculator.dao.ComputeDAO;
import calculator.utilities.IntermediateUtils;
import jakarta.servlet.annotation.WebServlet;

/**
 * Evaluates trigonometric expressions using all registered trig functions.
 *
 * FIX — replaced ExpressionBuilderFactory.create(expression).build().evaluate()
 * with IntermediateUtils.evaluateArithmeticExpression(expression).
 *
 * WHY:
 * The previous implementation called ExpressionBuilderFactory directly, which
 * passes the full expression to exp4j's evaluator. For any expression containing
 * a variadic function call (max, min, mean, median — valid in the Trig tab as
 * well as Combined), exp4j's evaluator throws "Invalid number of items on the
 * output queue" because the declared argument count (1) does not match the
 * actual count in the call.
 *
 * IntermediateUtils.evaluateArithmeticExpression() intercepts function calls
 * before they reach exp4j's evaluator, dispatches registered functions via
 * f.apply(args) directly, and only falls back to exp4j for built-in functions
 * like sin, cos, sqrt, etc. — which always have the correct argument count.
 *
 * All trig functions (sin, cos, tan, sind, cosd, tand, sec, cosec, cot, secd,
 * cosecd, cotd, asin, acos, atan, atan2, asec, acosec, acot) are registered
 * in FunctionRegistry by AppInitializer, so IntermediateUtils finds them in
 * the registry and dispatches them directly — no argument count issue.
 *
 * POST /api/calculator/trig
 * Body: { "expression": "sind(45)+cosd(45)" }
 */
@WebServlet("/api/calculator/trig")
public class SimpleTrigonometricCalculatorController extends AbstractCalculatorController {

    private static final long serialVersionUID = 1L;

    @Override
    protected double evaluate(String expression) throws Exception {
        try {
            double result = IntermediateUtils.evaluateArithmeticExpression(expression);
            ComputeDAO.storeExpressionResult(expression, Double.toString(result));
            return result;
        } catch (ArithmeticException ae) {
            // Division by zero or similar — return NaN for clean frontend display.
            return Double.NaN;
        }
    }
}