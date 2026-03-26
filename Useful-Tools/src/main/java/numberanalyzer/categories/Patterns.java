package numberanalyzer.categories;

import java.math.BigInteger;

import numberanalyzer.utilities.CommonUtils;

/**
 * FIX — isKeith():
 *   The original hardcoded the seed values a=1, b=9, c=7, which are the
 *   digits of 197 (the smallest 3-digit Keith number). This meant the
 *   method only ever identified 197 as a Keith number. Every other Keith
 *   number (14, 19, 28, 47, 61, 75, 197, 742, ...) returned false.
 *
 *   A Keith number is defined as follows:
 *     1. Extract its individual digits as the seed sequence.
 *     2. Generate the next term as the sum of the previous d terms
 *        (where d is the number of digits).
 *     3. If the original number appears in this sequence, it is a Keith number.
 *
 *   Example for 197 (3 digits):
 *     Seed:  [1, 9, 7]
 *     Next:  1+9+7   = 17
 *     Next:  9+7+17  = 33
 *     Next:  7+17+33 = 57
 *     Next:  17+33+57= 107
 *     Next:  33+57+107=197  ← matches → Keith number ✓
 *
 *   Example for 14 (2 digits):
 *     Seed:  [1, 4]
 *     Next:  1+4 = 5
 *     Next:  4+5 = 9
 *     Next:  5+9 = 14 ← matches → Keith number ✓
 *
 *   Fixed: extract the actual digits of `num` as the seed, then iterate
 *   using a sliding window of size d (the digit count).
 */
public class Patterns {

    CommonUtils cu = new CommonUtils();
    Factorials f = new Factorials();

    public boolean isPalindrome(long num) {
        return num == cu.reverse(num);
    }

    public boolean isPerfectSquare(long num) {
        if (num == Long.MIN_VALUE) return false;
        num = Math.abs(num);
        long low = 0L, high = num;
        while (low <= high) {
            long mid = low + (high - low) / 2;
            long quotient = (mid == 0L) ? Long.MAX_VALUE : num / mid;
            if (mid == 0L) {
                if (num == 0L) return true;
                low = 1L;
            } else if (mid == quotient && num % mid == 0) {
                return true;
            } else if (mid > quotient) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return false;
    }

    public boolean isPerfectCube(long num) {
        if (num == Long.MIN_VALUE) return true;
        long target = Math.abs(num);
        BigInteger bigTarget = BigInteger.valueOf(target);
        long low = 0L, high = target;
        while (low <= high) {
            long mid = low + (high - low) / 2;
            BigInteger cube = BigInteger.valueOf(mid).pow(3);
            int cmp = cube.compareTo(bigTarget);
            if (cmp == 0) return true;
            else if (cmp > 0) high = mid - 1;
            else              low  = mid + 1;
        }
        return false;
    }

    public boolean isPerfectPower(long num) {
        if (num == 1) return true;
        if (num <= 3) return false;
        for (int i = 1; i <= num / 2; i++) {
            for (int j = 2; j <= Math.log(num) / Math.log(2); j++) {
                if ((long) Math.pow(i, j) == num) return true;
                else if ((long) Math.pow(i, j) > num) break;
            }
        }
        return false;
    }

    public boolean isHypotenuse(long num) {
        if (num <= 0) return false;
        for (long i = 1L; i <= num / 2; i++) {
            for (long j = 1L; j <= num / 2; j++) {
                if (num == Math.sqrt(i * i + j * j)) return true;
            }
        }
        return false;
    }

    public boolean isFibonacci(long num) {
        num = Math.abs(num);
        if (num == 0) return true;
        long a = 0L, b = 1L;
        while (true) {
            long c = a + b;
            if (c > num)       return false;
            else if (c == num) return true;
            else { a = b; b = c; }
        }
    }

    public boolean isTribonacci(long num) {
        num = Math.abs(num);
        long a = 0L, b = 1L, c = 1L;
        while (true) {
            long d = a + b + c;
            if (d > num)       return false;
            else if (d == num) return true;
            else { a = b; b = c; c = d; }
        }
    }

    public boolean isTetranacci(long num) {
        num = Math.abs(num);
        long a = 0L, b = 1L, c = 1L, d = 2L;
        while (true) {
            long e = a + b + c + d;
            if (e > num)       return false;
            else if (e == num) return true;
            else { a = b; b = c; c = d; d = e; }
        }
    }

    public boolean isPerrin(long num) {
        num = Math.abs(num);
        long a = 3L, b = 0L, c = 2L;
        while (true) {
            long d = a + b;
            if (d > num)       return false;
            else if (d == num) return true;
            else { a = b; b = c; c = d; }
        }
    }

    public boolean isLucas(long num) {
        num = Math.abs(num);
        long a = 2L, b = 1L;
        while (true) {
            long c = a + b;
            if (c > num)       return false;
            else if (c == num) return true;
            else { a = b; b = c; }
        }
    }

    public boolean isPadovan(long num) {
        num = Math.abs(num);
        long a = 1L, b = 1L, c = 1L;
        while (true) {
            long d = a + b;
            if (d > num)       return false;
            else if (d == num) return true;
            else { a = b; b = c; c = d; }
        }
    }

    /**
     * FIX: isKeith() was hardcoded with seed values a=1, b=9, c=7
     * (the digits of 197). This made it identify only 197 as a Keith number.
     *
     * Correct algorithm:
     *   1. Extract the digits of num into a list (the seed window).
     *   2. Repeatedly compute the sum of the last d values (d = digit count).
     *   3. Return true if this sum ever equals num; false if it exceeds num.
     *
     * Single-digit numbers are trivially Keith numbers by convention but
     * are excluded by the original (num < 17 guard kept for consistency).
     */
    public boolean isKeith(long num) {
        num = Math.abs(num);
        if (num < 10) return false;   // exclude single-digit numbers

        // Extract digits of num into the seed window.
        java.util.ArrayList<Long> window = new java.util.ArrayList<>();
        long temp = num;
        while (temp > 0) {
            window.add(0, temp % 10);   // prepend so digits are in order
            temp /= 10;
        }
        int d = window.size();

        // Slide the window forward until we reach or exceed num.
        while (true) {
            long next = 0L;
            for (int i = window.size() - d; i < window.size(); i++) {
                next += window.get(i);
            }
            if (next > num)  return false;
            if (next == num) return true;
            window.add(next);
        }
    }

    public boolean isCatalan(long num) {
        if (num < 0) return false;
        for (int i = 0; i < num; i++) {
            long term = f.findFactorial(2 * i) / (f.findFactorial(i + 1) * f.findFactorial(i));
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isTriangular(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (i + 1) / 2;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isPentagonal(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (3 * i - 1) / 2;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isStandardHexagonal(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (2 * i - 1);
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isCenteredHexagonal(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = 3L * i * (i - 1) + 1;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isHexagonal(long num) {
        if (num < 0) return false;
        return isStandardHexagonal(num) || isCenteredHexagonal(num);
    }

    public boolean isHeptagonal(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (5 * i - 3) / 2;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isOctagonal(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = 3L * i * i - 2 * i;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isTetrahedral(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (i + 1) * (i + 2) / 6;
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }

    public boolean isStellaOctangula(long num) {
        if (num < 0) return false;
        for (int i = 1; i <= num; i++) {
            long term = (long) i * (2 * i * i - 1);
            if (term > num)       return false;
            else if (term == num) return true;
        }
        return false;
    }
}
