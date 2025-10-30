package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class tand extends Function{

	public tand() {
		super("tand", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.tan(Math.toRadians(args[0]));
	}
}
