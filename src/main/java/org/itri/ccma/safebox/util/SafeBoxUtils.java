package org.itri.ccma.safebox.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.jets3t.service.ServiceException;
import org.jets3t.service.security.AWSCredentials;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.jamesmurty.utils.XMLBuilder;

public class SafeBoxUtils {

	public static final String USER_HOME = "user.home";
	public static final String DEFAULT_USER_DATA_ROOT = ".safebox.dat";
	public static final String SBX_PROPERTIES_NAME = "safebox.cfg";
	public static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AccessKeyID";
	public static final String AWS_SECRET_KEY_PROPERTY_NAME = "SecretAccessKey";

	/**
	 * 
	 * @return the AWS credentials loaded from the samples properties file.
	 */
	public static AWSCredentials loadAWSCredentials() throws IOException {

		StringBuilder resourceName = new StringBuilder();
		resourceName.append(System.getProperty(USER_HOME)).append(File.separator).append(DEFAULT_USER_DATA_ROOT).append(File.separator).append(SBX_PROPERTIES_NAME);

		Properties sbxProperties = new Properties();
		sbxProperties.load(new FileInputStream(resourceName.toString()));

		if (!sbxProperties.containsKey(AWS_ACCESS_KEY_PROPERTY_NAME)) {
			throw new RuntimeException("Properties file '" + SBX_PROPERTIES_NAME + "' does not contain required property: " + AWS_ACCESS_KEY_PROPERTY_NAME);
		}
		if (!sbxProperties.containsKey(AWS_SECRET_KEY_PROPERTY_NAME)) {
			throw new RuntimeException("Properties file '" + SBX_PROPERTIES_NAME + "' does not contain required property: " + AWS_SECRET_KEY_PROPERTY_NAME);
		}

		String accessKey = sbxProperties.getProperty(AWS_ACCESS_KEY_PROPERTY_NAME);
		String secretKey = sbxProperties.getProperty(AWS_SECRET_KEY_PROPERTY_NAME);

		return new AWSCredentials(accessKey, secretKey);
	}

	public static String parseXMLError(ServiceException ex) {

		if (ex.getResponseCode() == -1 || StringUtils.isEmpty(ex.getResponseStatus())) {
			return Util.getStackTrace(ex);
		}

		StringBuilder str = new StringBuilder();

		str.append("ErrorResponses: { ResponseCode:").append(ex.getResponseCode());
		str.append(",ResponseStatus:").append(ex.getResponseStatus());

		String slah = "//";
		String attrs[] = { "Code", "Message", "Resource", "RequestId" };

		try {
			XMLBuilder root = ex.getXmlMessageAsBuilder();

			if (root == null) {
				str.append(" }");
				return str.toString();
			}

			XMLBuilder builder;
			for (int i = 0; i < attrs.length; i++) {
				builder = root.xpathFind(slah + attrs[i]);
				String textCnt = builder.getElement().getTextContent();
				str.append(",").append(attrs[i]).append(":").append(textCnt);
			}
			str.append(" }");
		} catch (IOException io) {
		} catch (ParserConfigurationException pce) {
		} catch (SAXException saxe) {
		} catch (XPathExpressionException xee) {
		}

		return str.toString();
	}

	public static String getXMLErrorValueByAttr(ServiceException ex, String attribute) {

		if (StringUtils.isEmpty(attribute)) {
			return null;
		}

		String value = null;
		String slah = "//";
		String attrs[] = { "Code", "Message", "Resource", "RequestId" };

		try {
			XMLBuilder root = ex.getXmlMessageAsBuilder();

			if (root == null) {
				return null;
			}

			XMLBuilder builder;
			for (int i = 0; i < attrs.length; i++) {

				if (attrs[i].equalsIgnoreCase(attribute)) {
					builder = root.xpathFind(slah + attrs[i]);
					value = builder.getElement().getTextContent();
					break;
				}
			}
		} catch (IOException io) {
		} catch (ParserConfigurationException pce) {
		} catch (SAXException saxe) {
		} catch (XPathExpressionException xee) {
		}

		return value;
	}

	public static String parseEBSError(JSONObject jsonResponse) {
		String element[] = { "code", "requestId", "message" };
		StringBuilder str = new StringBuilder();
		str.append("ErrorResponses: {");
		JSONArray jsonErrors;
		try {

			jsonErrors = jsonResponse.getJSONArray("errors");
			for (int i = 0; i < jsonErrors.length(); i++) {
				JSONObject error = jsonErrors.getJSONObject(i);
				for (int j = 0; j < element.length; j++) {
					if (j == element.length) {
						str.append(element[j]).append(":").append(error.getString(element[j]));
					} else {
						str.append(element[j]).append(":").append(error.getString(element[j])).append(",");
					}
				}
				if (i != jsonErrors.length() - 1) {
					str.append(",");
				}
			}
		} catch (JSONException jsone) {
		}
		str.append("}");

		return str.toString();
	}

	public static boolean isValidJSON(final String json) {
		boolean valid = false;
		try {
			final JsonParser parser = new ObjectMapper().getJsonFactory().createJsonParser(json);
			while (parser.nextToken() != null) {
			}
			valid = true;
		} catch (JsonParseException jpe) {
			jpe.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		return valid;
	}
}
