package calculator.utilities;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.function.Function;

public class ArithmeticUtils {

	public static void otherFunctions() {

		Function logn = new Function("logn", 2) {
			@Override
			public double apply(double... args) {
				double result = 0.0;
				try {
					double x = args[0];
					double n = args[1];
					result = Math.log(x)/Math.log(n);
					return result;
				}
				catch(ArithmeticException ae) {
					return Double.NaN;
				}
			}
		};

		Function max = new Function("max", -1) {
			@Override
			public double apply(double... args) {
				try {
					if(args.length < 1) {
						throw new IllegalArgumentException("The max function requires at least 1 argument");
					}
					else {
						double maxVal = args[0];
						for(int i=1;i<args.length;i++) {
							if(args[i]>maxVal) {
								maxVal = args[i];
							}
						}
						return maxVal;
					}
				}
				catch(Exception e) {
					return Double.NaN;
				}
			}
		};

		Function min = new Function("min", -1) {
			@Override
			public double apply(double... args) {
				try {
					if(args.length < 1) {
						throw new IllegalArgumentException("The min function requires at least 1 argument");
					}
					else {
						double minVal = args[0];
						for(int i=1;i<args.length;i++) {
							if(args[i]<minVal) {
								minVal = args[i];
							}
						}
						return minVal;
					}
				}
				catch(Exception e) {
					return Double.NaN;
				}
			}
		};

		Function avg = new Function("avg", -1) {
			@Override
			public double apply(double... args) {
				try {
					if(args.length < 1) {
						throw new IllegalArgumentException("The avg function requires at least 1 argument");
					}
					else {
						double sum = 0.0;
						for(int i=0;i<args.length;i++) {
							sum += args[i];
						}
						return sum/args.length;
					}
				}
				catch(Exception e) {
					return Double.NaN;
				}
			}
		};

		Function median = new Function("median", -1) {
			@Override
			public double apply(double... args) {
				try {
					if(args.length < 1) {
						throw new IllegalArgumentException("The avg function requires at least 1 argument");
					}
					else {
						if(args.length%2==0) {
							return args[args.length/2];
						}
						else {
							return (args[args.length/2]+args[args.length/2+1])/2;
						}
					}
				}
				catch(Exception e) {
					return Double.NaN;
				}
			}
		};
	}
	public static void complexFunction() {
		
		Function complexAdd = new Function("complexAdd", 4) {
			@Override
			public double apply(double... args) {
				try {
					double real = args[0]+args[2];
					double complex = args[1]+args[3];
					double result = Math.sqrt(real*real+complex*complex);
					return result;
				}
				catch(Exception e) {
					return Double.NaN;
				}
			}
		};
	}
}