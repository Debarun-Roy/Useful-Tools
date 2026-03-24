package calculator.service;

import calculator.expression.ExpressionBuilderFactory;
import calculator.repository.ComputeRepository;
import calculator.utilities.BooleanUtils;
import calculator.utilities.CombinedUtils;
import calculator.utilities.IntermediateUtils;

/**
 * FIX — validateForMode() now uses evaluation-based validation for all modes.
 *
 * The previous approach used ExpressionBuilderFactory.create(expr).build()
 * for standard modes and BooleanUtils.buildBooleanExpression(expr).build()
 * for boolean mode. Both relied on exp4j's build() succeeding.
 *
 * The problem: build() behaviour depends on the exact exp4j version and which
 * operator symbols it accepts. For some symbols (|, and possibly others),
 * build() may throw even when evaluation works correctly, because the tokenizer
 * and the operator-registration path have different validation logic.
 *
 * The correct approach: validate by attempting the SAME CODE PATH used for
 * actual evaluation. If evaluation succeeds, the expression is valid. If it
 * throws (for any reason other than a numeric error like division by zero),
 * the expression is invalid.
 *
 * Numeric errors (ArithmeticException for 1/0) mean the expression IS valid
 * syntactically — it parsed and evaluated, it just produced an undefined result.
 * These are treated as valid so that expressions like 1/0 show a green dot.
 */
public class CalculatorService {

    private final ComputeRepository repository = new ComputeRepository();

    public double evaluate(String expression) throws Exception {
        requireNonBlank(expression);
        try {
            return IntermediateUtils.evaluateArithmeticExpression(expression);
        } catch (ArithmeticException ae) {
            return Double.NaN;
        }
    }

    public double evaluateAndStore(String expression) throws Exception {
        double result = evaluate(expression);
        repository.storeExpressionResult(expression, Double.toString(result));
        return result;
    }

    /**
     * Validates an expression for a given calculator mode.
     *
     * Mode values match the tab IDs sent from the React frontend:
     *   "simple"       → tries IntermediateUtils evaluation
     *   "intermediate" → tries IntermediateUtils evaluation
     *   "trig"         → tries IntermediateUtils evaluation
     *   "boolean"      → tries BooleanUtils evaluation
     *   "combined"     → tries IntermediateUtils first, then BooleanUtils
     *
     * ArithmeticException (division by zero) is treated as VALID because it
     * means the expression parsed and evaluated correctly — it just produced
     * a numeric error, not a syntax error.
     *
     * All other exceptions and errors are treated as INVALID.
     */
    public boolean validateForMode(String expr, String mode) {
        if (expr == null || expr.isBlank()) return false;

        switch (mode) {

            case "boolean":
                return BooleanUtils.validateExpression(expr);

            case "combined":
                return tryCombinedValidation(expr);

            default:
                // simple, intermediate, trig
                return tryArithmeticValidation(expr);
        }
    }

    /**
     * Attempts to validate by actually evaluating through IntermediateUtils.
     * Returns true if evaluation succeeds or throws only ArithmeticException.
     * Returns false for all other exceptions and errors.
     */
    private boolean tryArithmeticValidation(String expr) {
        try {
            IntermediateUtils.evaluateArithmeticExpression(expr);
            return true;
        } catch (ArithmeticException ae) {
            // Division by zero etc. — expression IS syntactically valid.
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean tryCombinedValidation(String expr) {
        try {
            CombinedUtils.evaluateCombinedExpression(expr);
            return true;
        } catch (ArithmeticException ae) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Kept for backward compatibility with any code that calls this directly.
     * Delegates to tryArithmeticValidation.
     */
    public boolean validateExpression(String expression) {
        return tryArithmeticValidation(expression);
    }

    private void requireNonBlank(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression must not be null or blank.");
        }
    }
}
