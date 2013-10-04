package org.itri.ccma.safebox.util;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.IGlobal;

public class Util {
	static public void Sleep(int ms) {

		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	static public void Yield() {

		Thread.yield();
	}

	static public void moveDirectory(File src, File dest) {
		// Source is not existed
		if (!src.exists()) {
			return;
		}
		// Destination is existed
		if (dest.exists()) {
			for (File child : src.listFiles()) {
				try {
					if (child.isDirectory())
						FileUtils.moveDirectoryToDirectory(child, dest, false);
					else
						FileUtils.moveFileToDirectory(child, dest, false);
				} catch (IOException e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(null, e.getMessage());
				}
			}
			
			src.delete();
		}
		// Destination is not existed
		else {
			try {
				FileUtils.moveDirectoryToDirectory(src, dest.getParentFile(), true);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, e.getMessage());
			}
		}
	}
	
	static public void MsgBox(Frame f, String content) {

		if (f == null) {
			f = new Frame(IGlobal.APP_NAME + " " + IGlobal.APP_VER);
		}
		JOptionPane.showMessageDialog(f, content);
		f.toFront();
		f.setAlwaysOnTop(true);
	}

	static public int ConfirmBox(Frame f, String content) {

		int ret = 0;
		if (f == null) {
			f = new Frame(IGlobal.APP_NAME + " " + IGlobal.APP_VER);
		}
		ret = JOptionPane.showConfirmDialog(f, content, "Safebox", JOptionPane.YES_NO_OPTION);
		f.toFront();
		// f.setAlwaysOnTop(true);
		return ret;
	}

	public static int ReadKey() {
		int k = 0;

		try {
			k = System.in.read();
		} catch (IOException e) {
		}
		return k;
	}

	static public void SendMail(String addr, String content) {
		String host = "smtpx.itri.org.tw";
		String from = "emily.huang@itri.org.tw";
		String[] recipients = new String[5];

		recipients[0] = from;
		recipients[1] = "MacLin@itri.org.tw";
		recipients[2] = "RubioHuang@itri.org.tw";
		recipients[3] = "keanu.pang@itri.org.tw";
		recipients[4] = "MichaelYu@itri.org.tw";

		Properties props = System.getProperties();

		// Setup mail server
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.auth", "true");
		// props.put("mail.debug", "true");
		props.put("mail.smtp.port", "25");
		// Get session
		Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication("A00010", "");
			}
		});
		// Define message
		MimeMessage message = new MimeMessage(session);

		// Set the from address
		try {

			InternetAddress[] addressTo = new InternetAddress[recipients.length];
			for (int i = 0; i < recipients.length; i++) {
				addressTo[i] = new InternetAddress(recipients[i]);
			}

			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO, addressTo);

			// Set the subject
			message.setSubject("CSS status test");

			// Set the content
			message.setText(content);

			// Send message
			Transport.send(message);

		} catch (AddressException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}

	static public String GetUTCTimeString(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		if (date == null)
			date = new Date();

		return sdf.format(date);
	}

	static public String GetTimeStr(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		if (date == null)
			date = new Date();

		return sdf.format(date);
	}

	static public Date GetDateObj(String s) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			return sdf.parse(s);
		} catch (ParseException e) {
			return null;
		}
	}
	
	public void Error(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.ERR, msg);
	}
	public void Info(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.INFO, msg);
	}
	public void Debug(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, msg);
	}
	/*
	static public void WriteObjectToFile(Object object, String filePath) {
		ObjectOutputStream out;
		S3Logger log = S3Logger.GetInstance();
		try {
			File file = new File(filePath);
			if (!file.exists()) {
				file.createNewFile();
			}
			out = new ObjectOutputStream(new FileOutputStream(filePath));
			try {
				out.writeObject(object);
				out.flush();
			} catch (IOException e) {
				log.Info("Fatal exception while writing object: " + e);
				e.printStackTrace();
			} catch (Exception e) {
				log.Info("Fatal exception while writing object: " + e);
				e.printStackTrace();
			} finally {
				out.close();
			}
		} catch (FileNotFoundException e) {
			log.Info("Fatal exception while opening file for writing object: " + e);
			e.printStackTrace();
		} catch (IOException e) {
			log.Info("Fatal exception while opening file for writing object: " + e);
			e.printStackTrace();
		} catch (Exception e) {
			log.Info("Fatal exception while opening file for writing object: " + e);
			e.printStackTrace();
		}
	}
	 */
