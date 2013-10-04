package org.itri.ccma.safebox.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.Config;
import org.jets3t.service.utils.ServiceUtils;

public class FileUtil {

	public static void writeTextFile(String path, String contents) {
		File file = new File(path);
		BufferedWriter out = null;

		waitFileUnused(file, 5);

		try {
			out = new BufferedWriter(new FileWriter(file));
			out.write(contents);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static String readTextFile(String path) {
		File file = new File(path);
		String contents = "";
		String text = null;
		BufferedReader in = null;
		String lineSeparator = System.getProperty("line.separator");

		waitFileUnused(file, 5);

		try {
			in = new BufferedReader(new FileReader(file));
			while ((text = in.readLine()) != null) {
				contents += text + lineSeparator;
			}
		} catch (FileNotFoundException e) {

		} catch (IOException e) {

		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return contents;
	}

	public static void emptyFile(String path) {
		File f;
		RandomAccessFile ra = null;

		if (!path.isEmpty()) {
			f = new File(path);
			if (f != null && f.exists()) {
				try {
					ra = new RandomAccessFile(f, "rw");
					ra.setLength(0);
				} catch (Exception e) {
				} finally {
					try {
						if (ra != null)
							ra.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	static public void deleteFile(String localPath) {
		File f = new File(localPath);

		if (f.exists())
			deleteFile(f);
	}

	static public void moveFile(File src, File dest) {
		File dest_new;
		Boolean exist;

		if (!src.exists()) {
			return;
		}

		if (!dest.exists()) {
			src.renameTo(dest);
		} else {

			if (src.isDirectory()) {
				String files[] = src.list();
				for (String file : files) {
					File srcFile = new File(src, file);
					File destFile = new File(dest, file);
					moveFile(srcFile, destFile);
				}
				src.delete();
			} else {
				dest_new = new File(dest.getPath());
				do {
					dest_new = new File(dest_new.getPath() + ".bak");
					exist = dest_new.exists();
				} while (exist == true && dest_new.getPath().length() < 250);
				dest.renameTo(dest_new);
				src.renameTo(dest);
			}
		}
	}

	public static boolean makeDir(String localPath) {
		/*
		 * File file = new File(localPath); String dir = ""; int fromIndex,
		 * index; char slash = File.separatorChar;
		 * 
		 * if ((!file.exists() || !file.isDirectory())) { fromIndex = 1; do {
		 * index = localPath.indexOf(slash, fromIndex); if (index < 1) dir =
		 * localPath; else dir = localPath.substring(0, index); if
		 * (!dir.isEmpty()) { file = new File(dir); if (!file.exists() ||
		 * !file.isDirectory()) file.mkdir(); } fromIndex = index + 1; } while
		 * (index > 0); }
		 */
		boolean result = false;
		File file = new File(localPath);
		if (!file.exists())
			result = file.mkdir();
		else
			result = true;
		return result;
	}

	static public void removeDir(String localPath) {
		File f = new File(localPath);

		if (f.exists())
			deleteFile(f);
	}

	public static String getMD5(File file) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
		}
		String md5 = "";
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);

			byte[] dataBytes = new byte[2048];

			int nread = 0;
			while ((nread = fis.read(dataBytes)) != -1) {
				md.update(dataBytes, 0, nread);
			}
			;
			byte[] mdbytes = md.digest();

			md5 = ServiceUtils.toHex(mdbytes);

		} catch (FileNotFoundException e) {
			return md5;
		} catch (IOException e) {
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}

		return md5;
	}

	public static String getMD5(String filePath) {

		File file = new File(filePath);

		if (!file.exists()) {
			return "";
		}

		if (file.isDirectory()) {
			return Config.DIR_MD5;
		}

		return getMD5(file);
	}

	public static void deleteFile(File in_file) {

		if (in_file.isFile() == false) {
			File[] files = in_file.listFiles();
			if (files != null) {
				for (File f : files) {
					if (f.isDirectory()) {
						deleteFile(f);
					} else {
						f.delete();
					}
				}
			}
		}
		in_file.delete();
	}

	public static boolean isFileReadOnly(File f) {
		boolean isReadOnly = false;
		FileChannel channel = null;
		try {
			if (f.exists() && f.isFile()) {
				channel = new RandomAccessFile(f, "rw").getChannel();
			}
		} catch (Exception e) {
			String[] errDetailArr = e.getMessage().split("\\(");
			if (0 < errDetailArr.length) {
				String errDetail = errDetailArr[errDetailArr.length - 1];
				String errDetailUTF8 = "";
				try {
					errDetailUTF8 = new String(errDetail.getBytes("big5"), "UTF-8");
				} catch (UnsupportedEncodingException e1) {

				}
				if ("存取被拒。)".equals(errDetail) || "Access is denied)".equals(errDetail) || "Permission Denied)".equals(errDetail) || (StringUtils.isNotEmpty(errDetailUTF8))
				        && "存取被拒。)".equals(errDetailUTF8)) {
					// readonly file
					isReadOnly = true;
					Debug("File is readonly: " + f.getAbsolutePath());
				}
			}
		} finally {
			try {
				if (channel != null) {
					channel.close();
				}
			} catch (IOException e) {
			}
		}

		return isReadOnly;
	}

	public static boolean isFileLocked(File f) {
		boolean locked = false;
		FileChannel channel = null;

		if (isFileReadOnly(f))
			return locked;

		try {
			if (f.exists() && f.isFile()) {
				channel = new RandomAccessFile(f, "rw").getChannel();
			}
		} catch (Exception e) {
			locked = true;
			Debug("File access denied because of locked by other process: " + f.getAbsolutePath());

		} finally {
			try {
				if (channel != null) {
					channel.close();
				}
			} catch (IOException e) {
			}
		}

		return locked;
	}

	public static Boolean waitFileUnused(File f, long sec) {

		long loopCount = sec * 2;

		while (isFileLocked(f) == true && loopCount-- > 0) {
			Util.Sleep(500);
		}

		return (loopCount >= 0);
	}

	public static boolean copyFileNew(File in, File out) {
		if (null != in && in.exists() && null != out && !out.exists()) {
			in.renameTo(out);
		}

		return true;
	}

	public static Map<String, File> collectFiles(String dirPath) {
		Map<String, File> fileMap = new TreeMap<String, File>();
		collectFiles(fileMap, dirPath);
		return fileMap;
	}

	private static void collectFiles(Map<String, File> fileMap, String dirPath) {
		File[] files = new File(dirPath).listFiles();

		if (files == null)
			return;

		for (int i = 0; i < files.length; i++) {
			fileMap.put(files[i].getAbsolutePath(), files[i]);

			if (files[i].isDirectory())
				collectFiles(fileMap, files[i].getPath());
		}
	}

	public static void fileBackup(String object_key) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, "=== ready to backup file: " + object_key);
		String localPath = Util.translateLocalPath(object_key);
		String backupLocalPath = getBackupFileName(localPath);

		File localFile = new File(localPath);
		File backupFile = new File(backupLocalPath);
		if (localFile.isFile()) {
			try {
				FileUtils.copyFile(localFile, backupFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (localFile.isDirectory()) {
			// folder 不能用 rename 的方式, 會造成 jnotify 誤判以為真的是 rename event
			// 必須要用 copy 的方式建立 backupFile
			// localFile.renameTo(backupFile);
			try {
				if (localFile.isDirectory())
					FileUtils.moveDirectory(localFile, backupFile);
				else
					FileUtils.moveFile(localFile, backupFile);
			} catch (IOException e) {
				LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, "=== WARNING: backup failed: " + object_key);
			}
		}
	}

	private static String getBackupFileName(String localPath) {
		if (!Util.isStartFromRoot(localPath))
			return null;
		File oldFile = new File(localPath);
		String fileName = oldFile.getName();
		int pos = fileName.lastIndexOf('.');
		String nameWithoutExtension = null;
		String fileExtension = null;
		if (pos > 0 && pos < fileName.length() - 1) {
			nameWithoutExtension = fileName.substring(0, pos);
			fileExtension = fileName.substring(pos + 1);
		}
		if (nameWithoutExtension == null)
			nameWithoutExtension = fileName;
		if (fileExtension == null)
			fileExtension = "";
		String newName = nameWithoutExtension + "_Copy." + fileExtension;
		String newPath = oldFile.getParent() + File.separatorChar + newName;
		File newFile = new File(newPath);
		int counter = 1;
		while (newFile.exists()) {
			newName = nameWithoutExtension + "_Copy (" + counter + ")." + fileExtension;
			newPath = oldFile.getParent() + File.separatorChar + newName;
			newFile = new File(newPath);
			counter++;
		}
		return newPath;
	}

	public static String getBackupFolderName(String localPath) {
		if (!Util.isStartFromRoot(localPath))
			return null;

		File oldFile = new File(localPath);
		String fileName = oldFile.getName();

		String newName = fileName + "_Copy";
		String newPath = oldFile.getParent() + File.separatorChar + newName;
		File newFile = new File(newPath);
		int counter = 1;
		while (newFile.exists()) {
			newName = fileName + "_Copy (" + counter + ")";
			newPath = oldFile.getParent() + File.separatorChar + newName;
			newFile = new File(newPath);
			counter++;
		}
		return newPath;
	}

	public static void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, msg);
	}

}
