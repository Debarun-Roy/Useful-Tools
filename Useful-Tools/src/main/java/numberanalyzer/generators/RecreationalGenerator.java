package numberanalyzer.generators;

import java.util.LinkedHashMap;

import numberanalyzer.categories.Recreational;

public class RecreationalGenerator {

	Recreational rec = new Recreational();
	
	public LinkedHashMap<Long, String> generateArmstrong(int n){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(n<=0)
			return resultMap;
		for(long i=1L,c=0;c<n;i++) {
			if(rec.isArmstrong(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateHarshad(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isHarshad(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateDisarium(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isDisarium(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateHappy(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isHappy(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateSad(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(!rec.isHappy(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateDuck(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isDuck(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateDudeney(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isDudeney(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateBuzz(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isBuzz(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSpy(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isSpy(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateKaprekar(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isKaprekar(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateTech(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isTech(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateMagic(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isMagic(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateSmith(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isSmith(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateMunchausen(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isMunchausen(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateRepdigits(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isRepdigits(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateGapful(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isGapful(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	

	public LinkedHashMap<Long, String> generateHungry(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isHungry(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generatePronic(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isPronic(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}

	public LinkedHashMap<Long, String> generateNeon(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isNeon(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
	
	public LinkedHashMap<Long, String> generateAutomorphic(int num){
		LinkedHashMap<Long, String> resultMap = new LinkedHashMap<>();
		if(num<=0)
			return resultMap;
		for(long i=1L,c=0;c<num;i++) {
			if(rec.isAutomorphic(i)) {
				resultMap.put(++c,String.valueOf(i));
			}
		}
		return resultMap;
	}
}
