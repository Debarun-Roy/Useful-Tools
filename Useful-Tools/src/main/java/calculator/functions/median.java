package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

public class median extends Function {

	public median() {
		super("median", -1);
	}
	
	static {
		FunctionRegistry.register(new median());
	}
	
	@Override
	public double apply(double... args) {
		try {
			if(args.length < 1) {
				throw new IllegalArgumentException("The avg function requires at least 1 argument");
			}
			else {
				if(args.length%2==0) {
					return args[args.length/2];
				}
				else {
					return (args[args.length/2]+args[args.length/2+1])/2;
				}
			}
		}
		catch(Exception e) {
			return Double.NaN;
		}
	}
}
