package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class mod extends Operator {

	public mod() {
		super("mod", 2, true, Operator.PRECEDENCE_MULTIPLICATION);
	}
	
	static {
	    OperatorRegistry.register(new mod());
	}
	
	@Override
	public double apply(double... args) {
		return args[0]%args[1];
	}
}
