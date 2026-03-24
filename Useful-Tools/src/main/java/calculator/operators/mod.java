package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

/*\
 * CHANGE : java.IllegalArgumentException problem because exp4j only allows single character operands
 * So, we need to introduce normalized operators. For example, the operator "mod" for modulus can be normalized to 'r' (remainder)
 * without affecting the user-side experience at all Similarly, exp4j only allows ASCII characters, so unicode characters meant to be used as operators
 * need to be normalized too.
 * 
 * So, during function registration, we need to register the modulus operator as the symbol 'r'
*/
public class mod extends Operator {

	public mod() {
		super("mod", 2, true, Operator.PRECEDENCE_MULTIPLICATION);
	}
	
	@Override
	public double apply(double... args) {
		return args[0]%args[1];
	}
}
