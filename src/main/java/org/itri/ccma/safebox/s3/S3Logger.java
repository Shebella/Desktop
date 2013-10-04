package org.itri.ccma.safebox.s3;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.util.Digest;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.HTTPSSecureProtocolSocketFactory;
import org.itri.ccma.safebox.util.ServerConnectionUtil;

public class S3Logger {
	static private final int INFO = 0;
	static private final int DBG = 1;
	static private final int WARN = 2;
	static private final int ERR = 3;
	static private final int WARN_LEVEL_MAX = ERR;
	static private String warnText[] = { "INFO", "DBG ", "WARN", "ERR " };

	private String serverIP = "";
	private String serviceURL = "";

	private String content = "";
	private int counter = 0;
	private Logger log4j;
	private String logPath = "";
	private String logPrefix = "";
	private String osName, hostName, hwID;
	private String osAccount, cssAccount;
	private String sbVer, localIP;
	private String lastOp = "";
	private Digest digest, md5;
	private Boolean connStatus = false;
	private Boolean enableSend = true;

	public String GetHwID() {
		return hwID;
	}

	private static S3Logger instance;

	public static S3Logger GetInstance() {
		if (instance == null)
			instance = new S3Logger();
		return instance;
	}

	public S3Logger() {

		// init values
		logPrefix = "Safebox";
		serverIP = "";
		localIP = "";
		osName = System.getProperty("os.name") + "(" + System.getProperty("os.arch") + ")";
		osAccount = System.getProperty("user.name");
		cssAccount = "";
		sbVer = IGlobal.APP_VER;
		hostName = "";
		hwID = "";
		connStatus = false;
		digest = new Digest("SHA-256");
		md5 = new Digest("MD5");

		try {
			InetAddress localHost = InetAddress.getLocalHost();
			hostName = localHost.getCanonicalHostName();
			localIP = localHost.getHostAddress();
			NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
			if (ni != null) {
				hwID = GetHexStr(ni.getHardwareAddress());
			}
		} catch (UnknownHostException e) {
		} catch (SocketException e) {
		}

		if (hostName == null || hostName.isEmpty())
			hostName = System.getenv("COMPUTERNAME") + "." + System.getenv("USERDOMAIN");

		// init log4j
		log4j = Logger.getLogger("SB");

		// clean old logs
		Logger rootLogger = Logger.getRootLogger();
		FileAppender appender = (FileAppender) rootLogger.getAppender("fileAppender");
		if (appender != null) {
			logPath = appender.getFile().replace('/', '\\');
			// Truncate log file
			FileUtil.emptyFile(logPath);
		}
	}

	public void SetPrefix(String str) {

		logPrefix = str;
	}

	public void Connect(String ip, String acc) {

		if (!serverIP.equals(ip) || !cssAccount.equals(acc) || connStatus == false) {

			serverIP = ip;
			cssAccount = acc;
			connStatus = false;

			if (StringUtils.isNotEmpty(ip)) {
				serverIP = ip;
				serviceURL = ServerConnectionUtil.getSBXServiceURL(serverIP);

				if (StringUtils.isNotEmpty(serviceURL)) {
					connStatus = true;
				}
			}
		}
	}

	private String MakeMsg(int warnLevel, String callerName, String msg) {

		if (warnLevel < 0 || warnLevel > WARN_LEVEL_MAX)
			warnLevel = 0;

		if (!logPrefix.isEmpty()) {
			msg = warnText[warnLevel] + " " + "[" + logPrefix + "] " + callerName + ": " + msg;
		}

		return msg;
	}

	public void Info(String msg) {

		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		log4j.info(MakeMsg(INFO, caller.getClassName(), msg));
	}

	public void Debug(String msg) {

		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		log4j.debug(MakeMsg(DBG, caller.getClassName(), msg));
	}

	public void Warning(String msg) {

		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		log4j.debug(MakeMsg(WARN, caller.getClassName(), msg));
	}

	public void Error(String msg) {

		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		log4j.error(MakeMsg(ERR, caller.getClassName(), msg));
	}

	public void Flush() {

		if (enableSend == false || connStatus == false)
			return;

		UploadContent();
		counter = 0;
		content = "";
	}

	public void SendSingle(String op, String ext, Boolean succ) {

		Flush();
		Send(op, "", succ, true);
		Flush();
	}

	public void Send(String op, String ext, Boolean succ, Boolean sendAnyway) {
		String lineText = "";
		String timeStamp = "";

		if (enableSend == false || connStatus == false)
			return;

		lastOp = op;
		timeStamp = GetDateStr();

		lineText = timeStamp + "\t" + hostName + "\t" + hwID + "\t" + osName + "\t" + osAccount + "\t" + cssAccount
		        + "\t" + localIP + "\t" + serverIP + "\t" + sbVer + "\t" + op + "\t"
		        + (ext.isEmpty() ? "" : ext + "\t") + (succ ? "succ" : "fail") + "\n";
		content += lineText;
		counter += 1;

		// System.out.println(lineText);
		if (counter >= 1) {
			UploadContent();
			counter = 0;
			content = "";
		}
	}

