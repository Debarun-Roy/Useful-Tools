package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class atanh extends Function {

	public atanh() {
		super("atanh", 1);
	}
	
	static {
		FunctionRegistry.register(new atanh());
	}
	
	@Override
	public double apply(double... args) {
		if (Math.abs(args[0]) >= 1) 
			return Double.NaN;
		return 0.5 * Math.log((1 + args[0]) / (1 - args[0]));
	}
}
