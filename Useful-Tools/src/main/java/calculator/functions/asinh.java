package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class asinh extends Function {

	public asinh() {
		super("asinh", 1);
	}
	
	static {
		FunctionRegistry.register(new asinh());
	}
	
	@Override
	public double apply(double... args) {
		return Math.log(args[0] + Math.sqrt(args[0] * args[0] + 1));
	}
}
