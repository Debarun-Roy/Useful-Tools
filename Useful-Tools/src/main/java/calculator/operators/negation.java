package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class negation extends Operator {

	public negation() {
		super("!=", 2, true, Operator.PRECEDENCE_ADDITION-2);
	}
	
	static {
	    OperatorRegistry.register(new negation());
	}
	
	@Override
	public double apply(double... args) {
		return (args[0]!=args[1])? 1:0;
	}
}
