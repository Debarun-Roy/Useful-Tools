package numberanalyzer.service;

import java.util.LinkedHashMap;

import numberanalyzer.categories.BaseNRepresentation;
import numberanalyzer.categories.Factors;
import numberanalyzer.categories.NumberTheory;
import numberanalyzer.categories.Patterns;
import numberanalyzer.categories.PrimeNumbers;
import numberanalyzer.categories.Recreational;

/**
 * Sprint 8 addition — prime factorization.
 *
 * buildPrimes() now prepends a "Factorization: ..." entry for every n > 1,
 * using trial division bounded at max(√n, 10⁶) to keep response time
 * predictable for large inputs. The remaining cofactor (if any) is appended
 * as a likely-prime large factor.
 *
 * Examples:
 *   n = 12   → "Factorization: 2² × 3"
 *   n = 360  → "Factorization: 2³ × 3² × 5"
 *   n = 97   → "Factorization: 97"  (97 is prime, shows itself)
 *   n = 2    → "Factorization: 2"
 *
 * Unicode superscript digits are used for exponents (e.g. ² ³ ⁴).
 *
 * All other logic from the previous service is preserved unchanged.
 */
public class NumberAnalyzerService {

    private final PrimeNumbers        pn  = new PrimeNumbers();
    private final BaseNRepresentation bnr = new BaseNRepresentation();
    private final NumberTheory        nt  = new NumberTheory();
    private final Factors             fac = new Factors();
    private final Recreational        rec = new Recreational();
    private final Patterns            pat = new Patterns();

    /**
     * Analyses a number across all categories and returns a nested map:
     *   { "Category Name" → { 1 → "n is a X number.", 2 → ... } }
     *
     * @param number The number to analyse.
     * @return Ordered map of categories to ordered maps of findings.
     */
    public LinkedHashMap<String, LinkedHashMap<Integer, String>> analyzeNumber(long number) {
        LinkedHashMap<String, LinkedHashMap<Integer, String>> result = new LinkedHashMap<>();
        String fmt = number + " is a %s number.";

        result.put("Number Theory",      buildNumberTheory(number, fmt));
        result.put("Primes",             buildPrimes(number, fmt));
        result.put("Factors",            buildFactors(number, fmt));
        result.put("Recreational",       buildRecreational(number, fmt));
        result.put("Patterns",           buildPatterns(number, fmt));
        result.put("Base Representations", buildBaseRepresentations(number));

        return result;
    }

    // ── Category builders ────────────────────────────────────────────────────

