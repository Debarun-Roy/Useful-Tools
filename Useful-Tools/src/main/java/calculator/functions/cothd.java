package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cothd extends Function {

    public cothd() {
        super("cothd", 1);
    }

    static {
        FunctionRegistry.register(new cothd());
    }

    @Override
    public double apply(double... args) {

        double sinhVal = Math.sinh(Math.toRadians(args[0]));

        if (sinhVal == 0) return Double.NaN;

        double coshVal = Math.cosh(Math.toRadians(args[0]));

        return coshVal / sinhVal;
    }
}