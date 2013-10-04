package org.itri.ccma.safebox.s3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.itri.ccma.safebox.CSSHandler;
import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.db.FileObject;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.db.ServerObjectHandler;
import org.itri.ccma.safebox.util.Digest;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.SafeBoxUtils;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.Util;
import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.StorageObjectsChunk;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.io.BytesProgressWatcher;
import org.jets3t.service.io.InterruptableInputStream;
import org.jets3t.service.io.ProgressMonitoredInputStream;
import org.jets3t.service.io.TempFile;
import org.jets3t.service.io.UnrecoverableIOException;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.model.StorageOwner;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ServiceUtils;

public class S3Assist {
	private static S3Assist _instance = new S3Assist();

	private Config _config = Config.getInstance();

	boolean forceCancel = false;
	String publicBucket, rootPath, tmpPath;
	Digest digest;

	public RestS3Service s3Service;

	// public AWSCredentials awsCredentials;

	private static final int MAX_OBJS_CHUNK = 16;
	private static final String METADATA_CLINET_TYPE = "x-amz-meta-clientType";
	private static final String METADATA_REQUID_TYPE = "x-amz-meta-reqId";
	// private static final String METADATA_RETRYS_TYPE = "x-amz-meta-retry";
	private static final String METADATA_INSTID_TYPE = "x-amz-meta-instId";
	private static final String METADATA_TOO_MANY_REQ = "x-amz-meta-toomanyrequest";
	private static final String METADATA_MTIME = "mtime";
	private static final char SLASH_1 = '\\';
	private static final char SLASH_2 = '/';
	private static final String NO_DATA_FOUND = "No data found!";
	// private static final String DEFAULT_BUCKET = "Admin";
	private static final String PUBLIC_BUCKET = "Public";
	private static final String SHA_256 = "SHA-256";
	// private static final int RETRY_TTL = 3;
	// private static final String RETRY_STR = "retry";
	private static final String XML_MSG = "TooManyRequests";
	private static final String XML_MSG2 = "ServerSuccess";

	private List<InterruptableInputStream> s3FileStreams = Collections.synchronizedList(new ArrayList<InterruptableInputStream>());
	// private List<FileLock> s3FileLocks = Collections.synchronizedList(new
	// ArrayList<FileLock>());
	private List<FileChannel> s3FileChannels = Collections.synchronizedList(new ArrayList<FileChannel>());
	private LoggerHandler _logger = LoggerHandler.getInstance();

	private AtomicInteger eventCounts = new AtomicInteger(0);

	private S3Assist() {
		// _config.getValue(KEYS.DefaultBucket) = DEFAULT_BUCKET;
		publicBucket = PUBLIC_BUCKET;
		rootPath = "";
		tmpPath = Config.tmpDir;
		digest = new Digest(SHA_256);

		String slashStr = "";
		slashStr += SLASH_1;

		// _config.getValue(KEYS.DefaultBucket) =
		// _config.getValue(KEYS._config.getValue(KEYS.DefaultBucket));
		rootPath = _config.getValue(KEYS.SafeBoxLocation);
		// slash = Config.slash;

		if (new File(rootPath).exists() == false) {
			rootPath = System.getProperty("user.home") + File.separator + "Safebox";
		}

		if (StringUtils.isEmpty(tmpPath)) {
			tmpPath = rootPath;
		}

		if (!tmpPath.endsWith(slashStr)) {
			tmpPath += File.separator;
		}

		checkingConnStatus();
	}

	public static S3Assist getInstance() {
		return _instance;
	}

	public void closeFileChannels() {
		_logger.debug(LoggerType.CSS, "S3Assist s3FileChannels close start...");
		synchronized (s3FileChannels) {

			for (FileChannel channel : s3FileChannels) {

				if (channel == null) {
					continue;
				}

				try {
					channel.close();
					channel = null;
				} catch (IOException ioe) {
					_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
				}
			}

			s3FileChannels.clear();
		}
		_logger.debug(LoggerType.CSS, "S3Assist s3FileChannels close end");
	}

	/*
	 * public void releaseFileLocks() {
	 * 
	 * _logger.debug(LoggerType.CSS, "S3Assist s3FileLocks release start...");
	 * _logger.debug(LoggerType.CSS, "s3FileLocks size:" + s3FileLocks.size());
	 * synchronized (s3FileLocks) {
	 * 
	 * for (FileLock lock : s3FileLocks) {
	 * 
	 * if (lock == null) { continue; }
	 * 
	 * try { lock.release(); } catch (IOException e) {
	 * _logger.debug(LoggerType.CSS, Util.getStackTrace(e)); } }
	 * 
	 * s3FileLocks.clear(); } _logger.debug(LoggerType.CSS,
	 * "S3Assist s3FileLocks release end"); }
	 */

	public void cancelWorks() {
		_logger.debug(LoggerType.CSS, "S3Assist Cancel works start...");
		synchronized (s3FileStreams) {

			_logger.debug(LoggerType.CSS, "S3Assist s3FileStreams interrupt start...");

			for (InterruptableInputStream inputstream : s3FileStreams) {
				if (inputstream == null) {
					continue;
				}

				inputstream.interrupt();
			}

			s3FileStreams.clear();

			_logger.debug(LoggerType.CSS, "S3Assist s3FileStreams interrupt end");
		}

		_logger.debug(LoggerType.CSS, "S3Assist Cancel works end.");
	}

	public void setForceCancel(boolean flag) {
		forceCancel = flag;
	}

