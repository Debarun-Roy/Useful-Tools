package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class percentage extends Operator {

	public percentage() {
		super("%", 1, true, Operator.PRECEDENCE_POWER+1);
	}
	
	@Override
	public double apply(double... args) {
		return args[0]/100.0;
	}
}
