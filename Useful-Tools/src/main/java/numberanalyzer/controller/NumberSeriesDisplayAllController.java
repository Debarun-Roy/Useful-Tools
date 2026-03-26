package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.function.LongPredicate;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.categories.BaseNRepresentation;
import numberanalyzer.categories.Factors;
import numberanalyzer.categories.Patterns;
import numberanalyzer.categories.PrimeNumbers;
import numberanalyzer.categories.Recreational;
import numberanalyzer.generators.BaseNRepresentationGenerator;
import numberanalyzer.generators.FactorialGenerator;
import numberanalyzer.generators.NumberTheoryGenerator;
import numberanalyzer.generators.PatternsGenerator;
import numberanalyzer.generators.PrimeNumbersGenerator;
import numberanalyzer.generators.RecreationalGenerator;
import numberanalyzer.utilities.JsonBodyParser;
import numberanalyzer.utilities.RequestData;

/**
 * Returns the full N-term series for ALL number sequence categories.
 *
 * CHANGE 5: Responses now wrapped in ApiResponse with correct HTTP status codes.
 * CHANGE 6: Path renamed /NumberSeries/DisplayAll → /api/analyzer/series/all
 *
 * All prior fixes from the final_changes batch are preserved:
 *   - "Arithmetic" bug fix (was calling generateAbundant twice)
 *   - Session null-unboxing fix (boxed Integer)
 */
