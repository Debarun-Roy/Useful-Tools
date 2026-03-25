package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acothd extends Function {

    public acothd() {
        super("acothd", 1);
    }

    static {
        FunctionRegistry.register(new acothd());
    }

    @Override
    public double apply(double... args) {

        double x = Math.toRadians(args[0]);

        if (Math.abs(x) <= 1) return Double.NaN;

        return 0.5 * Math.log((x + 1) / (x - 1));
    }
}