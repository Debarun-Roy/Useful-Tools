package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class sind extends Function{
	
	public sind() {
		super("sind", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.sin(Math.toRadians(args[0]));
	}
}
