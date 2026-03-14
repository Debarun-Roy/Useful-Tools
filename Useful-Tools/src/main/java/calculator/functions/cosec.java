package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cosec extends Function {
	
	public cosec() {
		super("cosec", 1);
	}
	
	static {
		FunctionRegistry.register(new cosec());
	}
	
	@Override
	public double apply(double... args) {
		double result=0.0;
		try {
			result = 1.0/(Math.sin(args[0]));
		}
		catch(ArithmeticException ae) {
			result = Double.NaN;
		}
		return result;
	}
}
