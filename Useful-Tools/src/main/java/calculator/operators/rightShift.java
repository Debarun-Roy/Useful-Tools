package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class rightShift extends Operator {

	public rightShift() {
		super(">>", 2, true, Operator.PRECEDENCE_ADDITION-1);
	}

	@Override
	public double apply(double... args) {
		int a = (int) args[0];
		int b = (int) args[1];
		return a >> b;
	}
}
