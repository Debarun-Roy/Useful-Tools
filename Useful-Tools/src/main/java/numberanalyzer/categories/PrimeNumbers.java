package numberanalyzer.categories;
import java.util.ArrayList;

import numberanalyzer.utilities.CommonUtils;

public class PrimeNumbers {

	CommonUtils cu = new CommonUtils();
	Patterns p = new Patterns();
	
	public boolean isPrime(long num) {
		boolean result = true;
		for(long i=2L;i<=num/2;i++) {
			if(num%i==0) {
				result = false;
				return result;
			}
		}
		return result;
	}

	public boolean isSemiPrime(long num) {
		for(long a=2L;a<=num/2;a++) {
			if(isPrime(a)) {
				for(long b=3L;b<=num/2;b++) {
					if(isPrime(b)) {
						if(a*b==num)
							return true;
					}
				}
			}
		}
		return false;
	}
	
	public boolean isAdditivePrime(long num) {
		return isPrime(cu.findSumOfDigits(num));
	}
	
	public boolean isAnagrammaticPrime(long num) {
		if(num<0)
			return false;
		ArrayList<Long> allPermutations = cu.findPermutationsOfNumber(num);
		for(long n : allPermutations) {
			if(!isPrime(n))
				return false;
		}
		return true;
	}
	
	public boolean isCircularPrime(long num) {
		if(num<0)
			return false;
		ArrayList<Long> allRotations = cu.generateAllRotations(num);
		for(long n : allRotations) {
			if(!isPrime(n))
				return false;
		}
		return true;
	}

	public boolean isEmirp(long num) {
		return isPrime(num)&&isPrime(cu.reverse(num));
	}
	
	public boolean isPrimePalindrome(long num) {
		return isPrime(num)&&(p.isPalindrome(num));
	}
	
	public boolean isTwinPrime(long num) {
		return isPrime(num)&&(isPrime(num+2)||isPrime(num-2));
	}
	
	public boolean isCousinPrime(long num) {
		return isPrime(num)&&(isPrime(num+4)||isPrime(num-4));
	}
	
	public boolean isSexyPrime(long num) {
		return isPrime(num)&&(isPrime(num+6)||isPrime(num-6));
	}
	
	public boolean isSophieGermanPrime(long num) {
		return isPrime(num)&&(isPrime(num*2+1)||isPrime((num-1)/2));
	}
	
	public boolean isKillerPrime(long num) {
		return isPrime(num)&&!isPrime(num+2)&&!isPrime(num+4)&&!isPrime(num+8);
	}
	
	public boolean isThabitPrime(long num) {
		return isPrime(num) && isThabitNumber(num);
	}
	
	public boolean isTetrahedralPrime(long num) {
		return isPrime(num)&&p.isTetrahedral(num);
	}

	/**
	 * PrimeNumbers and NumberCheck originally instantiated each other as fields:
	 *
	 *   PrimeNumbers -> new NumberCheck()
	 *   NumberCheck  -> new PrimeNumbers()
	 *
	 * That constructor cycle caused a StackOverflowError as soon as the analyzer
	 * service created PrimeNumbers. Keep the Thabit check local here so
	 * PrimeNumbers no longer depends on NumberCheck during construction.
	 */
	private boolean isThabitNumber(long num) {
		if (num <= 0) {
			return false;
		}
		for (int i = 0; i <= num / 2; i++) {
			long value = 3 * (long) Math.pow(2, i) - 1;
			if (value == num) {
				return true;
			}
			if (value > num) {
				return false;
			}
		}
		return false;
	}
}
