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

@WebServlet("/NumberSeries/DisplaySelected")
public class NumberSeriesDisplaySelectedController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			Gson gson = new Gson();
			HashMap<String, ArrayList<String>> choiceMap = new HashMap<>();
			int terms;
			
			FactorsGenerator fg = new FactorsGenerator();
			BaseNRepresentationGenerator bnrg = new BaseNRepresentationGenerator();
			FactorialGenerator facg = new FactorialGenerator();
			NumberTheoryGenerator ntg = new NumberTheoryGenerator();
			PatternsGenerator pg = new PatternsGenerator();
			PrimeNumbersGenerator png = new PrimeNumbersGenerator();
			RecreationalGenerator rg = new RecreationalGenerator();
			BaseNRepresentation bnr = new BaseNRepresentation();
			JsonBodyParser jsonParser = new JsonBodyParser();

			String requestJson = jsonParser.getRequestBodyAsString(request);
			
			if(requestJson == null) {
				choiceMap = (HashMap<String, ArrayList<String>>) session.getAttribute("choiceMap");
				terms = (int) session.getAttribute("terms");
			}
			else {
				RequestData requestData = gson.fromJson(requestJson, RequestData.class);
				terms = requestData.getTerms();
				choiceMap = requestData.getChoiceMap();
				session.setAttribute("choiceMap", choiceMap);
				session.setAttribute("terms", terms);
			}

			//category -> number -> map of terms
			LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Long, String>>> responseJson = new LinkedHashMap<>();
			LinkedHashMap<String, LinkedHashMap<Long, String>> generatedSequence = new LinkedHashMap<>();
			
			for(Map.Entry<String, ArrayList<String>> entry : choiceMap.entrySet()) {
				String category = entry.getKey();
				ArrayList<String> selected = entry.getValue();
				
				if((category == null && selected != null)||(category != null && selected == null))
					throw new Exception("Invalid request");
				
				if(category.equals("Base Representation")) {
					for(String sel : selected) {
						if(sel.equals("Binary")) {
							generatedSequence.put(sel, bnrg.generateAllBinaryRepresentations(terms));
						}
						else if(sel.equals("Octal")) {
							generatedSequence.put(sel, bnrg.generateAllOctalRepresentations(terms));
						}
						else if(sel.equals("Hex")) {
							generatedSequence.put(sel, bnrg.generateAllHexRepresentations(terms));
						}
						else if(sel.equals("All")) {
							LinkedHashMap<Integer, String> allBaseRepresentations = bnr.findAllBases(terms);
							LinkedHashMap<Long, String> result = new LinkedHashMap<>();
							allBaseRepresentations.forEach((key, value) -> result.put((long)key, value));
							generatedSequence.put(sel,  result);
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Factorials")) {
					for(String sel : selected) {
						if(sel.equals("Factorial")) {
							generatedSequence.put(sel, facg.generateFactorial(terms));
						}
						else if(sel.equals("Superfactorial")) {
							generatedSequence.put(sel, facg.generateSuperfactorial(terms));
						}
						else if(sel.equals("Hyperfactorial")) {
							generatedSequence.put(sel, facg.generateHyperfactorial(terms));
						}
						else if(sel.equals("Primorial")) {
							generatedSequence.put(sel, facg.generatePrimorial(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Factors")) {
					for(String sel : selected) {
						if(sel.equals("Perfect")) {
							generatedSequence.put(sel, fg.generatePerfect(terms));
						}
						else if(sel.equals("Imperfect")) {
							generatedSequence.put(sel, fg.generateImperfect(terms));
						}
						else if(sel.equals("Arithmetic")) {
							generatedSequence.put(sel, fg.generateAbundant(terms));
						}
						else if(sel.equals("Inharmonious")) {
							generatedSequence.put(sel, fg.generateInharmonious(terms));
						}
						else if(sel.equals("Blum")) {
							generatedSequence.put(sel, fg.generateBlum(terms));
						}
						else if(sel.equals("Humble")) {
							generatedSequence.put(sel, fg.generateHumble(terms));
						}
						else if(sel.equals("Abundant")) {
							generatedSequence.put(sel, fg.generateAbundant(terms));
						}
						else if(sel.equals("Deficient")) {
							generatedSequence.put(sel, fg.generateDeficient(terms));
						}
						else if(sel.equals("Amicable")) {
							generatedSequence.put(sel, fg.generateAmicable(terms));
						}
						else if(sel.equals("Untouchable")) {
							generatedSequence.put(sel, fg.generateUntouchable(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Number Theory")) {
					for(String sel : selected) {
						if(sel.equals("Integer")) {
							generatedSequence.put(sel, ntg.generateIntegers(terms));
						}
						else if(sel.equals("Natural")) {
							generatedSequence.put(sel, ntg.generateNatural(terms));
						}
						else if(sel.equals("Odd")) {
							generatedSequence.put(sel, ntg.generateOdd(terms));
						}
						else if(sel.equals("Even")) {
							generatedSequence.put(sel, ntg.generateEven(terms));
						}
						else if(sel.equals("Whole")) {
							generatedSequence.put(sel, ntg.generateWhole(terms));
						}
						else if(sel.equals("Negative")) {
							generatedSequence.put(sel, ntg.generateNegative(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Primes")) {
					for(String sel : selected) {
						if(sel.equals("Prime")) {
							generatedSequence.put(sel, png.generatePrime(terms));
						}
						else if(sel.equals("Semi Prime")) {
							generatedSequence.put(sel, png.generateSemiPrime(terms));
						}
						else if(sel.equals("Emirp")) {
							generatedSequence.put(sel, png.generateEmirp(terms));
						}
						else if(sel.equals("Additive Prime")) {
							generatedSequence.put(sel, png.generateAdditivePrime(terms));
						}
						else if(sel.equals("Anagrammatic Prime")) {
							generatedSequence.put(sel, png.generateAnagrammaticPrime(terms));
						}
						else if(sel.equals("Circular Prime")) {
							generatedSequence.put(sel, png.generateCircularPrime(terms));
						}
						else if(sel.equals("Killer Prime")) {
							generatedSequence.put(sel, png.generateKillerPrime(terms));
						}
						else if(sel.equals("Prime Palindrome")) {
							generatedSequence.put(sel, png.generatePrimePalindrome(terms));
						}
						else if(sel.equals("Twin Primes")) {
							generatedSequence.put(sel, png.generateTwinPrimes(terms));
						}
						else if(sel.equals("Cousin Primes")) {
							generatedSequence.put(sel, png.generateCousinPrimes(terms));
						}
						else if(sel.equals("Sexy Primes")) {
							generatedSequence.put(sel, png.generateSexyPrimes(terms));
						}
						else if(sel.equals("Sophie German Primes")) {
							generatedSequence.put(sel, png.generateSophieGermanPrimes(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Patterns")) {
					for(String sel : selected) {
						if(sel.equals("Fibonacci")) {
							generatedSequence.put(sel, pg.generateFibonacci(terms));
						}
						else if(sel.equals("Tribonacci")) {
							generatedSequence.put(sel, pg.generateTribonacci(terms));
						}
						else if(sel.equals("Tetranacci")) {
							generatedSequence.put(sel, pg.generateTetranacci(terms));
						}
						else if(sel.equals("Pentanacci")) {
							generatedSequence.put(sel, pg.generatePentanacci(terms));
						}
						else if(sel.equals("Hexanacci")) {
							generatedSequence.put(sel, pg.generateHexanacci(terms));
						}
						else if(sel.equals("Heptanacci")) {
							generatedSequence.put(sel, pg.generateHeptanacci(terms));
						}
						else if(sel.equals("Perrin")) {
							generatedSequence.put(sel, pg.generatePerrin(terms));
						}
						else if(sel.equals("Lucas")) {
							generatedSequence.put(sel, pg.generateLucas(terms));
						}
						else if(sel.equals("Padovan")) {
							generatedSequence.put(sel, pg.generatePadovan(terms));
						}
						else if(sel.equals("Keith")) {
							generatedSequence.put(sel, pg.generateKeith(terms));
						}
						else if(sel.equals("Palindrome")) {
							generatedSequence.put(sel, pg.generatePalindrome(terms));
						}
						else if(sel.equals("Hypotenuse")) {
							generatedSequence.put(sel, pg.generateHypotenuse(terms));
						}
						else if(sel.equals("Perfect Square")) {
							generatedSequence.put(sel, pg.generatePerfectSquare(terms));
						}
						else if(sel.equals("Pefect Cube")) {
							generatedSequence.put(sel, pg.generatePerfectCube(terms));
						}
						else if(sel.equals("Perfect Powers")) {
							generatedSequence.put(sel, pg.generatePerfectPowers(terms));
						}
						else if(sel.equals("Catalan Numbers")) {
							generatedSequence.put(sel, pg.generateCatalanNumbers(terms));
						}
						else if(sel.equals("Triangular Numbers")) {
							generatedSequence.put(sel, pg.generateTriangularNumbers(terms));
						}
						else if(sel.equals("Pentagonal Numbers")) {
							generatedSequence.put(sel, pg.generatePentagonalNumbers(terms));
						}
						else if(sel.equals("Standard Hexagonal Numbers")) {
							generatedSequence.put(sel, pg.generateStandardHexagonalNumbers(terms));
						}
						else if(sel.equals("Centered Hexagonal Numebrs")) {
							generatedSequence.put(sel, pg.generateCenteredHexagonalNumbers(terms));
						}
						else if(sel.equals("Hexagonal Numbers")) {
							generatedSequence.put(sel, pg.generateHexagonalNumbers(terms));
						}
						else if(sel.equals("Heptagonal Numbers")) {
							generatedSequence.put(sel, pg.generateHeptagonalNumbers(terms));
						}
						else if(sel.equals("Octagonal Numbers")) {
							generatedSequence.put(sel, pg.generateOctagonalNumbers(terms));
						}
						else if(sel.equals("Tetrahedral Numbers")) {
							generatedSequence.put(sel, pg.generateTetrahedralNumbers(terms));
						}
						else if(sel.equals("Stella Octangula Numbers")) {
							generatedSequence.put(sel, pg.generateStellaOctangulaNumbers(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);
					generatedSequence = new LinkedHashMap<>();
				}
				else if(category.equals("Recreational")) {
					for(String sel : selected) {
						if(sel.equals("Armstrong")) {
							generatedSequence.put(sel, rg.generateArmstrong(terms));
						}
						else if(sel.equals("Harshad")) {
							generatedSequence.put(sel, rg.generateHarshad(terms));
						}
						else if(sel.equals("Disarium")) {
							generatedSequence.put(sel, rg.generateDisarium(terms));
						}
						else if(sel.equals("Happy")) {
							generatedSequence.put(sel, rg.generateHappy(terms));
						}
						else if(sel.equals("Sad")) {
							generatedSequence.put(sel, rg.generateSad(terms));
						}
						else if(sel.equals("Duck")) {
							generatedSequence.put(sel, rg.generateDuck(terms));
						}
						else if(sel.equals("Dudeney")) {
							generatedSequence.put(sel, rg.generateDudeney(terms));
						}
						else if(sel.equals("Buzz")) {
							generatedSequence.put(sel, rg.generateBuzz(terms));
						}
						else if(sel.equals("Spy")) {
							generatedSequence.put(sel, rg.generateSpy(terms));
						}
						else if(sel.equals("Kaprekar")) {
							generatedSequence.put(sel, rg.generateKaprekar(terms));
						}
						else if(sel.equals("Tech")) {
							generatedSequence.put(sel, rg.generateTech(terms));
						}
						else if(sel.equals("Magic")) {
							generatedSequence.put(sel, rg.generateMagic(terms));
						}
						else if(sel.equals("Smith")) {
							generatedSequence.put(sel, rg.generateSmith(terms));
						}
						else if(sel.equals("Munchausen")) {
							generatedSequence.put(sel, rg.generateMunchausen(terms));
						}
						else if(sel.equals("Repdigits")) {
							generatedSequence.put(sel, rg.generateRepdigits(terms));
						}
						else if(sel.equals("Gapful")) {
							generatedSequence.put(sel, rg.generateGapful(terms));
						}
						else if(sel.equals("Hungry")) {
							generatedSequence.put(sel, rg.generateHungry(terms));
						}
						else if(sel.equals("Pronic")) {
							generatedSequence.put(sel, rg.generatePronic(terms));
						}
						else if(sel.equals("Neon")) {
							generatedSequence.put(sel, rg.generateNeon(terms));
						}
						else if(sel.equals("Automorphic")) {
							generatedSequence.put(sel, rg.generateAutomorphic(terms));
						}
						else
							throw new Exception();
					}
					responseJson.put(category, generatedSequence);

				}
				else
					throw new Exception();
			}
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