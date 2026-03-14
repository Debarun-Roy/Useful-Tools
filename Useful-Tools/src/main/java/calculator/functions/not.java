package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class not extends Function {

	public not() {
		super("not", 1);
	}
	
	static {
		FunctionRegistry.register(new not());
	}
	
	@Override
	public double apply(double... args) {
		int a = (int) Math.rint(args[0]);
		
		if(a==1) {
			return 0;
		}
		else {
			return 1;
		}
	}
}
