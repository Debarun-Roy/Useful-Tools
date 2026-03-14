package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class trunc extends Function {

	public trunc() {
		super("trunc", 1);
	}
	
	static {
		FunctionRegistry.register(new trunc());
	}
	
	@Override
	public double apply(double... args) {
		return Math.floor(args[0]);
	}
}
