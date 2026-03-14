package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class mean extends Function {

	public mean() {
		super("mean", -1);
	}
	
	static {
		FunctionRegistry.register(new mean());
	}
	
	@Override
	public double apply(double... args) {
		try {
			if(args.length < 1) {
				throw new IllegalArgumentException("The avg function requires at least 1 argument");
			}
			else {
				double sum = 0.0;
				for(int i=0;i<args.length;i++) {
					sum += args[i];
				}
				return sum/args.length;
			}
		}
		catch(Exception e) {
			return Double.NaN;
		}
	}
}
