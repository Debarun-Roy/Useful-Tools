package calculator.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.objecthunter.exp4j.operator.Operator;

/**
 * Registry of all exp4j Operator instances available to ExpressionBuilderFactory.
 *
 * FIX: Same unmodifiable-list fix as FunctionRegistry.
 * getOperators() previously returned the private ArrayList by reference,
 * allowing callers to mutate the registry without going through register().
 */
public class OperatorRegistry {

    private static final List<Operator> operators = new ArrayList<>();

    public static void register(Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Cannot register a null Operator.");
        }
        operators.add(operator);
    }

    /**
     * Returns an unmodifiable view of the registered operators.
     * The underlying list can only be modified through register().
     */
    public static List<Operator> getOperators() {
        return Collections.unmodifiableList(operators);
    }
}