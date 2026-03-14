package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Returns the full N-term series for a user-selected subset of number sequence
 * categories, specified via a JSON request body containing a choiceMap.
 *
 * FIX 1 — "Arithmetic" branch in Factors called fg.generateAbundant() instead
 *   of fg.generateArithmetic() (copy-paste bug). Corrected.
 *
 * FIX 2 — Typo "Pefect Cube" corrected to "Perfect Cube" so the key now
 *   matches what the front-end sends. (Also present in Patterns: "Centered
 *   Hexagonal Numebrs" corrected to "Centered Hexagonal Numbers".)
 *
 * FIX 3 — Session null-unboxing: (int) session.getAttribute("terms") and
 *   (HashMap) session.getAttribute("choiceMap") throw NullPointerException
 *   when the session fallback is needed. Changed to boxed types with guards.
 *
 * FIX 4 — Error response was a plain string "Exception : ...". Now returns
 *   proper JSON so the client can parse it consistently.
 */
@WebServlet("/NumberSeries/DisplaySelected")
public class NumberSeriesDisplaySelectedController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
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

            HashMap<String, ArrayList<String>> choiceMap;
            Integer terms;

            String requestJson = jsonParser.getRequestBodyAsString(request);

            if (requestJson == null || requestJson.isBlank()) {
                // FIX: boxed types — unboxing null throws NPE
                choiceMap = (HashMap<String, ArrayList<String>>) session.getAttribute("choiceMap");
                terms     = (Integer) session.getAttribute("terms");
                if (choiceMap == null || terms == null) {
                    throw new IllegalStateException(
                            "No choiceMap or terms in session and no request body provided.");
                }
            } else {
                RequestData requestData = gson.fromJson(requestJson, RequestData.class);
                terms     = requestData.getTerms();
                choiceMap = requestData.getChoiceMap();
                session.setAttribute("choiceMap", choiceMap);
                session.setAttribute("terms", terms);
            }

            // category → sequence-name → (term-index → value)
            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> responseJson
                    = new LinkedHashMap<>();

            for (Map.Entry<String, ArrayList<String>> entry : choiceMap.entrySet()) {
                String category = entry.getKey();
                ArrayList<String> selected = entry.getValue();

                if (category == null || selected == null)
                    throw new IllegalArgumentException("Invalid request: null category or selection.");

                LinkedHashMap<String, LinkedHashMap<Long, String>> generatedSequence
                        = new LinkedHashMap<>();

                switch (category) {

                    case "Base Representation":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Binary":
                                    generatedSequence.put(sel, bnrg.generateAllBinaryRepresentations(terms)); break;
                                case "Octal":
                                    generatedSequence.put(sel, bnrg.generateAllOctalRepresentations(terms)); break;
                                case "Hex":
                                    generatedSequence.put(sel, bnrg.generateAllHexRepresentations(terms)); break;
                                case "All":
                                    LinkedHashMap<Integer, String> allBases = bnr.findAllBases(terms);
                                    LinkedHashMap<Long, String> converted = new LinkedHashMap<>();
                                    allBases.forEach((k, v) -> converted.put((long) k, v));
                                    generatedSequence.put(sel, converted); break;
                                default: throw new IllegalArgumentException("Unknown Base Representation type: " + sel);
                            }
                        }
                        break;

                    case "Factorials":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Factorial":      generatedSequence.put(sel, facg.generateFactorial(terms)); break;
                                case "Superfactorial": generatedSequence.put(sel, facg.generateSuperfactorial(terms)); break;
                                case "Hyperfactorial": generatedSequence.put(sel, facg.generateHyperfactorial(terms)); break;
                                case "Primorial":      generatedSequence.put(sel, facg.generatePrimorial(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Factorial type: " + sel);
                            }
                        }
                        break;

                    case "Factors":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Perfect":      generatedSequence.put(sel, fg.generatePerfect(terms)); break;
                                case "Imperfect":    generatedSequence.put(sel, fg.generateImperfect(terms)); break;
                                // FIX: was fg.generateAbundant() — copy-paste error. Corrected to generateArithmetic().
                                case "Arithmetic":   generatedSequence.put(sel, fg.generateArithmetic(terms)); break;
                                case "Inharmonious": generatedSequence.put(sel, fg.generateInharmonious(terms)); break;
                                case "Blum":         generatedSequence.put(sel, fg.generateBlum(terms)); break;
                                case "Humble":       generatedSequence.put(sel, fg.generateHumble(terms)); break;
                                case "Abundant":     generatedSequence.put(sel, fg.generateAbundant(terms)); break;
                                case "Deficient":    generatedSequence.put(sel, fg.generateDeficient(terms)); break;
                                case "Amicable":     generatedSequence.put(sel, fg.generateAmicable(terms)); break;
                                case "Untouchable":  generatedSequence.put(sel, fg.generateUntouchable(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Factor type: " + sel);
                            }
                        }
                        break;

                    case "Number Theory":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Integer":  generatedSequence.put(sel, ntg.generateIntegers(terms)); break;
                                case "Natural":  generatedSequence.put(sel, ntg.generateNatural(terms)); break;
                                case "Odd":      generatedSequence.put(sel, ntg.generateOdd(terms)); break;
                                case "Even":     generatedSequence.put(sel, ntg.generateEven(terms)); break;
                                case "Whole":    generatedSequence.put(sel, ntg.generateWhole(terms)); break;
                                case "Negative": generatedSequence.put(sel, ntg.generateNegative(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Number Theory type: " + sel);
                            }
                        }
                        break;

                    case "Primes":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Prime":                generatedSequence.put(sel, png.generatePrime(terms)); break;
                                case "Semi Prime":           generatedSequence.put(sel, png.generateSemiPrime(terms)); break;
                                case "Emirp":                generatedSequence.put(sel, png.generateEmirp(terms)); break;
                                case "Additive Prime":       generatedSequence.put(sel, png.generateAdditivePrime(terms)); break;
                                case "Anagrammatic Prime":   generatedSequence.put(sel, png.generateAnagrammaticPrime(terms)); break;
                                case "Circular Prime":       generatedSequence.put(sel, png.generateCircularPrime(terms)); break;
                                case "Killer Prime":         generatedSequence.put(sel, png.generateKillerPrime(terms)); break;
                                case "Prime Palindrome":     generatedSequence.put(sel, png.generatePrimePalindrome(terms)); break;
                                case "Twin Primes":          generatedSequence.put(sel, png.generateTwinPrimes(terms)); break;
                                case "Cousin Primes":        generatedSequence.put(sel, png.generateCousinPrimes(terms)); break;
                                case "Sexy Primes":          generatedSequence.put(sel, png.generateSexyPrimes(terms)); break;
                                case "Sophie German Primes": generatedSequence.put(sel, png.generateSophieGermanPrimes(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Prime type: " + sel);
                            }
                        }
                        break;

                    case "Patterns":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Fibonacci":                   generatedSequence.put(sel, pg.generateFibonacci(terms)); break;
                                case "Tribonacci":                  generatedSequence.put(sel, pg.generateTribonacci(terms)); break;
                                case "Tetranacci":                  generatedSequence.put(sel, pg.generateTetranacci(terms)); break;
                                case "Pentanacci":                  generatedSequence.put(sel, pg.generatePentanacci(terms)); break;
                                case "Hexanacci":                   generatedSequence.put(sel, pg.generateHexanacci(terms)); break;
                                case "Heptanacci":                  generatedSequence.put(sel, pg.generateHeptanacci(terms)); break;
                                case "Perrin":                      generatedSequence.put(sel, pg.generatePerrin(terms)); break;
                                case "Lucas":                       generatedSequence.put(sel, pg.generateLucas(terms)); break;
                                case "Padovan":                     generatedSequence.put(sel, pg.generatePadovan(terms)); break;
                                case "Keith":                       generatedSequence.put(sel, pg.generateKeith(terms)); break;
                                case "Palindrome":                  generatedSequence.put(sel, pg.generatePalindrome(terms)); break;
                                case "Hypotenuse":                  generatedSequence.put(sel, pg.generateHypotenuse(terms)); break;
                                case "Perfect Square":              generatedSequence.put(sel, pg.generatePerfectSquare(terms)); break;
                                // FIX: typo "Pefect Cube" corrected to "Perfect Cube"
                                case "Perfect Cube":                generatedSequence.put(sel, pg.generatePerfectCube(terms)); break;
                                case "Perfect Powers":              generatedSequence.put(sel, pg.generatePerfectPowers(terms)); break;
                                case "Catalan Numbers":             generatedSequence.put(sel, pg.generateCatalanNumbers(terms)); break;
                                case "Triangular Numbers":          generatedSequence.put(sel, pg.generateTriangularNumbers(terms)); break;
                                case "Pentagonal Numbers":          generatedSequence.put(sel, pg.generatePentagonalNumbers(terms)); break;
                                case "Standard Hexagonal Numbers":  generatedSequence.put(sel, pg.generateStandardHexagonalNumbers(terms)); break;
                                // FIX: typo "Centered Hexagonal Numebrs" corrected to "Centered Hexagonal Numbers"
                                case "Centered Hexagonal Numbers":  generatedSequence.put(sel, pg.generateCenteredHexagonalNumbers(terms)); break;
                                case "Hexagonal Numbers":           generatedSequence.put(sel, pg.generateHexagonalNumbers(terms)); break;
                                case "Heptagonal Numbers":          generatedSequence.put(sel, pg.generateHeptagonalNumbers(terms)); break;
                                case "Octagonal Numbers":           generatedSequence.put(sel, pg.generateOctagonalNumbers(terms)); break;
                                case "Tetrahedral Numbers":         generatedSequence.put(sel, pg.generateTetrahedralNumbers(terms)); break;
                                case "Stella Octangula Numbers":    generatedSequence.put(sel, pg.generateStellaOctangulaNumbers(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Pattern type: " + sel);
                            }
                        }
                        break;

                    case "Recreational":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Armstrong":   generatedSequence.put(sel, rg.generateArmstrong(terms)); break;
                                case "Harshad":     generatedSequence.put(sel, rg.generateHarshad(terms)); break;
                                case "Disarium":    generatedSequence.put(sel, rg.generateDisarium(terms)); break;
                                case "Happy":       generatedSequence.put(sel, rg.generateHappy(terms)); break;
                                case "Sad":         generatedSequence.put(sel, rg.generateSad(terms)); break;
                                case "Duck":        generatedSequence.put(sel, rg.generateDuck(terms)); break;
                                case "Dudeney":     generatedSequence.put(sel, rg.generateDudeney(terms)); break;
                                case "Buzz":        generatedSequence.put(sel, rg.generateBuzz(terms)); break;
                                case "Spy":         generatedSequence.put(sel, rg.generateSpy(terms)); break;
                                case "Kaprekar":    generatedSequence.put(sel, rg.generateKaprekar(terms)); break;
                                case "Tech":        generatedSequence.put(sel, rg.generateTech(terms)); break;
                                case "Magic":       generatedSequence.put(sel, rg.generateMagic(terms)); break;
                                case "Smith":       generatedSequence.put(sel, rg.generateSmith(terms)); break;
                                case "Munchausen":  generatedSequence.put(sel, rg.generateMunchausen(terms)); break;
                                case "Repdigits":   generatedSequence.put(sel, rg.generateRepdigits(terms)); break;
                                case "Gapful":      generatedSequence.put(sel, rg.generateGapful(terms)); break;
                                case "Hungry":      generatedSequence.put(sel, rg.generateHungry(terms)); break;
                                case "Pronic":      generatedSequence.put(sel, rg.generatePronic(terms)); break;
                                case "Neon":        generatedSequence.put(sel, rg.generateNeon(terms)); break;
                                case "Automorphic": generatedSequence.put(sel, rg.generateAutomorphic(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Recreational type: " + sel);
                            }
                        }
                        break;

                    default:
                        throw new IllegalArgumentException("Unknown category: " + category);
                }

                responseJson.put(category, generatedSequence);
            }

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