package org.itri.ccma.safebox.util;

public class SafeboxException extends Exception {

	/** Creates a new instance of SafeboxException */
	
	private static final long serialVersionUID = 7448755016016586112L;

	
	public SafeboxException() {
	}

	public SafeboxException(String message) {
		super(message);
	}

	public String getMessage() {
		return super.getMessage();
	}

	public SafeboxException(Exception e) {
		super(e);
	}

	public SafeboxException(Throwable e) {
		super(e);
	}

	public SafeboxException(String message, Throwable e) {
		super(message, e);
	}

}
