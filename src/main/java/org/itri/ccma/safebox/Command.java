package org.itri.ccma.safebox;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3BucketSize;
import org.itri.ccma.safebox.s3.S3Logger;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.SafeBoxUtils;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.Util;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;

public class Command {
	public static S3BucketSize BUCKET_INFO = new S3BucketSize();
	private static Command _instance = new Command();
	private LoggerHandler _logger = LoggerHandler.getInstance();

	private Command() {
	}

	public static Command getInstance() {
		return _instance;
	}

	public Boolean sendCommand(String[] args) {
		// try{checkPortFile();}catch(Throwable
		// t){System.out.println("found some error when checking port.");}

		Boolean hasSent = false;
		String msg;
		String acceptedCmd[] = { "-exit", "-web", "-info", "-folder", "-test_connection", "-login", "-shutdown", "-pausesync", "-resumesync", "-listinst", "-bucketinfo",
		        "-accesskey", "-movefolder", "-register", "-logout", "-shutdownall", "-clean_data", "-relogin" };
		int i;
		if (args.length >= 1) {
			for (i = 0; i < acceptedCmd.length; i++) {
				if (args[0].equalsIgnoreCase(acceptedCmd[i]))
					break;
			}

			if (i == 1) {
				// OpenWeb(null, ServerConnectionUtil.getWebURL());
				hasSent = true;
			} else if (i == 4) {
				testConnection();
				hasSent = true;
			} else if ((i >= 5 && i <= 8) || (i == 14) || (i == 17)) {
				if (args.length > 1) {
					_instance.testSync(args[0], args[1], args.length > 2 ? args[2] : "0", args.length > 3 ? args[3] : null, null);
					hasSent = true;
				}
			} else if (i == 12) {
				if (args.length == 4) {
					_instance.testSync(args[0], args[1], args[2], args[3], args[4]);
					hasSent = true;
				}
			} else if (i == 15) {
				shutdownAllInst();
				hasSent = true;
			} else if (i == 16) {
				if (args.length > 1) {
					cleanSyncData(args[1]);
					hasSent = true;
				}
			} else if (i == 9) {
				if (args.length > 1) {
					Config.listInstNum(args[1]);
				} else {
					Config.listInstNum(null);
				}
				hasSent = true;
			} else if (i == 10) {
				testBucketSize(args[1]);
				hasSent = true;
			} else if (i == 11) {
				testLoadKey(args[1]);
				hasSent = true;
			} else if (i == 13) {
				if (args.length >= 2) {
					if (args.length == 2)
						testRegister(args[1], "1");
					else
						testRegister(args[1], args[2]);

					hasSent = true;
				}
			} else if (i < acceptedCmd.length) {
				msg = ApplicationInstanceManager.NOTICE_CMD + args[0];
				System.out.println("=== acceptedCmd: " + msg);
				if (args.length >= 2)
					msg += " \"" + args[1] + "\"\n";
				msg += "\n";
				ApplicationInstanceManager.sendMessage(msg);
				System.out.println("=== sent msg: " + msg);
				hasSent = true;
			}
		}

		return hasSent;
	}

	private void testConnection() {
		Boolean status = false;
		Boolean lastStatus = true;
		Boolean firstTime = true;
		String content;

		Config c = Config.getInstance();
		c.load(Config.APP_CFG_FILE);
		S3Assist s3Assist = S3Assist.getInstance();

		while (true) {
			String serviceURL = ServerConnectionUtil.getCSSServiceURL(c.getValue(KEYS.HostIP));
			if (StringUtils.isNotEmpty(serviceURL)) {
				status = s3Assist.login();
			}

			if (firstTime || lastStatus.equals(status) == false) {
				content = firstTime ? "Service monitor starts\r\n\r\n" : "";
				content += "CSS " + c.getValue(KEYS.HostIP) + " is " + (status ? "up" : "down");
				content += "            -- " + Util.GetTimeStr();
				System.out.println(content);
				// Util.SendMail("", content);
				firstTime = false;
				lastStatus = status;
			}
			// Util.Sleep(300 * 1000); // check every 5min
		}
	}

