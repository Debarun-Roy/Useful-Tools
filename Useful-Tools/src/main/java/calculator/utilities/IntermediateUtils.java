package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.functions.acosec;
import calculator.functions.acot;
import calculator.functions.asec;
import calculator.functions.atan2;
import calculator.functions.cosd;
import calculator.functions.cosec;
import calculator.functions.cosecd;
import calculator.functions.cot;
import calculator.functions.cotd;
import calculator.functions.fact;
import calculator.functions.logn;
import calculator.functions.max;
import calculator.functions.mean;
import calculator.functions.median;
import calculator.functions.min;
import calculator.operators.greaterThan;
import calculator.operators.greaterThanOrEqualTo;
import calculator.operators.leftShift;
import calculator.operators.lesserThan;
import calculator.operators.lesserThanOrEqualTo;
import calculator.operators.mod;
import calculator.operators.percentage;
import calculator.operators.rightShift;
import calculator.functions.round;
import calculator.functions.secd;
import calculator.functions.sind;
import calculator.functions.tand;
import calculator.functions.trunc;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class IntermediateUtils {
	
	public static double evaluateArithmeticExpression(String expr) throws Exception {
		if(expr == null || expr.isEmpty()) {
			return 0.0;
		}
		
		Pattern p = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\(((?:[^()]+|\\((?:[^()]+|\\([^()]*\\))*\\))+)\\)");
		Matcher m = p.matcher(expr);
		
		if(m.find()) {
			String funcName = m.group(1);
			String argsStr = m.group(2);
			
			ArrayList<String> args = splitTopLevelCommas(argsStr);
			double evaluatedArgs[] = new double[args.size()];
			
			for(int i=0;i<args.size();i++) {
				evaluatedArgs[i] = evaluateArithmeticExpression(args.get(i));
			}
			
			return applyFunction(funcName, evaluatedArgs);
			
		}
		
		ExpressionBuilder exp = new ExpressionBuilder(expr)
				.function(new fact())
				.function(new logn())
				.function(new max())
				.function(new mean())
				.function(new median())
				.function(new min())
				.function(new round())
				.function(new trunc())
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
				.function(new tand())
				.operator(new mod())
				.operator(new percentage())
				.operator(new leftShift())
				.operator(new rightShift())
				.operator(new greaterThan())
				.operator(new lesserThan())
				.operator(new greaterThanOrEqualTo())
				.operator(new lesserThanOrEqualTo());
		
		Expression e = exp.build();
		double result = e.evaluate();
	    return result;
	}
	
	private static double applyFunction(String funcName, double[] args) {
		String expr = funcName + "(" + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(",")) + ")";
		
		ExpressionBuilder exp = new ExpressionBuilder(expr)
				.function(new fact())
				.function(new logn())
				.function(new max())
				.function(new mean())
				.function(new median())
				.function(new min())
				.function(new round())
				.function(new trunc())
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
				.function(new tand())
				.operator(new mod())
				.operator(new percentage())
				.operator(new leftShift())
				.operator(new rightShift())
				.operator(new greaterThan())
				.operator(new lesserThan())
				.operator(new greaterThanOrEqualTo())
				.operator(new lesserThanOrEqualTo());
		
		return exp.build().evaluate();
	}
	
	private static ArrayList<String> splitTopLevelCommas(String argsStr){
		
		ArrayList<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depth = 0;
		
		for(int i=0;i<argsStr.length();i++) {
			char ch = argsStr.charAt(i);
			
			if(ch==',' && depth==0) {
				args.add(current.toString().trim());
			}
			else {
				if(ch=='(') {
					depth++;
				}
				else if(ch==')') {
					depth--;
				}
				else {
					current.append(ch);
				}
			}
			if(current.length()>0) {
				args.add(current.toString().trim());
			}
		}
		
		return args;
	}
}
