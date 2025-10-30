package numberanalyzer.utilities;

import java.util.ArrayList;
import java.util.HashMap;

public class RequestData {

	private HashMap<String, ArrayList<String>> choiceMap;
	private int terms;
	private String choice;
	
	public HashMap<String, ArrayList<String>> getChoiceMap() {
		return this.choiceMap;
	}
	
	public int getTerms() {
		return this.terms;
	}
	
	public void setChoiceMap(HashMap<String, ArrayList<String>> choiceMap) {
		this.choiceMap = choiceMap;
	}
	
	public void setTerms(int terms) {
		this.terms = terms;
	}
	
	public String getChoice() {
		return this.choice;
	}
	
	public void setChoice(String choice) {
		this.choice = choice;
	}
}