@WebServlet("/api/analyzer/series/all")
public class NumberSeriesDisplayAllController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final long MIN_BOUNDED_SEARCH = 250L;
    private static final long MAX_BOUNDED_SEARCH = 5000L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            HttpSession session = request.getSession();
            Gson gson = new Gson();

            BaseNRepresentationGenerator bnrg = new BaseNRepresentationGenerator();
            FactorialGenerator           facg = new FactorialGenerator();
            NumberTheoryGenerator        ntg  = new NumberTheoryGenerator();
            PatternsGenerator            pg   = new PatternsGenerator();
            PrimeNumbersGenerator        png  = new PrimeNumbersGenerator();
            RecreationalGenerator        rg   = new RecreationalGenerator();
            BaseNRepresentation          bnr  = new BaseNRepresentation();
            Factors                      fac  = new Factors();
            PrimeNumbers                 pn   = new PrimeNumbers();
            Patterns                     pat  = new Patterns();
            Recreational                 rec  = new Recreational();
            JsonBodyParser               jsonParser = new JsonBodyParser();

            Integer terms;
            String requestJson = jsonParser.getRequestBodyAsString(request);

            if (requestJson == null || requestJson.isBlank()) {
                terms = (Integer) session.getAttribute("terms");
                if (terms == null) terms = 10;
            } else {
                RequestData requestData = gson.fromJson(requestJson, RequestData.class);
                terms = requestData.getTerms();
                if (terms == null || terms < 1) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "'terms' must be a positive integer.", "INVALID_TERMS")));
                    return;
                }
                session.setAttribute("terms", terms);
            }

            long boundedSearchLimit = computeBoundedSearchLimit(terms);

            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Object>>> categories
                    = new LinkedHashMap<>();
            LinkedHashMap<String, LinkedHashMap<String, Object>> seq;

            // Base Representation
            seq = new LinkedHashMap<>();
            seq.put("Binary", exactSeries(bnrg.generateAllBinaryRepresentations(terms), terms));
            seq.put("Octal",  exactSeries(bnrg.generateAllOctalRepresentations(terms), terms));
            seq.put("Hex",    exactSeries(bnrg.generateAllHexRepresentations(terms), terms));
            LinkedHashMap<Integer, String> allBases = bnr.findAllBases(terms);
            LinkedHashMap<Long, String> allResult = new LinkedHashMap<>();
            allBases.forEach((k, v) -> allResult.put((long) k, v));
            seq.put("All", exactSeries(allResult, terms));
            categories.put("Base Representation", seq);

            // Factorials
            seq = new LinkedHashMap<>();
            seq.put("Factorial",      exactSeries(facg.generateFactorial(terms), terms));
            seq.put("Superfactorial", exactSeries(facg.generateSuperfactorial(terms), terms));
            seq.put("Hyperfactorial", exactSeries(facg.generateHyperfactorial(terms), terms));
            seq.put("Primorial",      exactSeries(facg.generatePrimorial(terms), terms));
            categories.put("Factorials", seq);

            // Factors
            seq = new LinkedHashMap<>();
            seq.put("Perfect",      boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isPerfect), terms, boundedSearchLimit));
            seq.put("Imperfect",    boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isImperfect), terms, boundedSearchLimit));
            seq.put("Arithmetic",   boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isArithmetic), terms, boundedSearchLimit));
            seq.put("Inharmonious", boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isInharmonious), terms, boundedSearchLimit));
            seq.put("Blum",         boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isBlum), terms, boundedSearchLimit));
            seq.put("Humble",       boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isHumble), terms, boundedSearchLimit));
            seq.put("Abundant",     boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isAbundant), terms, boundedSearchLimit));
            seq.put("Deficient",    boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isDeficient), terms, boundedSearchLimit));
            seq.put("Amicable",     boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isAmicable), terms, boundedSearchLimit));
            seq.put("Untouchable",  boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, fac::isUntouchable), terms, boundedSearchLimit));
            categories.put("Factors", seq);

            // Number Theory
            seq = new LinkedHashMap<>();
            seq.put("Integers", exactSeries(ntg.generateIntegers(terms), terms));
            seq.put("Natural",  exactSeries(ntg.generateNatural(terms), terms));
            seq.put("Odd",      exactSeries(ntg.generateOdd(terms), terms));
            seq.put("Even",     exactSeries(ntg.generateEven(terms), terms));
            seq.put("Whole",    exactSeries(ntg.generateWhole(terms), terms));
            seq.put("Negative", exactSeries(ntg.generateNegative(terms), terms));
            categories.put("Number Theory", seq);

            // Primes
            seq = new LinkedHashMap<>();
            seq.put("Prime",                exactSeries(png.generatePrime(terms), terms));
            seq.put("Semi Prime",           exactSeries(png.generateSemiPrime(terms), terms));
            seq.put("Emirp",                exactSeries(png.generateEmirp(terms), terms));
            seq.put("Additive Prime",       exactSeries(png.generateAdditivePrime(terms), terms));
            seq.put("Anagrammatic Prime",   boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, pn::isAnagrammaticPrime), terms, boundedSearchLimit));
            seq.put("Circular Prime",       boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, pn::isCircularPrime), terms, boundedSearchLimit));
            seq.put("Killer Prime",         boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, pn::isKillerPrime), terms, boundedSearchLimit));
            seq.put("Prime Palindrome",     exactSeries(png.generatePrimePalindrome(terms), terms));
            seq.put("Twin Primes",          exactSeries(png.generateTwinPrimes(terms), terms));
            seq.put("Cousin Primes",        exactSeries(png.generateCousinPrimes(terms), terms));
            seq.put("Sexy Primes",          exactSeries(png.generateSexyPrimes(terms), terms));
            seq.put("Sophie German Primes", exactSeries(png.generateSophieGermanPrimes(terms), terms));
            categories.put("Primes", seq);

            // Patterns
            seq = new LinkedHashMap<>();
            seq.put("Fibonacci",                  exactSeries(pg.generateFibonacci(terms), terms));
            seq.put("Tribonacci",                 exactSeries(pg.generateTribonacci(terms), terms));
            seq.put("Tetranacci",                 exactSeries(pg.generateTetranacci(terms), terms));
            seq.put("Pentanacci",                 exactSeries(pg.generatePentanacci(terms), terms));
            seq.put("Hexanacci",                  exactSeries(pg.generateHexanacci(terms), terms));
            seq.put("Heptanacci",                 exactSeries(pg.generateHeptanacci(terms), terms));
            seq.put("Perrin",                     exactSeries(pg.generatePerrin(terms), terms));
            seq.put("Lucas",                      exactSeries(pg.generateLucas(terms), terms));
            seq.put("Padovan",                    exactSeries(pg.generatePadovan(terms), terms));
            seq.put("Keith",                      exactSeries(pg.generateKeith(terms), terms));
            seq.put("Palindrome",                 exactSeries(pg.generatePalindrome(terms), terms));
            seq.put("Hypotenuse",                 boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, pat::isHypotenuse), terms, boundedSearchLimit));
            seq.put("Perfect Square",             exactSeries(pg.generatePerfectSquare(terms), terms));
            seq.put("Perfect Cube",               exactSeries(pg.generatePerfectCube(terms), terms));
            seq.put("Perfect Powers",             boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, pat::isPerfectPower), terms, boundedSearchLimit));
            seq.put("Catalan Numbers",            exactSeries(pg.generateCatalanNumbers(terms), terms));
            seq.put("Triangular Numbers",         exactSeries(pg.generateTriangularNumbers(terms), terms));
            seq.put("Pentagonal Numbers",         exactSeries(pg.generatePentagonalNumbers(terms), terms));
            seq.put("Standard Hexagonal Numbers", exactSeries(pg.generateStandardHexagonalNumbers(terms), terms));
            seq.put("Centered Hexagonal Numbers", exactSeries(pg.generateCenteredHexagonalNumbers(terms), terms));
            seq.put("Hexagonal Numbers",          exactSeries(pg.generateHexagonalNumbers(terms), terms));
            seq.put("Heptagonal Numbers",         exactSeries(pg.generateHeptagonalNumbers(terms), terms));
            seq.put("Octagonal Numbers",          exactSeries(pg.generateOctagonalNumbers(terms), terms));
            seq.put("Tetrahedral Numbers",        exactSeries(pg.generateTetrahedralNumbers(terms), terms));
            seq.put("Stella Octangula Numbers",   exactSeries(pg.generateStellaOctangulaNumbers(terms), terms));
            categories.put("Patterns", seq);

            // Recreational
            seq = new LinkedHashMap<>();
            seq.put("Armstrong",   boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isArmstrong), terms, boundedSearchLimit));
            seq.put("Harshad",     exactSeries(rg.generateHarshad(terms), terms));
            seq.put("Disarium",    exactSeries(rg.generateDisarium(terms), terms));
            seq.put("Happy",       exactSeries(rg.generateHappy(terms), terms));
            seq.put("Sad",         exactSeries(rg.generateSad(terms), terms));
            seq.put("Duck",        exactSeries(rg.generateDuck(terms), terms));
            seq.put("Dudeney",     boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isDudeney), terms, boundedSearchLimit));
            seq.put("Buzz",        exactSeries(rg.generateBuzz(terms), terms));
            seq.put("Spy",         exactSeries(rg.generateSpy(terms), terms));
            seq.put("Kaprekar",    boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isKaprekar), terms, boundedSearchLimit));
            seq.put("Tech",        boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isTech), terms, boundedSearchLimit));
            seq.put("Magic",       exactSeries(rg.generateMagic(terms), terms));
            seq.put("Smith",       exactSeries(rg.generateSmith(terms), terms));
            seq.put("Munchausen",  boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isMunchausen), terms, boundedSearchLimit));
            seq.put("Repdigits",   exactSeries(rg.generateRepdigits(terms), terms));
            seq.put("Gapful",      exactSeries(rg.generateGapful(terms), terms));
            seq.put("Hungry",      exactSeries(rg.generateHungry(terms), terms));
            seq.put("Pronic",      exactSeries(rg.generatePronic(terms), terms));
            seq.put("Neon",        boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isNeon), terms, boundedSearchLimit));
            seq.put("Automorphic", boundedSeries(collectBoundedMatches(terms, boundedSearchLimit, rec::isAutomorphic), terms, boundedSearchLimit));
            categories.put("Recreational", seq);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "mixed");
            data.put("requestedTerms", terms);
            data.put("boundedSearchLimit", boundedSearchLimit);
            data.put("categories", categories);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(new Gson().toJson(ApiResponse.fail(
                        "Series generation failed: " + e.getMessage(), "INTERNAL_ERROR")));
            }
        }
    }

    private long computeBoundedSearchLimit(int requestedTerms) {
        long scaled = (long) requestedTerms * 40L;
        return Math.max(MIN_BOUNDED_SEARCH, Math.min(MAX_BOUNDED_SEARCH, scaled));
    }

    private LinkedHashMap<String, Object> exactSeries(LinkedHashMap<Long, String> values, int requestedTerms) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategy", "exact");
        payload.put("requestedTerms", requestedTerms);
        payload.put("returnedTerms", values.size());
        payload.put("fulfilledRequest", values.size() >= requestedTerms);
        payload.put("values", values);
        return payload;
    }

    private LinkedHashMap<String, Object> boundedSeries(
            LinkedHashMap<Long, String> values, int requestedTerms, long searchLimit) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategy", "bounded-search");
        payload.put("requestedTerms", requestedTerms);
        payload.put("returnedTerms", values.size());
        payload.put("fulfilledRequest", values.size() >= requestedTerms);
        payload.put("searchedUpTo", searchLimit);
        payload.put("values", values);
        return payload;
    }

    private LinkedHashMap<Long, String> collectBoundedMatches(
            int requestedTerms, long searchLimit, LongPredicate predicate) {
        LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
        if (requestedTerms <= 0 || searchLimit < 1) return resultMap;

        for (long candidate = 1L, count = 0L; candidate <= searchLimit && count < requestedTerms; candidate++) {
            if (predicate.test(candidate)) {
                resultMap.put(++count, String.valueOf(candidate));
            }
        }
        return resultMap;
    }
}
