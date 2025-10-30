package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class imag extends Function {
	
	public imag() {
		super("imag", 2);
	}
	
	@Override
	public double apply(double... args) {
		return args[1];
	}
}
