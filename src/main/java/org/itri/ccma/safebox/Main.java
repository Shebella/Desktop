package org.itri.ccma.safebox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.ui.ConfigDialog;
import org.itri.ccma.safebox.ui.TrayIconHandler;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.Ping;
import org.itri.ccma.safebox.util.SafeBoxUtils;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.Util;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;

public class Main {
	public static final String APP_PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	public static final CountDownLatch shutdownLatch = new CountDownLatch(1);

	private static Config _config = Config.getInstance();
	private static LoggerHandler _logger = LoggerHandler.getInstance();

	public void mainEntrance(String[] args) {
		Boolean canSkipInstanceCheck = false;

		Debug("Process Command");
		Command command = Command.getInstance();
		// Send command to host app
		if (command.sendCommand(args) == true)
			System.exit(0);
		// Init Config
		_config.load(null);
		_config.CheckAvailable();

		Debug("Loading config file: " + _config.cfgFile);

		if (args.length >= 1) {
			if (args[0].equalsIgnoreCase("-cmp") || args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("-h")) {
				canSkipInstanceCheck = true;
			}
		}

		Debug("Check instance");
		// Detect Instance
		if (!canSkipInstanceCheck && ApplicationInstanceManager.registerInstance(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), 0) == false) {
			System.out.println("Another instance of this application is already running. Exiting...");
			if (args.length > 0) {
				Util.ReadKey();
			}
			System.exit(0);
		}

		Debug("Setup message listener.");
		// Setup message listener
		ApplicationInstanceManager.setApplicationInstanceListener(new ApplicationInstanceListener() {
			public void newInstanceCreated() {
				System.out.println("New instance detected...");
			}

			public void noticeMessage(String msg) {
				if (msg.startsWith(ApplicationInstanceManager.NOTICE_CMD)) {
					String cmd = msg.substring(ApplicationInstanceManager.NOTICE_CMD.length());
					String[] tokens = cmd.split(" ");

					if (tokens.length >= 2 && tokens[1].contains("\"")) {
						String[] tok = cmd.split("\"");
						tokens = new String[2];
						tokens[0] = tok[0].replaceAll(" ", "");
						tokens[1] = tok[1];
					}

					System.out.println("Notice " + cmd + " ...");
					DoCommand(tokens);
				}
			}
		});

		// Debug("Handle CLI");
		// Handle CLI
		if (HandleCLI(args) == true) {
			ApplicationInstanceManager.unregisterInstance();
			System.exit(0);
		}

