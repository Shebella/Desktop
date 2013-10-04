package org.itri.ccma.safebox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.HTTPSSecureProtocolSocketFactory;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.SntpClient;
import org.itri.ccma.safebox.util.Util;

public class Config {

	public static int BUCKET_FILES_COUNT = 0;
	public static int BUCKET_FOLDERS_COUNT = 0;
	
	public static enum KEYS {
		HostIP, AccessKeyID, SecretAccessKey, SafeBoxLocation, User, Password, 
		InstanceKey, InstanceName, EncryptDir, DefaultBucket, EventSyncWorkerCount, 
		DataRootPath, LastSyncID, 
		NormalShutdown // including abnormal exit, full sync not finish 
	};

	private static Config _instance = new Config();

	private Properties _content = new Properties();
	
	public static final String DB_NAME = "fileInfo.db";
	public static final String APP_CFG_FILE = "safebox.cfg";
	public static final String PORTFILE_HOME = System.getProperty("user.home") + File.separator + ".safebox.dat" + File.separator + "portFile";
	public static final String MY_DOCUMENTS = String.valueOf(javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory());
	public static final String defaultUserDataRoot = ".safebox.dat";
	private static final String INSTALL_PATH = "InstallPath";
	private static final String SBX_REG_PATH = "HKEY_LOCAL_MACHINE\\Software\\Safebox";
	private static final String XP_REG = "! REG.EXE VERSION 3.0";
	
	static public final int DIFFTYPE_NONE = 0;
	static public final int DIFFTYPE_CONN = 1;
	static public final int DIFFTYPE_FOLDER = 2;
	static public final int DIFFTYPE_OPT = 4;
	public static final int EVENT_SYNC_WORKER_COUNT = 40;
	public static final int DEFAULT_LAST_SYNC_ID = -1;

	static public String osName = "";
	static public String userHome = System.getProperty("user.home");
	static public String dataRootPath = userHome + File.separatorChar + defaultUserDataRoot;
	static public String tmpDir = "";
	static public String serverAppValues = "";
	static public String localAppValues = "";
	static public String macAddress = "";
	static public long stdTimeOffset = 0;

	public String cfgFile = "";
	public String hardwareId = "";
	public String localIP = "";

	public String lastSecretKey = "";
	public String connText = "Not Logged in";
	public Boolean connStatus = false;
	public int instantID = 0; // Set in Command-line Mode to identify instant's ID. 0 means it is in Desktop Mode.
	public Set<String> encryptDir = new HashSet<String>();

//	private long lastSyncId = -1;
	public static final String DIR_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

	private Config() {

	}

	public static Config getInstance() {
		return _instance;
	}

	static public Boolean IsWinOS() {

		if (osName.isEmpty())
			osName = System.getProperty("os.name");
		if (osName.startsWith("Windows"))
			return true;
		else
			return false;
	}

	static public String GetInstDataPath() {

		return userHome + File.separatorChar + defaultUserDataRoot + File.separatorChar + "inst.txt";
	}

	public String GetWebURL() {
		String ip = getValue(KEYS.HostIP);

		if (ip.isEmpty())
			return "";

		return ServerConnectionUtil.getWebURL(ip);
	}

	public long getLastSyncId() {
		return Long.parseLong(getValue(KEYS.LastSyncID));
	}

	public void setLastSyncId(long syncId) {
		setValue(KEYS.LastSyncID, String.valueOf(syncId));
	}

	public String getName() {
		String name = "Safebox";
		// instance key might be empty, so return a formatted one
		if (instantID != 0) {
			name = getValue(KEYS.User);
			name += "- " + instantID;
		}
		return name;
	}

