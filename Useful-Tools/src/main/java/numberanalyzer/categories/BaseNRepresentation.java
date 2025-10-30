package numberanalyzer.categories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

public class BaseNRepresentation {

	//1-9, A-Z, a-z - digits in order
	public ArrayList<Character> getBaseRepresentation(long num, int base){
		if(base<=1&&base>=62)
			return new ArrayList<>();
		ArrayList<Character> list = new ArrayList<>();
		if(num<0)
			num=Math.abs(num);
		while(num>0) {
			char ch;
			int r = (int)num%base;
			if(r>=10&&r<=35) {
				ch = (char)(55+r);
			}
			else if(r>=36&&r<=61) {
				ch = (char)(61+r);
			}
			else {
				ch = (char)(48+r);
			}
			list.add(ch);
			num/=base;
		}
		Collections.reverse(list);
		return list;
	}

	public LinkedHashMap<Integer, String> findAllBases(long num){

		LinkedHashMap<Integer, String> resultMap = new LinkedHashMap<>();
		for(int i=2;i<=62;i++) {
			ArrayList<Character> st =  getBaseRepresentation(num, i);
			StringBuilder nst = new StringBuilder();
			st.forEach(nst::append);
			resultMap.put(i, nst.toString());
		}
		return resultMap;
	}

	public String getBinaryRepresentation(long num) {
		ArrayList<Character> nstr = getBaseRepresentation(num, 2);
		StringBuilder nst = new StringBuilder();
		
		//if number is negative, find 2's complement
		if(num<0) {
			for(int i=0;i<nstr.size();i++) {
				char ch = nstr.get(i);
				if(ch=='1')
					ch='0';
				else
					ch='1';
				nstr.set(i, ch);
			}
			boolean flagNoZero = true;
				for(int i=nstr.size()-1;i>=0;i--) {
					if(nstr.get(i)=='1') {
						nstr.set(i, '0');
					}
					else {
						flagNoZero = false;
						nstr.set(i, '1');
						break;
					}
			}
			if(flagNoZero==true) {
				nstr.add(0, '1');
			}
		}
		nstr.forEach(nst::append);
		String st = nst.toString();
		return st;
	}
	
	public String getOctalRepresentation(long num) {
		ArrayList<Character> nstr = getBaseRepresentation(num, 8);
		StringBuilder nst = new StringBuilder();
		//if negative, find 8's complement
		if(num<0){
			for(int i=0;i<nstr.size();i++) {
				int d = (int)nstr.get(i)-48;
				int cd = 7-d;
				nstr.set(i, (char)(cd+48));
			}
			boolean flagAllSeven = true;
			for(int i=nstr.size()-1;i>=0;i--) {
				if((int)(nstr.get(i)-48)==7) {
					nstr.set(i, '0');
				}
				else {
					flagAllSeven = false;
					nstr.set(i, (char)(((int)nstr.get(i)-47)+48));
				}
			}
			if(flagAllSeven==true) {
				nstr.add(0, '1');
			}
		}
		nstr.forEach(nst::append);
		String st = nst.toString();
		return st;
	}
	
	public String getHexRepresentation(long num) {
		ArrayList<Character> nstr = getBaseRepresentation(num, 16);
		StringBuilder nst = new StringBuilder();
		//if negative, find G's complement
		if(num<0) {
			//F's complement
			for(int i=0;i<nstr.size();i++) {
				if(Character.isAlphabetic(nstr.get(i))) {
					int d = 70-(int)(nstr.get(i));
					nstr.set(i, (char)(d+48));
				}
				else {
					int d = (int)(nstr.get(i)-48);
					int diff = 70-d;
					char ch = (char)diff;
					if(diff<65) {
						int df = 65-diff;
						ch = (char)(58-df);
					}
					nstr.set(i, ch);
				}
			}
			//Adding 1 to this
			boolean flagAllF = true;
			for(int i=0;i<nstr.size();i++) {
				if(nstr.get(i)=='F') {
					nstr.set(i, '0');
				}
				else if((int)(nstr.get(i)-48)==9){
					flagAllF = false;
					nstr.set(i, 'A');
					break;
				}
				else {
					flagAllF = false;
					nstr.set(i, (char)((int)nstr.get(i)+1));
					break;
				}
			}
			if(flagAllF==true) {
				nstr.add(0, '1');
			}
		}
		nstr.forEach(nst::append);
		String st = nst.toString();
		return st;
	}
}