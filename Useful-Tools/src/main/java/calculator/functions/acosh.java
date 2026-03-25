package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class acosh extends Function {

	public acosh() {
		super("acosh", 1);
	}
	
	static {
		FunctionRegistry.register(new acosh());
	}

	@Override
	public double apply(double... args) {
		if (args[0] < 1) 
			return Double.NaN;
		return Math.log(args[0] + Math.sqrt(args[0] * args[0] - 1));
	}
}
