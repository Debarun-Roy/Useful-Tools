package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cadd extends Function {
	public cadd() {
		super("cadd", 2);
	}
	
	static {
		FunctionRegistry.register(new cadd());
	}
	
	@Override
	public double apply(double... args) {
		double a = args[0];
		double b = args[1];
		return a+b;
	}
}