package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class not extends Operator {

	public not() {
		super("!", 1, false, Operator.PRECEDENCE_POWER+1);
	}
	
	static {
	    OperatorRegistry.register(new not());
	}
	
	@Override
	public double apply(double... args) {
		boolean r = args[0] != 0;
		return (!r)? 1:0;
	}
}
