package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class equality extends Operator {

	public equality() {
		super("==", 2, true, Operator.PRECEDENCE_ADDITION-2);
	}
	
	static {
	    OperatorRegistry.register(new equality());
	}
	
	@Override
	public double apply(double... args) {
		return (args[0]==args[1])? 1:0;
	}
}
