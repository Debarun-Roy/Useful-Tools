package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * max(a, b, ...) — returns the largest value among all arguments.
 *
 * The argument count is declared as 1 — the minimum positive integer exp4j
 * accepts in the Function constructor. This satisfies the constructor without
 * crashing at startup.
 *
 * The actual argument count is never enforced by exp4j because IntermediateUtils
 * and CombinedUtils now call f.apply(args) directly for any function found in
 * FunctionRegistry, bypassing exp4j's argument-count check at evaluation time.
 * The declared count here is therefore irrelevant to runtime behaviour.
 */
public class max extends Function {

    public max() {
        super("max", 1);
    }

    static {
        FunctionRegistry.register(new max());
    }

    @Override
    public double apply(double... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("max requires at least 1 argument.");
        }
        double maxVal = args[0];
        for (int i = 1; i < args.length; i++) {
            if (args[i] > maxVal) maxVal = args[i];
        }
        return maxVal;
    }
}