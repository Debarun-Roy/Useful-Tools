package numberanalyzer.categories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * FIX 1 — getBaseRepresentation() guard condition:
 *   Original: if (base <= 1 && base >= 62)
 *   A number cannot simultaneously be ≤ 1 AND ≥ 62. This condition is
 *   always false, so invalid bases (e.g. base 0 or base 100) were never
 *   caught and the method proceeded to compute with them.
 *   Fixed: if (base <= 1 || base > 62)
 *   (Note: base 62 is valid — digits 0-9, A-Z, a-z — so the upper bound
 *   is exclusive: base > 62 is invalid.)
 *
 * FIX 2 — getOctalRepresentation() complement add-1 loop direction:
 *   The loop that adds 1 to the 7's-complement (to form the 8's complement)
 *   iterated from i = nstr.size()-1 downward (i--), which processes the
 *   least significant digit first — correct for binary addition. However the
 *   loop body broke out immediately after finding the first non-seven digit
 *   but did NOT update the flagAllSeven correctly. A secondary off-by-one
 *   in the carry increment also existed. Corrected to process from the
 *   rightmost digit leftward and carry correctly.
 */
public class BaseNRepresentation {

    // Digit characters: 0-9, A-Z, a-z  (62 possible digits for base 2..62)
    public ArrayList<Character> getBaseRepresentation(long num, int base) {
        // FIX: was "base <= 1 && base >= 62" (always false). Correct guard:
        if (base <= 1 || base > 62) {
            return new ArrayList<>();
        }
        ArrayList<Character> list = new ArrayList<>();
        if (num < 0) num = Math.abs(num);
        while (num > 0) {
            char ch;
            int r = (int) num % base;
            if (r >= 10 && r <= 35) {
                ch = (char) (55 + r);   // A-Z
            } else if (r >= 36 && r <= 61) {
                ch = (char) (61 + r);   // a-z
            } else {
                ch = (char) (48 + r);   // 0-9
            }
            list.add(ch);
            num /= base;
        }
        Collections.reverse(list);
        return list;
    }

    public LinkedHashMap<Integer, String> findAllBases(long num) {
        LinkedHashMap<Integer, String> resultMap = new LinkedHashMap<>();
        for (int i = 2; i <= 62; i++) {
            ArrayList<Character> st = getBaseRepresentation(num, i);
            StringBuilder nst = new StringBuilder();
            st.forEach(nst::append);
            resultMap.put(i, nst.toString());
        }
        return resultMap;
    }

    public String getBinaryRepresentation(long num) {
        ArrayList<Character> nstr = getBaseRepresentation(num, 2);
        StringBuilder nst = new StringBuilder();
        if (num < 0) {
            // 1's complement: flip all bits
            for (int i = 0; i < nstr.size(); i++) {
                nstr.set(i, nstr.get(i) == '1' ? '0' : '1');
            }
            // 2's complement: add 1 from the least significant bit
            boolean carry = true;
            for (int i = nstr.size() - 1; i >= 0 && carry; i--) {
                if (nstr.get(i) == '0') {
                    nstr.set(i, '1');
                    carry = false;
                } else {
                    nstr.set(i, '0');
                }
            }
            if (carry) nstr.add(0, '1');
        }
        nstr.forEach(nst::append);
        return nst.toString();
    }

    public String getOctalRepresentation(long num) {
        ArrayList<Character> nstr = getBaseRepresentation(num, 8);
        StringBuilder nst = new StringBuilder();
        if (num < 0) {
            // 7's complement: replace each digit d with 7-d
            for (int i = 0; i < nstr.size(); i++) {
                int d = (int) nstr.get(i) - 48;
                nstr.set(i, (char) ((7 - d) + 48));
            }
            // FIX: Add 1 from the rightmost digit, carrying left
            boolean carry = true;
            for (int i = nstr.size() - 1; i >= 0 && carry; i--) {
                int d = (int) nstr.get(i) - 48;
                if (d < 7) {
                    nstr.set(i, (char) (d + 1 + 48));
                    carry = false;
                } else {
                    nstr.set(i, '0');
                }
            }
            if (carry) nstr.add(0, '1');
        }
        nstr.forEach(nst::append);
        return nst.toString();
    }

    public String getHexRepresentation(long num) {
        ArrayList<Character> nstr = getBaseRepresentation(num, 16);
        StringBuilder nst = new StringBuilder();
        if (num < 0) {
            // F's complement: replace each hex digit with F - digit
            for (int i = 0; i < nstr.size(); i++) {
                char c = nstr.get(i);
                int d = Character.isAlphabetic(c)
                        ? (c - 'A' + 10)
                        : (c - '0');
                int comp = 15 - d;
                nstr.set(i, comp < 10
                        ? (char) ('0' + comp)
                        : (char) ('A' + comp - 10));
            }
            // Add 1 (G's complement = F's complement + 1)
            boolean carry = true;
            for (int i = nstr.size() - 1; i >= 0 && carry; i--) {
                char c = nstr.get(i);
                int d = Character.isAlphabetic(c)
                        ? (c - 'A' + 10)
                        : (c - '0');
                if (d < 15) {
                    int next = d + 1;
                    nstr.set(i, next < 10
                            ? (char) ('0' + next)
                            : (char) ('A' + next - 10));
                    carry = false;
                } else {
                    nstr.set(i, '0');
                }
            }
            if (carry) nstr.add(0, '1');
        }
        nstr.forEach(nst::append);
        return nst.toString();
    }
}