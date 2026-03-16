package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.categories.BaseNRepresentation;
import numberanalyzer.generators.BaseNRepresentationGenerator;
import numberanalyzer.generators.FactorialGenerator;
import numberanalyzer.generators.FactorsGenerator;
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            HttpSession session = request.getSession();
            Gson gson = new Gson();

            FactorsGenerator             fg   = new FactorsGenerator();
            BaseNRepresentationGenerator bnrg = new BaseNRepresentationGenerator();
            FactorialGenerator           facg = new FactorialGenerator();
            NumberTheoryGenerator        ntg  = new NumberTheoryGenerator();
            PatternsGenerator            pg   = new PatternsGenerator();
            PrimeNumbersGenerator        png  = new PrimeNumbersGenerator();
            RecreationalGenerator        rg   = new RecreationalGenerator();
            BaseNRepresentation          bnr  = new BaseNRepresentation();
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

            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> result
                    = new LinkedHashMap<>();
            LinkedHashMap<String, LinkedHashMap<Long, String>> seq;

            // Base Representation
            seq = new LinkedHashMap<>();
            seq.put("Binary", bnrg.generateAllBinaryRepresentations(terms));
            seq.put("Octal",  bnrg.generateAllOctalRepresentations(terms));
            seq.put("Hex",    bnrg.generateAllHexRepresentations(terms));
            LinkedHashMap<Integer, String> allBases = bnr.findAllBases(terms);
            LinkedHashMap<Long, String> allResult = new LinkedHashMap<>();
            allBases.forEach((k, v) -> allResult.put((long) k, v));
            seq.put("All", allResult);
            result.put("Base Representation", seq);

            // Factorials
            seq = new LinkedHashMap<>();
            seq.put("Factorial",      facg.generateFactorial(terms));
            seq.put("Superfactorial", facg.generateSuperfactorial(terms));
            seq.put("Hyperfactorial", facg.generateHyperfactorial(terms));
            seq.put("Primorial",      facg.generatePrimorial(terms));
            result.put("Factorials", seq);

            // Factors
            seq = new LinkedHashMap<>();
            seq.put("Perfect",      fg.generatePerfect(terms));
            seq.put("Imperfect",    fg.generateImperfect(terms));
            seq.put("Arithmetic",   fg.generateArithmetic(terms));
            seq.put("Inharmonious", fg.generateInharmonious(terms));
            seq.put("Blum",         fg.generateBlum(terms));
            seq.put("Humble",       fg.generateHumble(terms));
            seq.put("Abundant",     fg.generateAbundant(terms));
            seq.put("Deficient",    fg.generateDeficient(terms));
            seq.put("Amicable",     fg.generateAmicable(terms));
            seq.put("Untouchable",  fg.generateUntouchable(terms));
            result.put("Factors", seq);

            // Number Theory
            seq = new LinkedHashMap<>();
            seq.put("Integers", ntg.generateIntegers(terms));
            seq.put("Natural",  ntg.generateNatural(terms));
            seq.put("Odd",      ntg.generateOdd(terms));
            seq.put("Even",     ntg.generateEven(terms));
            seq.put("Whole",    ntg.generateWhole(terms));
            seq.put("Negative", ntg.generateNegative(terms));
            result.put("Number Theory", seq);

            // Primes
            seq = new LinkedHashMap<>();
            seq.put("Prime",                png.generatePrime(terms));
            seq.put("Semi Prime",           png.generateSemiPrime(terms));
            seq.put("Emirp",                png.generateEmirp(terms));
            seq.put("Additive Prime",       png.generateAdditivePrime(terms));
            seq.put("Anagrammatic Prime",   png.generateAnagrammaticPrime(terms));
            seq.put("Circular Prime",       png.generateCircularPrime(terms));
            seq.put("Killer Prime",         png.generateKillerPrime(terms));
            seq.put("Prime Palindrome",     png.generatePrimePalindrome(terms));
            seq.put("Twin Primes",          png.generateTwinPrimes(terms));
            seq.put("Cousin Primes",        png.generateCousinPrimes(terms));
            seq.put("Sexy Primes",          png.generateSexyPrimes(terms));
            seq.put("Sophie German Primes", png.generateSophieGermanPrimes(terms));
            result.put("Primes", seq);

            // Patterns
            seq = new LinkedHashMap<>();
            seq.put("Fibonacci",                  pg.generateFibonacci(terms));
            seq.put("Tribonacci",                 pg.generateTribonacci(terms));
            seq.put("Tetranacci",                 pg.generateTetranacci(terms));
            seq.put("Pentanacci",                 pg.generatePentanacci(terms));
            seq.put("Hexanacci",                  pg.generateHexanacci(terms));
            seq.put("Heptanacci",                 pg.generateHeptanacci(terms));
            seq.put("Perrin",                     pg.generatePerrin(terms));
            seq.put("Lucas",                      pg.generateLucas(terms));
            seq.put("Padovan",                    pg.generatePadovan(terms));
            seq.put("Keith",                      pg.generateKeith(terms));
            seq.put("Palindrome",                 pg.generatePalindrome(terms));
            seq.put("Hypotenuse",                 pg.generateHypotenuse(terms));
            seq.put("Perfect Square",             pg.generatePerfectSquare(terms));
            seq.put("Perfect Cube",               pg.generatePerfectCube(terms));
            seq.put("Perfect Powers",             pg.generatePerfectPowers(terms));
            seq.put("Catalan Numbers",            pg.generateCatalanNumbers(terms));
            seq.put("Triangular Numbers",         pg.generateTriangularNumbers(terms));
            seq.put("Pentagonal Numbers",         pg.generatePentagonalNumbers(terms));
            seq.put("Standard Hexagonal Numbers", pg.generateStandardHexagonalNumbers(terms));
            seq.put("Centered Hexagonal Numbers", pg.generateCenteredHexagonalNumbers(terms));
            seq.put("Hexagonal Numbers",          pg.generateHexagonalNumbers(terms));
            seq.put("Heptagonal Numbers",         pg.generateHeptagonalNumbers(terms));
            seq.put("Octagonal Numbers",          pg.generateOctagonalNumbers(terms));
            seq.put("Tetrahedral Numbers",        pg.generateTetrahedralNumbers(terms));
            seq.put("Stella Octangula Numbers",   pg.generateStellaOctangulaNumbers(terms));
            result.put("Patterns", seq);

            // Recreational
            seq = new LinkedHashMap<>();
            seq.put("Armstrong",   rg.generateArmstrong(terms));
            seq.put("Harshad",     rg.generateHarshad(terms));
            seq.put("Disarium",    rg.generateDisarium(terms));
            seq.put("Happy",       rg.generateHappy(terms));
            seq.put("Sad",         rg.generateSad(terms));
            seq.put("Duck",        rg.generateDuck(terms));
            seq.put("Dudeney",     rg.generateDudeney(terms));
            seq.put("Buzz",        rg.generateBuzz(terms));
            seq.put("Spy",         rg.generateSpy(terms));
            seq.put("Kaprekar",    rg.generateKaprekar(terms));
            seq.put("Tech",        rg.generateTech(terms));
            seq.put("Magic",       rg.generateMagic(terms));
            seq.put("Smith",       rg.generateSmith(terms));
            seq.put("Munchausen",  rg.generateMunchausen(terms));
            seq.put("Repdigits",   rg.generateRepdigits(terms));
            seq.put("Gapful",      rg.generateGapful(terms));
            seq.put("Hungry",      rg.generateHungry(terms));
            seq.put("Pronic",      rg.generatePronic(terms));
            seq.put("Neon",        rg.generateNeon(terms));
            seq.put("Automorphic", rg.generateAutomorphic(terms));
            result.put("Recreational", seq);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(result)));
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
}