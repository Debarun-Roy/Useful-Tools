package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class tanhd extends Function {

    public tanhd() {
        super("tanhd", 1);
    }

    static {
        FunctionRegistry.register(new tanhd());
    }

    @Override
    public double apply(double... args) {
        return Math.tanh(Math.toRadians(args[0]));
    }
}