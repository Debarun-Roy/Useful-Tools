package numberanalyzer.categories;

import java.util.ArrayList;

import numberanalyzer.utilities.CommonUtils;

/**
 * Three bug fixes in this class:
 *
 * FIX 1 — isHappy(): Floyd's cycle detection was broken.
 *   The original advanced both slow and fast using `num` (the original
 *   parameter, never updated) instead of their own current values:
 *     slow = cu.findSumOfSquares(num);   ← always uses original num
 *     fast = cu.findSumOfSquares(cu.findSumOfSquares(num)); ← same
 *   Both pointers always computed the same next step from the original
 *   input, so they were always equal after the first iteration, making
 *   the loop terminate immediately. The result was: any number whose
 *   first sum-of-squares step equalled itself was "happy", all others
 *   were not — completely wrong.
 *   Fixed: slow and fast each advance from their own current value.
 *
 * FIX 2 — isDuck(): Logic was inverted AND incomplete.
 *   The original returned num % 10 != 0, i.e. "last digit is not zero".
 *   A Duck number is defined as a positive number that CONTAINS at least
 *   one zero digit (but does not start with zero). The original only
 *   checked the last digit and returned true when it was NOT zero —
 *   the exact opposite of the correct definition.
 *   Fixed: scan all digits for any zero occurrence.
 *
 * FIX 3 — isTech(): Infinite loop.
 *   The inner while loop modified local copies n1/n2/c1/c2 but never
 *   divided `num` by 10, so the loop condition (num > 0) was always
 *   true and the method never returned. Any call to isTech() hung the
 *   server permanently.
 *   Fixed: advance `num /= 10` at the end of each loop iteration, and
 *   correctly partition the digits into two equal halves.
 */
public class Recreational {

    CommonUtils cu = new CommonUtils();
    PrimeNumbers pn = new PrimeNumbers();

    public boolean isArmstrong(long num) {
        long sum = 0L;
        long d = cu.numberOfDigits(num);
        if (num < 0 && d % 2 == 0) return false;
        long numCopy = num;
        while (num > 0) {
            long r = num % 10;
            sum += (long) Math.pow(r, d);
            num /= 10;
        }
        return sum == numCopy;
    }

    public boolean isHarshad(long num) {
        return (num % cu.findSumOfDigits(num)) == 0;
    }

    public boolean isDisarium(long num) {
        long numCopy = num;
        long d = cu.numberOfDigits(num);
        long newnum = 0L;
        while (num > 0) {
            newnum += (long) Math.pow(num % 10, d--);
            num /= 10;
        }
        return newnum == numCopy;
    }

    /**
     * FIX 1: Floyd's cycle detection corrected.
     * slow and fast must advance from their own current values,
     * not from the original input `num` on every iteration.
     */
    public boolean isHappy(long num) {
        long slow = num;
        long fast = num;
        do {
            slow = cu.findSumOfSquares(slow);                           // FIX: was (num)
            fast = cu.findSumOfSquares(cu.findSumOfSquares(fast));      // FIX: was (num) twice
        } while (slow != fast);
        return slow == 1;
    }

    public boolean isKaprekar(long num) {
        long d = cu.numberOfDigits(num);
        if (d == 1) return false;
        long sq = num * num;
        long d2 = cu.numberOfDigits(sq);
        long n1 = 0L, n2 = 0L, c1 = 0L, c2 = 0L;
        if (d2 == d * 2) {
            while (sq > 0) {
                long r = sq % 10;
                if (c1 < d / 2) n1 = n1 + r * (long) Math.pow(10, c1++);
                if (c2 < d / 2) n2 = n2 + r * (long) Math.pow(10, c2++);
                sq /= 10;
            }
        } else {
            while (c1 < d / 2) {
                long r = sq % 10;
                n1 = n1 + r * (long) Math.pow(10, c1++);
                sq /= 10;
            }
            while (sq > 0 && c2 < d / 2 + 1) {
                long r = sq % 10;
                n2 = n2 + r * (long) Math.pow(10, c2++);
                sq /= 10;
            }
        }
        return num == (n1 + n2);
    }

