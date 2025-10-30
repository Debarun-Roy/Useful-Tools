package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.categories.BaseNRepresentation;
import numberanalyzer.categories.Factors;
import numberanalyzer.categories.NumberCheck;
import numberanalyzer.categories.NumberTheory;
import numberanalyzer.categories.Patterns;
import numberanalyzer.categories.PrimeNumbers;
import numberanalyzer.categories.Recreational;

@WebServlet("/NumberAnalyzer")
public class NumberAnalysisDisplayController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			long number = (long) session.getAttribute("number");
			PrimeNumbers pn = new PrimeNumbers();
			BaseNRepresentation bnr = new BaseNRepresentation();
			NumberTheory nt = new NumberTheory();
			Factors fac = new Factors();
			Recreational rec = new Recreational();
			Patterns p = new Patterns();
			if(request.getParameter("number") != null) {
				number = Long.parseLong(request.getParameter("number"));
				session.setAttribute("number", number);
			}
			LinkedHashMap<String, LinkedHashMap<Integer, String>> responseJSONMap = new LinkedHashMap<>();
			LinkedHashMap<Integer, String> categoryMap = new LinkedHashMap<>();
			String resp = number+" is a %s number.";
			int n=0;
			String category = "Number Theory";
			if(nt.isNatural(number)) {
				categoryMap.put(++n,resp.replace("%s", "Natural"));
			}
			if(nt.isWhole(number)) {
				categoryMap.put(++n, resp.replace("%s", "Whole"));
			}
			if(nt.isNegative(number)) {
				categoryMap.put(++n, resp.replace("%s", "Negative"));
			}
			if(nt.isInteger(number)) {
				categoryMap.put(++n, resp.replace("a %s number", "an Integer"));
			}
			if(nt.isOdd(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Odd"));
			}
			if(nt.isEven(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Even"));
			}
			if(nt.isInteger(number)) {
				categoryMap.put(++n, resp.replace("%s", "Real"));
			}
			if(nt.isInteger(number)) {
				categoryMap.put(++n, resp.replace("%s", "Rational"));
			}
			responseJSONMap.put(category, categoryMap);
			n=0;
			category="Primes";
			categoryMap = new LinkedHashMap<>();
			if(pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Prime"));
			}
			if(!pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Composite"));
			}
			if(pn.isSemiPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Semi Prime"));
			}
			if(pn.isEmirp(number)) {
				categoryMap.put(++n, resp.replace("%s", "Emirp"));
			}
			if(pn.isPrimePalindrome(number)) {
				categoryMap.put(++n, resp.replace("%s", "Prime Palindrome"));
			}
			if(fac.isPerfect(number)&&pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Perfect Prime"));
			}
			if(rec.isHappy(number)&&pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Happy Prime"));
			}
			if(!rec.isHappy(number)&&pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Sad Prime"));
			}
			if(pn.isAdditivePrime(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Additive Prime"));
			}
			if(pn.isAnagrammaticPrime(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Anagrammatic Prime"));
			}
			if(pn.isCircularPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Circular Prime"));
			}
			if(pn.isTwinPrime(number)) {
				categoryMap.put(++n, resp.replace("%s","Twin Prime"));
			}
			if(pn.isCousinPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Cousin Prime"));
			}
			if(pn.isSexyPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Sexy Prime"));
			}
			if(pn.isSophieGermanPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Sophie German Prime"));
			}
			if(pn.isKillerPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Killer Prime"));
			}
			responseJSONMap.put(category, categoryMap);
			n=0;
			category="Factors";
			categoryMap = new LinkedHashMap<>();
			if(fac.isPerfect(number)) {
				categoryMap.put(++n, resp.replace("%s", "Perfect"));
			}
			if(fac.isImperfect(number)) {
				categoryMap.put(++n, resp.replace("%s", "Imperfect"));
			}
			if(fac.isPerfect(number)&&pn.isPrime(number)) {
				categoryMap.put(++n, resp.replace("%s", "Perfect Prime"));
			}
			if(fac.isPerfect(number)&&p.isPalindrome(number)) {
				categoryMap.put(++n, resp.replace("%s", "Perfect Palindrome"));
			}
			if(fac.isArithmetic(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Arithmetic"));
			}
			if(fac.isInharmonious(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Inharmonious"));
			}
			if(fac.isBlum(number)) {
				categoryMap.put(++n, resp.replace("%s", "Blum"));
			}
			if(fac.isHumble(number)) {
				categoryMap.put(++n, resp.replace("%s", "Humble"));
			}
			if(fac.isAbundant(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Abundant"));
			}
			if(fac.isDeficient(number)) {
				categoryMap.put(++n, resp.replace("%s", "Deficient"));
			}
			if(fac.isAmicable(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Amicable"));
			}
			if(fac.isUntouchable(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Untouchable"));
			}
			responseJSONMap.put(category, categoryMap);
			n=0;
			category="Recreational";
			categoryMap = new LinkedHashMap<>();
			if(rec.isArmstrong(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Armstrong"));
			}
			if(rec.isHarshad(number)) {
				categoryMap.put(++n, resp.replace("%s", "Harshad"));
			}
			if(rec.isDisarium(number)) {
				categoryMap.put(++n, resp.replace("%s", "Disarium"));
			}
			if(rec.isKaprekar(number)) {
				categoryMap.put(++n, resp.replace("%s", "Kaprekar"));
			}
			if(rec.isHappy(number)) {
				categoryMap.put(++n, resp.replace("%s", "Happy"));
			}
			if(!rec.isHappy(number)) {
				categoryMap.put(++n, resp.replace("%s", "Sad"));
			}
			if(rec.isAutomorphic(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Automorphic"));
			}
			if(rec.isDuck(number)) {
				categoryMap.put(++n, resp.replace("%s", "Duck"));
			}
			if(rec.isDudeney(number)) {
				categoryMap.put(++n, resp.replace("%s", "Dudeney"));
			}
			if(rec.isGapful(number)) {
				categoryMap.put(++n, resp.replace("%s", "Gapful"));
			}
			if(rec.isHungry(number)) {
				categoryMap.put(++n, resp.replace("%s", "Hungry"));
			}
			if(rec.isBuzz(number)) {
				categoryMap.put(++n, resp.replace("%s", "Buzz"));
			}
			if(rec.isSpy(number)) {
				categoryMap.put(++n, resp.replace("%s", "Spy"));
			}
			if(rec.isTech(number)) {
				categoryMap.put(++n, resp.replace("%s", "Tech"));
			}
			if(rec.isNeon(number)) {
				categoryMap.put(++n, resp.replace("%s", "Neon"));
			}
			if(rec.isMagic(number)) {
				categoryMap.put(++n, resp.replace("%s", "Magic"));
			}
			if(rec.isSmith(number)) {
				categoryMap.put(++n, resp.replace("%s", "Smith"));
			}
			if(rec.isPronic(number)) {
				categoryMap.put(++n, resp.replace("%s", "Pronic"));
			}
			if(rec.isRepdigits(number)) {
				categoryMap.put(++n, resp.replace("%s", "Repdigit"));
			}
			if(rec.isMunchausen(number)) {
				categoryMap.put(++n, resp.replace("%s", "Munchausen"));
			}
			responseJSONMap.put(category, categoryMap);
			n=0;
			category="Patterns";
			categoryMap = new LinkedHashMap<>();

			if(p.isPalindrome(number)) {
				categoryMap.put(++n, resp.replace("%s", "Palindrome"));
			}
			if(pn.isPrimePalindrome(number)) {
				categoryMap.put(++n,resp.replace("%s", "Prime Palindrome"));
			}
			if(p.isPerfectSquare(number)) {
				categoryMap.put(++n,resp.replace("%s", "Perfect Square"));
			}
			if(p.isPerfectCube(number)) {
				categoryMap.put(++n,resp.replace("%s", "Perfect Cube"));
			}
			if(p.isPerfectPower(number)) {
				categoryMap.put(++n, resp.replace("%s", "Perfect Power"));
			}
			if(p.isHypotenuse(number)) {
				categoryMap.put(++n, resp.replace("%s", "Hypotenuse"));
			}
			if(p.isFibonacci(number)) {
				categoryMap.put(++n,resp.replace("%s", "Fibonacci"));
			}
			if(p.isTribonacci(number)) {
				categoryMap.put(++n, resp.replace("%s", "Tribonacci"));
			}
			if(p.isTetranacci(number)) {
				categoryMap.put(++n, resp.replace("%s", "Tetranacci"));
			}
			if(p.isPerrin(number)) {
				categoryMap.put(++n,resp.replace("%s", "Perrin"));
			}
			if(p.isLucas(number)) {
				categoryMap.put(++n, resp.replace("%s", "Lucas"));
			}
			if(p.isPadovan(number)) {
				categoryMap.put(++n, resp.replace("%s", "Padovan"));
			}
			if(p.isKeith(number)) {
				categoryMap.put(++n, resp.replace("%s", "Keith"));
			}
			if(p.isCatalan(number)) {
				categoryMap.put(++n, resp.replace("%s", "Catalan"));
			}
			if(p.isTriangular(number)) {
				categoryMap.put(++n, resp.replace("%s", "Triangular"));
			}
			if(p.isTetrahedral(number)) {
				categoryMap.put(++n, resp.replace("%s", "Tetrahedral"));
			}
			if(p.isPentagonal(number)) {
				categoryMap.put(++n, resp.replace("%s", "Pentagonal"));
			}
			if(p.isStandardHexagonal(number)) {
				categoryMap.put(++n, resp.replace("%s", "Standard Hexagonal"));
			}
			if(p.isCenteredHexagonal(number)) {
				categoryMap.put(++n, resp.replace("%s", "Centered Hexagonal"));
			}
			if(p.isHexagonal(number)) {
				categoryMap.put(++n, resp.replace("%s", "Hexagonal"));
			}
			if(p.isHeptagonal(number)) {
				categoryMap.put(++n, resp.replace("%s", "Heptagonal"));
			}
			if(p.isOctagonal(number)) {
				categoryMap.put(++n, resp.replace("a %s", "an Octagonal"));
			}
			if(p.isStellaOctangula(number)) {
				categoryMap.put(++n, resp.replace("%s", "Stella Octangula"));
			}
			responseJSONMap.put(category, categoryMap);
			n=0;
			category="Base Representations";
			categoryMap = new LinkedHashMap<>();
			categoryMap.put(++n, bnr.getBinaryRepresentation(number));
			categoryMap.put(++n, bnr.getOctalRepresentation(number));
			categoryMap.put(++n, bnr.getHexRepresentation(number));
			responseJSONMap.put(category, categoryMap); 
			
			try (PrintWriter out = response.getWriter()){
				out.print(responseJSONMap);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			String responseJSON = "Exception : "+e.getMessage();
			try (PrintWriter out = response.getWriter()){
				out.print(responseJSON);
				out.flush();
			}
		}
	}
}
