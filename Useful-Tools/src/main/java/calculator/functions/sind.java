package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class sind extends Function{
	
	public sind() {
		super("sind", 1);
	}
	
	static {
		FunctionRegistry.register(new sind());
	}
	
	@Override
	public double apply(double... args) {
		return Math.sin(Math.toRadians(args[0]));
	}
}
