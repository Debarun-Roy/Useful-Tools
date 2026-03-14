package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class imag extends Function {
	
	public imag() {
		super("imag", 2);
	}
	
	static {
		FunctionRegistry.register(new imag());
	}
	
	@Override
	public double apply(double... args) {
		return args[1];
	}
}
