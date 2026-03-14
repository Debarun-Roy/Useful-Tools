package numberanalyzer.utilities;

import java.util.ArrayList;
import java.util.Collections;

public class CommonUtils {

    public long numberOfDigits(long num) {
        long d = 0L;
        while (num > 0) {
            d++;
            num /= 10;
        }
        return d;
    }

    public long findSumOfDigits(long num) {
        long sum = 0L;
        while (num > 0) {
            sum += num % 10;
            num /= 10;
        }
        return sum;
    }

    public long findProductOfDigits(long num) {
        long p = 1L;
        while (num > 0) {
            p *= num % 10;
            num /= 10;
        }
        return p;
    }

    public long findSumOfSquares(long num) {
        long sum = 0L;
        while (num > 0) {
            long r = num % 10;
            sum += r * r;
            num /= 10;
        }
        return sum;
    }

    public long reverse(long num) {
        long rev = 0L;
        while (num > 0) {
            rev = rev * 10 + num % 10;
            num /= 10;
        }
        return rev;
    }

    public ArrayList<Long> findPermutationsOfNumber(long num) {
        if (num < 0) return new ArrayList<>();
        ArrayList<Integer> digits = generateListOfDigits(num);
        ArrayList<Long> result = new ArrayList<>();
        ArrayList<ArrayList<Integer>> heapResult = heapAlgorithm(new ArrayList<>(), digits, digits.size());
        for (ArrayList<Integer> n : heapResult) {
            StringBuilder st = new StringBuilder();
            for (int d : n) st.append(d);
            result.add(Long.parseLong(st.toString()));
        }
        return result;
    }

    /**
     * FIX: The original heapAlgorithm always returned "new ArrayList<>()" from
     * the recursive case, discarding all accumulated permutations. Only the
     * base case correctly added to the result list. The fix is to pass "result"
     * as the accumulator through every recursive call and return it — this is
     * the standard Heap's algorithm implementation.
     *
     * The unused "n" parameter (original 4-argument signature) has also been
     * removed since it served no purpose.
     */
    public ArrayList<ArrayList<Integer>> heapAlgorithm(
            ArrayList<ArrayList<Integer>> result,
            ArrayList<Integer> digits,
            int size) {

        if (size == 1) {
            result.add(new ArrayList<>(digits));
            return result;
        }

        for (int i = 0; i < size; i++) {
            heapAlgorithm(result, digits, size - 1);   // FIX: return value used via shared list
            if (size % 2 == 1) {
                Collections.swap(digits, 0, size - 1);
            } else {
                Collections.swap(digits, i, size - 1);
            }
        }

        return result;   // FIX: was "return new ArrayList<>()" — discarded all work
    }

    /**
     * FIX: The original rotation logic was broken. It used digits.add(j+1, ...) 
     * inside a loop that tried to shift elements right, but the indices were 
     * wrong and produced garbled or repeated digits. The correct algorithm for
     * a single left-rotation is to remove the first element and append it at 
     * the end, then rebuild the number.
     */
    public ArrayList<Long> generateAllRotations(long num) {
        ArrayList<Integer> digits = generateListOfDigits(num);
        ArrayList<Long> result = new ArrayList<>();
        result.add(num);

        for (int i = 1; i < digits.size(); i++) {
            // Rotate left by one: move the first digit to the end.
            int first = digits.remove(0);
            digits.add(first);

            StringBuilder st = new StringBuilder();
            for (int d : digits) st.append(d);
            result.add(Long.parseLong(st.toString()));
        }

        return result;
    }

    public ArrayList<Integer> generateListOfDigits(long num) {
        ArrayList<Integer> digits = new ArrayList<>();
        while (num > 0) {
            digits.add((int) (num % 10));
            num /= 10;
        }
        Collections.reverse(digits);
        return digits;
    }
}
