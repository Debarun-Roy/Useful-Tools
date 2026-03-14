package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

/**
 * FIX (package + base class): The original "or" was in calculator.functions
 *   extending Function. BooleanUtils imports it from calculator.operators as
 *   an Operator. It now lives in the correct package and extends Operator.
 *
 * FIX (symbol): Uses the "|" symbol as the infix operator token.
 */
public class or extends Operator {

    public or() {
        super("|", 2, true, Operator.PRECEDENCE_ADDITION - 3);
    }

    static {
        OperatorRegistry.register(new or());
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;
        return (a || b) ? 1 : 0;
    }
}
