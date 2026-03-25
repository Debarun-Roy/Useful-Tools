package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sinh extends Function {
	public sinh() {
		super("sinh", 1);
	}
	
	static {
		FunctionRegistry.register(new sinh());
	}
	
	@Override
	public double apply(double... args) {
		return Math.sinh(args[0]);
	}
}