	public void load(String fileName) {
		osName = System.getProperty("os.name");
		// usrName = System.getProperty("user.name");
		tmpDir = System.getProperty("java.io.tmpdir");

		if (fileName == null)
			cfgFile = userHome + File.separatorChar + defaultUserDataRoot + File.separatorChar + APP_CFG_FILE;
		else
			cfgFile = fileName;

		// CalcTimeOffset();
		_calcMacAddr();

		// rootPath = userHome + slash + "My Documents" + slash + "Safebox";

		if (new File(cfgFile).exists()) { // this will be executed in console
			                              // mode
			try {
				_content.load(new FileInputStream(cfgFile));

				// Default value
				setValue(KEYS.User, getValue(KEYS.User).toLowerCase());
				
				if (StringUtils.isEmpty(getValue(KEYS.InstanceName)))
					setValue(KEYS.InstanceName, "SafeBox");
				if (StringUtils.isEmpty(getValue(KEYS.DefaultBucket)))
					setValue(KEYS.DefaultBucket, "bkt-" + getValue(KEYS.User));
				if (StringUtils.isEmpty(getValue(KEYS.EventSyncWorkerCount)))
					setValue(KEYS.EventSyncWorkerCount, String.valueOf(EVENT_SYNC_WORKER_COUNT));
				if (StringUtils.isEmpty(getValue(KEYS.DataRootPath))) {
					int index = cfgFile.lastIndexOf(File.separatorChar);
					setValue(KEYS.DataRootPath, cfgFile.substring(0, index));
				}
				if (StringUtils.isEmpty(getValue(KEYS.LastSyncID)))
					setValue(KEYS.LastSyncID, String.valueOf(DEFAULT_LAST_SYNC_ID));

				// Sync Dir validation
				String strSyncDir = getValue(KEYS.SafeBoxLocation);
				/**
				 * This will cause error in console mode if the sync folder path
				 * is wrong
				 */
				if (!new File(strSyncDir).isDirectory()) {
					//strSyncDir = userHome + File.separatorChar + "My Documents" + File.separatorChar + "Safebox";
					strSyncDir = MY_DOCUMENTS + File.separatorChar + "Safebox";
					setValue(KEYS.SafeBoxLocation, strSyncDir);
				}

				dataRootPath = getValue(KEYS.DataRootPath);

				storeProperties();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else { // Create default empty safebox.cfg, this will be executed in
			     // GUI mode in the first time
			for (KEYS key : KEYS.values())
				_content.setProperty(key.toString(), "");

			setValue(KEYS.InstanceName, "SafeBox");
			//setValue(KEYS.SafeBoxLocation, userHome + File.separatorChar + "My Documents" + File.separatorChar + "Safebox");
			setValue(KEYS.SafeBoxLocation, MY_DOCUMENTS + File.separatorChar + "Safebox");
			setValue(KEYS.DataRootPath, userHome + File.separatorChar + defaultUserDataRoot);
			setValue(KEYS.EventSyncWorkerCount, String.valueOf(EVENT_SYNC_WORKER_COUNT));

			_loadAppValues();
			
			setValue(KEYS.HostIP, _getAppValue("ServicePoint"));
			setValue(KEYS.LastSyncID, String.valueOf(DEFAULT_LAST_SYNC_ID));

			storeProperties();
		}
		// If Sync folder is not exist, it should be created.
		// Normally, it will happen in the first install time.
		FileUtil.makeDir(getValue(KEYS.SafeBoxLocation));
	}

	public String getValue(KEYS key) {
		String strResult = _content.getProperty(key.toString());
		// For debug
		// System.out.println("Config Key: " + key.toString() + " Value: " +
		// strResult);

		return strResult;
	}

	public void setValue(KEYS key, String value) {
		_content.setProperty(key.toString(), value);
	}

	public boolean CheckAvailable() {
		boolean available = true;

		if (getValue(KEYS.HostIP) == null || getValue(KEYS.HostIP).isEmpty() || getValue(KEYS.AccessKeyID) == null || getValue(KEYS.AccessKeyID).isEmpty()
		        || getValue(KEYS.SecretAccessKey) == null || getValue(KEYS.SecretAccessKey).isEmpty() || getValue(KEYS.DefaultBucket) == null
		        || getValue(KEYS.DefaultBucket).isEmpty() || getValue(KEYS.SafeBoxLocation) == null || getValue(KEYS.SafeBoxLocation).isEmpty() || cfgFile.isEmpty()) {
			available = false;
		}

		return available;
	}

	public Boolean CheckVersion(String appVer, String webAddr) {
		String ver;
		int curVer = 0;
		int lastVer = 0;

		ver = _getAppValue("SafeboxVer");
		if (!ver.isEmpty() && !appVer.isEmpty()) {
			ver = ver.replace(".", "");
			ver = ver.replace("v", "");
			ver = ver.replace("V", "");

			try {
				lastVer = Integer.parseInt(ver);
			} catch (NumberFormatException e) {
				return false;
			}

			ver = appVer;
			ver = ver.replace(".", "");
			ver = ver.replace("v", ""); // skip v
			ver = ver.replace("V", "");
			ver = ver.replace("a", ""); // skip miner num
			ver = ver.replace("b", "");
			ver = ver.replace("c", "");
			ver = ver.replace("d", "");

			try {
				curVer = Integer.parseInt(ver);
			} catch (NumberFormatException e) {
				return false;
			}

			if (curVer < lastVer) {
				return true;
			}
		}
		return false;
	}

	public Boolean IsEncryptDir(String dirName) {
		Boolean isEncrypt = false;

		for (String key : encryptDir) {
			if (key.equals(dirName)) {
				isEncrypt = true;
				break;
			}
		}
		return isEncrypt;
	}

	public void SetEncryptDir(String dirName, Boolean encrypt) {
		Boolean found = false;

		for (String key : encryptDir) {
			if (key.equals(dirName)) {
				found = true;
				break;
			}
		}

		if (encrypt == true && !found) {
			encryptDir.add(dirName);
		} else if (encrypt == false && found) {
			encryptDir.remove(dirName);
		}
	}

	public void SetEncryptDir(Set<String> encryptDirNew) {

		encryptDir.clear();
		encryptDir.addAll(encryptDirNew);
	}

	public void storeProperties() {

		try {
			// Console scripts will save Password field, should be corrected
			// _content.remove(KEYS.Password);
			_content.store(new FileOutputStream(cfgFile), "Safebox");
		} catch (FileNotFoundException fnfe) {
			Error(Util.getStackTrace(fnfe));
		} catch (IOException ioe) {
			Error(Util.getStackTrace(ioe));
		}
	}

	public boolean loadAccessKey(String password) {
		String serviceURL = ServerConnectionUtil.getSBXServiceURL(getValue(KEYS.HostIP));
		if (StringUtils.isEmpty(serviceURL)) {
			return false;
		}

		GetMethod method = new GetMethod(serviceURL + "/check.jsp");
		HttpMethodParams params = method.getParams();
		Boolean succeed = false;
		int i;

		if (getValue(KEYS.HostIP).isEmpty() || getValue(KEYS.User).isEmpty() || password.isEmpty())
			return succeed;

		// fixme: to use normal function
		// client.setConnectionTimeout(4000);
		params.setParameter(HttpMethodParams.SO_TIMEOUT, 3000);
		params.setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
		method.addRequestHeader("user", getValue(KEYS.User));
		method.addRequestHeader("password", password);

		method.addRequestHeader("hardware_id", macAddress);
		method.addRequestHeader("instance_name", getValue(KEYS.InstanceName));

		if (serviceURL.startsWith("https")) {
			int port = 443;
			i = serviceURL.lastIndexOf(':');
			if (i > 0)
				port = Integer.valueOf(serviceURL.substring(i + 1));
			Protocol https = new Protocol("https", new HTTPSSecureProtocolSocketFactory(), port);
			Protocol.registerProtocol("https", https);
		}

		this.connStatus = false;
		this.connText = "Registration failed";

		try {
			int statusCode = new HttpClient().executeMethod(method);
			if (statusCode != HttpStatus.SC_OK) {
				System.err.println("Method failed: " + method.getStatusLine());
				if (statusCode == 400)
					this.connText += ": Bad Request";
				else if (statusCode == 401)
					this.connText += ": Unauthorized";
				else
					this.connText += ": " + method.getStatusLine().getReasonPhrase();
			} else {
				// Read the response body.
				byte[] responseBody = method.getResponseBody();
				String rsp = new String(responseBody);
				rsp = rsp.replaceAll("\r", "").replaceAll("\n", "");
				String[] tokens = rsp.split("\t");
				for (i = 0; i < tokens.length; i++) {
					if (tokens[i].equals(getValue(KEYS.User)) && (i + 2) < tokens.length) {
						setValue(KEYS.AccessKeyID, tokens[i + 1]);
						setValue(KEYS.SecretAccessKey, tokens[i + 2]);
						
						if(StringUtils.isEmpty(getValue(KEYS.InstanceKey))) {
							if ((i + 3) < tokens.length) {
								setValue(KEYS.InstanceKey, tokens[i + 3]);
							}
						}
						
						// added by keanu 20120711: instanceKey
						/*if ((i + 3) < tokens.length) {
							setValue(KEYS.InstanceKey, tokens[i + 3]);
						}*/

						break;
					}
				}
				if (!getValue(KEYS.AccessKeyID).isEmpty() && !getValue(KEYS.SecretAccessKey).isEmpty()) {
					succeed = true;
					this.connStatus = true;
					this.connText = "Registration succeed";
				}
			}
		} catch (HttpException e) {
			System.err.println("Fatal protocol violation: " + e.getMessage());
			this.connText = "Registration failed: " + e.getMessage();
		} catch (IOException e) {
			System.err.println("Fatal transport error: " + e.getMessage());
			this.connText = "Registration failed: " + e.getMessage();
		} finally {
			method.releaseConnection();
		}

		if (serviceURL.startsWith("https")) {
			Protocol.unregisterProtocol("https");
		}

		return succeed;
	}

	private void _loadAppValues() {
//		String preferedPath = getValue(KEYS.DataRootPath) + File.separatorChar + "safebox.txt";

		
//		String localMachine = "HKEY_LOCAL_MACHINE\\Software\\Safebox";

//        String installsPath = "InstallPath";

        /*String programFiles = WindowsReqistry.readRegistry(SBX_REG_PATH, INSTALL_PATH);

        if (programFiles.indexOf(XP_REG) != -1) {
        	programFiles = programFiles.replace(XP_REG, "");
        }
        
        String preferedPath = programFiles.trim() + File.separatorChar + "safebox.txt";
*/
		
		String preferedPath = IGlobal.APP_PATH + "safebox.txt";
		
        LoggerHandler _logger = LoggerHandler.getInstance();
        _logger.info(LoggerType.Main, "safebox.txt path : " + preferedPath);
        
		String s;
		File f = new File(preferedPath);

		localAppValues = "";
		serverAppValues = "";

		if (f.exists() && f.canRead()) {
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(f));
				while ((s = reader.readLine()) != null) {
					localAppValues += s + "\n";
				}
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			} finally {
				try {
					if (reader != null) {
						reader.close();
					}
				} catch (IOException e) {
				}
			}
		}

	}

