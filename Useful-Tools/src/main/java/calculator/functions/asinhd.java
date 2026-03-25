package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class asinhd extends Function {

    public asinhd() {
        super("asinhd", 1);
    }

    static {
        FunctionRegistry.register(new asinhd());
    }

    @Override
    public double apply(double... args) {

        double x = Math.toRadians(args[0]);

        return Math.log(x + Math.sqrt(x * x + 1));
    }
}