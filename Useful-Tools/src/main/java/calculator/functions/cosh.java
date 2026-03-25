package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cosh extends Function {

	public cosh() {
		super("cosh", 1);
	}
	
	static {
		FunctionRegistry.register(new cosh());
	}

	@Override
	public double apply(double... args) {
		return Math.cosh(args[0]);
	}
}
