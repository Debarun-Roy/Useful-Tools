package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cotd extends Function {
	
	public cotd() {
		super("cotd", 1);
	}
	
	static {
		FunctionRegistry.register(new cotd());
	}
	
	@Override
	public double apply(double... args) {
		double result=0.0;
		try {
			result = 1.0/(Math.tan(Math.toRadians(args[0])));
		}
		catch(ArithmeticException ae) {
			result = Double.NaN;
		}
		return result;
	}
}
