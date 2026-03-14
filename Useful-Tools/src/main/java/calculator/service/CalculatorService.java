package calculator.service;

import calculator.expression.ExpressionBuilderFactory;
import calculator.repository.ComputeRepository;
import net.objecthunter.exp4j.Expression;

public class CalculatorService {
	
	private final ComputeRepository repository = new ComputeRepository();

    public double evaluate(String expression) throws Exception {

        Expression e = ExpressionBuilderFactory
                .create(expression)
                .build();

        return e.evaluate();
    }
    
    public double evaluateAndStore(String expression) throws Exception {

        double result = evaluate(expression);

        repository.storeExpressionResult(
                expression,
                Double.toString(result)
        );

        return result;
    }
}