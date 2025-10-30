package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class or extends Operator {

	public or() {
		super("||", 2, true, Operator.PRECEDENCE_ADDITION-5);
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		return (a || b)? 1:0;
	}
}
