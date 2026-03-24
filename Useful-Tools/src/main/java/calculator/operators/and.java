package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

/**
 * FIX (package + base class): The original "and" was in calculator.functions
 *   extending Function. BooleanUtils imports it from calculator.operators as
 *   an Operator. It now lives in the correct package and extends Operator.
 *
 * FIX (symbol): Uses the "&" symbol as the infix operator token so that
 *   expressions like "1&1" work in the exp4j engine.
 *   Precedence is set below ADDITION so comparisons bind tighter.
 */
public class and extends Operator {

    public and() {
        super("&", 2, true, Operator.PRECEDENCE_ADDITION - 2);
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;
        return (a && b) ? 1 : 0;
    }
}
