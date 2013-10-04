package org.itri.ccma.safebox.db;

public class FileObject {
	public String		objectKey		= "";
	public int			sequence		= 0;
	public String		MD5				= "";
	public boolean		isFolder		= false;
	public long		modifiedDate	= System.currentTimeMillis();
	public long		size			= 0; //Only used in S3 API

	public FileObject() {
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(objectKey);
		buffer.append("_");
		buffer.append(sequence);
		buffer.append("_");
		buffer.append(MD5);		
		return buffer.toString();
	}
}
