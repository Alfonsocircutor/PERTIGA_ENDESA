package com.cemitec.circuitorBlue;

public class visualLog {
	String time;
	String message;
	
	visualLog(String t, String m){
		time=t;
		message=m;
	}
	
	public String getTime(){
		return time;
	}
	public String getMessage(){
		return message;
	}
}
