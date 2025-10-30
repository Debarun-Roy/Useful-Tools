package calculator.utilities;

import calculator.functions.acosec;
import calculator.functions.acot;
import calculator.functions.asec;
import calculator.functions.atan2;
import calculator.functions.cosd;
import calculator.functions.cosec;
import calculator.functions.cosecd;
import calculator.functions.cot;
import calculator.functions.cotd;
import calculator.functions.secd;
import calculator.functions.sind;
import calculator.functions.tand;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

public class TrigonometricUtils {
	
	public static void reciprocalFunctions() {
		
		Function cosecant = new Function("cosec", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result = 1.0/(Math.sin(args[0]));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};
		
		Function secant = new Function("sec", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result = 1.0/(Math.cos(args[0]));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};
		
		Function cotangent = new Function("cot", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result = 1.0/(Math.tan(args[0]));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};
	}
	
	public static void degreeFunctions() {
		
		Function sind = new Function("sind", 1) {
			@Override
			public double apply(double... args) {
				return Math.sin(Math.toRadians(args[0]));
			}
		};
		
		Function cosd = new Function("cosd", 1) {
			@Override
			public double apply(double... args) {
				return Math.cos(Math.toRadians(args[0]));
			}
		};
		
		Function tand = new Function("tand", 1) {
			@Override
			public double apply(double... args) {
				return Math.tan(Math.toRadians(args[0]));
			}
		};
		
		Function cosecd = new Function("cosecd", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result =  1.0/(Math.sin(Math.toRadians(args[0])));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};
		
		Function secd = new Function("secd", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result = 1.0/(Math.cos(Math.toRadians(args[0])));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};

		Function cotd = new Function("cotd", 1) {
			@Override
			public double apply(double... args) {
				double result=0.0;
				try {
					result = 1.0/(Math.tan(Math.toRadians(args[0])));
				}
				catch(ArithmeticException ae) {
					result = Double.NaN;
				}
				return result;
			}
		};
	}
	
	public static void specialFunctions() {
		
		Function atan2 = new Function("atan2", 2) {
			@Override
			public double apply(double... args) {
				double x=args[0];
				double y=args[1];
				
				double r = Math.sqrt(x*x+y*y);
				
				double theta = Math.asin(x/r);
				return theta;
			}
		};
	}
	public static double getUnaryResult(String ch, String fn, double a) {
		double ans = 0.0;
		try {
			if(ch.equalsIgnoreCase("trig")) {
				switch(fn) {
				case "sin":
					ans = Math.sin(a);
					break;
				case "cos":
					ans = Math.cos(a);
					break;
				case "tan":
					ans = Math.tan(a);
					break;
				case "cosec":
					ans = 1.0/(Math.sin(a));
					break;
				case "sec":
					ans = 1.0/(Math.cos(a));
					break;
				case "cot":
					ans = 1.0/(Math.tan(a));
					break;
				default:
					throw new Exception();
				}
			}
			else if(ch.equalsIgnoreCase("inverse")) {
				switch(fn) {
				case "asin":
					ans = Math.asin(a);
					break;
				case "acos":
					ans = Math.acos(a);
					break;
				case "atan":
					ans = Math.atan(a);
					break;
				default:
					throw new Exception();
				}
			}
			else if(ch.equalsIgnoreCase("other")) {
				switch(fn) {
				case "tanh":
					ans = Math.tanh(a);
					break;
				case "cosh":
					ans = Math.cosh(a);
					break;
				case "sinh":
					ans = Math.sinh(a);
					break;
				default:
					throw new Exception();
				}
			}
			else {
				throw new Exception();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return ans;
	}
	
	public double evaluateExpression(String expr) throws Exception {
		if(expr == null || expr.isEmpty()) {
			return 0.0;
		}
		
		ExpressionBuilder exp = new ExpressionBuilder(expr)
				.function(new acosec())
				.function(new acot())
				.function(new asec())
				.function(new atan2())
				.function(new cosd())
				.function(new cosec())
				.function(new cosecd())
				.function(new cot())
				.function(new cotd())
				.function(new secd())
				.function(new sind())
				.function(new tand());
		
		Expression e = exp.build();
		double result = e.evaluate();
		
		String resultStr = String.valueOf(result);
		
		if (resultStr.matches(".*[a-zA-Z]+\\(.*\\).*")) {
	        return evaluateExpression(resultStr);
	    }

	    return result;
	}
}
