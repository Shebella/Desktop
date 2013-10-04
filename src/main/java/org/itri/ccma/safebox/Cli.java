package org.itri.ccma.safebox;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3CallBack;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.SafeboxException;
import org.jets3t.service.model.S3Object;

public class Cli {
	private S3Assist _s3Assist = S3Assist.getInstance();
	
	String appName = "Safebox";
	String osName = "";
	String tstPath = "c:\\";
	String logPath = "c:\\temp\\safebox_cli.log";
	String logText = "";
	BufferedWriter fout = null;
	char slash = '\\';
	Config cfg = null;
	int fileCount;

	static public final int HEAD_TEXT_LEN = 48;
	static public final int FILE_OP_NONE = 0;
	static public final int FILE_OP_LS = 1;
	static public final int FILE_OP_PUT = 2;
	static public final int FILE_OP_GET = 3;
	static public final int FILE_OP_DEL = 4;
	static public final int FILE_OP_CMP = 5;
	static public final int FILE_OP_TEST = 6;
	static public final int FILE_OP_HELP = 7;

	public Cli(String s) {
		appName = s + " (" + IGlobal.APP_SUB_VER + ")";
		slash = File.separatorChar;
		logPath = Config.getInstance().getValue(KEYS.DataRootPath) + File.separatorChar + "safebox_cli.log";
		osName = System.getProperty("os.name");

		if (!Config.IsWinOS()) {
			slash = '/';
			tstPath = "/tmp/";
		}
	}

	public void Do(String[] args) {
		int op = FILE_OP_NONE;
		String path1 = "";
		String path2 = "";

		if (args.length >= 1) {
			if (args[0].equalsIgnoreCase("-h"))
				op = FILE_OP_HELP;
			else if (args[0].equalsIgnoreCase("-help"))
				op = FILE_OP_HELP;
			else if (args[0].equalsIgnoreCase("-ls"))
				op = FILE_OP_LS;
			else if (args[0].equalsIgnoreCase("-list"))
				op = FILE_OP_LS;
			else if (args[0].equalsIgnoreCase("-put"))
				op = FILE_OP_PUT;
			else if (args[0].equalsIgnoreCase("-get"))
				op = FILE_OP_GET;
			else if (args[0].equalsIgnoreCase("-del"))
				op = FILE_OP_DEL;
			else if (args[0].equalsIgnoreCase("-cmp"))
				op = FILE_OP_CMP;
			else if (args[0].equalsIgnoreCase("-test"))
				op = FILE_OP_TEST;
			if (args.length >= 2)
				path1 = args[1];
			if (args.length >= 3)
				path2 = args[2];
		}

		// Clean cli previous logs
		try {
			File f = new File(logPath);
			if (f.exists())
				f.delete();
			FileWriter fstream = new FileWriter(logPath);
			fout = new BufferedWriter(fstream);
		} catch (IOException e) {
		}

		DbgPrint("\n== CLI Starts ==\n");

		if (op != FILE_OP_NONE && op != FILE_OP_HELP && op != FILE_OP_CMP && _s3Assist.getConnStatus() == false) {

			DbgPrint("Filed to connect to remote server, please check network or user account.\n");
		} else {
			_s3Assist.setRootPath("");

			switch (op) {
				case FILE_OP_NONE:
					DbgPrint("\nIncorrect parameters. Please type \"-h\" to get the help.\n");
					break;
				case FILE_OP_HELP:
					PrintHelp();
					break;
				case FILE_OP_LS:
					List(path1);
					break;
				case FILE_OP_GET:
					Get(path1);
					break;
				case FILE_OP_DEL:
					Del(path1);
					break;
				case FILE_OP_CMP:
					Cmp(path1, path2);
					break;
				default:
					break;
			}
		}

		if (fout != null) {
			try {
				fout.close();
			} catch (IOException e) {
			}
		}

		if (Config.IsWinOS()) {
			DbgPrint("\nPress enter to return\n");
			try {
				System.in.read();
			} catch (IOException e) {
			}
		}
	}

