package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class csub extends Function {
	public csub() {
		super("cadd", 2);
	}
	
	@Override
	public double apply(double... args) {
		double a = args[0];
		double b = args[1];
		return a-b;
	}
}