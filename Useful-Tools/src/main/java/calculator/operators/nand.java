package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

public class nand extends Operator {

	public nand() {
		super("↑", 2, true, Operator.PRECEDENCE_ADDITION-3);
	}
	
	static {
	    OperatorRegistry.register(new nand());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 1;
		
		return (!(a && b))? 1:0;
	}
}