	public void PrintHelp() {

		String prefix = Config.IsWinOS() ? "d:\\" : "/tmp/";

		DbgPrint("\nPrintHelp \n");

		System.out.print("\n");
		System.out.print("NAME\n");
		System.out.print("    " + appName + "\n");
		System.out.print("\n");
		System.out.print("SYNOPSIS\n");
		System.out.print("    SafeBox -[op] [path] [path]\n");
		System.out.print("\n");
		System.out.print("DESCRIPTION\n");
		System.out.print("    SafeBox is a client for Cloud Storage Service which \n    provides CLI and non-CLI interfaces to operate the \n    files in Cloud.\n");
		System.out.print("\n");
		System.out.print("OPTIONS\n");
		System.out.print("    no op\n");
		System.out
		        .print("    Small GUI and synchronization mechanism would start\n    for transfering files. Most operations happen naturally\n    when users drag & drop files in file manager.\n");
		System.out.print("\n");
		System.out.print("    -h\n");
		System.out.print("    -help\n");
		System.out.print("        Print the help message on screen.\n");
		System.out.print("\n");
		System.out.print("    -ls\n");
		System.out.print("    -list\n");
		System.out.print("        List file(s)\n");
		System.out.print("\n");
		System.out.print("    -put local_dir_path dir\n");
		System.out.print("    -put local_file_path dir\n");
		System.out
		        .print("        Put one local directory or file to server. Assignment\n        of 'dir' could help to create parent directory on\n        remote side. As follows:\n");
		System.out.print("        if dir=\"\", no parent directory would create.\n");
		System.out.print("        if dir=\"auto_dir\", parent directory [auto_name] would create.\n");
		System.out.print("        if dir=[name], parent directory [name] would create.\n");
		System.out.print("\n");
		System.out.print("    -get dir_path\n");
		System.out.print("    -get file_path\n");
		System.out.print("        Get one directory or file to local storage.\n");
		System.out.print("\n");
		System.out.print("    -del dir_path\n");
		System.out.print("    -del file_path\n");
		System.out.print("        Delete one directory or file.\n");
		System.out.print("\n");
		System.out.print("    -cmp [local_dir_path1] [local_dir_path2]\n");
		System.out.print("        Compare two directories and report in " + logPath + "\n");
		System.out.print("\n");
		System.out.print("    -test [local_dir_path]\n");
		System.out
		        .print("        Perform the sequence of 'put' + 'get' + 'cmp' to test CSS.\n        If no [local_dir_path] was given, one of local folder would\n        be selected for test.\n");
		System.out.print("\n");
		System.out.print("\t-exit\t\n\t-web\t\n\t-info\t\n\t-folder\t\n\t-test_connection\t\n\t-login\t\n\t-shutdown\t\n\t-pausesync\t\n\t-resumesync\t\n\t-listinst\t\n\t-bucketinfo\t\n\t-accesskey\t\n\t-movefolder\t\n\t-register\t\n\t-logout\t\n\t-shutdownall\t\n\t-clean_data\t\n\t-relogin");
		System.out.print("Example:\n");
		System.out.print("    SafeBox -h\n");
		System.out.print("    SafeBox -ls\n");
		System.out.print("    SafeBox -ls ftp_folder\n");
		System.out.print("    SafeBox -put " + prefix + "ftp_folder\n");
		System.out.print("    SafeBox -put " + prefix + "ftp_folder ver13.0\n");
		System.out.print("    SafeBox -put " + prefix + "ftp_folder auto_dir\n");
		System.out.print("    SafeBox -put " + prefix + "weekly.doc\n");
		System.out.print("    SafeBox -put " + prefix + "weekly.doc today\n");
		System.out.print("    SafeBox -put " + prefix + "weekly.doc auto_dir\n");
		System.out.print("    SafeBox -get ftp_folder\n");
		System.out.print("    SafeBox -get weekly.doc\n");
		System.out.print("    SafeBox -del ftp_folder\n");
		System.out.print("    SafeBox -del weekly.doc\n");
		System.out.print("    SafeBox -cmp " + prefix + "ftp_folder_today " + prefix + "ftp_folder_yesterday\n");
		System.out.print("\n");
		System.out.print("Debug:\n");
		System.out.print("    See " + logPath + " for all details.\n");
		System.out.print("Info:\n");
		System.out.print("    http://192.168.213.12/safebox\n");
		System.out.print("\n");
	}

