package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acosechd extends Function {

    public acosechd() {
        super("acosechd", 1);
    }

    static {
        FunctionRegistry.register(new acosechd());
    }

    @Override
    public double apply(double... args) {

        double x = Math.toRadians(args[0]);

        if (x == 0) return Double.NaN;

        return Math.log((1 + Math.sqrt(1 + x * x)) / x);
    }
}