	private void removePortSingle(String server, String account, int seq, int port_file) {
		(new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq + File.separator + String.valueOf(port_file))).delete();
	}

	private String[] getPortsByInstance(String server, String account, int seq) {
		// query directory to get port number of this seq of account

		File seqFolder = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + seq);

		if (seqFolder.isDirectory())
			return seqFolder.list();

		return new String[0];
	}

	private void callInstance(String server, String account, int inst_seq, int port_num, String command) {
		Socket clientSocket = null;
		OutputStream out = null;

		try {
			clientSocket = new Socket(InetAddress.getLocalHost(), port_num);

			out = clientSocket.getOutputStream();
			out.write((ApplicationInstanceManager.NOTICE_CMD + command + "\n").getBytes());
		} catch (Exception e) {
			removePortSingle(server, account, inst_seq, Integer.valueOf(port_num));
		} finally {
			try {
				if (null != out) {
					out.close();
				}

				if (null != clientSocket) {
					clientSocket.close();
				}
			} catch (IOException e) {
			}
		}
	}

	private void testSync(String action, String cfgFile, String instantID, String log, String path) {
		final Config config = Config.getInstance();
		final String newRootPath = path;

		config.instantID = Integer.parseInt(instantID);
		// Instant seq num validate
		if (config.instantID == 0) {
			_logger.error(LoggerType.Main, "TestSync inst_seq can't be 0.");
			return;
		}

		config.load(cfgFile);

		LoggerHandler.getInstance().setReferece(log);

		if (action.equalsIgnoreCase("-login")) {
			File f = new File(Config.PORTFILE_HOME + File.separator + config.getValue(KEYS.HostIP) + File.separator + config.getValue(KEYS.User) + File.separator
			        + config.instantID + File.separator);
			if (f.isDirectory()) {
				String[] previousPortArr = f.list();
				boolean bReturn = false;
				if (0 < previousPortArr.length) {
					for (int i = 0; i < previousPortArr.length; i++) {
						ServerSocket socket = null;
						try {
							socket = new ServerSocket(Integer.valueOf(previousPortArr[i]));
							removePortSingle(config.getValue(KEYS.HostIP), config.getValue(KEYS.User), config.instantID, Integer.valueOf(previousPortArr[i]));
						} catch (IOException e) {
							// port is open
							System.err.println("=== One instance " + config.getValue(KEYS.User) + "(" + config.instantID + ") already existed port(" + previousPortArr[i] + ")");
							bReturn = true;
						} finally {
							if (socket != null) {
								try {
									socket.close();
								} catch (IOException e) {
								}
							}
						}
					}
					if (bReturn)
						return;
				}
			}
		} else {
			String[] portArr = getPortsByInstance(config.getValue(KEYS.HostIP), config.getValue(KEYS.User), config.instantID);
			for (int i = 0; i < portArr.length; i++) {
				callInstance(config.getValue(KEYS.HostIP), config.getValue(KEYS.User), config.instantID, Integer.valueOf(portArr[i]), action);
			}
			return;
		}

		_logger.debug(LoggerType.Main, "=== trying to registerInstance()(action= " + action + "): " + config.instantID);
		ApplicationInstanceManager.registerInstance(config.getValue(KEYS.HostIP), config.getValue(KEYS.User), config.instantID);

		if (action.equalsIgnoreCase("-login")) {
			if (config.CheckAvailable() == true) {
				System.out.println("Start instance id: " + config.instantID);
				_logger.debug(LoggerType.Main, "Start instance id: " + config.instantID);
				// Main.login(config.getValue(KEYS.Password));
				try {
					
					if (null == S3Assist.getInstance().s3Service.getBucket(config.getValue(KEYS.DefaultBucket))) {
						_logger.debug(LoggerType.Main, "Command#Create Bucket:" + config.getValue(KEYS.DefaultBucket) + " start...");
						S3Assist.getInstance().s3Service.createBucket(config.getValue(KEYS.DefaultBucket));
						_logger.debug(LoggerType.Main, "Command#Create Bucket:" + config.getValue(KEYS.DefaultBucket) + " end.");
					}
					
				} catch (ServiceException se) {
					_logger.error(LoggerType.Main, SafeBoxUtils.parseXMLError(se));
				}

				ApplicationInstanceManager.setApplicationInstanceListener(new ApplicationInstanceListener() {
					public void newInstanceCreated() {
					}

					public void noticeMessage(String msg) {
						if (msg.startsWith(ApplicationInstanceManager.NOTICE_CMD)) {
							String cmd = msg.substring(ApplicationInstanceManager.NOTICE_CMD.length());

							if (cmd.startsWith("-shutdown")) {
								_logger.debug(LoggerType.Main, "Stop sequence id: " + config.instantID);
								Main.shutdownLatch.countDown();
							} else if (cmd.startsWith("-pausesync")) {
								_logger.debug(LoggerType.Main, "Pause sequence id: " + config.instantID);
								SyncThread.getInstance().setPause(true);
							} else if (cmd.startsWith("-resumesync")) {
								_logger.debug(LoggerType.Main, "Resume sequence id: " + config.instantID);
								SyncThread.getInstance().setPause(false);
							} else if (cmd.startsWith("-logout")) {
								_logger.debug(LoggerType.Main, "Logout sequence id: " + config.instantID);
								Main.logout();
							} else if (cmd.startsWith("-relogin")) {
								_logger.debug(LoggerType.Main, "Re-Login sequence id: " + config.instantID);
								Main.login(config.getValue(KEYS.Password));
							} else if (cmd.startsWith("-movefolder")) {
								_logger.debug(LoggerType.Main, "Move folder to " + newRootPath);
								SyncThread.getInstance().setPause(true);
								JNotifyHandler.getInstance().stopWatchEvent();

								File oldPath = new File(config.getValue(KEYS.SafeBoxLocation));
								File newPath = new File(newRootPath);
								Util.moveDirectory(oldPath, newPath);
								config.setValue(KEYS.SafeBoxLocation, newRootPath);
								config.storeProperties();

								SyncThread.getInstance().setRootPath(newRootPath);
								JNotifyHandler.getInstance().startWatchEvent();
								SyncThread.getInstance().setPause(false);
							}
						}
					}
				});

				Main main = new Main();
				main.mainThread();

			}
		} else {
			String msg;
			msg = ApplicationInstanceManager.NOTICE_CMD + action;
			msg += " \"" + cfgFile + "\"\n";
			msg += "\n";

			System.out.println("=== not login, sendMessage: " + msg);
			ApplicationInstanceManager.sendMessage(msg);
		}

		System.out.println("=== trying to unregisterInstance(): " + config.instantID);
		ApplicationInstanceManager.unregisterInstance();
	}

	private void testBucketSize(String cfgFile) {
		Config cfg = Config.getInstance();
		cfg.load(cfgFile);

		try {
			CSSHandler.getInstance().getBucketInfo();
		} catch (SafeboxException e) {
			e.printStackTrace();
		}

		System.out.println("Bucket name = " + cfg.getValue(KEYS.DefaultBucket));
		System.out.println("Bucket total size = " + BUCKET_INFO.GetMaxSizeString() + " Bytes");
		System.out.println("Bucket total used = " + BUCKET_INFO.GetUsedSizeString() + " Bytes");
	}

	private void testLoadKey(String cfgFile) {
		Config cfg = Config.getInstance();
		cfg.load(cfgFile);

		boolean succ = cfg.loadAccessKey(cfg.getValue(KEYS.Password));

		if (cfg.getValue(KEYS.AccessKeyID).isEmpty()) {
			succ = false;
		}

		System.out.println("Accounts = " + cfg.getValue(KEYS.User));
		if (succ == true) {
			System.out.println("key pair = " + cfg.getValue(KEYS.AccessKeyID) + " & " + cfg.getValue(KEYS.SecretAccessKey));
			cfg.storeProperties();
		} else {
			System.out.println("Key pair = unknown");
		}
	}

	private void testRegister(String cfgFile, String loopCount) {
		Config cfg = Config.getInstance();
		cfg.load(cfgFile);

		int loop = Integer.parseInt(loopCount);
		int i = 0;
		int succCount = 0;

		if (!cfg.getValue(KEYS.Password).isEmpty()) {
			for (i = 0; i < loop; i++) {
				boolean succ = cfg.loadAccessKey(cfg.getValue(KEYS.Password));

				if (succ == true) {
					succCount = succCount + 1;
					System.out.println("Key pair = " + cfg.getValue(KEYS.AccessKeyID) + " & " + cfg.getValue(KEYS.SecretAccessKey));
					cfg.storeProperties();
				}

				else {
					System.out.println("Key pair = unknown");
				}
			}
		}

		System.out.println("Success  = " + succCount + " / " + loopCount);
	}

	private void shutdownAllInst() {
		List<String> serverList = Config.getAllServers();
		if (null == serverList) {
			return;
		}

		for (String server : serverList) {
			List<String> accountList = Config.getAllAccountsByServer(server);

			for (String account : accountList) {
				List<String> instanceList = Config.getAllInstancesByAccount(server, account);

				for (String instance : instanceList) {
					File seqFolder = new File(Config.PORTFILE_HOME + File.separator + server + File.separator + account + File.separator + instance);

					if (seqFolder.isDirectory()) {
						String[] portNums = seqFolder.list();
						for (int i = 0; i < portNums.length; i++) {
							System.out.println("=== Trying to shutdown " + account + "(" + instance + ") for server " + server + ": " + portNums[i]);
							callInstance(server, account, Integer.valueOf(instance), Integer.valueOf(portNums[i]), "-shutdown");
						}
					}
				}
			}
		}
	}

	private void cleanSyncData(String cfgFile) {
		Config cfg = Config.getInstance();
		cfg.load(cfgFile);

		String strPath = cfg.getValue(KEYS.DataRootPath);
		if (!strPath.isEmpty()) {
			FileUtil.deleteFile(strPath + File.separatorChar + "fileInfo.db");
			FileUtil.deleteFile(strPath + File.separatorChar + "safebox.log");
			FileUtil.deleteFile(strPath + File.separatorChar + "failed_remote_events.log");
		}

		if (!cfg.getValue(KEYS.SafeBoxLocation).isEmpty()) {
			FileUtil.removeDir(cfg.getValue(KEYS.SafeBoxLocation));
		}

		if (!cfg.getValue(KEYS.DefaultBucket).isEmpty()) {
			S3Assist s3Assist = S3Assist.getInstance();

			try {
				s3Assist.deleteAllObjects();
			} catch (ObjectException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		if (!cfg.getValue(KEYS.HostIP).isEmpty() && !cfg.getValue(KEYS.User).isEmpty()) {
			S3Logger.GetInstance().RemoveEventLog(cfg.getValue(KEYS.HostIP), cfg.getValue(KEYS.User));
		}
	}
}
