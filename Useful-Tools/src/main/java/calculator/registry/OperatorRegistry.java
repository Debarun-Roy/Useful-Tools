package calculator.registry;

import java.util.ArrayList;
import java.util.List;
import net.objecthunter.exp4j.operator.Operator;

public class OperatorRegistry {
	
	private static final List<Operator> operators = new ArrayList<>();
	
	public static void register(Operator operator) {
        operators.add(operator);
    }
	
	public static List<Operator> getOperators() {
        return operators;
    }
}
