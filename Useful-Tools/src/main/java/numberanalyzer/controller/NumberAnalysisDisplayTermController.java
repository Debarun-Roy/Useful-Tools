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

@WebServlet("/NumberAnalysis/DisplayTerm/Selected")
public class NumberAnalysisDisplayTermController extends HttpServlet {

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

			FactorsGenerator fg = new FactorsGenerator();
			BaseNRepresentationGenerator bnrg = new BaseNRepresentationGenerator();
			FactorialGenerator facg = new FactorialGenerator();
			NumberTheoryGenerator ntg = new NumberTheoryGenerator();
			PatternsGenerator pg = new PatternsGenerator();
			PrimeNumbersGenerator png = new PrimeNumbersGenerator();
			RecreationalGenerator rg = new RecreationalGenerator();
			BaseNRepresentation bnr = new BaseNRepresentation();
			JsonBodyParser jsonParser = new JsonBodyParser();

			HashMap<String, ArrayList<String>> choiceMap = new HashMap<>();
			int terms;
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
				session.setAttribute("(long)terms", terms);
			}
			LinkedHashMap<String, LinkedHashMap<String, String>> responseJsonSelected = new LinkedHashMap<>();
			LinkedHashMap<String, String> generatedSequenceSelected = new LinkedHashMap<>();
			
			for(Map.Entry<String, ArrayList<String>> entry : choiceMap.entrySet()) {
				String category = entry.getKey();
				ArrayList<String> selected = entry.getValue();

				if((category == null && selected != null)||(category != null && selected == null))
					throw new Exception("Invalid request");

				if(category.equals("Base Representation")) {
					for(String sel : selected) {
						if(sel.equals("Binary")) {
							generatedSequenceSelected.put(sel, bnrg.generateAllBinaryRepresentations(terms).get((long)terms));
						}
						else if(sel.equals("Octal")) {
							generatedSequenceSelected.put(sel, bnrg.generateAllOctalRepresentations(terms).get((long)terms));
						}
						else if(sel.equals("Hex")) {
							generatedSequenceSelected.put(sel, bnrg.generateAllHexRepresentations(terms).get((long)terms));
						}
						else if(sel.equals("All")) {
							LinkedHashMap<Integer, String> allBaseRepresentations = bnr.findAllBases((long)terms);
							for(Map.Entry<Integer, String> entrySet : allBaseRepresentations.entrySet()) {
								generatedSequenceSelected.put(String.valueOf(entrySet.getKey()), entrySet.getValue());
							}
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Factorials")) {
					for(String sel : selected) {
						if(sel.equals("Factorial")) {
							generatedSequenceSelected.put(sel, facg.generateFactorial(terms).get((long)terms));
						}
						else if(sel.equals("Superfactorial")) {
							generatedSequenceSelected.put(sel, facg.generateSuperfactorial(terms).get((long)terms));
						}
						else if(sel.equals("Hyperfactorial")) {
							generatedSequenceSelected.put(sel, facg.generateHyperfactorial(terms).get((long)terms));
						}
						else if(sel.equals("Primorial")) {
							generatedSequenceSelected.put(sel, facg.generatePrimorial(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Factors")) {
					for(String sel : selected) {
						if(sel.equals("Perfect")) {
							generatedSequenceSelected.put(sel, fg.generatePerfect(terms).get((long)terms));
						}
						else if(sel.equals("Imperfect")) {
							generatedSequenceSelected.put(sel, fg.generateImperfect(terms).get((long)terms));
						}
						else if(sel.equals("Arithmetic")) {
							generatedSequenceSelected.put(sel, fg.generateAbundant(terms).get((long)terms));
						}
						else if(sel.equals("Inharmonious")) {
							generatedSequenceSelected.put(sel, fg.generateInharmonious(terms).get((long)terms));
						}
						else if(sel.equals("Blum")) {
							generatedSequenceSelected.put(sel, fg.generateBlum(terms).get((long)terms));
						}
						else if(sel.equals("Humble")) {
							generatedSequenceSelected.put(sel, fg.generateHumble(terms).get((long)terms));
						}
						else if(sel.equals("Abundant")) {
							generatedSequenceSelected.put(sel, fg.generateAbundant(terms).get((long)terms));
						}
						else if(sel.equals("Deficient")) {
							generatedSequenceSelected.put(sel, fg.generateDeficient(terms).get((long)terms));
						}
						else if(sel.equals("Amicable")) {
							generatedSequenceSelected.put(sel, fg.generateAmicable(terms).get((long)terms));
						}
						else if(sel.equals("Untouchable")) {
							generatedSequenceSelected.put(sel, fg.generateUntouchable(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Number Theory")) {
					for(String sel : selected) {
						if(sel.equals("Integer")) {
							generatedSequenceSelected.put(sel, ntg.generateIntegers(terms).get((long)terms));
						}
						else if(sel.equals("Natural")) {
							generatedSequenceSelected.put(sel, ntg.generateNatural(terms).get((long)terms));
						}
						else if(sel.equals("Odd")) {
							generatedSequenceSelected.put(sel, ntg.generateOdd(terms).get((long)terms));
						}
						else if(sel.equals("Even")) {
							generatedSequenceSelected.put(sel, ntg.generateEven(terms).get((long)terms));
						}
						else if(sel.equals("Whole")) {
							generatedSequenceSelected.put(sel, ntg.generateWhole(terms).get((long)terms));
						}
						else if(sel.equals("Negative")) {
							generatedSequenceSelected.put(sel, ntg.generateNegative(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Primes")) {
					for(String sel : selected) {
						if(sel.equals("Prime")) {
							generatedSequenceSelected.put(sel, png.generatePrime(terms).get((long)terms));
						}
						else if(sel.equals("Semi Prime")) {
							generatedSequenceSelected.put(sel, png.generateSemiPrime(terms).get((long)terms));
						}
						else if(sel.equals("Emirp")) {
							generatedSequenceSelected.put(sel, png.generateEmirp(terms).get((long)terms));
						}
						else if(sel.equals("Additive Prime")) {
							generatedSequenceSelected.put(sel, png.generateAdditivePrime(terms).get((long)terms));
						}
						else if(sel.equals("Anagrammatic Prime")) {
							generatedSequenceSelected.put(sel, png.generateAnagrammaticPrime(terms).get((long)terms));
						}
						else if(sel.equals("Circular Prime")) {
							generatedSequenceSelected.put(sel, png.generateCircularPrime(terms).get((long)terms));
						}
						else if(sel.equals("Killer Prime")) {
							generatedSequenceSelected.put(sel, png.generateKillerPrime(terms).get((long)terms));
						}
						else if(sel.equals("Prime Palindrome")) {
							generatedSequenceSelected.put(sel, png.generatePrimePalindrome(terms).get((long)terms));
						}
						else if(sel.equals("Twin Primes")) {
							generatedSequenceSelected.put(sel, png.generateTwinPrimes(terms).get((long)terms));
						}
						else if(sel.equals("Cousin Primes")) {
							generatedSequenceSelected.put(sel, png.generateCousinPrimes(terms).get((long)terms));
						}
						else if(sel.equals("Sexy Primes")) {
							generatedSequenceSelected.put(sel, png.generateSexyPrimes(terms).get((long)terms));
						}
						else if(sel.equals("Sophie German Primes")) {
							generatedSequenceSelected.put(sel, png.generateSophieGermanPrimes(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Patterns")) {
					for(String sel : selected) {
						if(sel.equals("Fibonacci")) {
							generatedSequenceSelected.put(sel, pg.generateFibonacci(terms).get((long)terms));
						}
						else if(sel.equals("Tribonacci")) {
							generatedSequenceSelected.put(sel, pg.generateTribonacci(terms).get((long)terms));
						}
						else if(sel.equals("Tetranacci")) {
							generatedSequenceSelected.put(sel, pg.generateTetranacci(terms).get((long)terms));
						}
						else if(sel.equals("Pentanacci")) {
							generatedSequenceSelected.put(sel, pg.generatePentanacci(terms).get((long)terms));
						}
						else if(sel.equals("Hexanacci")) {
							generatedSequenceSelected.put(sel, pg.generateHexanacci(terms).get((long)terms));
						}
						else if(sel.equals("Heptanacci")) {
							generatedSequenceSelected.put(sel, pg.generateHeptanacci(terms).get((long)terms));
						}
						else if(sel.equals("Perrin")) {
							generatedSequenceSelected.put(sel, pg.generatePerrin(terms).get((long)terms));
						}
						else if(sel.equals("Lucas")) {
							generatedSequenceSelected.put(sel, pg.generateLucas(terms).get((long)terms));
						}
						else if(sel.equals("Padovan")) {
							generatedSequenceSelected.put(sel, pg.generatePadovan(terms).get((long)terms));
						}
						else if(sel.equals("Keith")) {
							generatedSequenceSelected.put(sel, pg.generateKeith(terms).get((long)terms));
						}
						else if(sel.equals("Palindrome")) {
							generatedSequenceSelected.put(sel, pg.generatePalindrome(terms).get((long)terms));
						}
						else if(sel.equals("Hypotenuse")) {
							generatedSequenceSelected.put(sel, pg.generateHypotenuse(terms).get((long)terms));
						}
						else if(sel.equals("Perfect Square")) {
							generatedSequenceSelected.put(sel, pg.generatePerfectSquare(terms).get((long)terms));
						}
						else if(sel.equals("Pefect Cube")) {
							generatedSequenceSelected.put(sel, pg.generatePerfectCube(terms).get((long)terms));
						}
						else if(sel.equals("Perfect Powers")) {
							generatedSequenceSelected.put(sel, pg.generatePerfectPowers(terms).get((long)terms));
						}
						else if(sel.equals("Catalan Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateCatalanNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Triangular Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateTriangularNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Pentagonal Numbers")) {
							generatedSequenceSelected.put(sel, pg.generatePentagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Standard Hexagonal Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateStandardHexagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Centered Hexagonal Numebrs")) {
							generatedSequenceSelected.put(sel, pg.generateCenteredHexagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Hexagonal Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateHexagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Heptagonal Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateHeptagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Octagonal Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateOctagonalNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Tetrahedral Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateTetrahedralNumbers(terms).get((long)terms));
						}
						else if(sel.equals("Stella Octangula Numbers")) {
							generatedSequenceSelected.put(sel, pg.generateStellaOctangulaNumbers(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
					generatedSequenceSelected = new LinkedHashMap<>();
				}
				else if(category.equals("Recreational")) {
					for(String sel : selected) {
						if(sel.equals("Armstrong")) {
							generatedSequenceSelected.put(sel, rg.generateArmstrong(terms).get((long)terms));
						}
						else if(sel.equals("Harshad")) {
							generatedSequenceSelected.put(sel, rg.generateHarshad(terms).get((long)terms));
						}
						else if(sel.equals("Disarium")) {
							generatedSequenceSelected.put(sel, rg.generateDisarium(terms).get((long)terms));
						}
						else if(sel.equals("Happy")) {
							generatedSequenceSelected.put(sel, rg.generateHappy(terms).get((long)terms));
						}
						else if(sel.equals("Sad")) {
							generatedSequenceSelected.put(sel, rg.generateSad(terms).get((long)terms));
						}
						else if(sel.equals("Duck")) {
							generatedSequenceSelected.put(sel, rg.generateDuck(terms).get((long)terms));
						}
						else if(sel.equals("Dudeney")) {
							generatedSequenceSelected.put(sel, rg.generateDudeney(terms).get((long)terms));
						}
						else if(sel.equals("Buzz")) {
							generatedSequenceSelected.put(sel, rg.generateBuzz(terms).get((long)terms));
						}
						else if(sel.equals("Spy")) {
							generatedSequenceSelected.put(sel, rg.generateSpy(terms).get((long)terms));
						}
						else if(sel.equals("Kaprekar")) {
							generatedSequenceSelected.put(sel, rg.generateKaprekar(terms).get((long)terms));
						}
						else if(sel.equals("Tech")) {
							generatedSequenceSelected.put(sel, rg.generateTech(terms).get((long)terms));
						}
						else if(sel.equals("Magic")) {
							generatedSequenceSelected.put(sel, rg.generateMagic(terms).get((long)terms));
						}
						else if(sel.equals("Smith")) {
							generatedSequenceSelected.put(sel, rg.generateSmith(terms).get((long)terms));
						}
						else if(sel.equals("Munchausen")) {
							generatedSequenceSelected.put(sel, rg.generateMunchausen(terms).get((long)terms));
						}
						else if(sel.equals("Repdigits")) {
							generatedSequenceSelected.put(sel, rg.generateRepdigits(terms).get((long)terms));
						}
						else if(sel.equals("Gapful")) {
							generatedSequenceSelected.put(sel, rg.generateGapful(terms).get((long)terms));
						}
						else if(sel.equals("Hungry")) {
							generatedSequenceSelected.put(sel, rg.generateHungry(terms).get((long)terms));
						}
						else if(sel.equals("Pronic")) {
							generatedSequenceSelected.put(sel, rg.generatePronic(terms).get((long)terms));
						}
						else if(sel.equals("Neon")) {
							generatedSequenceSelected.put(sel, rg.generateNeon(terms).get((long)terms));
						}
						else if(sel.equals("Automorphic")) {
							generatedSequenceSelected.put(sel, rg.generateAutomorphic(terms).get((long)terms));
						}
						else
							throw new Exception();
					}
					responseJsonSelected.put(category, generatedSequenceSelected);
				}
			}
			
			String responseJsonString = gson.toJson(responseJsonSelected);
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
