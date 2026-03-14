package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class fact extends Function{
	
	public fact() {
		super("fact", 1);
	}
	
	static {
		FunctionRegistry.register(new fact());
	}
	
	@Override
	public double apply(double... args) {
		double a = args[0];
		long f = 1;
		for(int i=2;i<=a;i++) {
			f *= i;
		}
		a = f;
		return a;
	}
}