	public void List(String remotePath) {
		List<S3Object> list = null;
		S3Object obj;
		String key = "";
		int i;

		if (!remotePath.isEmpty() && !remotePath.endsWith("/"))
			remotePath += "/";
		try {
			list = _s3Assist.listObjects(remotePath, false, null);
		} catch (ObjectException e) {
			e.printStackTrace();
		}

		if (list == null || list.size() == 0) {
			DbgPrint("\nDir/File not found.\n");
		} else {
			_s3Assist.downloadingObjectsOfDetails(list);
			for (i = 0; i < list.size(); i++) {
				obj = list.get(i);
				key = obj.getKey();
				if (key.indexOf('/') == -1 || !remotePath.isEmpty())
					try {
						_s3Assist.printObject(fout, obj, false);
					} catch (SafeboxException e) {
						e.printStackTrace();
					}
			}
		}
	}

	public String Put(String localPath, String remotePath, String sync_id) {
		File f = new File(localPath);
		String remoteDir;
		String tailName;

		if (!localPath.isEmpty() && f.exists()) {

			DbgPrint("\nPut " + localPath + "\n");

			fileCount = 0;
			tailName = GetTailName(localPath);
			if (remotePath.isEmpty())
				remoteDir = f.isDirectory() ? tailName : "";
			else if (remotePath.equalsIgnoreCase("auto_dir"))
				remoteDir = MakeRemoteDir(localPath);
			else
				remoteDir = remotePath;
			if (f.isDirectory()) {
				_s3Assist.setRootPath(localPath);
				PutDir(remoteDir, localPath, sync_id);
				remotePath = remoteDir;
			} else {
				_s3Assist.setRootPath(localPath.substring(0, localPath.lastIndexOf(slash)));
				PutFile(remoteDir, localPath, sync_id);
				remotePath = !remoteDir.isEmpty() ? remoteDir + "/" : "";
				remotePath += tailName;
			}

			DbgPrint("\nTotally " + fileCount + " file" + (fileCount > 1 ? "s" : "") + " put to path \"");
			DbgPrint(remotePath);
			DbgPrint("\".\n\n");
		}
		return remotePath;
	}

	public void PutFile(String remoteDir, String localPath, String sync_id) {
		if (_s3Assist.uploadingObject(remoteDir, localPath))
			fileCount++;
	}

	public void PutDir(String remoteDir, String localPath, String sync_id) {
		File dir = new File(localPath);
		int i;

		File[] files = dir.listFiles();

		if (files != null) {
			Arrays.sort(files);
			for (i = 0; i < files.length; i++) {
				if (files[i].isDirectory())
					PutDir(remoteDir, files[i].getPath(), sync_id);
				else
					PutFile(remoteDir, files[i].getPath(), sync_id);
			}
		}
	}

	public String Get(String remotePath) {
		String curPath = "";
		String curDir = "";
		File f = new File(".");

		try {
			curDir = f.getCanonicalPath();
		} catch (IOException e) {
			curDir = f.getAbsolutePath();
		}

		if (new File(curDir).isDirectory() == false)
			curDir = curDir.substring(0, curDir.lastIndexOf(slash));
		_s3Assist.setRootPath(curDir);

		S3CallBack s3cb = new S3CallBack() {
			public boolean Func(S3Object obj) throws ObjectException {
				if (_s3Assist.isDirObject(obj))
					return true;
				if (_s3Assist.downloadingObject(obj.getKey()))
					fileCount++;
				return true;
			}
		};

		if (!remotePath.isEmpty()) {

			DbgPrint("\nGet " + remotePath + "\n");

			fileCount = 0;
			// view as file first, and handle as dir if not a file
			try {
				if (_s3Assist.downloadingObject(remotePath) == true) {
					fileCount += 1;
				} else {
					if (!remotePath.endsWith("/"))
						remotePath += "/";
					_s3Assist.listObjects(remotePath, true, s3cb);
				}
			} catch (ObjectException e) {
				e.printStackTrace();
			}
			DbgPrint("\nTotally " + fileCount + " file" + (fileCount > 1 ? "s" : "") + " got from path \"");
			DbgPrint(remotePath);
			DbgPrint("\".\n\n");
			curPath = curDir + slash + remotePath.replace('/', slash);
		}
		return curPath;
	}

