package org.itri.ccma.safebox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.Util;

public class ApplicationInstanceManager {

	private static ApplicationInstanceListener subListener;

	/** Randomly chosen, but static, high socket number */
	public static final int SINGLE_INSTANCE_NETWORK_SOCKET = 44332;

	/** Must end with newline */
	public static final String SINGLE_INSTANCE_SHARED_KEY = "$$NewInstance$$\n";
	public static final String NOTICE_EXIT = "$$NoticeExit$$\n";
	public static final String NOTICE_CMD = "$$NoticeCmd$$\n";

	private static int SOCKET_NUM = SINGLE_INSTANCE_NETWORK_SOCKET;
	private static String SERVER_HOST;
	private static String SERVER_ACCOUNT;
	private static int SERVER_SEQ;

	private static SimpleDateFormat SDF = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	public static void unregisterInstance() {
		// close socket trigger thread's exception and cause its termination
		closeInstance();
	}

	public static ServerSocket socket = null;

	/**
	 * Registers this instance of the application.
	 * 
	 * @return true if first instance, false if not.
	 */
	public static boolean registerInstance(String server, String account, int seq) {
		// returnValueOnError should be true if lenient (allows app to run on
		// network error) or false if strict.
		boolean returnValueOnError = true;
		// try to open network socket
		// if success, listen to socket for new instance message, return true
		// if unable to open, connect to existing and send new instance message,
		// return false

		try {

			// socket = new ServerSocket(socket_num, 10,
			// InetAddress.getLocalHost());

			try {// Ben

				if (seq == 0) {
					socket = new ServerSocket(SOCKET_NUM, 10, InetAddress.getLocalHost());
				} else {
					socket = Main.openRandomSocket(server, account, seq);
					SOCKET_NUM = socket.getLocalPort();
				}

				SERVER_HOST = server;
				SERVER_ACCOUNT = account;
				SERVER_SEQ = seq;

				Debug("SERVER_HOST: " + server + ", SERVER_ACCOUNT: " + account + ", SERVER_SEQ: " + seq);
			} catch (Exception e2) {
				throw new IOException(e2.getMessage());
			}

			Thread instanceListenerThread = new Thread(new Runnable() {
				public void run() {
					boolean socketClosed = false;
					while (!socketClosed) {
						if (socket.isClosed()) {
							socketClosed = true;
						} else {
							Socket client = null;
							BufferedReader in = null;
							try {
								// function would blocks here...
								client = socket.accept();
								in = new BufferedReader(new InputStreamReader(client.getInputStream()));
								String message = in.readLine();

								Debug("Message from instanceListenerThread: " + message);
								if (message == null)
									message = "";
								// System.out.println(message);

								if (SINGLE_INSTANCE_SHARED_KEY.trim().equals(message.trim())) {
									Debug("Create NewInstance");
									fireNewInstance();
								} else if (NOTICE_EXIT.trim().equals(message.trim())) {
									Debug("Exit Instance");
									fireNotice(NOTICE_EXIT);
								} else if (NOTICE_CMD.trim().equals(message.trim())) {
									message = in.readLine();
									Debug(SDF.format(new Date()) + " Handle command: " + message);
									fireNotice(NOTICE_CMD + message);
								}
								in.close();
								client.close();
							} catch (IOException e) {
								socketClosed = true;
								try {
									if (in != null)
										in.close();
									if (client != null)
										client.close();
								} catch (IOException e1) {
								}
							}
						}
					}
					// closeInstance();
				}
			});
			instanceListenerThread.start();
			// listen
		} catch (UnknownHostException e) {
			return returnValueOnError;
		} catch (IOException e) {

			Error("Error on registerInstance " + seq + ": " + e.getMessage());
			try {

				if (seq != 0) {
					SOCKET_NUM = Main.getPort(server, account, seq);
				}

				Socket clientSocket = new Socket(InetAddress.getLocalHost(), SOCKET_NUM);
				OutputStream out = clientSocket.getOutputStream();
				out.write(SINGLE_INSTANCE_SHARED_KEY.getBytes());
				out.close();
				clientSocket.close();
				return false;
			} catch (UnknownHostException e1) {
				return returnValueOnError;
			} catch (IOException e1) {
				Error("Error on registerInstance " + seq + ": " + e.getMessage());
				return false;
			} catch (Exception e1) {
				Error("Error on registerInstance " + seq + ": " + e.getMessage());
				return returnValueOnError;
			}
		}
		return true;
	}

	public static boolean sendMessage(String msg) {
		boolean succ = false;
		Socket clientSocket = null;

		try {
			Debug("ApplicaitonInstance send message(" + SOCKET_NUM + "): " + msg);
			clientSocket = new Socket(InetAddress.getLocalHost(), SOCKET_NUM);

			OutputStream out = clientSocket.getOutputStream();
			out.write(msg.getBytes());
			out.close();
			clientSocket.close();
			clientSocket = null;
			succ = true;
		} catch (Exception e) {
			Error("ApplicaitonInstance error: " + e.getMessage());
		} finally {
			try {
				if (clientSocket != null)
					clientSocket.close();
			} catch (IOException e) {
				Error("ApplicaitonInstance error: " + e.getMessage());
			}
		}
		return succ;
	}

	public static void setApplicationInstanceListener(ApplicationInstanceListener listener) {
		subListener = listener;
	}

	private static void fireNewInstance() {
		if (subListener != null) {
			subListener.newInstanceCreated();
		}
	}

	private static void fireNotice(String msg) {
		if (subListener != null) {
			subListener.noticeMessage(msg);
		}
	}

	public static void closeInstance() {
		if (socket != null && !socket.isClosed()) {
			try {// It's means that this AIM is serverSocket.
				if (SERVER_SEQ == 0) {
					socket.close();
				} else {
					Main.closeSocket(SERVER_HOST, SERVER_ACCOUNT, SERVER_SEQ, socket);
				}

			} catch (IOException e) {
				Error("CloseInstance error: " + e.getMessage());
			} catch (Exception e) {
				Error("CloseInstance error: " + e.getMessage());
			}
		}
	}

	public static void Error(Throwable t) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.ERR, Util.getStackTrace(t));
	}

	public static void Error(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.ERR, msg);
	}

	public static void Info(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.INFO, msg);
	}

	public static void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.DBG, msg);
	}
}
