package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class reverseImplication extends Function {

	public reverseImplication() {
		super("reverseImplication", 2);
	}
	
	static {
		FunctionRegistry.register(new reverseImplication());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		
		return (a || !b)? 1:0;
	}
}
