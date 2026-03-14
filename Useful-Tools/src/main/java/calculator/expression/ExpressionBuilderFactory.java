package calculator.expression;

import calculator.registry.FunctionRegistry;
import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * IMPROVEMENT: Added a validate() convenience method that returns true if the
 *   expression can be parsed without error. Used by CalculatorService and can
 *   be exposed via a /ValidateExpression endpoint for live front-end feedback.
 *
 * IMPROVEMENT: Added requireNonBlank() guard — passing null or blank to
 *   ExpressionBuilder produces a cryptic NullPointerException from inside
 *   exp4j rather than a descriptive error. Now throws IllegalArgumentException
 *   with a clear message before reaching exp4j.
 */
public class ExpressionBuilderFactory {

    private ExpressionBuilderFactory() { }

    /**
     * Creates an ExpressionBuilder pre-loaded with all registered functions
     * and operators from FunctionRegistry and OperatorRegistry.
     *
     * @param expr The expression string.
     * @return A configured ExpressionBuilder ready for .build().
     * @throws IllegalArgumentException if expr is null or blank.
     */
    public static ExpressionBuilder create(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException(
                    "Expression must not be null or blank.");
        }
        ExpressionBuilder builder = new ExpressionBuilder(expr);
        FunctionRegistry.getFunctions().forEach(builder::function);
        OperatorRegistry.getOperators().forEach(builder::operator);
        return builder;
    }

    /**
     * Returns true if the expression can be parsed by exp4j with the currently
     * registered functions and operators, false otherwise.
     * Does not evaluate the expression.
     *
     * @param expr The expression string to validate.
     * @return true if parseable, false if null/blank/invalid.
     */
    public static boolean validate(String expr) {
        if (expr == null || expr.isBlank()) return false;
        try {
            create(expr).build();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}