//	static public Object ReadObjectFromFile(String filePath) {
//		ObjectInputStream in = null;
//		try {
//			(new File(filePath)).createNewFile();
//			in = new ObjectInputStream(new FileInputStream(filePath));
//
//			try {
//				return in.readObject();
//			} catch (Exception e) {
//			} finally {
//				in.close();
//			}
//		} catch (Exception e) {
//		}
//
//		return null;
//	}

	@SuppressWarnings("rawtypes")
	static public void Print(String message, Collection objects) {
		System.out.flush();
		System.out.println("Print: " + message + ":");
		if (objects == null)
			return;
		for (Object object : objects) {
			System.out.println("\t" + object.toString());
		}
		System.out.flush();
	}

	public static int CalcChkSum(String s) {
		int i, sum = 0;

		for (i = 0; i < s.length(); i++) {
			sum += s.charAt(i);
		}
		return sum;
	}

	static public String GetHexStr(byte[] data) {
		String s = "";
		int k;

		if (data != null) {
			for (k = 0; k < data.length; k++) {
				s += Integer.toString((data[k] & 0xff) + 0x100, 16).substring(1);
			}
		}
		return s.toUpperCase();
	}

	static public String GetTimeStr() {

		SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a");
		Date date = new Date();
		String strDate = sdFormat.format(date);

		return strDate;
	}

	public static String bytesToHex(byte[] data) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			buffer.append(byteToHex(data[i]));
		}
		return (buffer.toString());
	}

	public static String byteToHex(byte data) {
		StringBuffer hexString = new StringBuffer();
		hexString.append(toHex((data >>> 4) & 0x0F));
		hexString.append(toHex(data & 0x0F));
		return hexString.toString();
	}

	public static char toHex(int value) {
		if ((0 <= value) && (value <= 9))
			return (char) ('0' + value);
		else
			return (char) ('a' + (value - 10));
	}
	
	public static String getStackTrace(Throwable  throwable) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		throwable.printStackTrace(printWriter);
		
		return result.toString();
	}
	
	public static boolean isStartFromRoot(String localPath) {
		String		path			= localPath;
		String		root			= Config.getInstance().getValue(KEYS.SafeBoxLocation);
		String		slashStr		= "";
		boolean		startFromRoot	= false;

		slashStr += File.separatorChar;
		
		if (root.endsWith(slashStr) == false)
			root += slashStr;
		
		if (path.length() > root.length()) {
			path = localPath.substring(0, root.length());
			
			if (path.equalsIgnoreCase(root))
				startFromRoot = true;
		}

		return startFromRoot;
	}
	
	public static String translateObjectKey(String localPath) {
		String objectKey = "";

		if (isStartFromRoot(localPath)) {
			objectKey = localPath.substring(Config.getInstance().getValue(KEYS.SafeBoxLocation).length());
			objectKey = objectKey.replace('\\', '/');
			
			if (objectKey.startsWith("/"))
				objectKey = objectKey.substring(1);
		}
		
		return objectKey;
	}

	public static String translateLocalPath(String objectKey) {
		String localPath = Config.getInstance().getValue(KEYS.SafeBoxLocation);

		localPath += File.separatorChar + objectKey;
		localPath = localPath.replace('/', File.separatorChar);
		
		return localPath;
	}
	
	public static String urlEncode(String s) {
		/*
		 * try { s= java.net.URLEncoder.encode(s,"utf-8"); } catch
		 * (UnsupportedEncodingException e) {} return s;
		 */
		String s1 = s;
		// s1= s1.replace("_", "%5F");
		s1 = s1.replace("+", "%2B");
		s1 = s1.replace(' ', '+');
		return s1;
	}

	public static String urlDecode(String s) {
		/*
		 * try { s= java.net.URLDecoder.decode(s,"utf-8"); } catch
		 * (UnsupportedEncodingException e) {} return s;
		 */
		String s1 = s;
		String s2, s3;
		int i, iFound, iFrom = 0;

		s1 = s.replace('+', ' ');

		// handle the %xx form and replace them for special characters (+~).
		while (iFrom < (s1.length() - 3)) {
			iFound = s1.indexOf('%', iFrom);
			if (iFound < 0 || (iFound + 3) > s1.length())
				break;
			s2 = s1.substring(iFound, iFound + 3);
			i = ConverNum(s2.charAt(1));
			i <<= 4;
			i |= ConverNum(s2.charAt(2));
			if (i >= 0x20 && i <= 0x7e) {
				s3 = "";
				s3 += (char) i;
				s1 = s1.replace(s2, s3);
				iFrom += 3;
			} else {
				iFrom += 1;
			}
		}

		return s1;
	}
	
	public static int ConverNum(char ch) {
		int n = 0;

		if (ch >= '0' && ch <= '9')
			n = ch - '0';
		else if (ch >= 'A' && ch <= 'F')
			n = 10 + ch - 'A';
		else if (ch >= 'a' && ch <= 'f')
			n = 10 + ch - 'a';
		return n;
	}
}
