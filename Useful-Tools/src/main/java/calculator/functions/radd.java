package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class radd extends Function {
	
	public radd() {
		super("radd", 2);
	}
	
	@Override
	public double apply(double... args) {
		double a = args[0];
		double b = args[1];
		return a+b;
	}
}
