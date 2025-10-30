package numberanalyzer.generators;

import java.util.LinkedHashMap;

import numberanalyzer.categories.Factors;

public class FactorsGenerator {

	Factors fac = new Factors();
	public LinkedHashMap<Long, String> generatePerfect(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0L;c<n;i++) {
			if(fac.isPerfect(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateImperfect(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0;c<n;i++) {
			if(fac.isImperfect(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateArithmetic(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0;c<n;i++) {
			if(fac.isArithmetic(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateInharmonious(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0;c<n;i++) {
			if(fac.isInharmonious(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateBlum(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isBlum(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateHumble(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isHumble(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateAbundant(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isAbundant(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateDeficient(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isDeficient(i)) {
				resultMap.put(++c,String.valueOf(i));
				c++;
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateAmicable(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isAmicable(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateUntouchable(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(fac.isUntouchable(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
}
