package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class trunc extends Function {

	public trunc() {
		super("trunc", 1);
	}
	
	@Override
	public double apply(double... args) {
		return Math.floor(args[0]);
	}
}
