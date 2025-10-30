package numberanalyzer.categories;

public class NumberTheory {
	
	public boolean isOdd(long num) {
		return (num%2)==1;
	}

	public boolean isEven(long num) {
		return (num%2)==0;
	}
	
	public boolean isNatural(long num) {
		return num>=1;
	}

	public boolean isWhole(long num) {
		return num==0 || isNatural(num);
	}

	public boolean isInteger(long num) {
		return isNegative(num)||isWhole(num);
	}

	public boolean isNegative(long num) {
		return !isWhole(num);
	}
}
