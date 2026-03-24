package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * percent(x) — converts a percentage value to its decimal equivalent.
 *
 * Returns x / 100.0
 *
 * Examples:
 *   percent(50)        → 0.5
 *   200 * percent(15)  → 30.0   (15% of 200)
 *   percent(100)       → 1.0
 *
 * WHY A FUNCTION NOT AN OPERATOR:
 * The original design used a unary postfix '%' operator (percentage.java).
 * exp4j's built-in binary '%' operator (modulo) uses the same symbol.
 * Registering a second custom '%' operator on top of the built-in one causes
 * an IllegalArgumentException at ExpressionBuilder construction time.
 *
 * Implementing this as a named function avoids the symbol conflict entirely.
 * The '%' symbol remains available as exp4j's native binary modulo operator:
 *   10 % 3 → 1   (modulo)
 *   percent(10) → 0.1   (percentage to decimal)
 *
 * Both operations are available simultaneously with no conflict.
 */
public class percent extends Function {

    public percent() {
        super("percent", 1);
    }

    static {
        FunctionRegistry.register(new percent());
    }

    @Override
    public double apply(double... args) {
        return args[0] / 100.0;
    }
}
