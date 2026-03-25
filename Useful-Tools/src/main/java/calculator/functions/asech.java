package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class asech extends Function {

    public asech() {
        super("asech", 1);
    }

    static {
        FunctionRegistry.register(new asech());
    }

    @Override
    public double apply(double... args) {
        double x = args[0];

        if (x <= 0 || x > 1) return Double.NaN;

        return Math.log((1 + Math.sqrt(1 - x * x)) / x);
    }
}