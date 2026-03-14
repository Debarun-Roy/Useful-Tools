package calculator.expression;

import calculator.registry.FunctionRegistry;
import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.ExpressionBuilder;

public class ExpressionBuilderFactory {
	
	public static ExpressionBuilder create(String expr) {

        ExpressionBuilder builder = new ExpressionBuilder(expr);

        FunctionRegistry.getFunctions().forEach(builder::function);
        OperatorRegistry.getOperators().forEach(builder::operator);

        return builder;
    }
}
