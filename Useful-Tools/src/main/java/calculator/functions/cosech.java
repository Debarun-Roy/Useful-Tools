package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class cosech extends Function {

	public cosech() {
		super("cosech", 1);
	}
	
	static {
		FunctionRegistry.register(new cosech());
	}
	
	@Override
	public double apply(double... args) {
		return (1.0/Math.sinh(args[0]));
	}
}
