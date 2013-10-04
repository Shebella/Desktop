package org.itri.ccma.safebox.s3;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

import org.itri.ccma.safebox.db.FileObject;
import org.jets3t.service.model.S3Object;

public class S3Event implements Comparable<S3Event>, Serializable {
	public static enum FileAction {
		None, Create, Delete, Modify, Rename
	}

	public static enum EventState {
		New, Process, Success, Fail, Error, Delete
	}

	public S3Event() {

	}
	
	// Fields
	public String oldObjectKey = "";
	public FileAction fileAction = FileAction.None;
	public EventState state = EventState.New;
	public int retry = 0;
	public FileObject fileObject = new FileObject();

	public boolean barrier = false;

	public long syncID = 0;
	public long timestamp = -1;

	public S3Event(FileAction fileAction, String objectKey) {
		this.fileAction = fileAction;
		this.fileObject.objectKey = objectKey;
	}

	public void configFileObject(File file) {
		configFileObject(file.isDirectory(), file.lastModified(), file.length());
	}

	public void configFileObject(boolean isDirectory, long modifiedDate, long size) {
		fileObject.MD5 = "";
		fileObject.isFolder = isDirectory;
		fileObject.modifiedDate = modifiedDate;
		fileObject.size = size;
	}

	public void ResetTime(Date t) {
		if (t == null) {
			fileObject.modifiedDate = System.currentTimeMillis();
		} else {
			fileObject.modifiedDate = t.getTime();
		}
	}

	public String toString() {
		String s = "";
		// int sum= 0;

		// s = Util.GetTimeStr(modifiedDate);
		s += "\t";
		s += fileObject.objectKey;
		s += "\t";
		s += fileAction.toString();
		s += "\t";
		s += fileObject.MD5;
		s += "\t";
		// sum= Util.CalcChkSum(s);
		// s+= String.valueOf(sum);

		return s;
	}

	@Override
	public boolean equals(Object obj) {
		if (fileObject.objectKey.equals(((S3Event) obj).fileObject.objectKey) && ((S3Event) obj).fileAction == fileAction) {
			return true;
		}

		return false;
	}

	@Override
	public int compareTo(S3Event se) {
		return fileObject.objectKey.compareToIgnoreCase(se.fileObject.objectKey);
	}

	public static Comparator<S3Event> TimeComparator = new Comparator<S3Event>() {
		public int compare(S3Event arg0, S3Event arg1) {
			if (arg0.fileObject.modifiedDate == arg1.fileObject.modifiedDate)
				return 0;
			return (arg0.fileObject.modifiedDate < arg1.fileObject.modifiedDate) ? -1 : 1;
		}
	};

	public static int alphacompare(String obj_key1, String obj_key2) {
		String firstString = obj_key1;
		String secondString = obj_key2;

		if (secondString == null || firstString == null) {
			return 0;
		}

		int lengthFirstStr = firstString.length();
		int lengthSecondStr = secondString.length();

		int index1 = 0;
		int index2 = 0;

		while (index1 < lengthFirstStr && index2 < lengthSecondStr) {
			char ch1 = firstString.charAt(index1);
			char ch2 = secondString.charAt(index2);

			char[] space1 = new char[lengthFirstStr];
			char[] space2 = new char[lengthSecondStr];

			int loc1 = 0;
			int loc2 = 0;

			do {
				space1[loc1++] = ch1;
				index1++;

				if (index1 < lengthFirstStr) {
					ch1 = firstString.charAt(index1);
				} else {
					break;
				}
			} while (Character.isDigit(ch1) == Character.isDigit(space1[0]));

			do {
				space2[loc2++] = ch2;
				index2++;

				if (index2 < lengthSecondStr) {
					ch2 = secondString.charAt(index2);
				} else {
					break;
				}
			} while (Character.isDigit(ch2) == Character.isDigit(space2[0]));

			String str1 = new String(space1);
			String str2 = new String(space2);

			int result;

			if (Character.isDigit(space1[0]) && Character.isDigit(space2[0])) {
				Integer firstNumberToCompare = Integer.valueOf(str1.trim());
				Integer secondNumberToCompare = Integer.valueOf(str2.trim());
				result = firstNumberToCompare.compareTo(secondNumberToCompare);
			} else {
				result = str1.compareTo(str2);
			}

			if (result != 0) {
				return result;
			}
		}
		return lengthFirstStr - lengthSecondStr;
	}

	public static Comparator<S3Event> S3EventComparator = new Comparator<S3Event>() {
		public int compare(S3Event firstObjToCompare, S3Event secondObjToCompare) {
			return alphacompare(firstObjToCompare.fileObject.objectKey, secondObjToCompare.fileObject.objectKey);
		}
	};

	public static Comparator<S3Object> S3ObjectComparator = new Comparator<S3Object>() {
		public int compare(S3Object firstObjToCompare, S3Object secondObjToCompare) {
			return alphacompare(firstObjToCompare.getKey(), secondObjToCompare.getKey());
		}
	};
}