	private String _getAppValue(String name) {
		String value = "";
		String appValues;
		String[] tokens;
		int i;

		appValues = localAppValues;
		tokens = appValues.split("\n");
		for (i = 0; i < tokens.length; i++) {
			if (tokens[i].startsWith(name)) {
				value = tokens[i].substring(name.length() + 1);
				value = value.replace("\r", "").replace("\n", "").replace("=", "");
				break;
			}
		}

		if (value.isEmpty()) {
			appValues = serverAppValues;
			tokens = appValues.split("\n");
			for (i = 0; i < tokens.length; i++) {
				if (tokens[i].startsWith(name)) {
					value = tokens[i].substring(name.length() + 1);
					value = value.replace("\r", "").replace("\n", "").replace("=", "");
					break;
				}
			}
		}
		return value;
	}

	public void setEmptyAccessKey() {
		setValue(KEYS.SecretAccessKey, "");
		setValue(KEYS.AccessKeyID, "");
		
		connStatus = false;
		connText = "Not Logged in";
	}

	public void calcTimeOffset() {
		stdTimeOffset = SntpClient.GetStdTimeOffset();
	}

	private void _calcMacAddr() {

		// added by keanu, get hardware mac address
		macAddress = "";
		try {
			Enumeration<NetworkInterface> inetEnum = NetworkInterface.getNetworkInterfaces();
			while (inetEnum.hasMoreElements()) {
				NetworkInterface inet = inetEnum.nextElement();
				byte[] macArr = inet.getHardwareAddress();
				if (null != macArr) {
					StringBuilder sb = new StringBuilder(18);
					for (byte b : macArr) {
						if (sb.length() > 0)
							sb.append(':');
						sb.append(String.format("%02x", b));
					}

					macAddress = sb.toString();
					if (StringUtils.isNotEmpty(macAddress) && !macAddress.startsWith("00:00:00:00")) {
						break;
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private static void _printInstanceInfo(String server, String account, int inst_seq) {
		String[] status = Main.getStautsInPort(server, account, inst_seq);
		if (null != status && StringUtils.isNotEmpty(status[2])) {
			ServerSocket socket = null;

			try {
				socket = new ServerSocket(Integer.valueOf(status[2]));
			} catch (IOException e) {
				// port is open
				System.out.println("Server: " + server + "\tSeq: " + inst_seq + "\tUser: " + account + "\tStatus: " + status[1] + "\tPID: " + status[0] + "\tPort: " + status[2]);
			} finally {
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	public static void listInstNum(String config_path) {
		Config userConfig = Config.getInstance();
		if (StringUtils.isNotEmpty(config_path)) {
			userConfig.load(config_path);
		}

		if (null != userConfig && StringUtils.isNotEmpty(userConfig.getValue(KEYS.HostIP)) && StringUtils.isNotEmpty(userConfig.getValue(KEYS.User))) {

			List<String> instanceList = getAllInstancesByAccount(userConfig.getValue(KEYS.HostIP), userConfig.getValue(KEYS.User));
			for (String instance : instanceList) {
				_printInstanceInfo(userConfig.getValue(KEYS.HostIP), userConfig.getValue(KEYS.User), Integer.valueOf(instance));
			}
		} else {
			List<String> serverList = getAllServers();
			if (null == serverList) {
				return;
			}

			for (String server : serverList) {
				List<String> accountList = getAllAccountsByServer(server);

				for (String account : accountList) {
					List<String> instanceList = getAllInstancesByAccount(server, account);

					for (String instance : instanceList) {
						_printInstanceInfo(server, account, Integer.valueOf(instance));
					}
				}
			}
		}
	}

	public static List<String> getAllServers() {
		List<String> serverList = Arrays.asList(new File(PORTFILE_HOME).list());
		if (null != serverList) {
			return serverList;
		}

		return new ArrayList<String>();
	}

	public static List<String> getAllAccountsByServer(String server) {
		if (StringUtils.isNotEmpty(server)) {
			List<String> accountList = Arrays.asList((new File(PORTFILE_HOME + File.separator + server)).list());

			if (null != accountList) {
				return accountList;
			}
		}

		return new ArrayList<String>();
	}

	public static List<String> getAllInstancesByAccount(String server, String account) {
		if (StringUtils.isNotEmpty(server) && StringUtils.isNotEmpty(account)) {
			List<String> instanceList = Arrays.asList((new File(PORTFILE_HOME + File.separator + server + File.separator + account)).list());
			if (null != instanceList) {
				return instanceList;
			}
		}

		return new ArrayList<String>();

	}
	
	public void Error(Throwable t) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Root, LoggerHandler.ERR, Util.getStackTrace(t));
	}

	public void Error(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Root, LoggerHandler.ERR, msg);
	}

	public void Info(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Root, LoggerHandler.INFO, msg);
	}

	public void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Root, LoggerHandler.DBG, msg);
	}
}