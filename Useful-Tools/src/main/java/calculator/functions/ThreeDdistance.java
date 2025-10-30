package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class ThreeDdistance extends Function {
	
	public ThreeDdistance() {
		super("lambda", 6);
	}
	
	@Override
	public double apply(double... args) {
		double x1 = args[0];
		double y1 = args[1];
		double z1 = args[2];
		double x2 = args[3];
		double y2 = args[4];
		double z2 = args[5];
		return Math.sqrt(Math.pow((x2-x1), 2)+Math.pow((y2-y1), 2)+Math.pow((z2-z1), 2));
	}
}
