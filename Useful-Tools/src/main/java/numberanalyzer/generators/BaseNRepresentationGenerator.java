package numberanalyzer.generators;

import java.util.LinkedHashMap;

import numberanalyzer.categories.BaseNRepresentation;

public class BaseNRepresentationGenerator {

	BaseNRepresentation bnr = new BaseNRepresentation();
	
	public LinkedHashMap<Long, LinkedHashMap<Integer, String>> generateAllBaseNRepresentations(long num){
		LinkedHashMap<Long, LinkedHashMap<Integer, String>> resultMap = new LinkedHashMap<>();
		for(long i=0L;i<=num;i++) {
			LinkedHashMap<Integer, String> res = bnr.findAllBases(i);
			resultMap.put(i, res);
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateAllBinaryRepresentations(long num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<0) {
			for(long i=0L;i>=num;i--)
				resultMap.put(i, bnr.getBinaryRepresentation(i));
		}
		else {
			for(long i=0L;i<=num;i++)
				resultMap.put(i, bnr.getBinaryRepresentation(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateAllOctalRepresentations(long num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<0) {
			for(long i=0L;i>=num;i--)
				resultMap.put(i, bnr.getOctalRepresentation(i));
		}
		else {
			for(long i=0L;i<=num;i++)
				resultMap.put(i, bnr.getOctalRepresentation(i));
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateAllHexRepresentations(long num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<0) {
			for(long i=0L;i>=num;i--)
				resultMap.put(i, bnr.getHexRepresentation(i));
		}
		else {
			for(long i=0L;i<=num;i++)
				resultMap.put(i, bnr.getHexRepresentation(i));
		}
		return resultMap;
	}
}
