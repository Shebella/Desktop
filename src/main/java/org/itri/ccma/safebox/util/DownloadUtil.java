package org.itri.ccma.safebox.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class DownloadUtil {

	public static String downloadFile(String download_url) {
		if (StringUtils.isEmpty(download_url)) {
			return "";
		}

		File downloadFile = null;
		OutputStream outputStream = null;
		HttpMethod method = new GetMethod(download_url);

		try {
			downloadFile = File.createTempFile("safebox", ".exe");
			int statusCode = (new HttpClient()).executeMethod(method);

			if (statusCode != HttpStatus.SC_OK) {
				System.err.println("Method failed: " + method.getStatusLine());
				return "";
			}

			outputStream = new FileOutputStream(downloadFile);
			IOUtils.copy(method.getResponseBodyAsStream(), outputStream);

			return downloadFile.getAbsolutePath();

		} catch (Exception e) {
			System.err.println("=== download file error: " + e);
		} finally {
			if (method != null) {
				method.releaseConnection();
			}
			if (null != outputStream) {
				try {
					outputStream.close();
				} catch (IOException e) {
				}
			}
		}

		return "";
	}
}
