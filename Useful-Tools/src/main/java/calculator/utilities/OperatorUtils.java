package calculator.utilities;

public class OperatorUtils {
	
	public static double getBinaryResult(char s, double a, double b) {
		double sum = 0.0;
		try {
			if(s=='+') {
				sum = a+b;
			}
			else if(s=='-') {
				sum = a-b;
			}
			else if(s=='/') {
				sum = a/b;
			}
			else if(s=='*') {
				sum = a*b;
			}
			else if(s=='^') {
				sum = Math.pow(a, b);
			}
			else if(s=='%') {
				sum = a%b;
			}
			else {
				throw new Exception();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return sum;
	}
}