    private LinkedHashMap<Integer, String> buildNumberTheory(long n, String fmt) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        int i = 0;
        if (nt.isNatural(n))  map.put(++i, fmt.replace("%s", "Natural"));
        if (nt.isWhole(n))    map.put(++i, fmt.replace("%s", "Whole"));
        if (nt.isNegative(n)) map.put(++i, fmt.replace("%s", "Negative"));
        if (nt.isInteger(n))  map.put(++i, fmt.replace("a %s", "an Integer"));
        if (nt.isOdd(n))      map.put(++i, fmt.replace("a %s", "an Odd"));
        if (nt.isEven(n))     map.put(++i, fmt.replace("a %s", "an Even"));
        return map;
    }

    private LinkedHashMap<Integer, String> buildPrimes(long n, String fmt) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        int i = 0;

        // ── Sprint 8: Prime factorization ─────────────────────────────────
        // Show for all n > 1 regardless of primality.
        if (n > 1) {
            map.put(++i, "Factorization: " + formatPrimeFactorization(n));
        }

        boolean prime = pn.isPrime(n);
        boolean happy = rec.isHappy(n);

        if (prime)                          map.put(++i, fmt.replace("%s", "Prime"));
        if (pn.isSemiPrime(n))              map.put(++i, fmt.replace("%s", "Semi Prime"));
        if (pn.isEmirp(n))                  map.put(++i, fmt.replace("%s", "Emirp"));
        if (pn.isPrimePalindrome(n))        map.put(++i, fmt.replace("%s", "Prime Palindrome"));
        if (fac.isPerfect(n) && prime)      map.put(++i, fmt.replace("%s", "Perfect Prime"));
        if (happy && prime)                 map.put(++i, fmt.replace("%s", "Happy Prime"));
        if (pn.isAdditivePrime(n))          map.put(++i, fmt.replace("a %s", "an Additive Prime"));
        if (pn.isAnagrammaticPrime(n))      map.put(++i, fmt.replace("a %s", "an Anagrammatic Prime"));
        if (pn.isCircularPrime(n))          map.put(++i, fmt.replace("%s", "Circular Prime"));
        if (pn.isTwinPrime(n))              map.put(++i, fmt.replace("%s", "Twin Prime"));
        if (pn.isCousinPrime(n))            map.put(++i, fmt.replace("%s", "Cousin Prime"));
        if (pn.isSexyPrime(n))              map.put(++i, fmt.replace("%s", "Sexy Prime"));
        if (pn.isSophieGermanPrime(n))      map.put(++i, fmt.replace("%s", "Sophie Germain Prime"));
        if (pn.isKillerPrime(n))            map.put(++i, fmt.replace("%s", "Killer Prime"));
        return map;
    }

    private LinkedHashMap<Integer, String> buildFactors(long n, String fmt) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        int i = 0;
        boolean perfect = fac.isPerfect(n);
        if (perfect)                            map.put(++i, fmt.replace("%s", "Perfect"));
        if (fac.isImperfect(n))                 map.put(++i, fmt.replace("%s", "Imperfect"));
        if (perfect && pn.isPrime(n))           map.put(++i, fmt.replace("%s", "Perfect Prime"));
        if (perfect && pat.isPalindrome(n))     map.put(++i, fmt.replace("%s", "Perfect Palindrome"));
        if (fac.isArithmetic(n))                map.put(++i, fmt.replace("a %s", "an Arithmetic"));
        if (fac.isInharmonious(n))              map.put(++i, fmt.replace("a %s", "an Inharmonious"));
        if (fac.isBlum(n))                      map.put(++i, fmt.replace("%s", "Blum"));
        if (fac.isHumble(n))                    map.put(++i, fmt.replace("%s", "Humble"));
        if (fac.isAbundant(n))                  map.put(++i, fmt.replace("a %s", "an Abundant"));
        if (fac.isDeficient(n))                 map.put(++i, fmt.replace("%s", "Deficient"));
        if (fac.isAmicable(n))                  map.put(++i, fmt.replace("a %s", "an Amicable"));
        if (fac.isUntouchable(n))               map.put(++i, fmt.replace("a %s", "an Untouchable"));
        return map;
    }

    private LinkedHashMap<Integer, String> buildRecreational(long n, String fmt) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        int i = 0;
        if (rec.isArmstrong(n))   map.put(++i, fmt.replace("a %s", "an Armstrong"));
        if (rec.isHarshad(n))     map.put(++i, fmt.replace("%s", "Harshad"));
        if (rec.isDisarium(n))    map.put(++i, fmt.replace("%s", "Disarium"));
        if (rec.isKaprekar(n))    map.put(++i, fmt.replace("%s", "Kaprekar"));
        if (rec.isHappy(n))       map.put(++i, fmt.replace("%s", "Happy"));
        if (rec.isAutomorphic(n)) map.put(++i, fmt.replace("a %s", "an Automorphic"));
        if (rec.isDuck(n))        map.put(++i, fmt.replace("%s", "Duck"));
        if (rec.isDudeney(n))     map.put(++i, fmt.replace("%s", "Dudeney"));
        if (rec.isGapful(n))      map.put(++i, fmt.replace("%s", "Gapful"));
        if (rec.isHungry(n))      map.put(++i, fmt.replace("%s", "Hungry"));
        if (rec.isBuzz(n))        map.put(++i, fmt.replace("%s", "Buzz"));
        if (rec.isSpy(n))         map.put(++i, fmt.replace("%s", "Spy"));
        if (rec.isTech(n))        map.put(++i, fmt.replace("%s", "Tech"));
        if (rec.isNeon(n))        map.put(++i, fmt.replace("%s", "Neon"));
        if (rec.isMagic(n))       map.put(++i, fmt.replace("%s", "Magic"));
        if (rec.isSmith(n))       map.put(++i, fmt.replace("%s", "Smith"));
        if (rec.isPronic(n))      map.put(++i, fmt.replace("%s", "Pronic"));
        if (rec.isRepdigits(n))   map.put(++i, fmt.replace("%s", "Repdigit"));
        if (rec.isMunchausen(n))  map.put(++i, fmt.replace("%s", "Munchausen"));
        return map;
    }

    private LinkedHashMap<Integer, String> buildPatterns(long n, String fmt) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        int i = 0;
        if (pat.isPalindrome(n))           map.put(++i, fmt.replace("%s", "Palindrome"));
        if (pn.isPrimePalindrome(n))       map.put(++i, fmt.replace("%s", "Prime Palindrome"));
        if (pat.isPerfectSquare(n))        map.put(++i, fmt.replace("%s", "Perfect Square"));
        if (pat.isPerfectCube(n))          map.put(++i, fmt.replace("%s", "Perfect Cube"));
        if (pat.isPerfectPower(n))         map.put(++i, fmt.replace("%s", "Perfect Power"));
        if (pat.isHypotenuse(n))           map.put(++i, fmt.replace("%s", "Hypotenuse"));
        if (pat.isFibonacci(n))            map.put(++i, fmt.replace("%s", "Fibonacci"));
        if (pat.isTribonacci(n))           map.put(++i, fmt.replace("%s", "Tribonacci"));
        if (pat.isTetranacci(n))           map.put(++i, fmt.replace("%s", "Tetranacci"));
        if (pat.isPerrin(n))               map.put(++i, fmt.replace("%s", "Perrin"));
        if (pat.isLucas(n))                map.put(++i, fmt.replace("%s", "Lucas"));
        if (pat.isPadovan(n))              map.put(++i, fmt.replace("%s", "Padovan"));
        if (pat.isKeith(n))                map.put(++i, fmt.replace("%s", "Keith"));
        if (pat.isCatalan(n))              map.put(++i, fmt.replace("%s", "Catalan"));
        if (pat.isTriangular(n))           map.put(++i, fmt.replace("%s", "Triangular"));
        if (pat.isTetrahedral(n))          map.put(++i, fmt.replace("%s", "Tetrahedral"));
        if (pat.isPentagonal(n))           map.put(++i, fmt.replace("%s", "Pentagonal"));
        if (pat.isStandardHexagonal(n))    map.put(++i, fmt.replace("%s", "Standard Hexagonal"));
        if (pat.isCenteredHexagonal(n))    map.put(++i, fmt.replace("%s", "Centered Hexagonal"));
        if (pat.isHexagonal(n))            map.put(++i, fmt.replace("%s", "Hexagonal"));
        if (pat.isHeptagonal(n))           map.put(++i, fmt.replace("%s", "Heptagonal"));
        if (pat.isOctagonal(n))            map.put(++i, fmt.replace("a %s", "an Octagonal"));
        if (pat.isStellaOctangula(n))      map.put(++i, fmt.replace("%s", "Stella Octangula"));
        return map;
    }

    private LinkedHashMap<Integer, String> buildBaseRepresentations(long n) {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        map.put(1, bnr.getBinaryRepresentation(n));
        map.put(2, bnr.getOctalRepresentation(n));
        map.put(3, bnr.getHexRepresentation(n));
        return map;
    }

    // ── Prime factorization helpers ──────────────────────────────────────────

    /**
     * Returns a formatted prime factorization string for n > 1.
     *
     * Uses trial division up to min(√n, 10⁶) so the method is always fast
     * even for large inputs. If n still has a factor remaining after the
     * trial-division bound, it is appended as a single (likely prime) factor.
     *
     * Examples:
     *   360  → "2³ × 3² × 5"
     *   97   → "97"
     *   1000 → "2³ × 5³"
     */
    private String formatPrimeFactorization(long n) {
        if (n <= 1) return String.valueOf(n);

        StringBuilder sb = new StringBuilder();
        long temp = Math.abs(n);
        boolean first = true;

        // Trial division up to min(sqrt(n), 10^6)
        long limit = (long) Math.sqrt((double) temp) + 1L;
        if (limit > 1_000_000L) limit = 1_000_000L;

        for (long f = 2L; f <= limit && f * f <= temp; f++) {
            if (temp % f == 0L) {
                int exp = 0;
                while (temp % f == 0L) { exp++; temp /= f; }
                if (!first) sb.append(" \u00d7 "); // ×
                sb.append(f);
                if (exp > 1) sb.append(toSuperscript(exp));
                first = false;
                // Recalculate limit after dividing out the factor
                limit = (long) Math.sqrt((double) temp) + 1L;
                if (limit > 1_000_000L) limit = 1_000_000L;
            }
        }

        // Any remaining factor > 1 is either prime or a large composite
        // (the latter only if original n > 10^12; acceptable approximation).
        if (temp > 1L) {
            if (!first) sb.append(" \u00d7 "); // ×
            sb.append(temp);
        }

        // Edge case: n itself is prime (no factors found), result is just n.
        return first ? String.valueOf(Math.abs(n)) : sb.toString();
    }

    /**
     * Converts a positive integer to its Unicode superscript representation.
     * Supports multi-digit exponents (e.g. 12 → "¹²").
     */
    private static String toSuperscript(int n) {
        // Unicode superscript: ⁰¹²³⁴⁵⁶⁷⁸⁹
        final char[] SUPS = { '\u2070','\u00b9','\u00b2','\u00b3',
                              '\u2074','\u2075','\u2076','\u2077','\u2078','\u2079' };
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) {
            sb.append(SUPS[c - '0']);
        }
        return sb.toString();
    }
}