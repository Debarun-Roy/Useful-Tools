package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

public class converseNonimplication extends Operator {

    public converseNonimplication() {
        super("$", 2, false, Operator.PRECEDENCE_ADDITION - 6);
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;

        return (!a && b) ? 1 : 0;
    }
}
