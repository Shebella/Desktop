package org.itri.ccma.safebox.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher; 
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey; 
import javax.crypto.spec.IvParameterSpec; 
import javax.crypto.CipherInputStream; 
import javax.crypto.CipherOutputStream;  
import java.security.spec.AlgorithmParameterSpec; 
import javax.crypto.spec.SecretKeySpec;  

public class Crypto {
	Cipher ecipher;
	Cipher dcipher;
	// Buffer used to transport the bytes from one stream to another     
	byte[] buf = new byte[2048];      

	public Crypto(byte[] key) {		
		byte[] key0= new byte[16];
		int i;

		for(i=0; i<key.length && i<16; i++)
			key0[i]= key[i];
		for(i=key.length; i<16; i++)
			key0[i]= 0;
		SecretKeySpec skey = new SecretKeySpec(key0, "AES"); 
		setupCrypto(skey);	
	}

	public Crypto(String key) {
		SecretKeySpec skey = new SecretKeySpec(getMD5(key), "AES");
		setupCrypto(skey);
	} 
	
	private static byte[] getMD5(String input) {         
		try {
			byte[] bytesOfMessage = input.getBytes("UTF-8");         
			MessageDigest md = MessageDigest.getInstance("MD5");             
			return md.digest(bytesOfMessage);         
		} catch (Exception e) {              
			return null;
		}
	}
	
	private void setupCrypto(SecretKey key) {
		// Create an 8-byte initialization vector
		byte[] iv = new byte[] {
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
		};

		AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);				
		
		try {
			ecipher = Cipher.getInstance("AES/CBC/NoPadding"); //"AES/CBC/PKCS5Padding"
			dcipher = Cipher.getInstance("AES/CBC/NoPadding");
			// CBC requires an initialization vector             
			ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);             
			dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public byte[] encrypt(byte[] in) {
		byte[] ciphertext = null;
		try {
			ciphertext = ecipher.doFinal(in);
		}
		catch (IllegalBlockSizeException e) {
			System.out.println(e.getMessage());
		}
		catch (BadPaddingException e) {
			e.printStackTrace();
		}
		return ciphertext; 
	}
	
	public byte[] decrypt(byte[] in) {
		byte[] plaintext = null;
		try {
			plaintext= dcipher.doFinal(in);             
		}
		catch (BadPaddingException e) {
//			e.printStackTrace();              
		}
		catch (IllegalBlockSizeException e) {
//			e.printStackTrace();              
		}		
		return plaintext;
	}
		
	public void encrypt(InputStream in, OutputStream out) {
		try {
			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, ecipher);
			
			// Read in the cleartext bytes and write to out to encrypt             
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {                 
				out.write(buf, 0, numRead);             
			}
			out.close();
		}         
		catch (java.io.IOException e) {             
			e.printStackTrace();         
		}
	}

	public void decrypt(InputStream in, OutputStream out) {         
		try {             
			// Bytes read from in will be decrypted             
			in = new CipherInputStream(in, dcipher);              
			// Read in the decrypted bytes and write the cleartext to out             
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {                 
				out.write(buf, 0, numRead);             
			}             
			out.close();         
		} catch (java.io.IOException e) {              
			e.printStackTrace();         
		}     
	} 	
}
