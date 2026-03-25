package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class coshd extends Function {

    public coshd() {
        super("coshd", 1);
    }

    static {
        FunctionRegistry.register(new coshd());
    }

    @Override
    public double apply(double... args) {
        return Math.cosh(Math.toRadians(args[0]));
    }
}