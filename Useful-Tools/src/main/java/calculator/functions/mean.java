package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * mean(a, b, ...) — returns the arithmetic mean of all arguments.
 * Argument count declared as 1 — see max.java for the full explanation.
 */
public class mean extends Function {

    public mean() {
        super("mean", 1);
    }

    static {
        FunctionRegistry.register(new mean());
    }

    @Override
    public double apply(double... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("mean requires at least 1 argument.");
        }
        double sum = 0.0;
        for (double arg : args) sum += arg;
        return sum / args.length;
    }
}