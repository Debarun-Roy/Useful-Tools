package numberanalyzer.categories;

public class Factorials {
	
	PrimeNumbers pn = new PrimeNumbers();
	
	public long findNthFactorial(int num, int n) { // nth factorial of 2n is n!!!....!!! = 2n*(2n-n)*1
		if(num<=1)
			return 1;
		else
			return num*findNthFactorial(num-n, n);
	}
	
	public long findFactorial(int num) {
		if(num<=1)
			return 1;
		else
			return num*findFactorial(num-1);
	}
	
	public long findSuperfactorial(int num, int num2) {
		if(num<=1)
			return 1;
		else if(num2<=1)
			return num2*findSuperfactorial(num-1, num-1);
		else
			return num2*findSuperfactorial(num, num2-1);
	}
	
	public long findHyperfactorial(int num) {
		if(num<=1)
			return 1;
		else
			return (long)Math.pow(num, num)*findHyperfactorial(num-1);
	}
	
	public long findPrimorial(int num) {
		if(num==1)
			return 1;
		long p=1L;
		int c=0;
		for(int i=2;c<num;i++) {
			if(pn.isPrime(i)) {
				p*=i;
				c++;
			}
		}
		return p;
	}
}
