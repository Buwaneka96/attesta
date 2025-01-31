package com.fyp.agent.models;

public class Step {
	
	private String type;
	
	private String xpath;
	
	private String value;
	
	private int keycode;
	
	private String keywords;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getXpath() {
		return xpath;
	}

	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	public int getKeycode() {
		return keycode;
	}

	public void setKeycode(int keycode) {
		this.keycode = keycode;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

}
