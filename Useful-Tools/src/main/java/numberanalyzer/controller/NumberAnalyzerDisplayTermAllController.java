package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
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

@WebServlet("/NumberAnalyzer/DisplayTerm/All")
public class NumberAnalyzerDisplayTermAllController extends HttpServlet {

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
				session.setAttribute("(long)terms", terms);
			}
			LinkedHashMap<String, LinkedHashMap<String, String>> responseJson = new LinkedHashMap<>();
			LinkedHashMap<String, String> generatedSequence = new LinkedHashMap<>();
			String category = "Base Representations";
			String sel;
			
			sel = "Binary";
			generatedSequence.put(sel, bnrg.generateAllBinaryRepresentations(terms).get((long)terms));
			sel = "Octal";
			generatedSequence.put(sel, bnrg.generateAllOctalRepresentations(terms).get((long)terms));
			sel = "Hex";
			generatedSequence.put(sel, bnrg.generateAllHexRepresentations(terms).get((long)terms));
			sel = "All";
			LinkedHashMap<Integer, String> allBaseRepresentations = bnr.findAllBases((long)terms);
			for(Map.Entry<Integer, String> entrySet : allBaseRepresentations.entrySet()) {
				generatedSequence.put(String.valueOf(entrySet.getKey()), entrySet.getValue());
			}
			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();

			sel = "Factorial";
			generatedSequence.put(sel, facg.generateFactorial(terms).get((long)terms));
			sel = "Superfactorial";
			generatedSequence.put(sel, facg.generateSuperfactorial(terms).get((long)terms));
			sel = "Hyperfactorial";
			generatedSequence.put(sel, facg.generateHyperfactorial(terms).get((long)terms));
			sel = "Primorial";
			generatedSequence.put(sel, facg.generatePrimorial(terms).get((long)terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Factors";
			sel = "Perfect";
			generatedSequence.put(sel, fg.generatePerfect(terms).get((long)terms));
			sel = "Imperfect";
			generatedSequence.put(sel, fg.generateImperfect(terms).get((long)terms));
			sel = "Arithmetic";
			generatedSequence.put(sel, fg.generateAbundant(terms).get((long)terms));
			sel = "Inharmonious";
			generatedSequence.put(sel, fg.generateInharmonious(terms).get((long)terms));
			sel = "Blum";
			generatedSequence.put(sel, fg.generateBlum(terms).get((long)terms));
			sel = "Humble";
			generatedSequence.put(sel, fg.generateHumble(terms).get((long)terms));
			sel = "Abundant";
			generatedSequence.put(sel, fg.generateAbundant(terms).get((long)terms));
			sel = "Deficient";
			generatedSequence.put(sel, fg.generateDeficient(terms).get((long)terms));
			sel = "Amicable";
			generatedSequence.put(sel, fg.generateAmicable(terms).get((long)terms));
			sel = "Untouchable";
			generatedSequence.put(sel, fg.generateUntouchable(terms).get((long)terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Number Theory";
			sel = "Integer";
			generatedSequence.put(sel, ntg.generateIntegers(terms).get((long)terms));
			sel = "Natural";
			generatedSequence.put(sel, ntg.generateNatural(terms).get((long)terms));
			sel = "Odd";
			generatedSequence.put(sel, ntg.generateOdd(terms).get((long)terms));
			sel = "Even";
			generatedSequence.put(sel, ntg.generateEven(terms).get((long)terms));
			sel = "Whole";
			generatedSequence.put(sel, ntg.generateWhole(terms).get((long)terms));
			sel = "Negative";
			generatedSequence.put(sel, ntg.generateNegative(terms).get((long)terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();
			category = "Primes";
			sel = "Prime";
			generatedSequence.put(sel, png.generatePrime(terms).get((long)terms));
			sel = "Semi Prime";
			generatedSequence.put(sel, png.generateSemiPrime(terms).get((long)terms));
			sel = "Emirp";
			generatedSequence.put(sel, png.generateEmirp(terms).get((long)terms));
			sel = "Additive Prime";
			generatedSequence.put(sel, png.generateAdditivePrime(terms).get((long)terms));
			sel = "Anagrammatic Prime";
			generatedSequence.put(sel, png.generateAnagrammaticPrime(terms).get((long)terms));
			sel = "Circular Prime";
			generatedSequence.put(sel, png.generateCircularPrime(terms).get((long)terms));
			sel = "Killer Prime";
			generatedSequence.put(sel, png.generateKillerPrime(terms).get((long)terms));
			sel = "Prime Palindrome";
			generatedSequence.put(sel, png.generatePrimePalindrome(terms).get((long)terms));
			sel = "Twin Primes";
			generatedSequence.put(sel, png.generateTwinPrimes(terms).get((long)terms));
			sel = "Cousin Primes";
			generatedSequence.put(sel, png.generateCousinPrimes(terms).get((long)terms));
			sel = "Sexy Primes";
			generatedSequence.put(sel, png.generateSexyPrimes(terms).get((long)terms));
			sel = "Sophie German Primes";
			generatedSequence.put(sel, png.generateSophieGermanPrimes(terms).get((long)terms));

			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();

			category = "Patterns";

			sel = "Fibonacci";
			generatedSequence.put(sel, pg.generateFibonacci(terms).get((long)terms));
			sel = "Tribonacci";
			generatedSequence.put(sel, pg.generateTribonacci(terms).get((long)terms));
			sel = "Tetranacci";
			generatedSequence.put(sel, pg.generateTetranacci(terms).get((long)terms));
			sel = "Pentanacci";
			generatedSequence.put(sel, pg.generatePentanacci(terms).get((long)terms));
			sel = "Hexanacci";
			generatedSequence.put(sel, pg.generateHexanacci(terms).get((long)terms));
			sel = "Heptanacci";
			generatedSequence.put(sel, pg.generateHeptanacci(terms).get((long)terms));
			sel = "Perrin";
			generatedSequence.put(sel, pg.generatePerrin(terms).get((long)terms));
			sel = "Lucas";
			generatedSequence.put(sel, pg.generateLucas(terms).get((long)terms));
			sel = "Padovan";
			generatedSequence.put(sel, pg.generatePadovan(terms).get((long)terms));
			sel = "Keith";
			generatedSequence.put(sel, pg.generateKeith(terms).get((long)terms));
			sel = "Palindrome";
			generatedSequence.put(sel, pg.generatePalindrome(terms).get((long)terms));
			sel = "Hypotenuse";
			generatedSequence.put(sel, pg.generateHypotenuse(terms).get((long)terms));
			sel = "Perfect Square";
			generatedSequence.put(sel, pg.generatePerfectSquare(terms).get((long)terms));
			sel = "Pefect Cube";
			generatedSequence.put(sel, pg.generatePerfectCube(terms).get((long)terms));
			sel = "Perfect Powers";
			generatedSequence.put(sel, pg.generatePerfectPowers(terms).get((long)terms));
			sel = "Catalan Numbers";
			generatedSequence.put(sel, pg.generateCatalanNumbers(terms).get((long)terms));
			sel = "Triangular Numbers";
			generatedSequence.put(sel, pg.generateTriangularNumbers(terms).get((long)terms));
			sel = "Pentagonal Numbers";
			generatedSequence.put(sel, pg.generatePentagonalNumbers(terms).get((long)terms));
			sel = "Standard Hexagonal Numbers";
			generatedSequence.put(sel, pg.generateStandardHexagonalNumbers(terms).get((long)terms));
			sel = "Centered Hexagonal Numebrs";
			generatedSequence.put(sel, pg.generateCenteredHexagonalNumbers(terms).get((long)terms));
			sel = "Hexagonal Numbers";
			generatedSequence.put(sel, pg.generateHexagonalNumbers(terms).get((long)terms));
			sel = "Heptagonal Numbers";
			generatedSequence.put(sel, pg.generateHeptagonalNumbers(terms).get((long)terms));
			sel = "Octagonal Numbers";
			generatedSequence.put(sel, pg.generateOctagonalNumbers(terms).get((long)terms));
			sel = "Tetrahedral Numbers";
			generatedSequence.put(sel, pg.generateTetrahedralNumbers(terms).get((long)terms));
			sel = "Stella Octangula Numbers";
			generatedSequence.put(sel, pg.generateStellaOctangulaNumbers(terms).get((long)terms));


			responseJson.put(category, generatedSequence);
			generatedSequence = new LinkedHashMap<>();

			category = "Recreational";

			sel = "Armstrong";
			generatedSequence.put(sel, rg.generateArmstrong(terms).get((long)terms));
			sel = "Harshad";
			generatedSequence.put(sel, rg.generateHarshad(terms).get((long)terms));
			sel = "Disarium";
			generatedSequence.put(sel, rg.generateDisarium(terms).get((long)terms));
			sel = "Happy";
			generatedSequence.put(sel, rg.generateHappy(terms).get((long)terms));
			sel = "Sad";
			generatedSequence.put(sel, rg.generateSad(terms).get((long)terms));
			sel = "Duck";
			generatedSequence.put(sel, rg.generateDuck(terms).get((long)terms));
			sel = "Dudeney";
			generatedSequence.put(sel, rg.generateDudeney(terms).get((long)terms));
			sel = "Buzz";
			generatedSequence.put(sel, rg.generateBuzz(terms).get((long)terms));
			sel = "Spy";
			generatedSequence.put(sel, rg.generateSpy(terms).get((long)terms));
			sel = "Kaprekar";
			generatedSequence.put(sel, rg.generateKaprekar(terms).get((long)terms));
			sel = "Tech";
			generatedSequence.put(sel, rg.generateTech(terms).get((long)terms));
			sel = "Magic";
			generatedSequence.put(sel, rg.generateMagic(terms).get((long)terms));
			sel = "Smith";
			generatedSequence.put(sel, rg.generateSmith(terms).get((long)terms));
			sel = "Munchausen";
			generatedSequence.put(sel, rg.generateMunchausen(terms).get((long)terms));
			sel = "Repdigits";
			generatedSequence.put(sel, rg.generateRepdigits(terms).get((long)terms));
			sel = "Gapful";
			generatedSequence.put(sel, rg.generateGapful(terms).get((long)terms));
			sel = "Hungry";
			generatedSequence.put(sel, rg.generateHungry(terms).get((long)terms));
			sel = "Pronic";
			generatedSequence.put(sel, rg.generatePronic(terms).get((long)terms));
			sel = "Neon";
			generatedSequence.put(sel, rg.generateNeon(terms).get((long)terms));
			sel = "Automorphic";
			generatedSequence.put(sel, rg.generateAutomorphic(terms).get((long)terms));

			responseJson.put(category, generatedSequence);
			
			String responseJsonString = gson.toJson(responseJson);
			try (PrintWriter out = response.getWriter()){
				out.print(responseJsonString);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			String responseJsonString = "Exception : "+e.getMessage();
			try (PrintWriter out = response.getWriter()){
				out.print(responseJsonString);
				out.flush();
			}
		}
	}
}
