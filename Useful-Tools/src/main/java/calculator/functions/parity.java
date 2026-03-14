package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class parity extends Function {

	public parity() {
		super("P", -1);
	}
	
	static {
		FunctionRegistry.register(new parity());
	}
	
	@Override
	public double apply(double... args) {
		int tc = 0;
		for(int i=0;i<args.length;i++) {
			if(args[i] != 0) {
				tc++;
			}
		}
		return (tc%2!=0)? 1:0;
	}
}
