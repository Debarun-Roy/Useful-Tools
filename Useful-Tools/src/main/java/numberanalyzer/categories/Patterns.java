package numberanalyzer.categories;

import numberanalyzer.utilities.CommonUtils;

public class Patterns {

	CommonUtils cu = new CommonUtils();
	Factorials f = new Factorials();
	
	public boolean isPalindrome(long num) {
		return num == cu.reverse(num);
	}
	
	public boolean isPerfectSquare(long num) {
		num = Math.abs(num);
		long low = 0L;
		long high = num;
		long mid = low+high/2;
		while(low<=high) {
			mid = low+high/2;
			if(mid*mid==num)
				return true;
			else if(mid*mid>num) {
				high = mid-1;
			}
			else {
				low = mid+1;
			}
		}
		return false;	
	}

	public boolean isPerfectCube(long num) {
		long low = 0L;
		long high = num;
		long mid = low+high/2;
		while(low<=high) {
			mid = low+high/2;
			if(mid*mid*mid==num)
				return true;
			else if(mid*mid*mid>num) {
				high = mid - 1;
			}
			else {
				low = mid + 1;
			}
		}
		return false;
	}
	
	public boolean isPerfectPower(long num) {
		if(num==1)
			return true;
		else if(num<=3)
			return false;
		for(int i=1;i<=num/2;i++) {
			for(int j=2;j<=Math.log(num)/Math.log(2);j++) {
				if((long)Math.pow(i, j)==num) {
					return true;
				}
				else if((long)Math.pow(i, j)>num) {
					break;
				}
			}
		}
		return false;
	}
	
	public boolean isHypotenuse(long num) {
		if(num<=0)
			return false;
		for(long i=1L;i<=num/2;i++) {
			for(long j=1L;j<=num/2;j++) {
				if(num == Math.sqrt(i*i+j*j))
					return true;
			}
		}
		return false;
	}
	
	public boolean isFibonacci(long num) {
		num=Math.abs(num);
		if(num==0)
			return true;
		long a = 0L;
		long b = 1L;
		while(true) {
			long c = a+b;
			if(c>num) {
				return false;
			}
			else if(c==num) {
				return true;
			}
			else {
				a=b;
				b=c;
			}
		}
	}

	public boolean isTribonacci(long num) {
		num=Math.abs(num);
		long a=0L;
		long b=1L;
		long c=1L;
		while(true) {
			long d=a+b+c;
			if(d>num)
				return false;
			else if(d==num)
				return true;
			else {
				a=b;
				b=c;
				c=d;
			}
		}
	}

	public boolean isTetranacci(long num) {
		num=Math.abs(num);
		long a=0L;
		long b=1L;
		long c=1L;
		long d=2L;
		while(true) {
			long e=a+b+c+d;
			if(e>num)
				return false;
			else if(e==num)
				return true;
			else {
				a=b;
				b=c;
				c=d;
				d=e;
			}
		}
	}

	public boolean isPerrin(long num) {
		num=Math.abs(num);
		long a=3L;
		long b=0L;
		long c=2L;
		while(true) {
			long d=a+b;
			if(d>num)
				return false;
			else if(d==num)
				return true;
			else {
				a=b;
				b=c;
				c=d;
			}
		}
	}

	public boolean isLucas(long num) {
		num=Math.abs(num);
		long a=2L;
		long b=1L;
		while(true) {
			long c=a+b;
			if(c>num)
				return false;
			else if(c==num)
				return true;
			else {
				a=b;
				b=c;
			}
		}
	}

	public boolean isPadovan(long num) {
		num=Math.abs(num);
		long a=1L;
		long b=1L;
		long c=1L;
		while(true) {
			long d=a+b;
			if(d>num)
				return false;
			else if(d==num)
				return true;
			else {
				a=b;
				b=c;
				c=d;
			}
		}
	}

	public boolean isKeith(long num) {
		num=Math.abs(num);
		if(num<17&&num!=1)
			return false;
		long a=1L;
		long b=9L;
		long c=7L;
		while(true) {
			long d = a+b+c;
			if(d>num)
				return false;
			else if(d==num)
				return true;
			else {
				a=b;
				b=c;
				c=d;
			}
		}
	}
	
	public boolean isCatalan(long num) {
		if(num<0)
			return false;
		for(int i=0;i<num;i++) {
			long term = f.findFactorial(2*i)/(f.findFactorial(i+1)*f.findFactorial(i));
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}
	
	public boolean isTriangular(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(i+1)/2;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isPentagonal(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(3*i-1)/2;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isStandardHexagonal(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(2*i-1);
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isCenteredHexagonal(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = 3*i*(i-1)+1;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isHexagonal(long num) {
		if(num<0)
			return false;
		if(isStandardHexagonal(num)||isCenteredHexagonal(num))
			return true;
		else
			return false;
	}

	public boolean isHeptagonal(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(5*i-3)/2;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isOctagonal(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = 3*i*i-2*i;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isTetrahedral(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(i+1)*(i+2)/6;
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}

	public boolean isStellaOctangula(long num) {
		if(num<0)
			return false;
		for(int i=1;i<=num;i++) {
			long term = i*(2*i*i-1);
			if(term>num)
				return false;
			else if(term==num)
				return true;
		}
		return false;
	}
}