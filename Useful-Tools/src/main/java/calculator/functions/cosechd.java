package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cosechd extends Function {

    public cosechd() {
        super("cosechd", 1);
    }

    static {
        FunctionRegistry.register(new cosechd());
    }

    @Override
    public double apply(double... args) {

        double val = Math.sinh(Math.toRadians(args[0]));

        if (val == 0) return Double.NaN;

        return 1.0 / val;
    }
}