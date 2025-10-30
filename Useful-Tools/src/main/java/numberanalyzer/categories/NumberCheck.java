package numberanalyzer.categories;

import java.util.ArrayList;

import numberanalyzer.utilities.CommonUtils;

public class NumberCheck {

	CommonUtils cu = new CommonUtils();
	PrimeNumbers pn = new PrimeNumbers();
	Factorials f = new Factorials();
	
	public boolean isSquarefree(long num) {
		if(num<=4)
			return false;
		for(long i=2L;i<=num/2;i++) {
			if(i*i==num)
				return false;
			else if(i*i>num)
				return true;
		}
		return true;
	}

	public boolean isQuintic(long num) {
		long low = 0L;
		long high = num;
		long mid = low+high/2;
		while(low<=high) {
			mid = low+high/2;
			if(mid*mid*mid*mid*mid==num)
				return true;
			else if(mid*mid*mid*mid*mid>num) {
				high = mid - 1;
			}
			else {
				low = mid + 1;
			}
		}
		return false;
	}
	
	public boolean isSextic(long num) {
		long low = 0L;
		long high = num;
		long mid = low+high/2;
		while(low<=high) {
			mid = low+high/2;
			if(mid*mid*mid*mid*mid*mid==num)
				return true;
			else if(mid*mid*mid*mid*mid*mid>num) {
				high = mid - 1;
			}
			else {
				low = mid + 1;
			}
		}
		return false;
	}

	public boolean isTau(long num) {
		int c=0;
		if(num==0)
			return false;
		else if(num>0) {
			c=1;
			for(long i=1L;i<=num/2;i++) {
				if(num%i==0)
					c++;
			}
		}
		else {
			c=2;
			for(long i=1L;i>=num/2;i--) {
				if(num%i==0)
					c+=2;
			}
		}
		return num%c==0;
	}
	
	public boolean isThabit(long num) {
		if(num<=0)
			return false;
		for(int i=0;i<=num/2;i++) {
			if(3*(long)Math.pow(2, i)-1==num)
				return true;
			else if(3*(long)Math.pow(2, i)-1>num)
				return false;
		}
		return false;
	}
	
	public boolean isGeneralizedFibonacci(long num, int n) {
		num=Math.abs(num);
		ArrayList<Long> list = new ArrayList<>();
		list.add(0L);
		list.add(1L);
		long sum=0L;
		for(int i=2;i<n;i++) {
			for(int j=0;j<n;j++) {
				sum+=list.get(j);
			}
			list.add(sum);
			sum=0L;
		}
		long next = 0L;
		int c=0;
		while(true) {
			for(int i=c;i<c+n;i++) {
				next+=list.get(i);
			}
			if(next>num)
				return false;
			else if(next==num)
				return true;
			else
				c++;
		}
	}

	public boolean isLily(long num) {
		if(num<=0)
			return false;
		ArrayList<Long> allPermutations = cu.findPermutationsOfNumber(num);
		long sum=0L;
		for(long n : allPermutations) {
			sum+=n;
		}
		return sum==num*num*num;
	}

	public boolean isSphenic(long num) {
		if(num<=0)
			return false;
		for(long i=1L;i<=num/2;i++) {
			for(long j=i+1;j<=num/2;j++) {
				for(long k=j+1;k<=num/2;k++) {
				if(pn.isPrime(i)&&pn.isPrime(j)&&pn.isPrime(k)&&(i*j*k==num))
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isRegular(long num) {
		if(num<=0)
			return false;
		else if(num==1)
			return true;

		for(int i=2;i<=num/2;i++) {
			if(num%i==0 && pn.isPrime(i) && i>5)
				return false;
		}
		return true;
	}
}

