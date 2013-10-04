package org.itri.ccma.safebox;

import java.text.SimpleDateFormat;
import java.util.EnumSet;

public class IGlobal {
	public static enum APP_STATE {
		NORMAL, DISCONNECT, SYNCING, PAUSED, PROCESSING, SHUTDOWN, LOGOUT
	};

	public static enum SERVER_EVENT_TYPE {
		RUNAWAY("runaway"), NO_EVENT("fail"), HAS_EVENTS("true"), ERROR("error");

		private String text;

		SERVER_EVENT_TYPE(String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return this.text;
		}

		public static SERVER_EVENT_TYPE getByValue(String value) {
			for (final SERVER_EVENT_TYPE element : EnumSet.allOf(SERVER_EVENT_TYPE.class)) {
				if (element.toString().equals(value)) {
					return element;
				}
			}
			return SERVER_EVENT_TYPE.ERROR;
		}
	}

	public static final String APP_PATH = System.getProperty("user.dir") + "/";
	public static final String APP_NAME = "SafeBox";
	public static final String APP_VER = "v1.2.1";
	public static final String APP_SUB_VER = "2112";
	public static final String APP_FULL_NAME = APP_NAME + " " + APP_VER;
	public static final String APP_HELP_WEB_ADDR = "https://iscweb.itri.org.tw/";
	public static final String APP_NOTICE_WEB_ADDR = "http://www.itri.org.tw/eng/econtent/copyright/copyright01.aspx";
	public static final String APP_ICON = "safebox.png";

	public static final String PRIMARY_SBX_SVR_PORT = "80";
	public static final String SECOND_SBX_SVR_PORT = "8088";
	public static final String SBX_SVR_CHECK_ENDPOINT = "/test.jsp";

	public static final String PRIMARY_CSS_PORT = "80";
	public static final String SECOND_CSS_PORT = "8773";
	public static final String CSS_CHECK_ENDPOINT = "/services/Heartbeat";

	public static final String CLIENT_DOWNLOAD_LINK = "/downloadKit/SafeboxAutoExtract.exe";

	public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy/MM/dd HH:mm");

	public static APP_STATE appState = APP_STATE.NORMAL;
}
