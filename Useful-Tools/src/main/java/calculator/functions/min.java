package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * min(a, b, ...) — returns the smallest value among all arguments.
 * Argument count declared as 1 — see max.java for the full explanation.
 */
public class min extends Function {

    public min() {
        super("min", 1);
    }

    static {
        FunctionRegistry.register(new min());
    }

    @Override
    public double apply(double... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("min requires at least 1 argument.");
        }
        double minVal = args[0];
        for (int i = 1; i < args.length; i++) {
            if (args[i] < minVal) minVal = args[i];
        }
        return minVal;
    }
}