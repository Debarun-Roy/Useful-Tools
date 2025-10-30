package numberanalyzer.generators;

import java.util.LinkedHashMap;

public class NumberTheoryGenerator {
	
	public LinkedHashMap<Long, String> generateOdd(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=1L,c=0;c<n;i+=2,c++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateEven(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=2L,c=0;c<n;i+=2,c++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateNatural(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=1L,c=0;c<n;i++,c++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateWhole(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=0L,c=0;c<n;i++,c++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateNegative(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=-1L,c=0;c<n;i--,c++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateIntegers(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=-n/2,c=0;i<=n/2;i++) {
			resultMap.put(c,String.valueOf(i));
		}
		return resultMap;
	}
}
