package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class secd extends Function {
	
	public secd() {
		super("secd", 1);
	}
	
	@Override
	public double apply(double... args) {
		double result=0.0;
		try {
			result = 1.0/(Math.cos(Math.toRadians(args[0])));
		}
		catch(ArithmeticException ae) {
			result = Double.NaN;
		}
		return result;
	}
}
