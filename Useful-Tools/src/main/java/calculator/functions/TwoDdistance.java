package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class TwoDdistance extends Function {

	public TwoDdistance() {
		super("lambda", 4);
	}
	
	@Override
	public double apply(double... args) {
		double x1 = args[0];
		double y1 = args[1];
		double x2 = args[2];
		double y2 = args[3];
		return Math.sqrt(Math.pow((x2-x1), 2)+Math.pow((y2-y1), 2));
	}
}
