package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acoshd extends Function {

    public acoshd() {
        super("acoshd", 1);
    }

    static {
        FunctionRegistry.register(new acoshd());
    }

    @Override
    public double apply(double... args) {

        double x = Math.toRadians(args[0]);

        if (x < 1) return Double.NaN;

        return Math.log(x + Math.sqrt(x * x - 1));
    }
}