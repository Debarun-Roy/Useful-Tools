package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class asec extends Function {
	
	public asec() {
		super("asec", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.acos(1.0/args[0]);
	}
}
