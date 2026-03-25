package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sinhd extends Function {

    public sinhd() {
        super("sinhd", 1);
    }

    static {
        FunctionRegistry.register(new sinhd());
    }

    @Override
    public double apply(double... args) {
        return Math.sinh(Math.toRadians(args[0]));
    }
}