package org.itri.ccma.safebox.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

public class WindowsReqistry {
	/**
	 * @param location path in the registry
	 * @param key registry key
	 * @return registry value or null if not found
	 */
	public static String readRegistry(String location, String key) {
		try {
			// Run reg query, then read output with StreamReader (internal class)
			Process process = Runtime.getRuntime().exec("reg query " + '"' + location + "\" /v " + key);
			StreamReader streamReader = new StreamReader(process.getInputStream());

			streamReader.start();
			process.waitFor();
			streamReader.join();
			// Parse out the value
			/*String[] parsed = streamReader.getResult().split("\\s+");
			if(parsed.length > 1) {
				return parsed[parsed.length - 1]; 
			}*/
			return streamReader.getResult(location, key);
		} catch (Exception e) {

		}

		return null;
	}

	static class StreamReader extends Thread {
		private InputStream inputStream;
		private StringWriter stringWriter = new StringWriter();

		public StreamReader(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		public void run() {
			try {
				int c;

				while ((c = inputStream.read()) != -1) {
					stringWriter.write(c);
				}
			} catch (IOException e) {

			}
		}

		public String getResult(String location, String key) {
			String regKey = stringWriter.toString().replace(location, "").replace(key, "");
			String result = regKey.replace("REG_SZ", "").replace("  ", "").replace("\r\n", "");

			return result;
		}
	}

}
