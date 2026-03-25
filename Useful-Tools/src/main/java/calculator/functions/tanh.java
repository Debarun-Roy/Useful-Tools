package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class tanh extends Function {

	public tanh() {
		super("tanh", 1);
	}
	
	static {
		FunctionRegistry.register(new tanh());
	}

	@Override
	public double apply(double... args) {
		return Math.tanh(args[0]);
	}
}