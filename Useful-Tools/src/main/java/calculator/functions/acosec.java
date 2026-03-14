package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acosec extends Function {

	public acosec() {
		super("acosec", 1);
	}
	
	static {
		FunctionRegistry.register(new acosec());
	}
	
	@Override
	public double apply(double... args) {
		return Math.asin(1.0/args[0]);
	}
}
