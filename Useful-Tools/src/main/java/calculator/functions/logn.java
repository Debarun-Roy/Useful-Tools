package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class logn extends Function {
	
	public logn() {
		super("logn", 2);
	}
	
	static {
		FunctionRegistry.register(new logn());
	}
	
	@Override
	public double apply(double... args) {
		return Math.log(args[0])/Math.log(args[1]);
	}
}
