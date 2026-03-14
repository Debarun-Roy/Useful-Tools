package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class and extends Function {

	public and() {
		super("and", 2);
	}
	
	static {
		FunctionRegistry.register(new and());
	}
	
	@Override
	public double apply(double... args) {
		int a = (int) Math.rint(args[0]);
		int b = (int) Math.rint(args[1]);
		
		if(a==1 && b==1) {
			return 1;
		}
		else {
			return 0;
		}
	}
}
