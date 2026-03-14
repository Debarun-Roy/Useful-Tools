package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class asec extends Function {
	
	public asec() {
		super("asec", 1);
	}
	
	static {
		FunctionRegistry.register(new asec());
	}
	
	@Override
	public double apply(double... args) {
		return Math.acos(1.0/args[0]);
	}
}
