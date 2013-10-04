package org.itri.ccma.safebox.db;

public class ObjectException extends Exception {
	
	public ObjectException(String msg){
		super(msg);
	}
	
	public String getMessage()
    {
        return super.getMessage();
    }
}
