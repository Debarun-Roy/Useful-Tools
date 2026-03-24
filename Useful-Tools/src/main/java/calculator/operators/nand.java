package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

/**
 * NAND operator.
 */
public class nand extends Operator {

    public nand() {
        super("!&", 2, true, Operator.PRECEDENCE_ADDITION - 3);
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;
        return (!(a && b)) ? 1 : 0;
    }
}
