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

@WebServlet("/NumberSeries/DisplayAll")
public class NumberSeriesDisplayAllController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			
			Gson gson = new Gson();
			FactorsGenerator fg = new FactorsGenerator();
			BaseNRepresentationGenerator bnrg = new BaseNRepresentationGenerator();
			FactorialGenerator facg = new FactorialGenerator();
			NumberTheoryGenerator ntg = new NumberTheoryGenerator();
			PatternsGenerator pg = new PatternsGenerator();
			PrimeNumbersGenerator png = new PrimeNumbersGenerator();
			RecreationalGenerator rg = new RecreationalGenerator();
			BaseNRepresentation bnr = new BaseNRepresentation();
			JsonBodyParser jsonParser = new JsonBodyParser();

			int terms;
			String requestJson = jsonParser.getRequestBodyAsString(request);

			if(requestJson == null) {
				terms = (int) session.getAttribute("terms");
			}
			else {
				RequestData requestData = gson.fromJson(requestJson, RequestData.class);
				terms = requestData.getTerms();
				session.setAttribute("terms", terms);
			}

			//category -> number -> map of terms
			LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> responseJson = new LinkedHashMap<>();
			LinkedHashMap<String, LinkedHashMap<Long, String>> generatedSequence = new LinkedHashMap<>();
			String category = "Base Representation";

			generatedSequence.put("Binary", bnrg.generateAllBinaryRepresentations(terms));
			generatedSequence.put("Octal", bnrg.generateAllOctalRepresentations(terms));
			generatedSequence.put("Hex", bnrg.generateAllHexRepresentations(terms));
			LinkedHashMap<Integer, String> allBaseRepresentations = bnr.findAllBases(terms);
			LinkedHashMap<Long, String> result = new LinkedHashMap<>();
			allBaseRepresentations.forEach((key, value) -> result.put((long)key, value));
			generatedSequence.put("All",  result);

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Factorials";

			generatedSequence.put("Factorial", facg.generateFactorial(terms));
			generatedSequence.put("Superfactorial", facg.generateSuperfactorial(terms));
			generatedSequence.put("Hyperfactorial", facg.generateHyperfactorial(terms));
			generatedSequence.put("Primorial", facg.generatePrimorial(terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Factors";

			generatedSequence.put("Perfect", fg.generatePerfect(terms));
			generatedSequence.put("Imperfect", fg.generateImperfect(terms));
			generatedSequence.put("Abundant", fg.generateAbundant(terms));
			generatedSequence.put("Inharmonious", fg.generateInharmonious(terms));
			generatedSequence.put("Blum", fg.generateBlum(terms));
			generatedSequence.put("Humble", fg.generateHumble(terms));
			generatedSequence.put("Abundant", fg.generateAbundant(terms));
			generatedSequence.put("Deficient", fg.generateDeficient(terms));
			generatedSequence.put("Amicable", fg.generateAmicable(terms));
			generatedSequence.put("Untouchable", fg.generateUntouchable(terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Number Theory";

			generatedSequence.put("Integers", ntg.generateIntegers(terms));
			generatedSequence.put("Natural", ntg.generateNatural(terms));
			generatedSequence.put("Odd", ntg.generateOdd(terms));
			generatedSequence.put("Even", ntg.generateEven(terms));
			generatedSequence.put("Whole", ntg.generateWhole(terms));
			generatedSequence.put("Negative", ntg.generateNegative(terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Primes";

			generatedSequence.put("Prime", png.generatePrime(terms));
			generatedSequence.put("Semi Prime", png.generateSemiPrime(terms));
			generatedSequence.put("Emirp", png.generateEmirp(terms));
			generatedSequence.put("Additive Prime", png.generateAdditivePrime(terms));
			generatedSequence.put("Anagrammatic Prime", png.generateAnagrammaticPrime(terms));
			generatedSequence.put("Circular Prime", png.generateCircularPrime(terms));
			generatedSequence.put("Killer Prime", png.generateKillerPrime(terms));
			generatedSequence.put("Prime Palindrome", png.generatePrimePalindrome(terms));
			generatedSequence.put("Twin Primes", png.generateTwinPrimes(terms));
			generatedSequence.put("Cousin Primes", png.generateCousinPrimes(terms));
			generatedSequence.put("Sexy Primes", png.generateSexyPrimes(terms));
			generatedSequence.put("Sophie German Primes", png.generateSophieGermanPrimes(terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Patterns";

			generatedSequence.put("Fibonacci", pg.generateFibonacci(terms));
			generatedSequence.put("Tribonacci", pg.generateTribonacci(terms));
			generatedSequence.put("Tetranacci", pg.generateTetranacci(terms));
			generatedSequence.put("Pentanacci", pg.generatePentanacci(terms));
			generatedSequence.put("Hexanacci", pg.generateHexanacci(terms));
			generatedSequence.put("Heptanacci", pg.generateHeptanacci(terms));
			generatedSequence.put("Perrin", pg.generatePerrin(terms));
			generatedSequence.put("Lucas", pg.generateLucas(terms));
			generatedSequence.put("Padovan", pg.generatePadovan(terms));
			generatedSequence.put("Keith", pg.generateKeith(terms));
			generatedSequence.put("Palindrome", pg.generatePalindrome(terms));
			generatedSequence.put("Hypotenuse", pg.generateHypotenuse(terms));
			generatedSequence.put("Perfect Square", pg.generatePerfectSquare(terms));
			generatedSequence.put("Perfect Cube", pg.generatePerfectCube(terms));
			generatedSequence.put("Perfect Powers", pg.generatePerfectPowers(terms));
			generatedSequence.put("Catalan Numbers", pg.generateCatalanNumbers(terms));
			generatedSequence.put("Triangular Numbers", pg.generateTriangularNumbers(terms));
			generatedSequence.put("Pentagonal Numbers", pg.generatePentagonalNumbers(terms));
			generatedSequence.put("Standard Hexagonal Numbers", pg.generateStandardHexagonalNumbers(terms));
			generatedSequence.put("Centered Hexagonal Numbers", pg.generateCenteredHexagonalNumbers(terms));
			generatedSequence.put("Hexagonal Numbers", pg.generateHexagonalNumbers(terms));
			generatedSequence.put("Heptagonal Numbers", pg.generateHeptagonalNumbers(terms));
			generatedSequence.put("Octagonal Numbers", pg.generateOctagonalNumbers(terms));
			generatedSequence.put("Tetrahedral Numbers", pg.generateTetrahedralNumbers(terms));
			generatedSequence.put("Stella Octangula Numbers", pg.generateStellaOctangulaNumbers(terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Recreational";

			generatedSequence.put("Armstrong", rg.generateArmstrong(terms));
			generatedSequence.put("Harshad", rg.generateHarshad(terms));
			generatedSequence.put("Disarium", rg.generateDisarium(terms));
			generatedSequence.put("Happy", rg.generateHappy(terms));
			generatedSequence.put("Sad", rg.generateSad(terms));
			generatedSequence.put("Duck", rg.generateDuck(terms));
			generatedSequence.put("Dudeney", rg.generateDudeney(terms));
			generatedSequence.put("Buzz", rg.generateBuzz(terms));
			generatedSequence.put("Spy", rg.generateSpy(terms));
			generatedSequence.put("Kaprekar", rg.generateKaprekar(terms));
			generatedSequence.put("Tech", rg.generateTech(terms));
			generatedSequence.put("Magic", rg.generateMagic(terms));
			generatedSequence.put("Smith", rg.generateSmith(terms));
			generatedSequence.put("Munchausen", rg.generateMunchausen(terms));
			generatedSequence.put("Repdigits", rg.generateRepdigits(terms));
			generatedSequence.put("Gapful", rg.generateGapful(terms));
			generatedSequence.put("Hungry", rg.generateHungry(terms));
			generatedSequence.put("Pronic", rg.generatePronic(terms));
			generatedSequence.put("Neon", rg.generateNeon(terms));
			generatedSequence.put("Automorphic", rg.generateAutomorphic(terms));

			responseJson.put(category, generatedSequence);

			String responseJsonString = gson.toJson(responseJson);
			try (PrintWriter out = response.getWriter()){
				out.print(responseJsonString);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
