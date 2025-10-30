package numberanalyzer.categories;

import java.util.LinkedHashMap;

public class NumberSeries {

	public LinkedHashMap<Integer,Long> generateUlamNumbers(int num){
		LinkedHashMap<Integer, Long> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(findUlam(i)) {
				resultMap.put((int)++c, i);
			}
		}
		return resultMap;
	}

	public boolean findUlam(long num) {
		if(num<=0)
			return false;
		int c=0;
		for(long i=1L;i<=num;i++) {
			for(long j=i+1;j<=num;j++) {
				if(i+j==num)
					c++;
			}
		}
		if(c==1)
			return true;
		else
			return false;
	}
	
	public LinkedHashMap<Integer, Long> generateSylvesterSequence(int num){
		LinkedHashMap<Integer, Long> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(int i=1;i<=num;i++) {
			resultMap.put(i, findSylvesterTerm(i));
		}
		return resultMap;
	}
	
	public long findSylvesterTerm(int n) {
		if(n==1)
			return 2;
		else if(n==2)
			return 3;
		else {
			long p=1L;
			for(int i=1;i<n;i++) {
				p*=findSylvesterTerm(i);
			}
			return p;
		}
	}
}
