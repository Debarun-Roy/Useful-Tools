package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class max extends Function {
	
	public max() {
		super("max", -1);
	}
	@Override
	public double apply(double... args) {
		try {
			if(args.length < 1) {
				throw new IllegalArgumentException("The max function requires at least 1 argument");
			}
			else {
				double maxVal = args[0];
				for(int i=1;i<args.length;i++) {
					if(args[i]>maxVal) {
						maxVal = args[i];
					}
				}
				return maxVal;
			}
		}
		catch(Exception e) {
			return Double.NaN;
		}
	}
}
