package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
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
 * FIX 1 — "Arithmetic" was missing from the Factors category and "Abundant"
 *   was listed twice (original lines 92 and 96 both called generateAbundant()).
 *   Corrected: first entry is now "Arithmetic" calling fg.generateArithmetic().
 *
 * FIX 2 — Session null-unboxing: (int) session.getAttribute("terms") throws
 *   NullPointerException when the session fallback path is taken on a fresh
 *   session. Changed to boxed Integer with a null-guard.
 *
 * NOTE: The two DisplayTerm controllers (/NumberAnalyzer/DisplayTerm/All and
 *   /NumberAnalysis/DisplayTerm/Selected) have been deleted as redundant.
 *   Clients that previously used those endpoints should call this controller
 *   (or /NumberSeries/DisplaySelected) and read the last entry of the response
 *   map to obtain the Nth term.
 */
@WebServlet("/NumberSeries/DisplayAll")
public class NumberSeriesDisplayAllController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
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

            // FIX: use boxed Integer so null-check works on first request.
            Integer terms;
            String requestJson = jsonParser.getRequestBodyAsString(request);

            if (requestJson == null || requestJson.isBlank()) {
                terms = (Integer) session.getAttribute("terms");
                if (terms == null) terms = 10; // sensible default
            } else {
                RequestData requestData = gson.fromJson(requestJson, RequestData.class);
                terms = requestData.getTerms();
                session.setAttribute("terms", terms);
            }

            // category → sequence-name → (term-index → value)
            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> responseJson
                    = new LinkedHashMap<>();
            LinkedHashMap<String, LinkedHashMap<Long, String>> generatedSequence;

            // ── Base Representation ─────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Binary", bnrg.generateAllBinaryRepresentations(terms));
            generatedSequence.put("Octal",  bnrg.generateAllOctalRepresentations(terms));
            generatedSequence.put("Hex",    bnrg.generateAllHexRepresentations(terms));
            LinkedHashMap<Integer, String> allBaseRepresentations = bnr.findAllBases(terms);
            LinkedHashMap<Long, String> allResult = new LinkedHashMap<>();
            allBaseRepresentations.forEach((k, v) -> allResult.put((long) k, v));
            generatedSequence.put("All", allResult);
            responseJson.put("Base Representation", generatedSequence);

            // ── Factorials ──────────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Factorial",      facg.generateFactorial(terms));
            generatedSequence.put("Superfactorial", facg.generateSuperfactorial(terms));
            generatedSequence.put("Hyperfactorial", facg.generateHyperfactorial(terms));
            generatedSequence.put("Primorial",      facg.generatePrimorial(terms));
            responseJson.put("Factorials", generatedSequence);

            // ── Factors ─────────────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Perfect",     fg.generatePerfect(terms));
            generatedSequence.put("Imperfect",   fg.generateImperfect(terms));
            // FIX: "Arithmetic" was missing; "Abundant" was listed twice instead.
            generatedSequence.put("Arithmetic",  fg.generateArithmetic(terms));
            generatedSequence.put("Inharmonious",fg.generateInharmonious(terms));
            generatedSequence.put("Blum",        fg.generateBlum(terms));
            generatedSequence.put("Humble",      fg.generateHumble(terms));
            generatedSequence.put("Abundant",    fg.generateAbundant(terms));
            generatedSequence.put("Deficient",   fg.generateDeficient(terms));
            generatedSequence.put("Amicable",    fg.generateAmicable(terms));
            generatedSequence.put("Untouchable", fg.generateUntouchable(terms));
            responseJson.put("Factors", generatedSequence);

            // ── Number Theory ───────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Integers", ntg.generateIntegers(terms));
            generatedSequence.put("Natural",  ntg.generateNatural(terms));
            generatedSequence.put("Odd",      ntg.generateOdd(terms));
            generatedSequence.put("Even",     ntg.generateEven(terms));
            generatedSequence.put("Whole",    ntg.generateWhole(terms));
            generatedSequence.put("Negative", ntg.generateNegative(terms));
            responseJson.put("Number Theory", generatedSequence);

            // ── Primes ──────────────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Prime",               png.generatePrime(terms));
            generatedSequence.put("Semi Prime",          png.generateSemiPrime(terms));
            generatedSequence.put("Emirp",               png.generateEmirp(terms));
            generatedSequence.put("Additive Prime",      png.generateAdditivePrime(terms));
            generatedSequence.put("Anagrammatic Prime",  png.generateAnagrammaticPrime(terms));
            generatedSequence.put("Circular Prime",      png.generateCircularPrime(terms));
            generatedSequence.put("Killer Prime",        png.generateKillerPrime(terms));
            generatedSequence.put("Prime Palindrome",    png.generatePrimePalindrome(terms));
            generatedSequence.put("Twin Primes",         png.generateTwinPrimes(terms));
            generatedSequence.put("Cousin Primes",       png.generateCousinPrimes(terms));
            generatedSequence.put("Sexy Primes",         png.generateSexyPrimes(terms));
            generatedSequence.put("Sophie German Primes",png.generateSophieGermanPrimes(terms));
            responseJson.put("Primes", generatedSequence);

            // ── Patterns ────────────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Fibonacci",                  pg.generateFibonacci(terms));
            generatedSequence.put("Tribonacci",                 pg.generateTribonacci(terms));
            generatedSequence.put("Tetranacci",                 pg.generateTetranacci(terms));
            generatedSequence.put("Pentanacci",                 pg.generatePentanacci(terms));
            generatedSequence.put("Hexanacci",                  pg.generateHexanacci(terms));
            generatedSequence.put("Heptanacci",                 pg.generateHeptanacci(terms));
            generatedSequence.put("Perrin",                     pg.generatePerrin(terms));
            generatedSequence.put("Lucas",                      pg.generateLucas(terms));
            generatedSequence.put("Padovan",                    pg.generatePadovan(terms));
            generatedSequence.put("Keith",                      pg.generateKeith(terms));
            generatedSequence.put("Palindrome",                 pg.generatePalindrome(terms));
            generatedSequence.put("Hypotenuse",                 pg.generateHypotenuse(terms));
            generatedSequence.put("Perfect Square",             pg.generatePerfectSquare(terms));
            generatedSequence.put("Perfect Cube",               pg.generatePerfectCube(terms));
            generatedSequence.put("Perfect Powers",             pg.generatePerfectPowers(terms));
            generatedSequence.put("Catalan Numbers",            pg.generateCatalanNumbers(terms));
            generatedSequence.put("Triangular Numbers",         pg.generateTriangularNumbers(terms));
            generatedSequence.put("Pentagonal Numbers",         pg.generatePentagonalNumbers(terms));
            generatedSequence.put("Standard Hexagonal Numbers", pg.generateStandardHexagonalNumbers(terms));
            generatedSequence.put("Centered Hexagonal Numbers", pg.generateCenteredHexagonalNumbers(terms));
            generatedSequence.put("Hexagonal Numbers",          pg.generateHexagonalNumbers(terms));
            generatedSequence.put("Heptagonal Numbers",         pg.generateHeptagonalNumbers(terms));
            generatedSequence.put("Octagonal Numbers",          pg.generateOctagonalNumbers(terms));
            generatedSequence.put("Tetrahedral Numbers",        pg.generateTetrahedralNumbers(terms));
            generatedSequence.put("Stella Octangula Numbers",   pg.generateStellaOctangulaNumbers(terms));
            responseJson.put("Patterns", generatedSequence);

            // ── Recreational ────────────────────────────────────────────────
            generatedSequence = new LinkedHashMap<>();
            generatedSequence.put("Armstrong",   rg.generateArmstrong(terms));
            generatedSequence.put("Harshad",     rg.generateHarshad(terms));
            generatedSequence.put("Disarium",    rg.generateDisarium(terms));
            generatedSequence.put("Happy",       rg.generateHappy(terms));
            generatedSequence.put("Sad",         rg.generateSad(terms));
            generatedSequence.put("Duck",        rg.generateDuck(terms));
            generatedSequence.put("Dudeney",     rg.generateDudeney(terms));
            generatedSequence.put("Buzz",        rg.generateBuzz(terms));
            generatedSequence.put("Spy",         rg.generateSpy(terms));
            generatedSequence.put("Kaprekar",    rg.generateKaprekar(terms));
            generatedSequence.put("Tech",        rg.generateTech(terms));
            generatedSequence.put("Magic",       rg.generateMagic(terms));
            generatedSequence.put("Smith",       rg.generateSmith(terms));
            generatedSequence.put("Munchausen",  rg.generateMunchausen(terms));
            generatedSequence.put("Repdigits",   rg.generateRepdigits(terms));
            generatedSequence.put("Gapful",      rg.generateGapful(terms));
            generatedSequence.put("Hungry",      rg.generateHungry(terms));
            generatedSequence.put("Pronic",      rg.generatePronic(terms));
            generatedSequence.put("Neon",        rg.generateNeon(terms));
            generatedSequence.put("Automorphic", rg.generateAutomorphic(terms));
            responseJson.put("Recreational", generatedSequence);

            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(responseJson));
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(new Gson().toJson("Error: " + e.getMessage()));
            }
        }
    }
}