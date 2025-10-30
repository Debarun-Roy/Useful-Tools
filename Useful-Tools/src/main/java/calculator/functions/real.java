package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class real extends Function {
	
	public real() {
		super("real", 2);
	}
	
	@Override
	public double apply(double... args) {
		return args[0];
	}
}