    public boolean isAutomorphic(long num) {
        long sq = num * num;
        long d = cu.numberOfDigits(num);
        long newnum = 0L;
        long c = 0;
        while (c < d) {
            long r = sq % 10;
            newnum = newnum + r * (long) Math.pow(10, c++);
            sq /= 10;
        }
        return newnum == num;
    }

    public boolean isDudeney(long num) {
        return cu.findSumOfDigits(num) == Math.cbrt(num);
    }

    public boolean isGapful(long num) {
        if (num <= 9) return false;
        ArrayList<Integer> digits = cu.generateListOfDigits(num);
        int n = digits.get(0) * 10 + digits.get(digits.size() - 1);
        return num % n == 0;
    }

    public boolean isHungry(long num) {
        if (num < 0) return false;
        for (long i = 0L; i <= num / 2; i++) {
            if (7 * ((long) Math.pow(2, i) / 47) == num) return true;
        }
        return false;
    }

    /**
     * FIX 2: Duck number — contains at least one zero digit (excluding
     * a leading zero). Original returned num%10 != 0 which was inverted
     * and only checked the last digit.
     */
    public boolean isDuck(long num) {
        if (num <= 0) return false;
        while (num > 0) {
            if (num % 10 == 0) return true;
            num /= 10;
        }
        return false;
    }

    public boolean isBuzz(long num) {
        return (num % 7 == 0) || (num % 10 == 7);
    }

    public boolean isSpy(long num) {
        return cu.findSumOfDigits(num) == cu.findProductOfDigits(num);
    }

    /**
     * FIX 3: isTech() — infinite loop fixed by dividing num by 10 inside
     * the loop. Also restructured to correctly extract the two equal halves
     * of the digit sequence (low digits and high digits separately).
     *
     * A Tech number (also called a Peterson number) is an even-digit number
     * where splitting it into two equal halves and summing them, then
     * squaring the result, gives back the original number.
     * Example: 2025 → halves 20 and 25 → (20+25)² = 45² = 2025 ✓
     */
    public boolean isTech(long num) {
        long d = cu.numberOfDigits(num);
        if (d % 2 != 0) return false;   // must have even digit count

        long half = d / 2;
        long divisor = (long) Math.pow(10, half);

        long lower = num % divisor;   // right half
        long upper = num / divisor;   // left half
        long sum = lower + upper;

        return sum * sum == num;
    }

    public boolean isNeon(long num) {
        return cu.findSumOfDigits(num * num) == num;
    }

    /**
     * A magic number repeatedly reduces to 1 by summing its digits.
     *
     * The previous base case used `num < 9`, which left 9 out:
     *   isMagic(9) -> isMagic(sumDigits(9)) -> isMagic(9) -> ...
     * causing infinite recursion and a StackOverflowError.
     *
     * Any single-digit value other than 1 should terminate as false.
     */
    public boolean isMagic(long num) {
        num = Math.abs(num);

        if (num == 1) return true;
        if (num <= 9) return false;

        return isMagic(cu.findSumOfDigits(num));
    }

    public boolean isSmith(long num) {
        ArrayList<Long> list = new ArrayList<>();
        long sum1 = cu.findSumOfDigits(num);
        long sum2 = 0L;
        for (long i = 1L; i <= num; i++) {
            if (num % i == 0 && pn.isPrime(i)) list.add(i);
        }
        for (int j = 0; j < list.size(); j++) {
            sum2 += cu.findSumOfDigits(list.get(j));
        }
        return sum1 == sum2;
    }

    public boolean isMunchausen(long num) {
        long sum = 0L;
        long numCopy = num;
        while (num > 0) {
            long r = num % 10;
            sum += (long) Math.pow(r, r);
            num /= 10;
        }
        return sum == numCopy;
    }

    public boolean isRepdigits(long num) {
        if (num <= 9) return true;
        long d = num % 10;
        while (num > 0) {
            if (num % 10 != d) return false;
            num /= 10;
        }
        return true;
    }

    public boolean isPronic(long num) {
        long a = 0L;
        long b = 1L;
        while (a <= num / 2 || b <= num / 2) {
            long c = a++ * b++;
            if (c == num) return true;
        }
        return false;
    }
}
