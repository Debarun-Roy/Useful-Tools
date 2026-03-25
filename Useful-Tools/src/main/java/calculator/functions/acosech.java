package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acosech extends Function {

    public acosech() {
        super("acosech", 1);
    }

    static {
        FunctionRegistry.register(new acosech());
    }

    @Override
    public double apply(double... args) {
        double x = args[0];

        if (x == 0) 
        	return Double.NaN;

        return Math.log((1 + Math.sqrt(1 + x * x)) / x);
    }
}