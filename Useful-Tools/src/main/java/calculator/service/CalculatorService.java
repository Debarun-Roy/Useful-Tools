package calculator.service;

import calculator.expression.ExpressionBuilderFactory;
import calculator.repository.ComputeRepository;
import net.objecthunter.exp4j.Expression;

/**
 * IMPROVEMENT: Added validateExpression() so callers can check an expression
 *   is syntactically valid before evaluating it — useful for giving the user
 *   early feedback rather than an exception on "=".
 *
 * IMPROVEMENT: evaluate() and evaluateAndStore() now throw descriptive
 *   IllegalArgumentException on blank/null input instead of letting exp4j
 *   produce a confusing internal parse error.
 */
public class CalculatorService {

    private final ComputeRepository repository = new ComputeRepository();

    /**
     * Evaluates the expression and returns the result.
     * Does NOT persist the result to the database.
     */
    public double evaluate(String expression) throws Exception {
        requireNonBlank(expression);
        Expression e = ExpressionBuilderFactory.create(expression).build();
        return e.evaluate();
    }

    /**
     * Evaluates the expression, persists (expression, result) to the database,
     * and returns the result.
     */
    public double evaluateAndStore(String expression) throws Exception {
        double result = evaluate(expression);
        repository.storeExpressionResult(expression, Double.toString(result));
        return result;
    }

    /**
     * Returns true if the expression can be parsed without error.
     * Does not evaluate — only checks syntax.
     * Useful for front-end validation before the user presses "=".
     */
    public boolean validateExpression(String expression) {
        if (expression == null || expression.isBlank()) return false;
        try {
            ExpressionBuilderFactory.create(expression).build();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void requireNonBlank(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression must not be null or blank.");
        }
    }
}