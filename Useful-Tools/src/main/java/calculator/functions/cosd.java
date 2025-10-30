package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class cosd extends Function{
	
	public cosd() {
		super("cosd", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.cos(Math.toRadians(args[0]));
	}
}