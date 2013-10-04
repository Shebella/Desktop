package org.itri.ccma.safebox;

import java.io.File;

//import java.nio.file.Files;

public class FileNode {
	String path;
	Boolean dir;
	long time;
	long ver;
	long size;

	String fileName = "";

	public FileNode() {
		path = "";
		dir = false;
		time = 0;
		ver = 0;
		size = 0;
	}

	// create node according to f's attribute,
	// f attr might change later, so memorize snapshot.
	public FileNode(File f) {
		path = f.getPath();
		time = f.lastModified();
		dir = f.isDirectory();
		ver = 0;
		size = f.length();

		fileName = f.getName();
	}

	public Boolean isDirectory() {
		return dir;
	}

	public Boolean isDiff(FileNode fn) {

		if (fn == null)
			return true;

		if (path.equals(fn.path) == false)
			return true;
		else if (time != fn.time)
			return true;
		else
			return false;
	}

	public Boolean isDiffPath(FileNode fn) {

		if (fn == null)
			return true;

		if (path.equals(fn.path) == false)
			return true;
		else
			return false;
	}

	public long lastModified() {
		return time;
	}

	public String getPath() {
		return path;
	}

	public long getSize() {
		return size;
	}

	public void setTime(long t) {
		time = t;
	}

	public void resetTime() {
		File f0 = new File(path);
		time = f0.lastModified();
	}

	public String getName() {
		return fileName;
	}

	public String toString() {
		String s;

		s = path + "\t";
		s += (dir ? "true" : "false") + "\t";
		s += String.valueOf(time) + "\t";
		return s;
	}
}
