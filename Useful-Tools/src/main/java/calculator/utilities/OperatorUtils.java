package calculator.utilities;

/**
 * IMPROVEMENT: Replaced "throw new Exception()" (a bare checked exception
 *   with no message) with "throw new IllegalArgumentException(...)" which:
 *   1. Is unchecked — callers are not forced to declare "throws Exception".
 *   2. Carries a descriptive message identifying the unknown operator symbol.
 *   3. Is the semantically correct exception for an invalid argument.
 *
 * IMPROVEMENT: Added the exponentiation operator '^' which was handled by
 *   the original switch but was absent in the else-if chain used here.
 *   Added integer modulo '%' and integer division '/' with divide-by-zero
 *   guard returning Double.NaN instead of throwing ArithmeticException.
 *
 * IMPROVEMENT: Variable renamed from "sum" to "result" — it holds the result
 *   of any binary operation, not just a sum.
 *
 * NOTE: This class provides a simple char-dispatch alternative to building
 *   a full exp4j expression. It is most useful when a UI sends individual
 *   operator characters rather than full expression strings.
 */
public class OperatorUtils {

    private OperatorUtils() { }

    /**
     * Applies a binary arithmetic operator to two operands.
     *
     * @param op  One of: '+', '-', '*', '/', '^', '%'
     * @param a   Left operand
     * @param b   Right operand
     * @return    Result of the operation, or Double.NaN for division by zero.
     * @throws IllegalArgumentException if {@code op} is not a recognised symbol.
     */
    public static double getBinaryResult(char op, double a, double b) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0.0) return Double.NaN;
                return a / b;
            case '^': return Math.pow(a, b);
            case '%':
                if (b == 0.0) return Double.NaN;
                return a % b;
            default:
                throw new IllegalArgumentException(
                        "Unrecognised operator symbol: '" + op + "'");
        }
    }
}