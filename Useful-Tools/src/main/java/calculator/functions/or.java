package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class or extends Function {
	
	public or() {
		super("or", 2);
	}
	
	static {
		FunctionRegistry.register(new or());
	}
	
	@Override
	public double apply(double... args) {
		int a = (int) Math.rint(args[0]);
		int b = (int) Math.rint(args[1]);
		
		if(a==1 || b==1) {
			return 1;
		}
		else {
			return 0;
		}
	}
}
