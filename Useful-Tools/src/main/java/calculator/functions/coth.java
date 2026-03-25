package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class coth extends Function {

	public coth() {
		super("coth", 1);
	}
	
	static {
		FunctionRegistry.register(new coth());
	}
	
	@Override
	public double apply(double... args) {
		return (Math.cosh(args[0])/Math.sinh(args[0]));
	}
}
