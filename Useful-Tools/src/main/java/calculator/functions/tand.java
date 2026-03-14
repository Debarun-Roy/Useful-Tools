package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class tand extends Function{

	public tand() {
		super("tand", 1);
	}
	
	static {
		FunctionRegistry.register(new tand());
	}
	
	@Override
	public double apply(double... args) {
		return Math.tan(Math.toRadians(args[0]));
	}
}