		mainThread();
	}// End of mainEntrance

	/*
	 * (non-Java-doc)
	 * 
	 * @see java.lang.Object#Object()
	 */
	public Main() {
		super();
	}

	/*
	 * Config.localSequenceID == 0 表示是處於 Desktop Mode, 只允許一個 Instant 存在.
	 * Command-line Mode 可以允許多個 Instant 存在.
	 */
	public void mainThread() {
		TrayIconHandler trayIconHandler = null;
		// Init Main UI
		if (Config.getInstance().instantID == 0) {
			trayIconHandler = TrayIconHandler.getInstance();
			trayIconHandler.register();

			ConfigDialog configDlg = ConfigDialog.getInstance();

			if (!configDlg.dlgOpened && StringUtils.isEmpty(_config.getValue(KEYS.AccessKeyID))) {
				configDlg.dlgOpened = true;
				configDlg.updateStatusField();
				configDlg.Open(0, true);
			}
		}

		_logger.info(LoggerType.Main, "Current CSS server conn status: " + S3Assist.getInstance().getConnStatus());
		// Init Background sync thread
		if (_config.connStatus == true) {
			_config.storeProperties();
			SyncThread.getInstance().start();
		} else
			_logger.error(LoggerType.Main, "Can't connect to CSS server, no sync thread auto start.");

		try {
			shutdownLatch.await();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		if (Config.getInstance().instantID == 0)
			trayIconHandler.remove();
		// Unregister instance first, and allow next instance performing at this
		// moment.
		ApplicationInstanceManager.unregisterInstance();

		Timer exitTimer = new Timer();
		exitTimer.schedule(new TimerTask() {
			public void run() {
				_logger.info(LoggerType.Main, "[" + _config.getName() + "] " + "exits abnormally.");
				System.exit(1);
			}
		}, 60000);
		// Stop threading works
		Ping.StopWork();
		shutdown();
		exitTimer.cancel();

		_logger.info(LoggerType.Main, "[" + _config.getName() + "] " + "exits.");
		System.exit(0);
	}

	private boolean HandleCLI(String[] args) {
		boolean hasHandled = false;

		if (args.length >= 1) {
			Cli Cli = new Cli(IGlobal.APP_FULL_NAME);
			Cli.Do(args);
			hasHandled = true;
		}

		return hasHandled;
	}

	public static void login(String password) {
		_logger.info(LoggerType.Main, "Login starting...");
		// set instance ID
		if (_config.loadAccessKey(password)) {
			S3Assist.getInstance().login();
		}

		if (_config.connStatus) {
			try {

				// S3Assist.getInstance().s3Service.getOrCreateBucket(_config.getValue(KEYS.DefaultBucket));

				if (null == S3Assist.getInstance().s3Service.getBucket(_config.getValue(KEYS.DefaultBucket))) {
					_logger.debug(LoggerType.Main, "Main#Create Bucket:" + _config.getValue(KEYS.DefaultBucket) + " start...");
					S3Assist.getInstance().s3Service.createBucket(_config.getValue(KEYS.DefaultBucket));
					_logger.debug(LoggerType.Main, "Main#Create Bucket:" + _config.getValue(KEYS.DefaultBucket) + " end.");
				}
			} catch (ServiceException se) {
				_logger.error(LoggerType.Main, SafeBoxUtils.parseXMLError(se));
			}

			try {
				CSSHandler.getInstance().getBucketInfo();
			} catch (SafeboxException se) {
				_logger.error(LoggerType.Main, se.getMessage());
			}

			IGlobal.appState = IGlobal.APP_STATE.NORMAL;
			// setStautsInPort(_config.getValue(KEYS.HostIP),
			// _config.getValue(KEYS.User), _config.instantID, "NONE");

			TrayIconHandler.getInstance().updateSpaceRatio();
			_config.storeProperties();
			SyncThread.getInstance().start();
		}
	}

	public static void logout() {
		_logger.info(LoggerType.Main, "Logout starting...");
		IGlobal.appState = IGlobal.APP_STATE.LOGOUT;
		setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, "LOGOUT");

		SyncThread.getInstance().terminate();

		try {
			S3Assist.getInstance().s3Service.shutdown();
			S3Assist.getInstance().s3Service = null;
		} catch (ServiceException se) {
			_logger.error(LoggerType.Main, SafeBoxUtils.parseXMLError(se));
		}

		_config.setValue(KEYS.AccessKeyID, "");
		_config.setValue(KEYS.SecretAccessKey, "");
		_config.storeProperties();
	}

	private void shutdown() {
		_logger.info(LoggerType.Main, "Shutdown starting...");
		IGlobal.appState = IGlobal.APP_STATE.SHUTDOWN;

		SyncThread.getInstance().terminate();

		try {
			S3Assist.getInstance().s3Service.shutdown();
		} catch (ServiceException se) {
			_logger.error(LoggerType.Main, SafeBoxUtils.parseXMLError(se));
		}
	}

	public static void DoCommand(String[] args) {

		if (args.length >= 1) {

			if (args[0].equalsIgnoreCase("-exit")) {
				shutdownLatch.countDown();
				// } else if (args[0].equalsIgnoreCase("-web")) {
				// if (taskWorker != null)
				// taskWorker.AddWork("-web", cfg.GetWebURL(), null);
				// } else if (args[0].equalsIgnoreCase("-info") && args.length
				// >= 2) {
				// if (taskWorker != null)
				// taskWorker.AddWork("-info", args[1], s3Assist);
				// } else if (args[0].equalsIgnoreCase("-folder")) {
				// if (taskWorker != null)
				// taskWorker.AddWork("-folder", cfg.rootPath, null);
			}
		}
	}

	static public void SetWorkingDir() {

		String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		try {
			path = URLDecoder.decode(path, "UTF-8");
			if (path.endsWith(".jar"))
				path = path.substring(0, path.lastIndexOf('/') + 1);
			if (new File(path).isDirectory())
				System.setProperty("user.dir", path);
		} catch (UnsupportedEncodingException e1) {
		}
	}

	public static void addPort(String server, String account, int seq, int port) {
		// record the port and it's account ,seq in to directory structure.

		File seqFolder = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq + File.separator);
		seqFolder.mkdirs();

		if (seqFolder.list().length != 0) {
			System.err.println("try to add port file,but there already has one.");
		}

		try {
			File portFile = new File(seqFolder.getAbsolutePath() + File.separator + port);
			if (portFile.createNewFile()) {
				BufferedWriter bufw = null;
				try {

					bufw = new BufferedWriter(new FileWriter(portFile, false));
					bufw.write(APP_PID);
				} finally {
					bufw.close();
				}
			}
		} catch (Exception e) {
			System.err.println("=== write portFile error: " + e.getMessage());
			e.printStackTrace();
		}

	}

	public static int getPort(String server, String account, int seq) {
		// query directory to get port number of this seq of account

		File seqFolder = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq);

		if (seqFolder.isDirectory()) {
			String[] str = seqFolder.list();
			if (1 == str.length) {
				return Integer.parseInt(str[0]);
			}
		}

		return -1;

	}

	public static void setStautsInPort(String server, String account, int seq, String status) {
		BufferedWriter bufw = null;
		System.out.println(APP_PID + " seq: " + seq + " status: " + status);
		try {
			File f = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq);
			if (!f.exists()) {
				throw new Exception("this instance has not be assigned a port.");
			}
			if (!f.isDirectory()) {
				throw new Exception("error of the port file become dirct.");
			}
			File[] strF = f.listFiles();
			if (strF.length != 1)
				throw new Exception("there are more than one port file in the folder");

			bufw = new BufferedWriter(new FileWriter(strF[0], false));

			bufw.write(APP_PID);
			bufw.newLine();
			bufw.write(status);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (null != bufw) {
				try {
					bufw.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static String[] getStautsInPort(String server, String account, int seq) {
		BufferedReader bufr = null;
		String[] status = new String[3];
		try {
			File f = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq);
			if (f.isDirectory()) {
				File[] strF = f.listFiles();
				if (0 < strF.length) {
					bufr = new BufferedReader(new FileReader(strF[0]));
					status[0] = bufr.readLine();
					status[1] = bufr.readLine();
					status[2] = strF[0].getName();
				}
			}
		} catch (Exception e) {
			System.err.println("=== getStatusInPort error: " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (null != bufr) {
				try {
					bufr.close();
				} catch (IOException e) {
				}
			}
		}

		if (StringUtils.isEmpty(status[0])) {
			// PID is null
			status[0] = "N/A";
		}

		if (StringUtils.isEmpty(status[1])) {
			// STATUS is null
			status[1] = "DISCONN";
		}

		return status;
	}

	public static void removePort(String server, String account, int seq) {
		// remove port recore of this seq of account from directory structrue.

		File f = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq);
		File[] fs = f.listFiles();
		for (int i = 0; i < fs.length; i++) {
			fs[i].delete();
		}
	}

	public static ServerSocket openRandomSocket(String server, String account, int seq) throws Exception {
		// open a random socket ,record this port,and return the socket .
		ServerSocket sck = new ServerSocket(0);
		try {
			Main.addPort(server, account, seq, sck.getLocalPort());
		} catch (Exception e) {
			sck.close();
			throw e;
		}
		return sck;
	}

	public static void closeSocket(String server, String account, int seq, ServerSocket sck) throws Exception {
		if (sck.getLocalPort() != Main.getPort(server, account, seq)) {
			throw new Exception("the port is not consist with directory record.");
		}

		sck.close();
		Main.removePort(server, account, seq);
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
