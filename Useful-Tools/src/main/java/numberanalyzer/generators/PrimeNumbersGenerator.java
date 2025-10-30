package numberanalyzer.generators;

import java.util.LinkedHashMap;

import numberanalyzer.categories.PrimeNumbers;

public class PrimeNumbersGenerator {

	PrimeNumbers pn = new PrimeNumbers();
	
	public LinkedHashMap<Long, String> generatePrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSemiPrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isSemiPrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateEmirp(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isEmirp(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateAdditivePrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isAdditivePrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateAnagrammaticPrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isAnagrammaticPrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateCircularPrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isCircularPrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateKillerPrime(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isKillerPrime(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generatePrimePalindrome(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrimePalindrome(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateTwinPrimes(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		String pair = "";
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrime(i)&&pn.isPrime(i+2)) {
				pair+="("+i+", ";
				pair+=(i+2)+")";
				resultMap.put(++c, pair);
				pair = "";
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateCousinPrimes(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		String pair = "";
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrime(i)&&pn.isPrime(i+4)) {
				pair+="("+i+", ";
				pair+=(i+4)+")";
				resultMap.put(++c, pair);
				pair = "";
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSexyPrimes(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		String pair = "";
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrime(i)&&pn.isPrime(i+6)) {
				pair+="("+i+", ";
				pair+=(i+6)+")";
				resultMap.put(++c, pair);
				pair = "";
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSophieGermanPrimes(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		String pair = "";
		for(long i=1L,c=0;c<num;i++) {
			if(pn.isPrime(i)&&pn.isPrime(2*i+1)) {
				pair+="("+i+", ";
				pair+=(2*i+1)+")";
				resultMap.put(++c, pair);
				pair = "";
			}
		}
		return resultMap;
	}
}
