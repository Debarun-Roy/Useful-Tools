package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class real extends Function {
	
	public real() {
		super("real", 2);
	}
	
	static {
		FunctionRegistry.register(new real());
	}
	
	@Override
	public double apply(double... args) {
		return args[0];
	}
}
