package org.itri.ccma.safebox.db;

public class EventException extends Exception {
	
	public EventException(String msg){
		super(msg);
	}
	
	public String getMessage()
    {
        return super.getMessage();
    }
}
