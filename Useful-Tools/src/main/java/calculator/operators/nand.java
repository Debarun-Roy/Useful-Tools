package calculator.operators;

import net.objecthunter.exp4j.operator.Operator;

/**
 * NAND (↑) — true when NOT both inputs are true.
 * Truth table:
 *   0↑0 = 1,  0↑1 = 1,  1↑0 = 1,  1↑1 = 0
 *
 * BUG FIX: The original had:
 *   boolean b = args[1] != 1;
 * This inverts b — it treats b as TRUE when the input is NOT 1 (i.e. when
 * it is 0), which is the exact opposite of every other boolean operator in
 * this project. The same inverted-b bug was previously fixed in xor.java.
 *
 * Corrected to: boolean b = args[1] != 0;
 * With the original bug, NAND(1,1) returned 1 instead of 0, making every
 * result in the truth table wrong.
 */
public class nand extends Operator {

    public nand() {
        super("↑", 2, true, Operator.PRECEDENCE_ADDITION - 3);
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;  // FIX: was "!= 1" which inverted b
        return (!(a && b)) ? 1 : 0;
    }
}