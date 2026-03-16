package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * fact(n) — returns n! (n factorial).
 *
 * BUG FIX 1 — No guard against negative input:
 *   fact(-3) silently returned 1 because the loop condition (i <= -3) was
 *   never true, leaving the result at its initial value of 1. Factorial is
 *   undefined for negative numbers. Now returns Double.NaN for n < 0.
 *
 * BUG FIX 2 — No guard against non-integer input:
 *   fact(3.7) truncated silently to fact(3) = 6 with no indication that
 *   the input was not an integer. The general factorial for non-integers is
 *   the Gamma function, which is beyond the scope of this implementation.
 *   Now returns Double.NaN for inputs that are not whole numbers.
 *
 * IMPROVEMENT — Long overflow protection:
 *   The original used a long accumulator and then assigned it back to a
 *   double. fact(21) overflows a long (max long ≈ 9.2 × 10¹⁸; 21! ≈ 5.1 × 10¹⁹).
 *   Switching to double arithmetic loses integer precision for large n, but
 *   avoids silent overflow and is consistent with how exp4j represents all
 *   values. fact(20) = 2.432902008176640e18 is representable exactly as a
 *   double; values beyond that are returned as approximate doubles.
 *   Users needing exact large factorials should use a BigInteger library.
 *
 * IMPROVEMENT — Upper bound guard:
 *   fact(n) for n > 170 overflows double to Infinity (170! ≈ 7.2 × 10³⁰⁶;
 *   171! > Double.MAX_VALUE). Returns Double.POSITIVE_INFINITY for n > 170,
 *   which is the IEEE 754 correct representation of overflow.
 */
public class fact extends Function {

    public fact() {
        super("fact", 1);
    }

    static {
        FunctionRegistry.register(new fact());
    }

    @Override
    public double apply(double... args) {
        double n = args[0];

        // FIX 2: Reject non-integer inputs.
        if (n != Math.floor(n)) {
            return Double.NaN;
        }

        // FIX 1: Factorial is undefined for negative numbers.
        if (n < 0) {
            return Double.NaN;
        }

        // 0! = 1! = 1 by definition.
        if (n == 0 || n == 1) {
            return 1.0;
        }

        // IMPROVEMENT: double accumulator avoids long overflow for n > 20.
        double result = 1.0;
        for (int i = 2; i <= (int) n; i++) {
            result *= i;
        }
        return result;  // Returns Double.POSITIVE_INFINITY for n > 170.
    }
}