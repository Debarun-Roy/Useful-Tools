package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class round extends Function {
	
	public round() {
		super("round", 2);
	}
	
	static {
		FunctionRegistry.register(new round());
	}
	
	@Override
	public double apply(double... args) {
		double num = args[0];
		int dec = (int) Math.rint(args[0]);
		
		String numStr = Double.toString(num);
		int indexOfDecimal = numStr.indexOf('.');
		String resultStr = numStr.substring(0, indexOfDecimal+dec+1);
		return Double.parseDouble(resultStr);
	}
}
