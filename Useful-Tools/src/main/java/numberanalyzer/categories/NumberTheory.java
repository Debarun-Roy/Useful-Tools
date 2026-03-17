package numberanalyzer.categories;

/**
 * FIX — isOdd() for negative numbers:
 *   In Java, the % operator preserves the sign of the dividend.
 *   -3 % 2 evaluates to -1, not 1.
 *   The original test (num % 2) == 1 is therefore false for every
 *   negative odd number (-1, -3, -5, ...), returning an incorrect result.
 *
 *   Fix: test (num % 2) != 0, which correctly identifies odd numbers
 *   regardless of sign:
 *     -3 % 2 == -1, and -1 != 0  → true  ✓
 *      3 % 2 ==  1, and  1 != 0  → true  ✓
 *     -4 % 2 ==  0, and  0 != 0  → false ✓
 *      4 % 2 ==  0, and  0 != 0  → false ✓
 */
public class NumberTheory {

    public boolean isOdd(long num) {
        return (num % 2) != 0;   // FIX: was == 1, which fails for negative odd numbers
    }

    public boolean isEven(long num) {
        return (num % 2) == 0;
    }

    public boolean isNatural(long num) {
        return num >= 1;
    }

    public boolean isWhole(long num) {
        return num == 0 || isNatural(num);
    }

    public boolean isInteger(long num) {
        return isNegative(num) || isWhole(num);
    }

    public boolean isNegative(long num) {
        return !isWhole(num);
    }
}