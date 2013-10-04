package org.itri.ccma.safebox.util;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Digest {
	MessageDigest md = null;
	
	public Digest(String algo) { //ex"SHA-256"
		try {
			md= MessageDigest.getInstance(algo);
		}
		catch ( NoSuchAlgorithmException e) {
			System.out.println(e.getMessage());
		}
	}

	public byte[] calcValue(byte[] dataBuffer, int len) {
		if( md!=null ) {
			md.reset();
			md.update(dataBuffer, 0, len);
			byte[] mdbytes = md.digest();
			return mdbytes;
		}
		return null;
	}
}
