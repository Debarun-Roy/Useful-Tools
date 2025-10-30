package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class acot extends Function {

	public acot() {
		super("acot", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.atan(1.0/args[0]);
	}
}
