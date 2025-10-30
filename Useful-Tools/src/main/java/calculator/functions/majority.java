package calculator.functions;

import net.objecthunter.exp4j.function.Function;

public class majority extends Function {

	public majority() {
		super("M", 3);
	}
	
	@Override
	public double apply(double... args) {
		boolean a = args[0] != 0;
		boolean b = args[1] != 0;
		boolean c = args[2] != 0;
		
		return ((a && b) || (a && c) || (b && c))? 1:0;
	}
}