	public void Del(String remotePath) {

		S3CallBack s3cb = new S3CallBack() {
			public boolean Func(S3Object obj) {
				if (_s3Assist.deletingObject(obj.getKey()))
					fileCount += 1;
				return true;
			}
		};

		if (!remotePath.isEmpty()) {

			DbgPrint("\nDel " + remotePath + "\n");
			fileCount = 0;
			if (_s3Assist.deletingObject(remotePath)) {
				fileCount += 1;
			} else {
				if (!remotePath.endsWith("/"))
					remotePath += "/";
				try {
					_s3Assist.listObjects(remotePath, true, s3cb);
				} catch (ObjectException e) {
					e.printStackTrace();
				}
			}
			DbgPrint("\nTotally " + fileCount + " file" + (fileCount > 1 ? "s" : "") + " deleted from path \"");
			DbgPrint(remotePath);
			DbgPrint("\".\n\n");
		}
	}

	public void Cmp(String localPath1, String localPath2) {
		Map<String, FileNode> preFiles = new HashMap<String, FileNode>();
		Map<String, FileNode> curFiles = new HashMap<String, FileNode>();
		List<Map.Entry<String, FileNode>> preList;
		List<Map.Entry<String, FileNode>> curList;
		FileNode curFile, preFile;
		File f1, f2;
		Boolean diff;
		int diffCount = 0;

		f1 = new File(localPath1);
		f2 = new File(localPath2);

		if (f1.isDirectory() != f2.isDirectory()) {
			return;
		}

		DbgPrint("\nCmp " + localPath1 + " and " + localPath2 + "\n");

		if (!f1.isDirectory() && !f2.isDirectory()) {
			diff = CmpFile(f1.getPath(), f2.getPath());
			PrintHead(slash + f1.getName(), HEAD_TEXT_LEN);
			DbgPrint(diff ? "different\n\n" : "identical\n\n");
			return;
		}

		// otherwise both are directories
		String slashStr = "";
		slashStr += slash;
		if (!localPath1.endsWith(slashStr))
			localPath1 += slash;
		if (!localPath2.endsWith(slashStr))
			localPath2 += slash;
		CollectCurFiles(preFiles, localPath1, localPath1);
		CollectCurFiles(curFiles, localPath2, localPath2);
		preList = GetSortedList(preFiles);

		for (Map.Entry<String, FileNode> entry : preList) {
			String key = entry.getKey();
			preFile = preFiles.get(key);
			curFile = curFiles.get(key);
			if (preFile.isDirectory())
				continue;

			PrintHead("." + slash + key, HEAD_TEXT_LEN);
			if (curFile == null) {
				diffCount++;
				DbgPrint("only in " + localPath1 + "\n");
			} else {
				curFiles.remove(key);
				diff = CmpFile(localPath1 + key, localPath2 + key);
				DbgPrint(diff ? "different\n" : "identical\n");
				if (diff)
					diffCount++;
			}
		}

		curList = GetSortedList(curFiles);
		for (Map.Entry<String, FileNode> entry : curList) {
			String key = entry.getKey();
			preFile = preFiles.get(key);
			curFile = curFiles.get(key);
			if (curFile.isDirectory())
				continue;

			PrintHead("." + slash + key, HEAD_TEXT_LEN);
			if (preFile == null) {
				diffCount++;
				DbgPrint("only in " + localPath2 + "\n");
			} else {
				diff = CmpFile(localPath1 + key, localPath2 + key);
				DbgPrint(diff ? "different\n" : "identical\n");
				if (diff)
					diffCount++;
			}
		}

		if (diffCount == 0)
			DbgPrint("All files are identical.\n\n");
		else
			DbgPrint("Totally " + diffCount + " files are different.\n\n");

		// Send report to server
		System.out.println("The comparaion logged in " + logPath);

		// Remove sending
		/*
		 * Date d= new Date(); SimpleDateFormat sdf = new
		 * SimpleDateFormat("MMddHHmm");
		 * s3Assist.putObjectToPublicBucket(Config.logPath, cfg.usrName + "_" +
		 * sdf.format(d) + ".log");
		 */
	}

	public String MakeRemoteDir(String localPath) {
		String path = "";
		String tailName = localPath.substring(localPath.lastIndexOf(slash) + 1);
		Date d = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

		path = tailName + "." + sdf.format(d);
		return path;
	}

