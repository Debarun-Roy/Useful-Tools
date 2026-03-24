package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class implication extends Function {

	public implication() {
		super("implication", 2);
	}
	
	static {
		FunctionRegistry.register(new implication());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		
		return (!a || b)? 1:0;
	}
}
