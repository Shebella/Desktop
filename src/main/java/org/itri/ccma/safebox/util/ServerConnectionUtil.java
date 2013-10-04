package org.itri.ccma.safebox.util;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;

public class ServerConnectionUtil {
	private static final int TIME_OUT_MILLISECOND = 5000;
	private static LoggerHandler _logger = LoggerHandler.getInstance();
	
	public static String getWebURL(String host_ip) {
		String serviceURL = getSBXServiceURL(host_ip);
		if (StringUtils.isNotEmpty(serviceURL)) {
			return serviceURL.substring(0, serviceURL.lastIndexOf('/')) + "/safebox";
		}

		return serviceURL;
	}

	public static String getSBXServiceURL(String host_ip) {
		_logger.debug(LoggerType.CSS, "Testing Safebox (" + host_ip +" ) service start...");
		String serviceURL = getServiceURL(host_ip, IGlobal.PRIMARY_SBX_SVR_PORT, IGlobal.SECOND_SBX_SVR_PORT, "/sbx_svr" + IGlobal.SBX_SVR_CHECK_ENDPOINT);
		if (StringUtils.isNotEmpty(serviceURL)) {
			_logger.debug(LoggerType.CSS, "Testing Safebox service RUL:" + serviceURL + "/sbx_svr");
			return serviceURL + "/sbx_svr";
		}

		return "";
	}

	public static String getCSSServiceURL(String host_ip) {
		_logger.debug(LoggerType.CSS, "Testing CSS (" + host_ip +" ) service start...");
		String serviceURL = getServiceURL(host_ip, IGlobal.PRIMARY_CSS_PORT, IGlobal.SECOND_CSS_PORT, IGlobal.CSS_CHECK_ENDPOINT);
		if (StringUtils.isNotEmpty(serviceURL)) {
			_logger.debug(LoggerType.CSS, "Testing CSS service RUL:" + serviceURL);
			return serviceURL;
		}

		return "";
	}

	public static String getServiceURL(String host_ip, String primary_port, String second_port, String check_url) {
		String serviceURL = "";

		if (getServiceURL(host_ip, primary_port, check_url)) {
			serviceURL = "http://" + host_ip + ":" + primary_port;
		} else if (getServiceURL(host_ip, second_port, check_url)) {
			serviceURL = "http://" + host_ip + ":" + second_port;
		}

		return serviceURL;
	}

	private static boolean getServiceURL(String host_ip, String port, String check_url) {
		_logger.debug(LoggerType.CSS, "get service URL: http://" + host_ip + ":" + port);
		
		HttpClient client = new HttpClient();
		client.getHttpConnectionManager().getParams().setConnectionTimeout(TIME_OUT_MILLISECOND);
		client.getHttpConnectionManager().getParams().setSoTimeout(TIME_OUT_MILLISECOND);

		GetMethod method = new GetMethod("http://" + host_ip + ":" + port);
		method.setPath(check_url);
		try {
			int responseCode = client.executeMethod(method);
//			_logger.debug(LoggerType.CSS, "http responseCode: " + responseCode);
			if (HttpStatus.SC_OK == responseCode) {
				return true;
			}
		} catch (HttpException he) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(he));
        } catch (IOException ioe) {
        	_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
        } finally {
			method.releaseConnection();
		}

		return false;
	}
}
