package calculator.exception;

/**
 * NEW FILE — CalculatorException
 *
 * WHY THIS IS NEEDED:
 * Every utility class (BooleanUtils, IntermediateUtils, CombinedUtils,
 * CalculatorService) currently declares "throws Exception", which:
 *   1. Forces every caller to handle the broadest possible checked exception.
 *   2. Makes it impossible to distinguish a parse error from an evaluation
 *      error from an I/O error in a catch block.
 *   3. Is considered an anti-pattern in production Java code.
 *
 * With CalculatorException, callers can write:
 *   catch (CalculatorException ce) { ... }
 * instead of catching all Exceptions and hoping for the best.
 *
 * HOW TO MIGRATE:
 * Replace "throws Exception" with "throws CalculatorException" in:
 *   - CalculatorService.evaluate()
 *   - CalculatorService.evaluateAndStore()
 *   - BooleanUtils.evaluateBooleanExpression()
 *   - IntermediateUtils.evaluateArithmeticExpression()
 *   - CombinedUtils.evaluateCombinedExpression()
 * Wrap internal exceptions with:
 *   throw new CalculatorException("Parse error: " + e.getMessage(), e);
 */
public class CalculatorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates a CalculatorException with a descriptive message. */
    public CalculatorException(String message) {
        super(message);
    }

    /**
     * Creates a CalculatorException wrapping a lower-level exception.
     * The original cause is preserved for stack-trace diagnostics.
     */
    public CalculatorException(String message, Throwable cause) {
        super(message, cause);
    }
}