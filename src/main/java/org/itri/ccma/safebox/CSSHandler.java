package org.itri.ccma.safebox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.db.FileObject;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.SafeBoxUtils;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CSSHandler {
	public enum UNLOCK_TYPE {
		SUCC, FAIL
	}

	private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";
	private static final String DETAIL_DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
	private static final String ERROR = "error";
	private static CSSHandler instance = new CSSHandler();

	private LoggerHandler _logger = LoggerHandler.getInstance();
	private String _serviceURL = "";
	private HttpClient _client = null;
	private MultiThreadedHttpConnectionManager _connectionManager = null;
	private Config _config = Config.getInstance();
	private String currentSyncId = "-1";

	private static AtomicInteger eventCounts = new AtomicInteger(0);

	public static CSSHandler getInstance() {
		return instance;
	}

	private CSSHandler() {
		_connectionManager = new MultiThreadedHttpConnectionManager();
		int threadCount = Integer.parseInt(_config.getValue(KEYS.EventSyncWorkerCount));
		_connectionManager.getParams().setDefaultMaxConnectionsPerHost(threadCount);
		_connectionManager.getParams().setMaxTotalConnections(threadCount * 5);

		_client = new HttpClient(_connectionManager);
		_client.getParams().setBooleanParameter("http.protocol.expect-continue", false);

		_serviceURL = ServerConnectionUtil.getSBXServiceURL(_config.getValue(KEYS.HostIP));
	}

	public long getCurrentSyncId() {
		return Long.parseLong(currentSyncId);
	}

	public ComparableVersion getNewClientVersion() {
		String response = null;
		try {
			response = _sendRequest("queryversion", "get", null);
		} catch (SafeboxException ex) {
			_logger.error(LoggerType.CSS, ex.getMessage());
		}

		if (StringUtils.isEmpty(response)) {
			return null;
		}

		String serverVersion = null;
		try {
			serverVersion = (new JSONObject(response)).getString("version").trim();
			if (serverVersion.startsWith("v")) {
				serverVersion = serverVersion.substring(1);
			}
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		return new ComparableVersion(serverVersion);
	}

	public IGlobal.SERVER_EVENT_TYPE getExistingEvents(long lastSyncId) {

		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)),
		        new NameValuePair("lastsyncid", String.valueOf(lastSyncId)) };

		StringBuilder debugBuilder = new StringBuilder("getExistingEvents#request: {account:").append(_config.getValue(KEYS.User));
		debugBuilder.append(",instanceid:").append(_config.getValue(KEYS.InstanceKey));
		debugBuilder.append(",lastsyncid:").append(lastSyncId);
		debugBuilder.append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = null;
		try {
			response = _sendRequest("existingeventsV", "get", queryString);
		} catch (SafeboxException se) {
			_logger.error(LoggerType.CSS, se.getMessage());
		}

		if (StringUtils.isEmpty(response)) {
			_logger.error(LoggerType.CSS, "CSS#getExistingEvents response empty or null.");
			return IGlobal.SERVER_EVENT_TYPE.ERROR;
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			_logger.error(LoggerType.CSS, "CSS#getExistingEvents response (None JSON format):" + response);
			return IGlobal.SERVER_EVENT_TYPE.ERROR;
		}

		_logger.debug(LoggerType.CSS, "getExistingEvents#response: " + response);

		try {

			JSONObject jsonResponse = new JSONObject(response);
			boolean result = jsonResponse.getBoolean("result");

			if (result) {
				if (jsonResponse.getBoolean("isrunaway")) {
					return IGlobal.SERVER_EVENT_TYPE.RUNAWAY;
				} else if (jsonResponse.getBoolean("hasevent")) {
					return IGlobal.SERVER_EVENT_TYPE.HAS_EVENTS;
				} else {
					return IGlobal.SERVER_EVENT_TYPE.NO_EVENT;
				}

			} else {
				// false
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseEBSError(jsonResponse));
			}

		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		}

		return IGlobal.SERVER_EVENT_TYPE.ERROR;
	}

	/*
	 * Request for sync id infinite times till we get the sync lock.
	 */
	public Map<String, String> initSyncRequest() throws SafeboxException {

		currentSyncId = "-1"; // initial current sync id

		Map<String, String> syncInfoMap = new HashMap<String, String>();

		syncInfoMap.put("syncId", currentSyncId);

		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)) };

		StringBuilder debugBuilder = new StringBuilder("initSyncRequest#request: {account:").append(_config.getValue(KEYS.User));
		debugBuilder.append(",instanceid:").append(_config.getValue(KEYS.InstanceKey)).append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		// boolean result = false;

		String response = null;
		try {
			response = _sendRequest("syncrequestV", "put", queryString);
		} catch (SafeboxException ex) {
			_logger.error(LoggerType.CSS, ex.getMessage());
		}

		if (StringUtils.isEmpty(response)) {
			_logger.error(LoggerType.CSS, "CSS#initSyncRequest response empty.");
			currentSyncId = syncInfoMap.get("syncId");
			return syncInfoMap;
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			_logger.error(LoggerType.CSS, "CSS#initSyncRequest response (None JSON format):" + response);
			currentSyncId = syncInfoMap.get("syncId");
			return syncInfoMap;
		}

		_logger.debug(LoggerType.CSS, "initSyncRequest#response: " + response);

		try {
			JSONObject jsonResponse = new JSONObject(response);
			boolean result = jsonResponse.getBoolean("result");
			if (result) {
				if (StringUtils.isNotEmpty(jsonResponse.getString("syncId"))) {
					syncInfoMap.put("syncId", jsonResponse.getString("syncId"));
				}

			}

		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		currentSyncId = syncInfoMap.get("syncId");

		return syncInfoMap;
	}

	public void unlockSyncId(UNLOCK_TYPE unlockType) {
		int totaleventCounts = this.getEventCount() + S3Assist.getInstance().getEventCount();

		_logger.debug(LoggerType.CSS, "CSS#unlockSyncId css eventcount:" + this.getEventCount() + ", s3assist eventcount:" + S3Assist.getInstance().getEventCount());

		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), 
				new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)),
		        new NameValuePair("result", unlockType.toString()), 
		        new NameValuePair("eventcount", String.valueOf(totaleventCounts)) };

		StringBuilder debugBuilder = new StringBuilder("unlockSyncId#request: {account:").append(_config.getValue(KEYS.User));
		debugBuilder.append(",instanceid:").append(_config.getValue(KEYS.InstanceKey));
		debugBuilder.append(",result:").append(unlockType);
		debugBuilder.append(",eventcount:").append(String.valueOf(totaleventCounts)).append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = null;
		try {
			response = _sendRequest("unlockV", "put", queryString);
		} catch (SafeboxException se) {
			_logger.error(LoggerType.CSS, se.getMessage());
		}

		if (StringUtils.isEmpty(response)) {
			_logger.error(LoggerType.CSS, "CSS#unlockSyncId response empty.");
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			_logger.error(LoggerType.CSS, "CSS#unlockSyncId response (None JSON format):" + response);
		}

		_logger.debug(LoggerType.CSS, "unlockSyncId#response: " + response);
	}

	public HashMap<Long, List<S3Event>> getServerEvents() throws SafeboxException {
		HashMap<Long, List<S3Event>> eventGroups = new HashMap<Long, List<S3Event>>();

		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)),
		        new NameValuePair("fromsyncid", String.valueOf(_config.getLastSyncId())), new NameValuePair("tosyncid", String.valueOf(currentSyncId)) };

		StringBuilder debugBuilder = new StringBuilder("getServerEvents#request: {account:").append(_config.getValue(KEYS.User));
		debugBuilder.append(",instanceid:").append(_config.getValue(KEYS.InstanceKey));
		debugBuilder.append(",fromsyncid:").append(_config.getLastSyncId());
		debugBuilder.append(",tosyncid:").append(currentSyncId);
		debugBuilder.append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = _sendRequest("queryeventsV", "get", queryString);

		if (StringUtils.isEmpty(response)) {
			throw new SafeboxException("CSS#getServerEvents response empty.");
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			throw new SafeboxException("CSS#getServerEvents response (None JSON format):" + response);
		}

		// Debug("getServerEvents#response: " + response);

		List<S3Event> events = null;
		try {
			JSONObject jsonResponse = new JSONObject(response);
			boolean result = jsonResponse.getBoolean("result");
			if (result) {
				int size = jsonResponse.getInt("size");

				if (size == 0) {
					return eventGroups;
				}

				// TODO EBS could be response empty key:value ex.record it's
				// need fix on next phase
				JSONArray jsonRecords = jsonResponse.getJSONArray("record");

				if (size != jsonRecords.length()) {
					throw new SafeboxException("Error !! response size not equals records size.");
				}

				for (int i = 0; i < jsonRecords.length(); i++) {
					// JSONObject record = jsonRecords.getJSONObject(i);
					S3Event event = _parseJsonToS3Event(jsonRecords.getJSONObject(i));
					Long groupKey = new Long(event.syncID);
					events = eventGroups.get(groupKey);

					if (events == null) {
						events = new ArrayList<S3Event>();
						events.add(event);
						eventGroups.put(groupKey, events);
					} else {
						events.add(event);
					}
				}

			} else {
				// false
				throw new SafeboxException(SafeBoxUtils.parseEBSError(jsonResponse));
			}
		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		}

		return eventGroups;
	}

	private FileObject _parseJsonToFileObject(JSONObject jsonObj) {
		SimpleDateFormat sdf = new SimpleDateFormat(DETAIL_DATE_FORMAT_PATTERN);
		FileObject obj = null;

		try {
			obj = new FileObject();
			obj.objectKey = Util.urlDecode(jsonObj.getString("objectId"));
			obj.MD5 = jsonObj.getString("etagId");

			if (jsonObj.getInt("isFolder") == 1)
				obj.isFolder = true;
			else
				obj.isFolder = false;

			String modTime = jsonObj.getString("modifiedDate");
			if (StringUtils.isNotEmpty(jsonObj.getString("modifiedDate"))) {
				try {
					obj.modifiedDate = sdf.parse(modTime).getTime();
				} catch (ParseException e) {
					SimpleDateFormat sdf_simple = new SimpleDateFormat(DATE_FORMAT_PATTERN);
					try {
						obj.modifiedDate = sdf_simple.parse(modTime).getTime();
					} catch (ParseException e1) {
						_logger.error(LoggerType.CSS, Util.getStackTrace(e1));
					}
				}
			}
		} catch (JSONException e) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(e));
		}

		return obj;
	}

	private S3Event _parseJsonToS3Event(JSONObject jsonObj) {
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_PATTERN);
		S3Event event = null;

		try {
			S3Event.FileAction fileAction = S3Event.FileAction.valueOf(jsonObj.getString("op"));
			String objectKey = Util.urlDecode(jsonObj.getString("fpth"));
			// Rename's objectKey's format is special (A||B for rename A to B),
			// needs to separate it.
			if (fileAction.equals(S3Event.FileAction.Rename)) {
				String[] renameKeys = objectKey.split("\\|\\|"); // "\\|\\|" ->
				                                                 // "||"
				event = new S3Event(fileAction, renameKeys[1]);
				event.oldObjectKey = renameKeys[0];

				_logger.debug(LoggerType.CSS, "Get rename event: " + objectKey + " And devide it into old: " + event.oldObjectKey + " new: " + event.fileObject.objectKey);
			} else {
				event = new S3Event(fileAction, objectKey);
			}

			event.syncID = jsonObj.getLong("syncid");

			String modTime = jsonObj.getString("time");
			if (StringUtils.isNotEmpty(jsonObj.getString("time"))) {
				event.fileObject.modifiedDate = sdf.parse(modTime).getTime();
			}

			event.fileObject.isFolder = jsonObj.getBoolean("isfolder");
			if (event.fileObject.isFolder) {
				event.fileObject.MD5 = Config.DIR_MD5;
			}

			int objSeq = jsonObj.getInt("file_version");
			event.fileObject.sequence = objSeq == -1 ? 0 : objSeq;
			// file size
			event.fileObject.size = jsonObj.getInt("fsz");

		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		return event;
	}

	public boolean renameObject(long syncid, String srcObj, String destObj) throws SafeboxException {
		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)),
		        new NameValuePair("syncid", String.valueOf(syncid)), new NameValuePair("src_object", Util.urlEncode(srcObj)),
		        new NameValuePair("dest_object", Util.urlEncode(destObj)), new NameValuePair("bucket_name", _config.getValue(KEYS.DefaultBucket)), };

		StringBuilder debugBuilder = new StringBuilder("renameObject#request: {account:").append(_config.getValue(KEYS.User));
		debugBuilder.append(",instanceid:").append(_config.getValue(KEYS.InstanceKey));
		debugBuilder.append(",syncid:").append(syncid);
		debugBuilder.append(",src_object:").append(srcObj);
		debugBuilder.append(",dest_object:").append(destObj);
		debugBuilder.append(",bucket_name:").append(_config.getValue(KEYS.DefaultBucket));
		debugBuilder.append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = _sendRequest("renameobject", "post", queryString, null, syncid);

		if (StringUtils.isEmpty(response)) {
			throw new SafeboxException("CSS#renameObject response empty#" + "syncid:" + syncid + " | src_object:" + srcObj + " | dest_object:" + destObj);
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			throw new SafeboxException("CSS#renameObject response (None JSON format):" + response);
		}

		try {
			boolean result = new JSONObject(response).getBoolean("success");
			if (result) {
				eventCounts.getAndIncrement();
				_logger.debug(LoggerType.CSS, "renameObject#response: " + response + " | syncid:" + syncid + " | src_object:" + srcObj + " | dest_object:" + destObj + ", eventCounts:" + eventCounts.get());
			} else {
				_logger.debug(LoggerType.CSS, "renameObject#response: " + response + " | syncid:" + syncid + " | src_object:" + srcObj + " | dest_object:" + destObj);
			}

			return result;
		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		return false;
	}

	public void getBucketInfo() throws SafeboxException {
		String bucketName = _config.getValue(KEYS.DefaultBucket);

		Command.BUCKET_INFO.setValidate(false);

		if (StringUtils.isNotEmpty(bucketName)) {
			NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("bucketname", bucketName) };
			String response = null;
			try {
				response = _sendRequest("querybucketinfo", "get", queryString);
			} catch (SafeboxException ex) {
				_logger.error(LoggerType.CSS, ex.getMessage());
			}

			if (StringUtils.isEmpty(response)) {
				throw new SafeboxException("CSS#getBucketInfo response empty.");
			} else if (!SafeBoxUtils.isValidJSON(response)) {
				throw new SafeboxException("CSS#getBucketInfo response (None JSON format):" + response);
			}
			_logger.debug(LoggerType.CSS, "getBucketInfo#response: " + response);

			try {
				JSONObject jsonResponse = new JSONObject(response);

				if (ERROR.equals(jsonResponse.getString("result"))) {
					_logger.error(LoggerType.CSS, SafeBoxUtils.parseEBSError(jsonResponse));
				} else {
					boolean result = jsonResponse.getBoolean("result");
					if (result) {
						Command.BUCKET_INFO.setMaxBytes(new BigDecimal(jsonResponse.getString("maxSize")));
						Command.BUCKET_INFO.setUsedBytes(new BigDecimal(jsonResponse.getString("totalUsed")));
						Command.BUCKET_INFO.setValidate(true);

						Config.BUCKET_FILES_COUNT = jsonResponse.getInt("objectCount");
						Config.BUCKET_FOLDERS_COUNT = jsonResponse.getInt("folderCount");

						Command.BUCKET_INFO.setObjectCount(Config.BUCKET_FILES_COUNT + Config.BUCKET_FOLDERS_COUNT);
					}
				}

			} catch (JSONException jsone) {
				_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
			} catch (Throwable throwable) {
				_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
			}
		}
	}

	public void startHeartBeat() throws SafeboxException {
		NameValuePair[] queryString = { new NameValuePair("account", _config.getValue(KEYS.User)), new NameValuePair("instanceid", _config.getValue(KEYS.InstanceKey)) };

		StringBuilder debugBuilder = new StringBuilder("startHeartBeat#request: {account:").append(_config.getValue(KEYS.User)).append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = _sendRequest("heartbeat", "post", queryString);

		if (StringUtils.isEmpty(response)) {
			throw new SafeboxException("CSS#startHeartBeat response empty.");
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			throw new SafeboxException("CSS#startHeartBeat response (None JSON format):" + response);
		}
		_logger.debug(LoggerType.CSS, "startHeartBeat#response: " + response);

		JSONObject jsonResponse;
		try {
			jsonResponse = new JSONObject(response);

			boolean result = jsonResponse.getBoolean("result");

			_logger.debug(LoggerType.CSS, "Is heart beat: " + (result ? "success" : "failed"));

			if (!result) {
				throw new SafeboxException(SafeBoxUtils.parseEBSError(jsonResponse));
			}

		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		}
	}

	private String _sendRequest(String ebsApi, String httpMethod, NameValuePair[] queryString) throws SafeboxException {
		return _sendRequest(ebsApi, httpMethod, queryString, null, null);
	}

	private String _sendRequest(String ebsApi, String httpMethod, NameValuePair[] queryString, List<S3Event> event, Long syncId) throws SafeboxException {

		String result = null;

		/*
		 * serviceURL =
		 * ServerConnectionUtil.getSBXServiceURL(config.getValue(KEYS.HostIP));
		 * if (StringUtils.isEmpty(serviceURL)) {
		 * Error("Send Http Request failed! serviceURL is null or empty.");
		 * return result; }
		 */

		String URL = _serviceURL + "/rest/EBS/" + ebsApi;
		HttpMethod method = null;

		try {
			// client = new HttpClient();
			if (httpMethod.compareToIgnoreCase("get") == 0) {
				method = new GetMethod(URL);
			} else if (httpMethod.compareToIgnoreCase("put") == 0) {
				method = new PutMethod(URL);
			} else if (httpMethod.compareToIgnoreCase("post") == 0) {
				method = new PostMethod(URL);
				method.addRequestHeader(new Header("Connection", "close"));
			}

			if (method == null) {
				return result;
			}

			if (queryString != null) {
				method.setQueryString(queryString);
			}

			// Execute the method.
			int statusCode = _client.executeMethod(method);

			if (statusCode == HttpStatus.SC_OK) {
				// Read the response body.
				StringBuffer stb = new StringBuffer();

				InputStream ins = method.getResponseBodyAsStream();
				InputStreamReader insReader = new InputStreamReader(ins);
				BufferedReader br = new BufferedReader(insReader);
				String buffText = br.readLine();
				while (null != buffText) {
					stb.append(buffText);
					buffText = br.readLine();
				}

				if (stb.length() == 0 || StringUtils.isEmpty(stb.toString())) {
					_logger.debug(LoggerType.CSS, "HttpStatusCode:" + statusCode + " Http Response Body is empty!");
				}

				result = stb.toString();

			} else {
				throw new SafeboxException("HttpStatusCode: " + statusCode + " Method failed: " + method.getStatusLine());
			}

		} catch (SafeboxException se) {
			throw se;
		} catch (URIException urie) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(urie));
		} catch (IOException ioe) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		} finally {
			// Release the connection.
			if (method != null) {
				method.releaseConnection();
			}
		}
		return result;
	}

	public boolean ListObjectsDetails(List<FileObject> objList, List<FileObject> objDIRList) throws SafeboxException {
		boolean result = true;

		NameValuePair[] queryString = { new NameValuePair("bucket", "bkt-" + _config.getValue(KEYS.User)) };

		StringBuilder debugBuilder = new StringBuilder("getServerEvents#request: {bucket:").append("bkt-" + _config.getValue(KEYS.User));
		debugBuilder.append("}");

		_logger.debug(LoggerType.CSS, debugBuilder.toString());

		String response = _sendRequest("listobjectsdetail", "get", queryString);

		if (StringUtils.isEmpty(response)) {
			throw new SafeboxException("CSS#getServerEvents response empty.");
		} else if (!SafeBoxUtils.isValidJSON(response)) {
			throw new SafeboxException("CSS#getServerEvents response (None JSON format):" + response);
		}

		_logger.debug(LoggerType.CSS, "CSS#getServerEvents receive response.");

		try {
			JSONObject jsonResponse = new JSONObject(response);

			_logger.debug(LoggerType.CSS, "CSS#getServerEvents finish json transform.");

			result = jsonResponse.getBoolean("result");
			if (result) {
				JSONArray jsonRecords = jsonResponse.getJSONArray("objBeanList");

				if (jsonRecords != null) {
					for (int i = 0; i < jsonRecords.length(); i++) {
						FileObject obj = null;
						obj = _parseJsonToFileObject(jsonRecords.getJSONObject(i));
						if (obj.isFolder)
							objDIRList.add(obj);
						else
							objList.add(obj);
					}

				}

			} else {
				// false
				throw new SafeboxException(SafeBoxUtils.parseEBSError(jsonResponse));
			}

			_logger.debug(LoggerType.CSS, "CSS#getServerEvents finish json parsoning.");

		} catch (JSONException jsone) {
			_logger.debug(LoggerType.CSS, Util.getStackTrace(jsone));
		}

		return result;
	}

	public void initEventCount() {
		eventCounts.set(0);
	}

	public int getEventCount() {
		return eventCounts.get();
	}
}