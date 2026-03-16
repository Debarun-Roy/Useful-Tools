package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Returns the full N-term series for a user-selected subset of categories.
 *
 * CHANGE 5: Responses now wrapped in ApiResponse with correct HTTP status codes.
 * CHANGE 6: Path renamed /NumberSeries/DisplaySelected → /api/analyzer/series/selected
 *
 * All prior fixes from the final_changes batch are preserved:
 *   - Arithmetic branch was calling generateAbundant (copy-paste bug) — fixed
 *   - Typo "Pefect Cube" → "Perfect Cube" — fixed
 *   - Typo "Centered Hexagonal Numebrs" → "Centered Hexagonal Numbers" — fixed
 *   - Session null-unboxing (boxed Integer/HashMap) — fixed
 */
@WebServlet("/api/analyzer/series/selected")
public class NumberSeriesDisplaySelectedController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
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

            HashMap<String, ArrayList<String>> choiceMap;
            Integer terms;

            String requestJson = jsonParser.getRequestBodyAsString(request);

            if (requestJson == null || requestJson.isBlank()) {
                choiceMap = (HashMap<String, ArrayList<String>>) session.getAttribute("choiceMap");
                terms     = (Integer) session.getAttribute("terms");
                if (choiceMap == null || terms == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "No choiceMap or terms found in session. Please provide a JSON request body.",
                            "MISSING_REQUEST_DATA")));
                    return;
                }
            } else {
                RequestData requestData = gson.fromJson(requestJson, RequestData.class);
                terms     = requestData.getTerms();
                choiceMap = requestData.getChoiceMap();
                if (choiceMap == null || terms == null || terms < 1) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Request body must contain 'terms' (positive integer) and 'choiceMap'.",
                            "INVALID_REQUEST_DATA")));
                    return;
                }
                session.setAttribute("choiceMap", choiceMap);
                session.setAttribute("terms", terms);
            }

            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> result
                    = new LinkedHashMap<>();

            for (Map.Entry<String, ArrayList<String>> entry : choiceMap.entrySet()) {
                String category = entry.getKey();
                ArrayList<String> selected = entry.getValue();

                if (category == null || selected == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Invalid choiceMap entry: null category or selection list.",
                            "INVALID_CHOICE_MAP")));
                    return;
                }

                LinkedHashMap<String, LinkedHashMap<Long, String>> seq = new LinkedHashMap<>();

                switch (category) {
                    case "Base Representation":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Binary": seq.put(sel, bnrg.generateAllBinaryRepresentations(terms)); break;
                                case "Octal":  seq.put(sel, bnrg.generateAllOctalRepresentations(terms));  break;
                                case "Hex":    seq.put(sel, bnrg.generateAllHexRepresentations(terms));    break;
                                case "All": {
                                    LinkedHashMap<Integer, String> all = bnr.findAllBases(terms);
                                    LinkedHashMap<Long, String> conv = new LinkedHashMap<>();
                                    all.forEach((k, v) -> conv.put((long) k, v));
                                    seq.put(sel, conv); break;
                                }
                                default: throw new IllegalArgumentException("Unknown Base Representation type: " + sel);
                            }
                        }
                        break;
                    case "Factorials":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Factorial":      seq.put(sel, facg.generateFactorial(terms));      break;
                                case "Superfactorial": seq.put(sel, facg.generateSuperfactorial(terms)); break;
                                case "Hyperfactorial": seq.put(sel, facg.generateHyperfactorial(terms)); break;
                                case "Primorial":      seq.put(sel, facg.generatePrimorial(terms));      break;
                                default: throw new IllegalArgumentException("Unknown Factorial type: " + sel);
                            }
                        }
                        break;
                    case "Factors":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Perfect":      seq.put(sel, fg.generatePerfect(terms));      break;
                                case "Imperfect":    seq.put(sel, fg.generateImperfect(terms));    break;
                                case "Arithmetic":   seq.put(sel, fg.generateArithmetic(terms));   break;
                                case "Inharmonious": seq.put(sel, fg.generateInharmonious(terms)); break;
                                case "Blum":         seq.put(sel, fg.generateBlum(terms));         break;
                                case "Humble":       seq.put(sel, fg.generateHumble(terms));       break;
                                case "Abundant":     seq.put(sel, fg.generateAbundant(terms));     break;
                                case "Deficient":    seq.put(sel, fg.generateDeficient(terms));    break;
                                case "Amicable":     seq.put(sel, fg.generateAmicable(terms));     break;
                                case "Untouchable":  seq.put(sel, fg.generateUntouchable(terms));  break;
                                default: throw new IllegalArgumentException("Unknown Factor type: " + sel);
                            }
                        }
                        break;
                    case "Number Theory":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Integer":  seq.put(sel, ntg.generateIntegers(terms)); break;
                                case "Natural":  seq.put(sel, ntg.generateNatural(terms));  break;
                                case "Odd":      seq.put(sel, ntg.generateOdd(terms));      break;
                                case "Even":     seq.put(sel, ntg.generateEven(terms));     break;
                                case "Whole":    seq.put(sel, ntg.generateWhole(terms));    break;
                                case "Negative": seq.put(sel, ntg.generateNegative(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Number Theory type: " + sel);
                            }
                        }
                        break;
                    case "Primes":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Prime":                seq.put(sel, png.generatePrime(terms));               break;
                                case "Semi Prime":           seq.put(sel, png.generateSemiPrime(terms));           break;
                                case "Emirp":                seq.put(sel, png.generateEmirp(terms));               break;
                                case "Additive Prime":       seq.put(sel, png.generateAdditivePrime(terms));       break;
                                case "Anagrammatic Prime":   seq.put(sel, png.generateAnagrammaticPrime(terms));   break;
                                case "Circular Prime":       seq.put(sel, png.generateCircularPrime(terms));       break;
                                case "Killer Prime":         seq.put(sel, png.generateKillerPrime(terms));         break;
                                case "Prime Palindrome":     seq.put(sel, png.generatePrimePalindrome(terms));     break;
                                case "Twin Primes":          seq.put(sel, png.generateTwinPrimes(terms));          break;
                                case "Cousin Primes":        seq.put(sel, png.generateCousinPrimes(terms));        break;
                                case "Sexy Primes":          seq.put(sel, png.generateSexyPrimes(terms));          break;
                                case "Sophie German Primes": seq.put(sel, png.generateSophieGermanPrimes(terms));  break;
                                default: throw new IllegalArgumentException("Unknown Prime type: " + sel);
                            }
                        }
                        break;
                    case "Patterns":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Fibonacci":                  seq.put(sel, pg.generateFibonacci(terms));                  break;
                                case "Tribonacci":                 seq.put(sel, pg.generateTribonacci(terms));                 break;
                                case "Tetranacci":                 seq.put(sel, pg.generateTetranacci(terms));                 break;
                                case "Pentanacci":                 seq.put(sel, pg.generatePentanacci(terms));                 break;
                                case "Hexanacci":                  seq.put(sel, pg.generateHexanacci(terms));                  break;
                                case "Heptanacci":                 seq.put(sel, pg.generateHeptanacci(terms));                 break;
                                case "Perrin":                     seq.put(sel, pg.generatePerrin(terms));                     break;
                                case "Lucas":                      seq.put(sel, pg.generateLucas(terms));                      break;
                                case "Padovan":                    seq.put(sel, pg.generatePadovan(terms));                    break;
                                case "Keith":                      seq.put(sel, pg.generateKeith(terms));                      break;
                                case "Palindrome":                 seq.put(sel, pg.generatePalindrome(terms));                 break;
                                case "Hypotenuse":                 seq.put(sel, pg.generateHypotenuse(terms));                 break;
                                case "Perfect Square":             seq.put(sel, pg.generatePerfectSquare(terms));             break;
                                case "Perfect Cube":               seq.put(sel, pg.generatePerfectCube(terms));               break;
                                case "Perfect Powers":             seq.put(sel, pg.generatePerfectPowers(terms));             break;
                                case "Catalan Numbers":            seq.put(sel, pg.generateCatalanNumbers(terms));            break;
                                case "Triangular Numbers":         seq.put(sel, pg.generateTriangularNumbers(terms));         break;
                                case "Pentagonal Numbers":         seq.put(sel, pg.generatePentagonalNumbers(terms));         break;
                                case "Standard Hexagonal Numbers": seq.put(sel, pg.generateStandardHexagonalNumbers(terms));  break;
                                case "Centered Hexagonal Numbers": seq.put(sel, pg.generateCenteredHexagonalNumbers(terms));  break;
                                case "Hexagonal Numbers":          seq.put(sel, pg.generateHexagonalNumbers(terms));          break;
                                case "Heptagonal Numbers":         seq.put(sel, pg.generateHeptagonalNumbers(terms));         break;
                                case "Octagonal Numbers":          seq.put(sel, pg.generateOctagonalNumbers(terms));          break;
                                case "Tetrahedral Numbers":        seq.put(sel, pg.generateTetrahedralNumbers(terms));        break;
                                case "Stella Octangula Numbers":   seq.put(sel, pg.generateStellaOctangulaNumbers(terms));    break;
                                default: throw new IllegalArgumentException("Unknown Pattern type: " + sel);
                            }
                        }
                        break;
                    case "Recreational":
                        for (String sel : selected) {
                            switch (sel) {
                                case "Armstrong":   seq.put(sel, rg.generateArmstrong(terms));   break;
                                case "Harshad":     seq.put(sel, rg.generateHarshad(terms));     break;
                                case "Disarium":    seq.put(sel, rg.generateDisarium(terms));    break;
                                case "Happy":       seq.put(sel, rg.generateHappy(terms));       break;
                                case "Sad":         seq.put(sel, rg.generateSad(terms));         break;
                                case "Duck":        seq.put(sel, rg.generateDuck(terms));        break;
                                case "Dudeney":     seq.put(sel, rg.generateDudeney(terms));     break;
                                case "Buzz":        seq.put(sel, rg.generateBuzz(terms));        break;
                                case "Spy":         seq.put(sel, rg.generateSpy(terms));         break;
                                case "Kaprekar":    seq.put(sel, rg.generateKaprekar(terms));    break;
                                case "Tech":        seq.put(sel, rg.generateTech(terms));        break;
                                case "Magic":       seq.put(sel, rg.generateMagic(terms));       break;
                                case "Smith":       seq.put(sel, rg.generateSmith(terms));       break;
                                case "Munchausen":  seq.put(sel, rg.generateMunchausen(terms));  break;
                                case "Repdigits":   seq.put(sel, rg.generateRepdigits(terms));   break;
                                case "Gapful":      seq.put(sel, rg.generateGapful(terms));      break;
                                case "Hungry":      seq.put(sel, rg.generateHungry(terms));      break;
                                case "Pronic":      seq.put(sel, rg.generatePronic(terms));      break;
                                case "Neon":        seq.put(sel, rg.generateNeon(terms));        break;
                                case "Automorphic": seq.put(sel, rg.generateAutomorphic(terms)); break;
                                default: throw new IllegalArgumentException("Unknown Recreational type: " + sel);
                            }
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown category: " + category);
                }

                result.put(category, seq);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(result)));
            out.flush();

        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.print(new Gson().toJson(ApiResponse.fail(iae.getMessage(), "INVALID_SELECTION")));
            }
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