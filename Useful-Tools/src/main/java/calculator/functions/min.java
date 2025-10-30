package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class min extends Function {

	public min() {
		super("min", -1);
	}
	
	@Override
	public double apply(double... args) {
		try {
			if(args.length < 1) {
				throw new IllegalArgumentException("The min function requires at least 1 argument");
			}
			else {
				double minVal = args[0];
				for(int i=1;i<args.length;i++) {
					if(args[i]<minVal) {
						minVal = args[i];
					}
				}
				return minVal;
			}
		}
		catch(Exception e) {
			return Double.NaN;
		}
	}
}
