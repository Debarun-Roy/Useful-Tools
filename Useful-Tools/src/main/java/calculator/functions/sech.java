package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sech extends Function {

	public sech() {
		super("sech", 1);
	}
	
	static {
		FunctionRegistry.register(new sech());
	}

	@Override
	public double apply(double... args) {
		return (1.0/Math.cosh(args[0]));
	}
}
