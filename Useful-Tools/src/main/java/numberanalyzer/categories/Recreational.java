package numberanalyzer.categories;

import java.util.ArrayList;

import numberanalyzer.utilities.CommonUtils;

public class Recreational {

	CommonUtils cu = new CommonUtils();
	PrimeNumbers pn = new PrimeNumbers();
	
	public boolean isArmstrong(long num) {
		long sum = 0L;
		long d = cu.numberOfDigits(num);
		if(num<0&&d%2==0)
			return false;
		long numCopy = num;
		while(num>0) {
			long r = num%10;
			sum += (int)Math.pow(r, d);
			num /= 10;
		}
		return sum==numCopy;
	}
	
	public boolean isHarshad(long num) {
		return (num%cu.findSumOfDigits(num))==0;
	}
	
	public boolean isDisarium(long num) {
		long numCopy = num;
		long d=cu.numberOfDigits(num);
		long newnum=0L;
		while(num>0) {
			newnum += (long)Math.pow(num%10, d--);
			num /= 10;
		}
		return newnum==numCopy;
	}

	public boolean isHappy(long num) {
		long slow = num;
		long fast = num;
		do {
			slow=cu.findSumOfSquares(num);
			fast=cu.findSumOfSquares(cu.findSumOfSquares(num));
		}
		while(slow != fast);
		return slow == 1;
	}
	
	public boolean isKaprekar(long num) {
		long d = cu.numberOfDigits(num);
		if(d==1)
			return false;
		long sq = num*num;
		long d2 = cu.numberOfDigits(sq);
		long n1=0L;
		long n2=0L;
		long c1=0L;
		long c2=0L;
		if(d2==d*2) {
			while(sq>0) {
				long r=sq%10;
				if(c1<d/2) {
					n1=n1+r*(long)Math.pow(10, c1++);
				}
				if(c2<d/2) {
					n2=n2+r*(long)Math.pow(10, c2++);
				}
				sq/=10;
			}
		}
		else {
			while(c1<d/2) {
				long r=sq%10;
				n1=n1+r*(long)Math.pow(10, c1++);
				sq/=10;
			}
			while(sq>0&&c2<d/2+1) {
				long r=sq%10;
				n2=n2+r*(long)Math.pow(10, c2++);
				sq/=10;
			}
		}
		return num==(n1+n2);
	}
	
	public boolean isAutomorphic(long num) {
		long sq = num*num;
		long d = cu.numberOfDigits(num);
		long newnum = 0L;
		long c=0;
		while(c<d) {
			long r = sq % 10;
			newnum = newnum + r*(int)Math.pow(10, c++);
			sq /= 10;
		}
		return newnum==num;
	}
	
	public boolean isDudeney(long num) {
		return cu.findSumOfDigits(num)==Math.cbrt(num);
	}
	
	public boolean isGapful(long num) {
		if(num<=9)
			return false;
		ArrayList<Integer> digits = cu.generateListOfDigits(num);
		int n = digits.get(0)*10+digits.get(digits.size()-1);
		if(num%n==0)
			return true;
		else
			return false;
	}

	public boolean isHungry(long num) {
		if(num<0)
			return false;
		for(long i=0L;i<=num/2;i++) {
			if(7*((long)Math.pow(2, i)/47)==num)
				return true;
		}
		return false;
	}
	
	public boolean isDuck(long num) {
		return num%10!=0;
	}

	public boolean isBuzz(long num) {
		return (num%7==0)||(num%10==7);
	}

	public boolean isSpy(long num) {
		return cu.findSumOfDigits(num)==cu.findProductOfDigits(num);
	}
	
	public boolean isTech(long num) {
		long d= cu.numberOfDigits(num);
		long n1=0L;
		long n2=0L;
		if(d%2==0) {
			long c1=0;
			long c2=0;
			while(num>0) {
				long r=num%10;
				if(c1<d/2) {
					n1=n1+r*(long)Math.pow(10, c1++);
				}
				if(c2<d/2) {
					n2=n2+r*(long)Math.pow(10, c2++);
				}
			}
			long sum=n1+n2;
			return sum*sum==num;
		}
		return false;
	}
	
	public boolean isNeon(long num) {
		return cu.findSumOfDigits(num*num) == num;
	}
	
	public boolean isMagic(long num) {
		if(num==1)
			return true;
		else if(num<9)
			return false;
		else
			return isMagic(cu.findSumOfDigits(num));
	}

	public boolean isSmith(long num) {
		ArrayList<Long> list = new ArrayList<>();
		long sum1=cu.findSumOfDigits(num);
		long sum2=0L;
		for(long i=1L;i<=num;i++) {
			if(num%i==0 && pn.isPrime(i))
				list.add(i);
		}

		for(int j=0;j<list.size();j++) {
			sum2+=cu.findSumOfDigits(list.get(j));
		}
		return sum1==sum2;
	}
	
	public boolean isMunchausen(long num) {
		long sum=0L;
		long numCopy=num;
		while(num>0) {
			long r=num%10;
			sum+=(long)Math.pow(r, r);
			num/=10;
		}
		return sum==numCopy;
	}

	public boolean isRepdigits(long num) {
		if(num<=9)
			return true;
		long d = num%10;
		while(num>0) {
			if(num%10!=d)
				return false;
			num/=10;
		}
		return true;
	}
	
	public boolean isPronic(long num) {
		long a=0L;
		long b=1L;

		while(a<=num/2||b<=num/2) {
			long c=a++*b++;
			if(c==num)
				return true;
		}
		return false;
	}
}