	public String getRootPath() {
		return rootPath;
	}

	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}

	public boolean checkingConnStatus() {
		_logger.debug(LoggerType.CSS, "S3Assist checking Connection Status start...");
		try {
			_initS3Service();
		} catch (ServiceException se) {
			_config.connStatus = false;
			_config.connText = SafeBoxUtils.parseXMLError(se);
			_logger.debug(LoggerType.CSS, _config.connText);
			return false;
		}

		if (null == s3Service) {
			_config.connStatus = false;
			_config.connText = "S3Server is null";
			_logger.debug(LoggerType.CSS, _config.connText);
			return false;
		}

		Jets3tProperties property = s3Service.getJetS3tProperties();
		if (!checkingConnParameters() || null == property) {
			_config.connStatus = false;
			_config.connText = "Not logged in";
			_logger.debug(LoggerType.CSS, _config.connText);
			return false;
		}

		if (StringUtils.isEmpty(ServerConnectionUtil.getSBXServiceURL(_config.getValue(KEYS.HostIP)))) {
			_config.connStatus = false;
			_config.connText = "Safebox Service URL is empty or null";
			_logger.debug(LoggerType.CSS, _config.connText);
			return false;
		}

		if (StringUtils.isEmpty(ServerConnectionUtil.getCSSServiceURL(property.getStringProperty("s3service.s3-endpoint", _config.getValue(KEYS.HostIP))))) {
			_config.connStatus = false;
			_config.connText = "CSS Service URL is empty or null";
			_logger.debug(LoggerType.CSS, _config.connText);
			return false;
		}

		try {
			_logger.debug(LoggerType.CSS, "S3Assist checking Connection Status - List All Buckets start...");
			s3Service.listAllBuckets();

			/*
			 * if (null ==
			 * s3Service.getBucket(_config.getValue(KEYS.DefaultBucket))) {
			 * _logger.debug(LoggerType.CSS,
			 * "S3Assist checking Connection Status - Create Buckets start...");
			 * s3Service.createBucket(_config.getValue(KEYS.DefaultBucket)); }
			 */

			_config.connStatus = true;
			_config.connText = "Connection success";
		} catch (ServiceException se) {
			if (XML_MSG.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
				_config.setValue(KEYS.EventSyncWorkerCount, SafeBoxUtils.getXMLErrorValueByAttr(se, "Resource"));
			}

			_config.connStatus = false;
			_config.connText = SafeBoxUtils.parseXMLError(se);
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));

		} catch (Throwable throwable) {
			_config.connStatus = false;
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		_logger.debug(LoggerType.CSS, "Connection Status: " + _config.connStatus + " Message: " + _config.connText);
		_logger.debug(LoggerType.CSS, "S3Assist checking Connection Status end.");

		return _config.connStatus;
	}

	private void _initS3Service() throws S3ServiceException {
		if (s3Service != null) {
			_logger.debug(LoggerType.CSS, "S3Service was ready for service");
			return;
		}

		_logger.debug(LoggerType.CSS, "S3Service initialization start...");

		if (!checkingConnParameters()) {
			return;
		}

		AWSCredentials awsCredentials = new AWSCredentials(_config.getValue(KEYS.AccessKeyID), _config.getValue(KEYS.SecretAccessKey));
		// AWSCredentials awsCredentials = SafeBoxUtils.loadAWSCredentials();

		Jets3tProperties jets3tProperties = Jets3tProperties.getInstance("jets3t.properties");
		jets3tProperties.setProperty("s3service.s3-endpoint", _config.getValue(KEYS.HostIP));

		String serviceURL = ServerConnectionUtil.getCSSServiceURL(_config.getValue(KEYS.HostIP));
		if (StringUtils.isNotEmpty(serviceURL)) {
			String servicePort = serviceURL.substring(serviceURL.lastIndexOf(':') + 1);
			jets3tProperties.setProperty("s3service.s3-endpoint-http-port", servicePort);
			jets3tProperties.setProperty("s3service.s3-endpoint-https-port", servicePort);
		}

		s3Service = new RestS3Service(awsCredentials, "", null, jets3tProperties) {
			@Override
			protected void performRequest(HttpMethodBase httpMethod, int[] args) throws ServiceException {

				if (-1 != CSSHandler.getInstance().getCurrentSyncId()) {
					httpMethod.setRequestHeader(METADATA_CLINET_TYPE, String.valueOf(CSSHandler.getInstance().getCurrentSyncId()));
				}
				httpMethod.setRequestHeader(METADATA_INSTID_TYPE, _config.getValue(KEYS.InstanceKey));
				UUID requestId = UUID.randomUUID();
				httpMethod.setRequestHeader(METADATA_REQUID_TYPE, String.valueOf(requestId));

				if ("HEAD".equalsIgnoreCase(httpMethod.getName()) && "/services/Walrus/".equals(httpMethod.getPath())) {
					_logger.debug(LoggerType.CSS, "Get Object Detail Request ID: " + requestId);
				} else if ("GET".equalsIgnoreCase(httpMethod.getName()) && "/services/Walrus/".equals(httpMethod.getPath())) {
					_logger.debug(LoggerType.CSS, "Get Object Request ID: " + requestId);
				} else if ("PUT".equalsIgnoreCase(httpMethod.getName()) && "/services/Walrus/".equals(httpMethod.getPath())) {
					_logger.debug(LoggerType.CSS, "Put Object Request ID: " + requestId);
				} else if ("/services/Walrus/".equals(httpMethod.getPath())) {
					_logger.debug(LoggerType.CSS, "List all my buckets Request ID: " + requestId);
				}

				super.performRequest(httpMethod, args);
			}
		};

		s3Service.getHttpClient().getParams().setBooleanParameter("http.protocol.expect-continue", false);

		_logger.debug(LoggerType.CSS, "S3Service has been initialization");
	}

	private boolean checkingConnParameters() {
		if (null == _config) {
			return false;
		}

		if (StringUtils.isEmpty(_config.getValue(KEYS.HostIP))) {
			_logger.error(LoggerType.CSS, "WARING! Host IP is empty or null");
			return false;
		} else if (StringUtils.isEmpty(_config.getValue(KEYS.AccessKeyID))) {
			_logger.error(LoggerType.CSS, "WARING! Access Key ID is empty or null");
			return false;
		} else if (StringUtils.isEmpty(_config.getValue(KEYS.SecretAccessKey))) {
			_logger.error(LoggerType.CSS, "WARING! Secret Access Key is empty or null");
			return false;
		} else if (StringUtils.isEmpty(_config.getValue(KEYS.DefaultBucket))) {
			_logger.error(LoggerType.CSS, "WARING! Default Bucket is empty or null");
			return false;
		}

		return true;
	}

	public boolean getConnStatus() {
		if (null == s3Service) {
			_logger.error(LoggerType.CSS, "S3Assist getConnStatus: S3Service is null");
			_config.connStatus = false;
			return false;
		} else if (_config == null) {
			_logger.error(LoggerType.CSS, "S3Assist getConnStatus: config is null");
			return false;
		} else if (_config.connStatus == false) {
			_logger.error(LoggerType.CSS, "S3Assist getConnStatus: config.connStatus is false");
			return false;
		}

		return true;
	}

	public boolean login() {
		if (!checkingConnParameters())
			return false;
		// checking Connection Status again
		checkingConnStatus();

		return getConnStatus();
	}

	public List<S3Object> listObjects(String dir, boolean needSubDir, S3CallBack s3CallBack) throws ObjectException {
		List<S3Object> s3Objects = new ArrayList<S3Object>(10000);

		listObjects(dir, needSubDir, s3CallBack, s3Objects);

		return s3Objects;
	}

	public boolean listObjects(String dir, boolean needSubDir, S3CallBack s3CallBack, List<S3Object> s3Objects) throws ObjectException {
		StorageObjectsChunk chunk[] = null;
		StorageObject[] chunkObjects = null;
		S3Object obj;
		String commonPrefix[];
		String lastKey = "";
		String key;
		int chunkIndex, chunkCount, validCount = 0;
		// int retry = 0;
		// List<S3Object> list, subList;
		boolean inProgress = true;

		if (getConnStatus() == false) {
			return false;
		}

		if (forceCancel) {
			return false;
		}

		dir = dir.replace(SLASH_1, SLASH_2);
		chunk = new StorageObjectsChunk[MAX_OBJS_CHUNK];
		chunkCount = 0;
		chunkIndex = 0;
		// System.out.print("listing");
		do {
			try {
				if (forceCancel) {
					break;
				}
				chunk[chunkIndex] = s3Service.listObjectsChunked(_config.getValue(KEYS.DefaultBucket), Util.urlEncode(dir), "/", 2000, lastKey, false);
				chunkObjects = chunk[chunkIndex].getObjects();
				lastKey = chunk[chunkIndex].getPriorLastKey();
				chunkIndex++;

				// filter out .eo file
				// System.out.print(".");
				if (chunkObjects != null) {
					for (int i = 0; i < chunkObjects.length; i++) {
						if (forceCancel) {
							break;
						}

						// System.out.print((i % 128) == 0 ? "\n" : ".");
						key = chunkObjects[i].getKey();
						if (!key.endsWith(".eo")) {
							validCount += 1;
						}
					}
				}
			} catch (ServiceException se) {
				if (se.getResponseCode() != 404) {
					_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
				}
				validCount = 0;
				chunkIndex = 0;
				lastKey = "";
				// retry++;
			} catch (Throwable throwable) {
				_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
				validCount = 0;
				chunkIndex = 0;
				lastKey = "";
				// retry++;
			}
			// } while (lastKey != null && chunkIndex < MAX_OBJS_CHUNK && retry
			// < 3);
		} while (lastKey != null && chunkIndex < MAX_OBJS_CHUNK);

		// if (retry >= 3) {
		// _updateConnStatus(false, "");
		// return false;
		// }

		if (forceCancel) {
			return false;
		}

		// Debug("List Folder: " + dir + " Valid Count: " + validCount);
		_logger.debug(LoggerType.CSS, "List Folder: " + dir + " Valid Count: " + validCount);

		// list = new ArrayList<S3Object>(validCount);
		chunkCount = chunkIndex;
		validCount = 0;
		inProgress = true;

		for (chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
			if (forceCancel || !getConnStatus() || !inProgress) {
				break;
			}

			chunkObjects = chunk[chunkIndex].getObjects();
			for (int i = 0; i < chunkObjects.length; i++) {
				if (forceCancel || !getConnStatus() || !inProgress) {
					break;
				}
				key = Util.urlDecode(chunkObjects[i].getKey());
				if (key.endsWith(".eo")) {
					continue;
				}
				obj = (S3Object) chunkObjects[i];
				obj.setKey(key);
				s3Objects.add(obj);

				if (s3CallBack != null) {
					inProgress = s3CallBack.Func(obj);
				}

			}

			commonPrefix = chunk[chunkIndex].getCommonPrefixes();

			for (int i = 0; i < commonPrefix.length; i++) {
				if (forceCancel || !getConnStatus() || !inProgress) {
					break;
				}
				if (needSubDir == false) {
					obj = new S3Object(dir + commonPrefix[i]);
					s3Objects.add(obj);
				} else {
					if (false == listObjects(Util.urlDecode(dir + commonPrefix[i]), true, s3CallBack, s3Objects)) {
						return false;
					}
				}
			}
		}

		chunk = null;
		commonPrefix = null;

		if (forceCancel || getConnStatus() == false || inProgress == false)
			return false;
		else
			return true;
	}

	public boolean uploadingObject(File file, String objectKey) {

		if (StringUtils.isEmpty(objectKey) || getConnStatus() == false) {
			return false;
		}

		boolean isSuccess = false;
		S3Object s3Object = null;

		objectKey = objectKey.replace(SLASH_1, SLASH_2);

		s3Object = new S3Object();
		s3Object.setKey(Util.urlEncode(objectKey));
		s3Object.addMetadata(METADATA_CLINET_TYPE, String.valueOf(CSSHandler.getInstance().getCurrentSyncId()));

		// Throttling
		/*
		 * UUID requestId = UUID.randomUUID();
		 * s3Object.addMetadata(METADATA_REQUID_TYPE,
		 * String.valueOf(requestId));
		 */

		// Do check file is on used by User
		if (FileUtil.isFileLocked(file)) {
			// Error("Uploading object#current Sync ID: [" +
			// CSSHandler.getInstance().getCurrentSyncId() +
			// "] is can not lock. objectKey:" + objectKey);
			_logger.error(LoggerType.CSS, "Uploading object#current Sync ID: [" + CSSHandler.getInstance().getCurrentSyncId() + "] is can not lock. objectKey:" + objectKey);
			return isSuccess;
		}

		// Require file lock
		FileLock lock = null;
		FileChannel channel = null;
		InputStream inputStream = null;
		try {
			if (FileUtil.isFileReadOnly(file)) {
				channel = new RandomAccessFile(file, "r").getChannel();
				lock = channel.tryLock(0L, Long.MAX_VALUE, true);
			} else {
				channel = new RandomAccessFile(file, "rw").getChannel();
				lock = channel.tryLock(0L, Long.MAX_VALUE, false);
			}

			inputStream = Channels.newInputStream(channel.position(0));

		} catch (FileNotFoundException fnfe) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(fnfe));
		} catch (IOException ioe) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
		}

		if (lock == null) {
			_logger.error(LoggerType.CSS, "uploading get Lock failed:" + file.getAbsolutePath());
			return isSuccess;
		} else {
			synchronized (s3FileChannels) {
				s3FileChannels.add(channel);
			}
			/*
			 * synchronized (s3FileLocks) { s3FileLocks.add(lock); }
			 */
		}

		// Obtain the content length of the Input stream for S3 header
		/*
		 * if (in != null) { try { IOUtils.toByteArray(in); } catch (IOException
		 * e) { Debug("Failed while reading bytes from " + e.getMessage()); } }
		 */

		s3Object.setContentType(Mimetypes.getInstance().getMimetype(file));
		s3Object.setContentLength(file.length());
		s3Object.setDataInputStream(inputStream);
		try {
			s3Object.setMd5Hash(ServiceUtils.computeMD5Hash(new FileInputStream(file)));
		} catch (NoSuchAlgorithmException e1) {
		} catch (FileNotFoundException e1) {
		} catch (IOException e1) {
		}
		s3Object.addMetadata("x-amz-meta-" + METADATA_MTIME, String.valueOf(file.lastModified()));

		isSuccess = uploadingObject(_config.getValue(KEYS.DefaultBucket), s3Object);

		try {
			lock.release();
			channel.close();

		} catch (IOException ioe) {
		}

		synchronized (s3FileChannels) {
			if (channel != null) {
				s3FileChannels.remove(channel);
			}
		}

		lock = null;
		channel = null;

		try {
			s3Object.closeDataInputStream();
		} catch (IOException e) {
			// Error(e);
		}

		if (isSuccess) {
			eventCounts.getAndIncrement();
			_logger.debug(LoggerType.CSS,"Uploading object#current Sync ID: [" + CSSHandler.getInstance().getCurrentSyncId() + "] :" + objectKey + ", eventcount:" + eventCounts.get());
		}

		return isSuccess;
	}

	public boolean uploadingObject(String remoteDir, String localPath) {

		if (StringUtils.isEmpty(localPath) || getConnStatus() == false) {
			return false;
		}

		File file = new File(localPath);

		if (file.exists() == false || !file.canRead() || !Util.isStartFromRoot(localPath)) {
			return false;
		}

		if (file.isDirectory()) {
			return uploadingDirObject(localPath);
		}

		String objectKey = Util.translateObjectKey(localPath);

		if (StringUtils.isNotEmpty(remoteDir)) {
			if (!remoteDir.endsWith("/")) {
				remoteDir += "/";
			}
			objectKey = remoteDir + objectKey;
		}

		return uploadingObject(file, objectKey);
	}

	public boolean downloadingObject(String localPath, String objectKey) throws ObjectException {

		if (getConnStatus() == false || StringUtils.isEmpty(objectKey)) {
			return false;
		}

		String eventObjectKey = objectKey;
		boolean isSuccess = false;
		S3Object s3Object = null;

		if (StringUtils.isEmpty(localPath)) {
			localPath = Util.translateLocalPath(objectKey);
		}

		localPath = localPath.replace(SLASH_2, SLASH_1);
		objectKey = objectKey.replace(SLASH_1, SLASH_2);

		try {
			s3Object = (S3Object) s3Service.getObjectDetails(_config.getValue(KEYS.DefaultBucket), Util.urlEncode(objectKey));
		} catch (ServiceException se) {
			if (se.getResponseCode() != 404) {
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
			}
		} finally {
			if (null != s3Object) {
				try {
					s3Object.closeDataInputStream();
				} catch (IOException e) {
				}
			}
		}

		if (null == s3Object) {
			_logger.error(LoggerType.CSS, "s3Object not found for " + objectKey);
			return false;
		}

		if (isDirObject(s3Object)) {
			boolean result = FileUtil.makeDir(localPath);
			if (result) {
				eventCounts.getAndIncrement();
				_logger.debug(LoggerType.CSS, "Downloading object request done#Current Sync ID: " + CSSHandler.getInstance().getCurrentSyncId() + " | Object Key:" + objectKey
				        + ", eventcount:" + eventCounts.get());
			}

			return result;
		}

		String filename = eventObjectKey.substring(1, eventObjectKey.length());
		File tmpFile = new File(tmpPath + filename.hashCode());

		String subfileName = null;
		if (filename.lastIndexOf("/") != -1)
			subfileName = eventObjectKey.substring(filename.lastIndexOf("/") + 2, filename.length() + 1);
		else
			subfileName = filename;

		File tmpCopyStatusFile = new File(tmpPath + subfileName + "_status.txt");

		if (tmpFile.exists()) {
			try {
				FileUtils.forceDelete(tmpFile);
			} catch (IOException ioe) {
				_logger.error(LoggerType.CSS, "Deleted File:" + tmpFile.getAbsolutePath() + " error:" + Util.getStackTrace(ioe));
			}
		}

		_logger.debug(LoggerType.CSS, "Downloading object request send#Current Sync ID: " + CSSHandler.getInstance().getCurrentSyncId() + " | Object Key:" + objectKey);

		try {
			isSuccess = downloadingObject(_config.getValue(KEYS.DefaultBucket), s3Object.getKey(), tmpFile);

		} catch (ServiceException se) {
			if (XML_MSG.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
				_config.setValue(KEYS.EventSyncWorkerCount, SafeBoxUtils.getXMLErrorValueByAttr(se, "Resource"));
			}
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
		} finally {
			if (null != s3Object) {
				try {
					s3Object.closeDataInputStream();
				} catch (IOException ioe) {
					isSuccess = false;
					_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
					// Error(e);
				}
			}
		}

		/*
		 * if (null != s3Object) { try { s3Object.closeDataInputStream(); }
		 * catch (IOException e) { isSuccess = false; Error(e); } }
		 */
		// 當檔案下載成功之後, 因為時間差的緣故, 所以 file 在從 temp folder copy 到 sync folder 時,
		// 還必須作以下的檢查:
		if (isSuccess) {
			// Check 1. 目的檔案是否存在
			File localFile = new File(localPath);

			if (localFile.exists()) {
				// Check 2. 確認目的檔案是否要進行覆蓋, 方法有二:
				// 首先檢查 DB Snapshot(上一次 Sync 資訊), 如果有找到該物件,
				// 而目的檔案的 MD5 又跟上一次 Sync 相同,
				// 表示目的檔案在下載這段時間內都沒有被使用者變更過, 所以直接覆蓋,
				// 否則就要將目的檔案進行備份.
				FileObject recordedObject = ServerObjectHandler.getInstance().get(eventObjectKey);
				String localFileMd5 = FileUtil.getMD5(localFile);
				boolean isOverwrite = true;

				if (!tmpCopyStatusFile.exists()) {
					if (recordedObject == null) {
						// 確認方法二, 如果 DB Snapshot 找不到, 表示上一次 Sync Server 沒有, 是
						// Local
						// create,
						// 就比對目的檔案's MD5 是不是跟下載檔案相同,
						// 若相同表示這次 server event 可以忽略.
						String tmpFileMd5 = FileUtil.getMD5(tmpFile);

						if (!localFileMd5.equals(tmpFileMd5))
							FileUtil.fileBackup(eventObjectKey);
						else
							isOverwrite = false;
					} else {
						if (!localFileMd5.equals(recordedObject.MD5)) {
							FileUtil.fileBackup(eventObjectKey);
						}
					}

				}

				try {
					if (isOverwrite) {
						tmpCopyStatusFile.createNewFile();
						FileUtils.copyFile(tmpFile, localFile);
						FileUtils.forceDelete(tmpCopyStatusFile);
					}

				} catch (IOException ioe) {
					isSuccess = false;
					_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
				}
			} else {
				try {
					tmpCopyStatusFile.createNewFile();
					FileUtils.moveFile(tmpFile, localFile);
					FileUtils.forceDelete(tmpCopyStatusFile);
				} catch (IOException ioe) {
					isSuccess = false;
					_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
				}
			}
		}
		/* 邏輯可能不合理先取消 */
		// else {
		// _updateConnStatus(false, "");
		// }

		if (tmpFile.exists()) {
			try {
				FileUtils.forceDelete(tmpFile);
			} catch (IOException ioe) {
				_logger.error(LoggerType.CSS, "Deleted File:" + tmpFile.getAbsolutePath() + " error:" + Util.getStackTrace(ioe));
			}
		}

		if (isSuccess) {
			eventCounts.getAndIncrement();
			_logger.debug(LoggerType.CSS, "Downloading object request done#Current Sync ID: " + CSSHandler.getInstance().getCurrentSyncId() + " | Object Key:" + objectKey
			        + ", eventcount:" + eventCounts.get());
		}

		return isSuccess;
	}

	public boolean downloadingObjectForFullSync(String objectKey) {

		if (getConnStatus() == false) {
			return false;
		}

		boolean isSuccess = false;
		String localPath = Util.translateLocalPath(objectKey);

		String filename = objectKey.substring(1, objectKey.length());
		File tmpFile = new File(tmpPath + filename.hashCode());

		if (tmpFile.exists()) {
			try {
				FileUtils.forceDelete(tmpFile);
			} catch (IOException ioe) {
				_logger.error(LoggerType.CSS, "Deleted File:" + tmpFile.getAbsolutePath() + " error:" + Util.getStackTrace(ioe));
			}
		}

		_logger.debug(LoggerType.CSS, "Downloading object request send#Current Sync ID: " + CSSHandler.getInstance().getCurrentSyncId() + " | Object Key:" + objectKey);

		try {
			isSuccess = downloadingObject(_config.getValue(KEYS.DefaultBucket), Util.urlEncode(objectKey), tmpFile);
		} catch (ServiceException se) {
			if (XML_MSG.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
				_config.setValue(KEYS.EventSyncWorkerCount, SafeBoxUtils.getXMLErrorValueByAttr(se, "Resource"));
			}
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
		}

		if (isSuccess) {
			File localFile = new File(localPath);

			// Require file lock
//			FileChannel channel = null;
			try {
				FileUtils.copyFile(tmpFile, localFile);
				// Try to lock file
				/*try {
					FileLock lock = null;

					if (FileUtil.isFileReadOnly(localFile)) {
						channel = new RandomAccessFile(localFile, "r").getChannel();
						lock = channel.tryLock(0L, Long.MAX_VALUE, true);
					} else {
						channel = new RandomAccessFile(localFile, "rw").getChannel();
						// lock = channel.tryLock(0L, Long.MAX_VALUE, false);
						lock = channel.tryLock();
					}

				} catch (FileNotFoundException fnfe) {
					_logger.debug(LoggerType.CSS, Util.getStackTrace(fnfe));
				} catch (IOException ioe) {
					_logger.debug(LoggerType.CSS, Util.getStackTrace(ioe));
				}*/

				/*if (channel != null) {
					synchronized (s3FileChannels) {
						s3FileChannels.add(channel);
					}
				}*/

			} catch (IOException ioe) {
				isSuccess = false;
				_logger.error(LoggerType.CSS, "Copy File error:" + Util.getStackTrace(ioe));
			}
		} else {
			_logger.error(LoggerType.CSS, "Download File error:" + objectKey);
		}

		if (tmpFile.exists()) {
			try {
				FileUtils.forceDelete(tmpFile);
			} catch (IOException ioe) {
				_logger.error(LoggerType.CSS, "Deleted File:" + tmpFile.getAbsolutePath() + " error:" + Util.getStackTrace(ioe));
			}
		}

		return isSuccess;
	}

	public boolean downloadingObject(String objectKey) throws ObjectException {

		if (getConnStatus() == false || StringUtils.isEmpty(objectKey)) {
			return false;
		}

		String localPath;

		objectKey = objectKey.replace(SLASH_1, SLASH_2);

		if (objectKey.startsWith("/")) {
			localPath = Util.translateLocalPath(objectKey.substring(1));
		} else {
			localPath = Util.translateLocalPath(objectKey);
		}

		return downloadingObject(localPath, objectKey);
	}

	public boolean deletingObject(String objectKey) {

		if (getConnStatus() == false || StringUtils.isEmpty(objectKey)) {
			return false;
		}

		boolean succ = false;

		try {
			objectKey = Util.urlEncode(objectKey.replace(SLASH_1, SLASH_2));
			s3Service.deleteObject(_config.getValue(KEYS.DefaultBucket), objectKey);
			succ = true;

		} catch (ServiceException se) {
			// _updateConnStatus(false, SafeBoxUtils.parseXMLError(se));
			if (404 == se.getResponseCode()) {// 404=Not Found
				succ = true;
			} else {
				if (XML_MSG.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
					_config.setValue(KEYS.EventSyncWorkerCount, SafeBoxUtils.getXMLErrorValueByAttr(se, "Resource"));
				}
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
			}
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		if (succ) {
			eventCounts.getAndIncrement();
			_logger.debug(LoggerType.CSS, "Delete object key :" + objectKey + " success, eventCount:" + eventCounts.get());
		} else {
			_logger.debug(LoggerType.CSS, "Delete object key :" + objectKey + " failed");
		}

		return succ;
	}

	public boolean uploadingDirObject(String localPath) {

		if (getConnStatus() == false) {
			return false;
		}

		File file = new File(localPath);

		if (!file.exists() || !file.isDirectory()) {
			return false;
		}

		String objectKey = Util.translateObjectKey(localPath);

		if (StringUtils.isEmpty(objectKey)) {
			return false;
		}

		boolean succ = false;

		_logger.debug(LoggerType.CSS, "uploadingDirObject() objectKey: " + objectKey);

		try {
			S3Object s3Object = new S3Object("");
			s3Object.addMetadata(METADATA_CLINET_TYPE, String.valueOf(CSSHandler.getInstance().getCurrentSyncId()));
			s3Object.addMetadata("x-amz-meta-" + METADATA_MTIME, String.valueOf(file.lastModified()));
			s3Object.setContentLength(0);
			s3Object.setContentType("application/x-directory");
			s3Object.setKey(objectKey);

			s3Object = s3Service.putObject(_config.getValue(KEYS.DefaultBucket), s3Object);
			succ = true;
		} catch (ServiceException ex) {
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(ex));
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		if (succ) {
			eventCounts.getAndIncrement();
			_logger.debug(LoggerType.CSS, "uploadingDirObject() objectKey: " + objectKey + ", eventcount:" + eventCounts.get());
		}

		return succ;
	}

	public boolean deleteAllObjects() throws ObjectException, SQLException {

		S3CallBack s3cb = new S3CallBack() {
			public boolean Func(S3Object obj) {
				deletingObject(obj.getKey());
				return true;
			}
		};
		listObjects("", true, s3cb);

		return true;
	}

	public boolean isObjectExist(String objectKey) {

		if (getConnStatus() == false) {
			return false;
		}

		boolean isExist = false;

		try {
			isExist = s3Service.isObjectInBucket(_config.getValue(KEYS.DefaultBucket), objectKey);
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		return isExist;
	}

	public boolean isDirObject(S3Object s3Object) {
		boolean isDir = false;

		if (s3Object.isDirectoryPlaceholder() || "application/x-directory".equals(s3Object.getContentType())) {
			isDir = true;
		}

		return isDir;
	}

	public StorageObject downloadingObjectDetails(String objectKey) throws SafeboxException {

		if (getConnStatus() == false) {
			return null;
		}

		StorageObject objectDetails = null;

		try {
			objectDetails = s3Service.getObjectDetails(_config.getValue(KEYS.DefaultBucket), objectKey);
			if (null != objectDetails) {
				try {
					objectDetails.closeDataInputStream();
				} catch (IOException e) {
				}
			}

		} catch (ServiceException se) {
			// 404 no data found
			if (se.getResponseCode() != 404) {

				_logger.debug(LoggerType.CSS, "downloadingObjectDetails error response:" + se.getResponseHeaders());

				if (se.getResponseHeaders().get(METADATA_TOO_MANY_REQ) != null) {
					_config.setValue(KEYS.EventSyncWorkerCount, se.getResponseHeaders().get(METADATA_TOO_MANY_REQ));
				}
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));

				throw new SafeboxException(SafeBoxUtils.parseXMLError(se));
			}

		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
		}

		return objectDetails;
	}

	public long getObjectTime(S3Object s3Object) throws SafeboxException {

		long lastModified = 0;
		String mtime = "";
		StorageObject objectDetails;
		Map<String, Object> metadata;

		if (s3Object == null) {
			return 0;
		}

		/*
		 * actually "x-amz-meta-mtime" in http head
		 */
		mtime = (String) s3Object.getMetadata(METADATA_MTIME);

		if (StringUtils.isEmpty(mtime)) {
			objectDetails = downloadingObjectDetails(s3Object.getKey());
			if (objectDetails != null) {
				metadata = objectDetails.getMetadataMap();
				s3Object.addAllMetadata(metadata);
				mtime = (String) s3Object.getMetadata(METADATA_MTIME);
			}
		} else {
			try {
				lastModified = Long.parseLong(mtime);
			} catch (Exception e) {
				lastModified = (new Date()).getTime();
			}
		}

		return lastModified;
	}

	public void downloadingObjectsOfDetails(List<S3Object> list) {
		S3Object obj;
		StorageObject objectDetails;
		Map<String, Object> metadata;

		for (int i = 0; i < list.size(); i++) {
			if (forceCancel)
				break;
			try {
				obj = list.get(i);
				objectDetails = s3Service.getObjectDetails(_config.getValue(KEYS.DefaultBucket), obj.getKey());
				if (objectDetails != null) {
					objectDetails.closeDataInputStream();
					metadata = objectDetails.getMetadataMap();
					obj.addAllMetadata(metadata);
				}
			} catch (ServiceException se) {
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
			} catch (Throwable throwable) {
				_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
			}
		}
	}

	public String getDisplayObjectDetails(String objectKey) throws SafeboxException {

		if (getConnStatus() == false || StringUtils.isEmpty(objectKey)) {
			return NO_DATA_FOUND;
		}

		StringBuilder details = new StringBuilder();
		StorageOwner owner;
		S3Bucket s3Bucket;

		StorageObject objectDetails = downloadingObjectDetails(objectKey);

		if (objectDetails == null) {
			return NO_DATA_FOUND;
		}

		owner = objectDetails.getOwner();
		if (owner == null) {
			try {
				s3Bucket = s3Service.getBucket(_config.getValue(KEYS.DefaultBucket));
				if (s3Bucket != null) {
					owner = s3Bucket.getOwner();
				}

			} catch (ServiceException se) {
				_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
			} catch (Throwable throwable) {
				_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
			}
		}

		details.append("Service Point : ").append(_config.getValue(KEYS.HostIP)).append("\n");
		details.append("Bucket : ").append(objectDetails.getBucketName()).append("\n");
		details.append("Key : ").append(objectDetails.getKey()).append("\n");
		details.append("Owner : ").append((owner != null ? owner.getDisplayName() : "")).append("\n");
		details.append("ETag : ").append(objectDetails.getETag()).append("\n");
		details.append("Encrypted : ").append(isObjectExist(objectKey + ".eo") ? "Yes" : "No");

		return details.toString();
	}

	public void printObject(BufferedWriter fout, S3Object s3Object, Boolean printDetail) throws SafeboxException {
		long time = getObjectTime(s3Object);
		long len = s3Object.getContentLength();
		int i;
		String ObjectKey = s3Object.getKey();
		String info;
		Map<String, Object> metadata;
		Boolean isDir = isDirObject(s3Object);

		Date d = new Date(time);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		info = isDir ? "d" : "-";
		info += " " + sdf.format(d);
		info += "\t" + ObjectKey;
		if (!isDir) {
			if (ObjectKey.length() < 23) {
				for (i = 0; i < (24 - ObjectKey.length()); i++)
					info += " ";
			} else {
				info += "\t";
			}
			info += len + " bytes";
		}

		System.out.println(info);
		if (fout != null)
			try {
				fout.write(info + "\n");
			} catch (IOException e) {
			}

		if (printDetail) {
			metadata = s3Object.getMetadataMap();
			for (Object key : metadata.keySet()) {
				String k = (String) key;
				if (k.startsWith("ETag") || k.startsWith("md5-") || k.startsWith("Content-Length") || k.startsWith("Content-Type"))
					continue;
				k = "\t" + key + ": " + metadata.get(key);
				System.out.println(k);
				if (fout != null)
					try {
						fout.write(k + "\n");
					} catch (IOException e) {
					}
			}
		}
	}

	public void printObjects(S3Object[] s3Objects) throws SafeboxException {
		System.out.println("\n===============================================================");
		for (int i = 0; i < s3Objects.length; i++)
			printObject(null, s3Objects[i], true);
	}

	public boolean downloadingObject(String bucketName, String objectKey, File tmpFile) throws ServiceException {
		boolean isSuccess = true;
		BufferedInputStream bufferedInputStream = null;
		BufferedOutputStream bufferedOutputStream = null;
		InterruptableInputStream interruptableInputStream = null;
		StorageObject object = null;
		MessageDigest messageDigest = null;

		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException nsae) {
			_logger.error(LoggerType.CSS, "Unable to calculate MD5 hash of data received as algorithm is not available");
			return false;
		}

		try {

			// s3service could be null via syncthread.cancelWork
			if (s3Service == null) {
				_logger.error(LoggerType.CSS, "S3 Service is null, it's could be maked via syncthread cancelWork");
				return false;
			}

			object = s3Service.getObject(bucketName, objectKey);

			BytesProgressWatcher progressMonitor = new BytesProgressWatcher(object.getContentLength());
			// Setup monitoring of stream bytes transferred.
			interruptableInputStream = new InterruptableInputStream(object.getDataInputStream());

			bufferedInputStream = new BufferedInputStream(new ProgressMonitoredInputStream(interruptableInputStream, progressMonitor));
			bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(tmpFile));

			synchronized (s3FileStreams) {
				s3FileStreams.add(interruptableInputStream);
			}

			byte[] buffer = new byte[2048];
			int byteCount = -1;

			while ((byteCount = bufferedInputStream.read(buffer)) != -1) {
				bufferedOutputStream.write(buffer, 0, byteCount);

				if (messageDigest != null) {
					messageDigest.update(buffer, 0, byteCount);
				}
			}

		} catch (UnrecoverableIOException uioe) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(uioe));
			isSuccess = false;
		} catch (ServiceException se) {
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
			isSuccess = false;
			throw se;
		} catch (SocketTimeoutException ste) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(ste));
			isSuccess = false;
		} catch (IOException ioe) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(ioe));
			isSuccess = false;
		} catch (Throwable throwable) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(throwable));
			isSuccess = false;
		} finally {
			if (interruptableInputStream != null)
				synchronized (s3FileStreams) {
					s3FileStreams.remove(interruptableInputStream);
				}

			if (bufferedInputStream != null) {
				try {
					bufferedInputStream.close();
				} catch (Exception ex) {
					isSuccess = false;
					_logger.error(LoggerType.CSS, "Unable to close Object input stream:" + objectKey);
				}
			}

			if (bufferedOutputStream != null) {
				try {
					bufferedOutputStream.close();
				} catch (Exception ex) {
					isSuccess = false;
					_logger.error(LoggerType.CSS, "Unable to close download output stream:" + objectKey);
				}
			}
		}

		if (!isSuccess) {
			return false;
		}

		// Check that actual bytes received match expected hash value
		if (messageDigest != null) {
			byte[] dataMD5Hash = messageDigest.digest();
			String hexMD5OfDownloadedData = ServiceUtils.toHex(dataMD5Hash);

			// Don't check MD5 hash against ETag if ETag doesn't look
			// like an MD5 value
			if (!ServiceUtils.isEtagAlsoAnMD5Hash(object.getETag())) {
				// Use JetS3t's own MD5 hash metadata value for
				// comparison, if it's available
				if (!hexMD5OfDownloadedData.equals(object.getMd5HashAsHex())) {
					_logger.error(LoggerType.CSS, "Unable to verify MD5 hash of downloaded data against" + " ETag returned by service because ETag value \"" + object.getETag()
					        + "\" is not an MD5 hash value" + ", for object key: " + object.getKey());
					return false;
				}
			} else {
				if (!hexMD5OfDownloadedData.equals(object.getETag())) {
					_logger.error(LoggerType.CSS, "Mismatch between MD5 hash of downloaded data (" + hexMD5OfDownloadedData + ") and ETag returned by service (" + object.getETag()
					        + ") for object key: " + object.getKey());
					return false;
				}
			}
		}

		object.setDataInputStream(null);
		object.setDataInputFile(tmpFile);

		// If data was downloaded to a file, set the file's Last Modified
		// date to the original last modified date metadata stored with the
		// object.
		if (tmpFile != null) {
			String metadataLocalFileDate = (String) object.getMetadata(Constants.METADATA_JETS3T_LOCAL_FILE_DATE);

			if (metadataLocalFileDate != null) {
				try {
					tmpFile.setLastModified(ServiceUtils.parseIso8601Date(metadataLocalFileDate).getTime());
				} catch (ParseException e) {
					_logger.error(LoggerType.CSS, Util.getStackTrace(e));
				}
			}
		}

		return isSuccess;
	}

	public boolean uploadingObject(String bucketName, StorageObject object) {
		boolean isSuccess = true;

		File underlyingFile = object.getDataInputFile();
		InterruptableInputStream interruptableInputStream = null;

		try {
			interruptableInputStream = new InterruptableInputStream(object.getDataInputStream());
		} catch (ServiceException se) {
			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));
		}

		synchronized (s3FileStreams) {
			s3FileStreams.add(interruptableInputStream);
		}

		BytesProgressWatcher progressMonitor = new BytesProgressWatcher(object.getContentLength());
		ProgressMonitoredInputStream pmInputStream = new ProgressMonitoredInputStream(interruptableInputStream, progressMonitor);
		object.setDataInputStream(pmInputStream);

		try {
			s3Service.putObject(bucketName, object);
		} catch (ServiceException se) {
			isSuccess = false;
			if (XML_MSG.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
				_config.setValue(KEYS.EventSyncWorkerCount, SafeBoxUtils.getXMLErrorValueByAttr(se, "Resource"));
			}
			if (XML_MSG2.equals(SafeBoxUtils.getXMLErrorValueByAttr(se, "Message"))) {
				isSuccess = true;
			}

			_logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se));

			/*
			 * if (isSuccess) { Debug(SafeBoxUtils.parseXMLError(se)); } else {
			 * _logger.error(LoggerType.CSS, SafeBoxUtils.parseXMLError(se)); }
			 */
		} finally {
			if (interruptableInputStream != null)
				synchronized (s3FileStreams) {
					s3FileStreams.remove(interruptableInputStream);
				}
		}

		if (underlyingFile instanceof TempFile) {
			underlyingFile.delete();
		}

		if (interruptableInputStream != null)
			synchronized (s3FileStreams) {
				s3FileStreams.remove(interruptableInputStream);
			}

		return isSuccess;
	}

	public void initEventCount() {
		eventCounts.set(0);
	}

	public int getEventCount() {
		return eventCounts.get();
	}
}
