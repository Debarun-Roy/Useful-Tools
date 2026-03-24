package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class xor extends Function {

	public xor() {
		super("xor", 2);
	}
	
	static {
		FunctionRegistry.register(new xor());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		
		return ((a || b) && !(a && b))? 1:0;
	}
}
