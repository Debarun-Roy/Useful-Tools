package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/*
 * CHANGES REQUIRED : exp4j does not allow custom operators because its tokenizer only understands the pre-defined operators that come with the package.
 * To implement custom operators like biconditional we have to define them as custom functions.
 * Similar normalization needs to be made in the frontend to ensure that something like 1 <-> 0 is converted to biconditional(1, 0) in the backend.
 */
public class biconditional extends Function {

	public biconditional() {
		super("biconditional", 2);
	}
	
	static {
		FunctionRegistry.register(new biconditional());
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		
		return ((a || !b) && (!a || b))? 1:0;
	}
}
