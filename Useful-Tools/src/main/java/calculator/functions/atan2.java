package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class atan2 extends Function {
	
	public atan2() {
		super("atan2", 1);
	}
	
	@Override
	public double apply(double... args) {
		double x=args[0];
		double y=args[1];
		
		double r = Math.sqrt(x*x+y*y);
		
		double theta = Math.asin(x/r);
		return theta;
	}
}
