package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * trunc(x) — truncates x toward zero (removes the fractional part).
 *
 * Examples:
 *   trunc(3.7)  →  3.0   (floor for positives)
 *   trunc(-3.7) → -3.0   (ceil for negatives — toward zero, not toward -∞)
 *
 * BUG FIX: The original used Math.floor(args[0]), which truncates toward
 * negative infinity. For negative inputs this gives the wrong result:
 *   Math.floor(-3.7) = -4.0   ← truncation away from zero (WRONG)
 *   Math.ceil(-3.7)  = -3.0   ← truncation toward zero    (CORRECT)
 *
 * The standard definition of truncation in mathematics and virtually every
 * programming language (C, Python, JavaScript, etc.) is truncation TOWARD
 * zero. Math.floor is correct only for non-negative inputs.
 *
 * Fixed: use (double)(long) cast which drops the fractional part and
 * always truncates toward zero regardless of sign.
 */
public class trunc extends Function {

    public trunc() {
        super("trunc", 1);
    }

    static {
        FunctionRegistry.register(new trunc());
    }

    @Override
    public double apply(double... args) {
        // Casting to long drops the fractional part, truncating toward zero.
        // (double)(long)(-3.7) == -3.0  ✓
        // (double)(long)(3.7)  ==  3.0  ✓
        return (double) (long) args[0];
    }
}