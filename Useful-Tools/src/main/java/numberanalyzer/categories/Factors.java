package numberanalyzer.categories;

import numberanalyzer.utilities.CommonUtils;

public class Factors {
	
	CommonUtils cu = new CommonUtils();
	PrimeNumbers pn = new PrimeNumbers();
	
	public boolean isPerfect(long num) {
		if(num<=0)
			return false;
		long sum = 0L;
		for(long i=1L;i<=num/2;i++) {
			if(num%i==0) {
				sum += i;
			}
		}
		return sum==num;
	}

	public boolean isImperfect(long num) {
		if(num<=0)
			return false;
		long sum = 0L;
		for(long i=1L;i<=num/2;i++) {
			if(num%i==0) {
				sum += i;
			}
		}
		return (sum+num) == (2*num)-1;
	}
	
	public boolean isArithmetic(long num) {
		num=Math.abs(num);
		long sum = 0L;
		int c=1;
		for(long i=1L;i<=num/2;i++) {
			if(num%i==0) {
				sum += i;
				c++;
			}
		}
		sum+=num;
		return (sum%c==0);
	}

	public boolean isInharmonious(long num) {
		if(num<=0)
			return false;
		return (cu.findSumOfDigits(num)%cu.findProductOfDigits(num)==0);
	}
	
	public boolean isBlum(long num) {
		if(num<=0)
			return false;
		for(long i=1L;i<=num/2;i++) {
			for(long j=i+1;j<=num/2;j++) {
				if(pn.isPrime(i)&&pn.isPrime(j)&&(i*j==num))
					return true;
			}
		}
		return false;
	}
	
	public boolean isHumble(long num) {
		if(num<=0)
			return false;
		else if(num==1)
			return true;

		for(int i=2;i<=num/2;i++) {
			if(num%i==0 && pn.isPrime(i) && i>7)
				return false;
		}
		return true;
	}
	
	public boolean isAbundant(long num) {
		return cu.findSumOfDigits(num) > num;
	}

	public boolean isDeficient(long num) {
		return cu.findSumOfDigits(num) < num;
	}
	
	public boolean isAmicable(long num) {
		long sum=0L;
		for(long i=1L;i<=num/2;i++) {
			if(num%i==0)
				sum+=i;
		}
		long sum2=0L;
		for(long j=1L;j<=sum;j++) {
			if(sum%j==0)
				sum2+=j;
		}
		return sum==sum2;
	}

	public boolean isUntouchable(long num) {
		for(long i=1;i<=num;i++) {
			long sum=0L;
			for(long j=1;j<=i/2;j++) {
				if(i%j==0)
					sum+=j;
			}
			if(num==sum)
				return false;
		}
		return true;
	}
}