	private Boolean UploadContent() {
		HttpClient client;
		HttpMethodParams params;
		PostMethod method;
		Boolean succeed = false;
		RequestEntity rEnt = null;
		String signature, dateString, path = "/test.jsp";
		String logType = "";
		int i, retry = 3;
		byte[] hashBytes;
		byte[] textBytes;

		if (serverIP.isEmpty() || content.isEmpty())
			return succeed;

		try {
			rEnt = new StringRequestEntity(content, null, null);
		} catch (UnsupportedEncodingException e) {
		}

		if (rEnt == null)
			return false;

		client = new HttpClient();
		method = new PostMethod(serviceURL + path);
		params = method.getParams();

		dateString = GetDateStr();
		signature = "POST " + path + " HTTP/1.1\n";
		signature += "Host: " + serverIP + "\n";
		signature += "Date: " + dateString + "\n";
		textBytes = signature.getBytes();
		hashBytes = digest.calcValue(textBytes, textBytes.length);
		signature = GetHexStr(hashBytes);
		textBytes = content.getBytes();
		hashBytes = md5.calcValue(textBytes, textBytes.length);
		// String s= GetHexStr(hashBytes);

		// System.out.println("signature=" + signature);
		// System.out.println("md5=" + s);
		if (lastOp.equals("REG_USR"))
			logType = "registration";
		else if (lastOp.equals("PRE_INST") || lastOp.equals("POST_INST") || lastOp.equals("PRE_UNINST")
		        || lastOp.equals("POST_UNINST"))
			logType = "installation";
		else
			logType = "file-operation";

		params.setParameter(HttpMethodParams.SO_TIMEOUT, 3000);
		params.setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
		method.addRequestHeader("Host", serverIP);
		method.addRequestHeader("Date", dateString);
		method.addRequestHeader("Authentication", "CCMA123"/* signature */);
		method.addRequestHeader("Content-Length", String.valueOf(content.length()));
		method.addRequestHeader("ETag", GetHexStr(hashBytes)); // "Content-MD5"
		method.addRequestHeader("Log-type", logType);
		method.setRequestEntity(rEnt);

		if (serviceURL.startsWith("https")) {
			int port = 443;
			i = serviceURL.lastIndexOf(':');
			if (i > 0)
				port = Integer.valueOf(serviceURL.substring(i + 1));
			Protocol https = new Protocol("https", new HTTPSSecureProtocolSocketFactory(), port);
			Protocol.registerProtocol("https", https);
		}

		do {
			try {
				int statusCode = client.executeMethod(method);
				if (statusCode != HttpStatus.SC_OK) {
					System.err.println("Method failed: " + method.getStatusLine());
				} else {
					// byte[] responseBody = method.getResponseBody();
					// System.out.println(new String(responseBody));
					succeed = true;
				}
			} catch (HttpException e) {
				System.err.println("Fatal protocol violation: " + e.getMessage());

			} catch (IOException e) {
				System.err.println("Fatal transport error: " + e.getMessage());
			} finally {
				method.releaseConnection();
			}
		} while (succeed == false && (retry--) > 0);

		if (serviceURL.startsWith("https")) {
			Protocol.unregisterProtocol("https");
		}

		if (succeed == false && retry <= 0) {
			connStatus = false;
		}

		return succeed;
	}

	public void RemoveEventLog(String ip, String cssAccount) {
		// TODO need to update

		final String url = "jdbc:postgresql://" + ip + ":5432/safebox_logging";
		final String sql = "DELETE FROM opt_log WHERE opt_log.cssact = '" + cssAccount + "'";
		Connection con = null;
		Statement select = null;

		try {
			Class.forName("org.postgresql.Driver");
		} catch (Exception e) {
			System.out.println("Failed to load postgresql driver.");
			return;
		}

		try {
			con = DriverManager.getConnection(url, "postgres", "");
			select = con.createStatement();
			select.executeUpdate(sql);
		} catch (Exception e) {
			this.Info(e.getMessage());
		} finally {
			try {
				if (select != null)
					select.close();
				if (con != null)
					con.close();
			} catch (SQLException e1) {
			}
		}
	}

	private String GetHexStr(byte[] data) {
		String s = "";
		int k;

		if (data != null) {
			for (k = 0; k < data.length; k++) {
				s += Integer.toString((data[k] & 0xff) + 0x100, 16).substring(1);
			}
		}
		return s.toUpperCase();
	}

	private String GetDateStr() {
		String t;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		t = sdf.format(new Date());
		return t;
	}

}
