package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class percentage extends Operator {

	public percentage() {
		super("%", 1, true, Operator.PRECEDENCE_POWER+1);
	}
	
	static {
	    OperatorRegistry.register(new percentage());
	}
	
	@Override
	public double apply(double... args) {
		return args[0]/100.0;
	}
}
