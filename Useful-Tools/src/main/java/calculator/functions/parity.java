package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * P(a, b, ...) — parity function. Returns 1 if an odd number of arguments
 * are non-zero (true), 0 otherwise.
 * Argument count declared as 1 — see max.java for the full explanation.
 */
public class parity extends Function {

    public parity() {
        super("P", 1);
    }

    static {
        FunctionRegistry.register(new parity());
    }

    @Override
    public double apply(double... args) {
        int trueCount = 0;
        for (double arg : args) {
            if (arg != 0) trueCount++;
        }
        return (trueCount % 2 != 0) ? 1 : 0;
    }
}