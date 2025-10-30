package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class cosecd extends Function{

	public cosecd() {
		super("cosecd", 1);
	}
	
	@Override
	public double apply(double... args) {
		double result=0.0;
		try {
			result =  1.0/(Math.sin(Math.toRadians(args[0])));
		}
		catch(ArithmeticException ae) {
			result = Double.NaN;
		}
		return result;
	}
}
