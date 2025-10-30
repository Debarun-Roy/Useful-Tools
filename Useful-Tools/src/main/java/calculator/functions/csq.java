package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class csq extends Function {

	public csq() {
		super("csq", 2);
	}
	
	@Override
	public double apply(double... args) {
		double r = args[0];
		double c = args[1];
		double ans = r*r - c*c;
		return ans;
	}
}