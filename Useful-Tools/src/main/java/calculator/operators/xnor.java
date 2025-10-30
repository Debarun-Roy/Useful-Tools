package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class xnor extends Operator {

	public xnor() {
		super("⊙", 2, true, Operator.PRECEDENCE_ADDITION-4);
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 1;
		
		return ((a && b) || (!a && !b))? 1:0;
	}
}
