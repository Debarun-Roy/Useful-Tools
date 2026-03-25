package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acoth extends Function {

    public acoth() {
        super("acoth", 1);
    }

    static {
        FunctionRegistry.register(new acoth());
    }

    @Override
    public double apply(double... args) {
        double x = args[0];

        if (Math.abs(x) <= 1) return Double.NaN;

        return 0.5 * Math.log((x + 1) / (x - 1));
    }
}