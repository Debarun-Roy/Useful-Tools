package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sechd extends Function {

    public sechd() {
        super("sechd", 1);
    }

    static {
        FunctionRegistry.register(new sechd());
    }

    @Override
    public double apply(double... args) {

        double val = Math.cosh(Math.toRadians(args[0]));

        if (val == 0) return Double.NaN;

        return 1.0 / val;
    }
}