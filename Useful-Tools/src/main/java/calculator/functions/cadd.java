package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class cadd extends Function {
	public cadd() {
		super("cadd", 2);
	}
	
	@Override
	public double apply(double... args) {
		double a = args[0];
		double b = args[1];
		return a+b;
	}
}