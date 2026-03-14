package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class greaterThan extends Operator {
	
	public greaterThan() {
		super(">", 2, true, Operator.PRECEDENCE_ADDITION-2);
	}
	
	static {
	    OperatorRegistry.register(new greaterThan());
	}
	
	@Override
	public double apply(double... args) {
		return (args[0]>args[1])? 1:0;
	}
}
