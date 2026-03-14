package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cosd extends Function{
	
	public cosd() {
		super("cosd", 1);
	}
	
	static {
		FunctionRegistry.register(new cosd());
	}
	
	@Override
	public double apply(double... args) {
		return Math.cos(Math.toRadians(args[0]));
	}
}