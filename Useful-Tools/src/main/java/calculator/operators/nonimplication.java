package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class nonimplication extends Operator {

	public nonimplication() {
		super("⊄", 2, false, Operator.PRECEDENCE_ADDITION-6);
	}
	
	static {
	    OperatorRegistry.register(new nonimplication());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		
		return (a && !b)? 1:0;
	}
}
