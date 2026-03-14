package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class leftShift extends Operator {

	public leftShift() {
		super("<<", 2, true, Operator.PRECEDENCE_ADDITION-1);
	}
	
	static {
	    OperatorRegistry.register(new leftShift());
	}

	@Override
	public double apply(double... args) {
		int a = (int) args[0];
		int b = (int) args[1];
		return a << b;
	}
}
