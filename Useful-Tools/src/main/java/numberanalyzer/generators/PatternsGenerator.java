package numberanalyzer.generators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import numberanalyzer.categories.Factorials;
import numberanalyzer.categories.Patterns;

public class PatternsGenerator {

	Patterns p = new Patterns();
	Factorials f = new Factorials();
	
	public LinkedHashMap<Long, String> generateFibonacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateFibonacciTerm(i)));
		}
		return resultMap;
	}

	public long generateFibonacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else
			return generateFibonacciTerm(n-1)+generateFibonacciTerm(n-2);
	}

	public LinkedHashMap<Long, String> generateTribonacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateTribonacciTerm(i)));
		}
		return resultMap;
	}

	public long generateTribonacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else if(n==3)
			return 1L;
		else
			return generateTribonacciTerm(n-1)+generateTribonacciTerm(n-2)+generateTribonacciTerm(n-3);
	}

	public LinkedHashMap<Long, String> generateTetranacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateTetranacciTerm(i)));
		}
		return resultMap;
	}

	public long generateTetranacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else if(n==3)
			return 1L;
		else if(n==4)
			return 2L;
		else
			return generateTetranacciTerm(n-1)+generateTetranacciTerm(n-2)+generateTetranacciTerm(n-3)+generateTetranacciTerm(n-4);
	}

	public LinkedHashMap<Long, String> generatePentanacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generatePentanacciTerm(i)));
		}
		return resultMap;
	}

	public long generatePentanacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else if(n==3)
			return 1L;
		else if(n==4)
			return 2L;
		else if(n==5)
			return 4L;
		else
			return generatePentanacciTerm(n-1)+generatePentanacciTerm(n-2)+generatePentanacciTerm(n-3)+generatePentanacciTerm(n-4)+generatePentanacciTerm(n-5);
	}

	public LinkedHashMap<Long, String> generateHexanacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateHexanacciTerm(i)));
		}
		return resultMap;
	}

	public long generateHexanacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else if(n==3)
			return 1L;
		else if(n==4)
			return 2L;
		else if(n==5)
			return 4L;
		else if(n==6)
			return 8L;
		else
			return generateHexanacciTerm(n-1)+generateHexanacciTerm(n-2)+generateHexanacciTerm(n-3)+generateHexanacciTerm(n-4)+generateHexanacciTerm(n-5)+generateHexanacciTerm(n-6);
	}

	public LinkedHashMap<Long, String> generateHeptanacci(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateHeptanacciTerm(i)));
		}
		return resultMap;
	}

	public long generateHeptanacciTerm(int n) {
		if(n<=1)
			return 0L;
		else if(n==2)
			return 1L;
		else if(n==3)
			return 1L;
		else if(n==4)
			return 2L;
		else if(n==5)
			return 4L;
		else if(n==6)
			return 8L;
		else if(n==7)
			return 16L;
		else
			return generateHeptanacciTerm(n-1)+generateHeptanacciTerm(n-2)+generateHeptanacciTerm(n-3)+generateHeptanacciTerm(n-4)+generateHeptanacciTerm(n-5)+generateHeptanacciTerm(n-6)+generateHeptanacciTerm(n-7);
	}

	public LinkedHashMap<Integer, ArrayList<Long>> generateGeneralizedFibonacci(int num, int n){
		LinkedHashMap<Integer, ArrayList<Long>> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put(i, generateNthFibonacci(num, i));
		}
		return resultMap;
	}

	public ArrayList<Long> generateNthFibonacci(int num, int n){
		ArrayList<Long> terms = generateInitialTerms(n);
		for(int i=terms.size()+1;i<=num;i++) {
			generateNthFibonacciTerm(i,n,terms);
		}
		return terms;
	}

	public ArrayList<Long> generateInitialTerms(int n){
		ArrayList<Long> terms = new ArrayList<>();
		terms.add(0L);
		if(n==1)
			return terms;
		terms.add(1L);
		if(n==2)
			return terms;
		long sum=0L;
		for(int i=2;i<n;i++){
			for(int j=0;j<i;j++) {
				sum+=terms.get(j);
			}
			terms.add(sum);
			sum=0L;
		}
		return terms;
	}

	public long generateNthFibonacciTerm(int k, int n, ArrayList<Long> terms) {
		if(k<=terms.size())
			return terms.get(k-1);
		else {
			long sum=0L;
			for(int i=1;i<=n;i++) {
				sum+=generateNthFibonacciTerm(k-i,n,terms);
			}
			terms.add(sum);
			return sum;
		}
	}
	
	public LinkedHashMap<Long, String> generatePerrin(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(generatePerrinTerm(i)));
		}
		return resultMap;
	}

	public long generatePerrinTerm(int n) {
		if(n==1)
			return 3;
		else if(n==2)
			return 0;
		else if(n==3)
			return 2;
		else
			return generatePerrinTerm(n-2)+generatePerrinTerm(n-3);
	}

	public LinkedHashMap<Long, String> generateLucas(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(generateLucasTerm(i)));
		}
		return resultMap;
	}

	public long generateLucasTerm(int n) {
		if(n==1)
			return 2;
		else if(n==2)
			return 1;
		else
			return generateLucasTerm(n-1)+generateLucasTerm(n-2);
	}

	public LinkedHashMap<Long, String> generatePadovan(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(generatePadovanTerm(i)));
		}
		return resultMap;
	}

	public long generatePadovanTerm(int n) {
		if(n<=3)
			return 1;
		else
			return generatePadovanTerm(n-2)+generatePadovanTerm(n-3);
	}
	
	public LinkedHashMap<Long, String> generateKeith(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(int i=1;i<=n;i++) {
			resultMap.put((long)i,String.valueOf(generateKeithTerm(i)));
		}
		return resultMap;
	}

	public long generateKeithTerm(int n) {
		if(n<=1)
			return 1L;
		else if(n==2)
			return 9L;
		else if(n==3)
			return 7L;
		else
			return generateKeithTerm(n-1)+generateKeithTerm(n-2)+generateKeithTerm(n-3);
	}
	
	public LinkedHashMap<Long, String> generatePalindrome(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0;c<n;i++) {
			if(p.isPalindrome(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateHypotenuse(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(p.isHypotenuse(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	

	public LinkedHashMap<Long, String> generatePerfectSquare(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=1L,c=0;c<n;i++,c++) {
			resultMap.put(c,String.valueOf(i*i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generatePerfectCube(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=1L,c=0;c<n;i++,c++) {
			resultMap.put(c,String.valueOf(i*i*i));
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generatePerfectPowers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(p.isPerfectPower(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateCatalanNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<0)
			return resultMap;
		for(int i=0;i<=num-1;i++) {
			long term = (f.findFactorial(i*2)/(f.findFactorial(i+1)*f.findFactorial(i)));
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateTriangularNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = i*(i+1)/2;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generatePentagonalNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = i*(3*i-1)/2;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateStandardHexagonalNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num/2;i++) {
			long term = i*(2*i-1);
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateCenteredHexagonalNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = 3*i*(i-1)+1;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateHexagonalNumbers(int num){
		LinkedHashMap<Long, String> standard = generateStandardHexagonalNumbers(num);
		LinkedHashMap<Long, String> centered = generateCenteredHexagonalNumbers(num);
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(Map.Entry<Long, String> entry : standard.entrySet()) {
			long key = entry.getKey();
			String value = entry.getValue();
			resultMap.put(key*2, value);
		}
		for(Map.Entry<Long, String> entry : centered.entrySet()) {
			long key = entry.getKey();
			String value = entry.getValue();
			resultMap.put(key*2+1, value);
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateHeptagonalNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = i*(5*i-3)/2;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateOctagonalNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = 3*i*i-2*i;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateTetrahedralNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = i*(i+1)*(i+2)/6;
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateStellaOctangulaNumbers(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			long term = i*(2*i*i-1);
			resultMap.put((long)i,String.valueOf(term));
		}
		return resultMap;
	}
}