package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sec extends Function{
	
	public sec() {
		super("sec", 1);
	}
	
	static {
		FunctionRegistry.register(new sec());
	}
	
	@Override
	public double apply(double... args) {
		double result=0.0;
		try {
			result = 1.0/(Math.cos(args[0]));
		}
		catch(ArithmeticException ae) {
			result = Double.NaN;
		}
		return result;
	}
}