	public String GetTailName(String path) {
		String tailName = path;
		int i = path.lastIndexOf(slash);

		if (i != -1)
			tailName = path.substring(i + 1);
		return tailName;
	}

	public void CollectCurFiles(Map<String, FileNode> Files, String rootPath, String dirPath) {
		File dir = new File(dirPath);
		FileNode node;
		int i;
		String key;

		File[] files = dir.listFiles();
		if (files != null) {
			for (i = 0; i < files.length; i++) {
				node = new FileNode(files[i]);
				key = files[i].getPath();
				key = key.substring(rootPath.length());
				Files.put(key, node);
				if (files[i].isDirectory())
					CollectCurFiles(Files, rootPath, files[i].getPath());
			}
		}
	}

	public List<Map.Entry<String, FileNode>> GetSortedList(Map<String, FileNode> Files) {

		List<Map.Entry<String, FileNode>> list_Data = new ArrayList<Map.Entry<String, FileNode>>(Files.entrySet());

		Collections.sort(list_Data, new Comparator<Map.Entry<String, FileNode>>() {

			public int compare(Map.Entry<String, FileNode> entry1, Map.Entry<String, FileNode> entry2) {
				return (entry1.getKey().compareTo(entry2.getKey()));
			}
		});

		return list_Data;
	}

	public String PickOneFolder() {
		String path = "";
		File dir = new File(tstPath);
		File[] files = dir.listFiles();
		int i, index;

		i = 0;
		if (files != null && files.length >= 1) {
			for (i = 0; i < 10 && path.equals(""); i++) {
				index = (int) (System.currentTimeMillis() % files.length);
				if (files[index].isDirectory())
					path = files[index].getPath();
			}

			if (path.isEmpty()) {
				for (i = 0; i < files.length && path.equals(""); i++) {
					if (files[i].isDirectory())
						path = files[i].getPath();
				}
			}
		}
		return path;
	}

	public Boolean CmpMem(byte[] buf1, byte[] buf2, int len) {
		Boolean diff = false;
		int i;

		for (i = 0; i < len && !diff; i++) {
			diff = (buf1[i] != buf2[i]) ? true : false;
		}
		return diff;
	}

	public Boolean CmpFile(String path1, String path2) {
		Boolean diff = true;
		FileInputStream in1 = null;
		FileInputStream in2 = null;
		int len1, len2, size = 8192;
		byte[] buf1, buf2;

		buf1 = new byte[size];
		buf2 = new byte[size];
		if (buf1 != null && buf2 != null) {
			diff = false;
			try {
				in1 = new FileInputStream(path1);
				in2 = new FileInputStream(path2);
				do {
					len1 = in1.read(buf1, 0, size);
					len2 = in2.read(buf2, 0, size);
					if (len1 != len2)
						diff = true;
					else if (len1 <= 0)
						break;
					else
						diff = CmpMem(buf1, buf2, len1);
				} while (!diff);
				in1.close();
				in2.close();
				in1 = null;
				in2 = null;
			} catch (FileNotFoundException e) {
				DbgPrint(e.getMessage() + "\n");
				diff = true;
			} catch (IOException e) {
				DbgPrint(e.getMessage() + "\n");
				diff = true;
			} finally {
				try {
					if (in1 != null)
						in1.close();
					if (in2 != null)
						in2.close();
				} catch (IOException e) {
				}
			}
		}
		return diff;
	}

	public void PrintHead(String text, int fixedSize) {
		int i;

		DbgPrint(text);
		if (text.length() < (fixedSize - 1)) {
			for (i = 0; i < (fixedSize - text.length()); i++)
				DbgPrint(" ");
		} else {
			DbgPrint("\t");
		}
	}

	public void DbgPrint(String text) {

		// System.out.print(text);

		try {
			if (fout != null)
				fout.write(text);
		} catch (IOException e) {
		}

		if (text.startsWith("\n")) {
			text = text.substring(1);
			Log("");
		}

		if (text.endsWith("\n")) {
			text = text.substring(0, text.length() - 1);
			Log(logText + text);
			logText = "";
		} else {
			logText += text;
		}
	}

	void Log(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.INFO, msg);
	}

}
