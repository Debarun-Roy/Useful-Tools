package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * rsub(a, b) — Real part subtraction: returns a - b.
 *
 * BUG FIX (CRITICAL): The original had super("rsub", 2) → WRONG.
 * Actually the original had super("cadd", 2) — it registered itself
 * under the name "cadd" instead of "rsub". This meant:
 *   1. The registry contained TWO entries named "cadd" (this class and
 *      cadd.java), so exp4j would use whichever was loaded first — either
 *      addition or subtraction, unpredictably.
 *   2. The registry contained ZERO entries named "rsub", so any expression
 *      containing rsub(...) would throw an unknown-function error at runtime.
 * Fixed: super("rsub", 2).
 */
public class rsub extends Function {

    public rsub() {
        super("rsub", 2);   // FIX: was "cadd" — wrong name causing registry collision
    }

    static {
        FunctionRegistry.register(new rsub());
    }

    @Override
    public double apply(double... args) {
        return args[0] - args[1];
    }
}