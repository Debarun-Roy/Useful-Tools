package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class mod extends Operator {

	public mod() {
		super("mod", 2, true, Operator.PRECEDENCE_MULTIPLICATION);
	}
	
	@Override
	public double apply(double... args) {
		return args[0]%args[1];
	}
}
