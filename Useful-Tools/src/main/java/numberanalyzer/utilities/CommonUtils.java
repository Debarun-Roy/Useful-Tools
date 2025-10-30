package numberanalyzer.utilities;

import java.util.ArrayList;
import java.util.Collections;

public class CommonUtils {
	
	public long numberOfDigits(long num) {
		long d=0L;
		while(num>0) {
			d++;
			num/=10;
		}
		return d;
	}
	
	public long findSumOfDigits(long num) {
		long sum=0L;
		while(num>0) {
			sum+=num%10;
			num/=10;
		}
		return sum;
	}
	
	public long findProductOfDigits(long num) {
		long p=1L;
		while(num>0) {
			p*=num%10;
			num/=10;
		}
		return p;
	}
	
	public long findSumOfSquares(long num) {
		long sum=0L;
		while(num>0) {
			long r=num%10;
			sum+=r*r;
			num/=10;
		}
		return sum;
	}
	
	public long reverse(long num) {
		long rev=0L;
		while(num>0) {
			rev = rev*10 + num%10;
			num/=10;
		}
		return rev;
	}
	
	public ArrayList<Long> findPermutationsOfNumber(long num){
		if(num<0)
			return new ArrayList<>();
		ArrayList<Integer> digits = generateListOfDigits(num);
		ArrayList<Long> result = new ArrayList<>();
		ArrayList<ArrayList<Integer>> heapResult = heapAlgorithm(new ArrayList<>(), digits, digits.size(), digits.size());
		for(ArrayList<Integer> n : heapResult) {
			StringBuilder st = new StringBuilder();
			for(int d : n) {
				st.append(d);
			}
			result.add(Long.parseLong(st.toString()));
		}
		return result;
	}
	
	public ArrayList<ArrayList<Integer>> heapAlgorithm(ArrayList<ArrayList<Integer>> result, ArrayList<Integer> digits, int size, int n){
		if(size==1) {
			result.add(new ArrayList<>(digits));
			return result;
		}
		for(int i=0;i<size;i++) {
			heapAlgorithm(result, digits, size-1, n);
			if(size%2==1) {
				Collections.swap(digits, 0, size-1);
			}	
			else {
				Collections.swap(digits, i, size-1);
			}
		}
		return new ArrayList<>();
	}
	
	public ArrayList<Long> generateAllRotations(long num){
		ArrayList<Integer> digits = generateListOfDigits(num);
		ArrayList<Long> result = new ArrayList<>();
		result.add(num);
		for(int i=1;i<digits.size();i++) {
			int temp = digits.get(digits.size()-1);
			for(int j=0;j<digits.size()-1;j++) {
				digits.add(j+1, digits.get(j));
			}
			digits.add(0, temp);
			StringBuilder st = new StringBuilder();
			for(int d : digits)
				st.append(d);
			result.add(Long.parseLong(st.toString()));
		}
		return result;
	}
	
	public ArrayList<Integer> generateListOfDigits(long num){
		ArrayList<Integer> digits = new ArrayList<>();
		while(num>0) {
			digits.add((int)num%10);
			num/=10;
		}
		Collections.reverse(digits);
		return digits;
	}
}
