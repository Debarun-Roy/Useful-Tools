package numberanalyzer.generators;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import numberanalyzer.categories.Factorials;

public class FactorialGenerator {

	Factorials f = new Factorials();
	
	public LinkedHashMap<Long, String> generateFactorial(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(f.findFactorial(i)));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSuperfactorial(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(f.findSuperfactorial(i,i)));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateHyperfactorial(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(f.findHyperfactorial(i)));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, ArrayList<Long>> generateGeneralizedFactorial(int num, int n){
		LinkedHashMap<Long, ArrayList<Long>> resultMap = new LinkedHashMap<>();
		if(n<=0||num<0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			ArrayList<Long> result = findAllFactorialForms(i,n);
			resultMap.put((long)i, result);
		}
		return resultMap;
	}

	public ArrayList<Long> findAllFactorialForms(int num, int n){
		ArrayList<Long> result = new ArrayList<>();
		for(int i=1;i<=n;i++) {
			result.add(f.findNthFactorial(num, n));
		}
		return result;
	}

	public LinkedHashMap<Long, String> generatePrimorial(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put((long)i,String.valueOf(f.findPrimorial(i)));
		}
		return resultMap;
	}
}
