package passwordgenerator.utilities;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * FIX: getRandomSpecialChars() used range [33, 127) with a fixed count then
 *   filtered — this discards letter/digit characters from the fixed-size stream,
 *   producing fewer than `length` special characters. The password total length
 *   is therefore always less than requested.
 *
 *   Corrected to use an infinite stream + filter + limit(length), which
 *   guarantees exactly `length` special characters are returned regardless of
 *   how many candidates are rejected by the filter.
 *
 * FIX: getRandomNumbers() used range [48, 57) which excludes '9' (ASCII 57).
 *   Corrected to [48, 58).
 *
 * FIX: getRandomAlphabets() used range ['a','z'] and ['A','Z'] — both exclusive
 *   on the upper bound in Random.ints(), excluding 'z' and 'Z'. Corrected to
 *   ['a','z'+1) and ['A','Z'+1).
 */
public class PasswordGeneratorUtils {

    /**
     * Returns exactly {@code length} special (non-letter, non-digit) characters
     * in the printable ASCII range [33, 127).
     *
     * Uses an infinite stream filtered to special chars, then limited to the
     * requested count — this guarantees the exact count regardless of how many
     * raw candidates the filter rejects.
     */
    public static Stream<Character> getRandomSpecialChars(int length) {
        Random random = new SecureRandom();
        // FIX: was random.ints(length, 33, 127).filter(...) which produced
        // FEWER than `length` results because filter discards from a finite stream.
        return random.ints(33, 127)
                .filter(i -> !Character.isLetterOrDigit(i))
                .limit(length)
                .mapToObj(data -> (char) data);
    }

    public static Stream<Character> getRandomNumbers(int length) {
        Random random = new SecureRandom();
        // FIX: upper bound corrected from 57 to 58 so '9' (ASCII 57) is included.
        IntStream numberIntStream = random.ints(length, 48, 58);
        return numberIntStream.mapToObj(data -> (char) data);
    }

    /**
     * @param lower  true → lowercase letters, false → uppercase letters
     */
    public static Stream<Character> getRandomAlphabets(int length, boolean lower) {
        Random random = new SecureRandom();
        // FIX: upper bounds corrected to 'z'+1 and 'Z'+1 so 'z' and 'Z' are included.
        IntStream stream = lower
                ? random.ints(length, 'a', 'z' + 1)
                : random.ints(length, 'A', 'Z' + 1);
        return stream.mapToObj(data -> (char) data);
    }

    /**
     * Splits a total password length into four random sub-lengths that sum to
     * exactly {@code length}: [numbers, specialChars, lowercase, uppercase].
     *
     * Each of the first three buckets is assigned a random value in
     * [0, length/4], and the fourth bucket absorbs the remainder so the
     * sum is always exactly length.
     */
    public static int[] getRandomizedValues(int length) {
        Random random = new SecureRandom();
        int v1 = random.nextInt(length / 4 + 1);
        int v2 = random.nextInt(length / 4 + 1);
        int v3 = random.nextInt(length / 4 + 1);
        int v4 = length - v1 - v2 - v3;
        // Guard: if rounding pushed v4 negative, redistribute evenly.
        if (v4 < 0) {
            v1 = length / 4;
            v2 = length / 4;
            v3 = length / 4;
            v4 = length - v1 - v2 - v3;
        }
        return new int[]{v1, v2, v3, v4};
    }

    public static String generateSecurePassword(int numbers, int special, int uppercase, int lowercase) {
        Stream<Character> combined = Stream.concat(
                getRandomNumbers(numbers),
                Stream.concat(
                        getRandomSpecialChars(special),
                        Stream.concat(
                                getRandomAlphabets(lowercase, true),
                                getRandomAlphabets(uppercase, false))));
        List<Character> chars = combined.collect(Collectors.toList());
        Collections.shuffle(chars, new SecureRandom());
        return chars.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }
}