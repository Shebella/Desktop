package org.itri.ccma.safebox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.db.DataBaseHandler;
import org.itri.ccma.safebox.db.EventException;
import org.itri.ccma.safebox.db.FileObject;
import org.itri.ccma.safebox.db.LocalQueueHandler;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.db.ServerObjectHandler;
import org.itri.ccma.safebox.db.ServerQueueHandler;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.ui.TrayIconHandler;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.Util;

public class SyncThread extends Observable implements Runnable {
	private static final int MAX_MODIFIYTIME_DURATION = 15 * 60 * 1000; // millisecond
	private static final int MAX_SYNC_DURATION = 10; // second
	private static final int MAX_WAIT_SYNC_DURATION_HOUR = 24; // hour
	private static final int MAX_WAIT_TERMINATION_SECONDS = 60000; // 1 minutes
	private static final int FULL_SYNC_CHECK_DURATION = 100;
	private static final int CLIENT_VERSION_CHECK_DURATION = 420;

	// Main thread variables
	private static SyncThread _instance = new SyncThread();
	private static Thread _thread = null;
	private ExecutorService _threadPool = null;
	// App Flow Control variables
	private volatile boolean _isPause = false; // For App Pause/Resume
	private volatile boolean _isCancel = false; // For Canceling Sync round once

	/**
	 * _isFullSyncDone value 
	 *   0: initialize and no need to do fullsync 
	 *   1: do fullsync and complete 
	 *  -1: do fullsync but no syncid 
	 * -99: do fullsync but shutdown 
	 * -98: do fullsync but logout 
	 *  -2: do fullsync but has error
	 */
	private volatile int _isFullSyncDone = 0;
	// Assistance variables
	private LoggerHandler _logger = LoggerHandler.getInstance();
	private Config _config = Config.getInstance();
	private S3Assist _s3Assist = S3Assist.getInstance();
	private CSSHandler _cssHandler = CSSHandler.getInstance();
	private LocalQueueHandler _leqHandler = LocalQueueHandler.getInstance();
	private ServerQueueHandler _seqHandler = ServerQueueHandler.getInstance();
	private ServerObjectHandler _soHandler = ServerObjectHandler.getInstance();

	private int _fullSyncCheckCount = 0;
	private int _clientVersionCheckCount = 0;
	private List<Future<EventSyncWorker>> workers = null;
//	private List<FileChannel> fileChannels = null;
	private Map<String, FileChannel> fileChannels = null;
	
	private SyncThread() {
	}

	public static SyncThread getInstance() {
		return _instance;
	}

	public void start() {
		// Initial DB.
		DataBaseHandler.getInstance().init();
		// Initial JNotify's Path ==> SyncFolder.
		JNotifyHandler.getInstance().setRootPath(_config.getValue(KEYS.SafeBoxLocation));
		JNotifyHandler.getInstance().startWatchEvent();

		if (_thread != null) {
			_thread.interrupt();
			_thread = null;
		}

		_thread = new Thread(_instance);
		_thread.start();
	}

	public void setRootPath(String path) {
		new File(path).mkdir();

		JNotifyHandler.getInstance().setRootPath(path);

		Util.Sleep(3000);
		_updateOfflineEvents();
	}

	public void setPause(boolean value) {
		_isPause = value;

		if (_isPause) {
			IGlobal.appState = IGlobal.APP_STATE.PAUSED;

			if (!_isCancel)
				cancelWork();
		}
	}

	public boolean isPaused() {
		return _isPause;
	}

	private void cancelWork() {
		_logger.info(LoggerType.Main, "SyncThraed cancel work trigger...");
		_isCancel = true;

		if (workers != null) {
			_logger.info(LoggerType.Main, "workers cancel now...");
			for (Future<EventSyncWorker> worker : workers) {
				worker.cancel(true);
			}
		}

		if (_threadPool != null) {
			_logger.info(LoggerType.Main, "Thread pool shutdown now...");
			_threadPool.shutdownNow();
		}

		_s3Assist.setForceCancel(true);
		_s3Assist.cancelWorks();
		_s3Assist.closeFileChannels();
		_s3Assist.setForceCancel(false);

		_logger.info(LoggerType.Main, "SyncThraed cancel work done.");
	}

	public void terminate() {
		_logger.info(LoggerType.Main, "SyncThread termination starting...");

		JNotifyHandler.getInstance().stopWatchEvent();

		if (workers != null) {
			_logger.info(LoggerType.Main, "workers cancel now...");
			for (Future<EventSyncWorker> worker : workers) {
				worker.cancel(true);
			}
		}

		if (_threadPool != null) {
			_logger.info(LoggerType.Main, "Thread pool shutdown now...");
			_threadPool.shutdownNow();
		}

		_s3Assist.setForceCancel(true);
		_s3Assist.cancelWorks();
		_s3Assist.closeFileChannels();
		_s3Assist.setForceCancel(false);

		try {
			// Wait for sync thread run() completed.
			_thread.join(MAX_WAIT_TERMINATION_SECONDS);
			_thread = null;
			_threadPool = null;
		} catch (InterruptedException e) {
			_logger.error(LoggerType.Main, "Waiting for app end has been interrupted: " + e.getMessage());
		}

		DataBaseHandler.getInstance().shutdown();
		// For FullSync Mechanism
		if (_isFullSyncDone >= 0) {
			_config.setValue(KEYS.NormalShutdown, "true");
			_config.storeProperties();
			_logger.info(LoggerType.Main, "Normal Shutdown Confirm.");
		}
	}

	public void run() {
		_logger.debug(LoggerType.Main, "APP VER: " + IGlobal.APP_VER + ", APP SUB VER: " + IGlobal.APP_SUB_VER);
		// throttle = MAX_SYNC_DURATION;

		if (_s3Assist == null) {
			return;
		}
		// for the first time after start program, we need to get the last
		// syncId from EBS Server, then keep this syncId runtime locally
		// _config.setLastSyncId(_cssHandler.getLastSyncId());

		// check new version from server
		_clientVersionCheckCount = CLIENT_VERSION_CHECK_DURATION;
		_checkNewVersion();
		// Full Sync perform checking
		IGlobal.SERVER_EVENT_TYPE serverEventType = _cssHandler.getExistingEvents(_config.getLastSyncId());
		boolean isDoingFullSync = false;

		if (_config.getValue(KEYS.NormalShutdown) == null) {
			_config.setValue(KEYS.NormalShutdown, "false");
			_config.storeProperties();
			isDoingFullSync = true;
		} else if (Boolean.parseBoolean(_config.getValue(KEYS.NormalShutdown))) {
			_config.setValue(KEYS.NormalShutdown, "false");
			_config.storeProperties();
		} else {
			isDoingFullSync = true;
		}

		if (isDoingFullSync || serverEventType == IGlobal.SERVER_EVENT_TYPE.RUNAWAY) {
			// Clear DB's fileList table data & event queue
			try {
				_fullSync();
			} catch (InterruptedException e) {
				_logger.error(LoggerType.Main, e.getMessage());
			} catch (EventException e) {
				_logger.error(LoggerType.Main, e.getMessage());
			}
		} else {
			_updateOfflineEvents();
		}

		try {
			while (!IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) && !IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT)) {
				_logger.info(LoggerType.Main, "Sync thread starting...");

				if (_isPause) {
					_updateProgress(IGlobal.APP_STATE.PAUSED.toString(), IGlobal.APP_STATE.PAUSED.toString());
					Util.Sleep(5000); // Sleep for 5 seconds
					continue;
				}
				// check connection status and initial S3Service if not exist
				if (_s3Assist.checkingConnStatus()) {
					_updateProgress("CONN", "");
				} else {
					_updateProgress("DISCONN", "Disconnected");
					_logger.info(LoggerType.Main, "SyncThread stop because of disconnected with CSS Server.");
				}

				_checkNewVersion();
				// Throttling
				int eventSyncWorkerCount = Integer.valueOf(_config.getValue(KEYS.EventSyncWorkerCount));

				if (eventSyncWorkerCount < Config.EVENT_SYNC_WORKER_COUNT) {
					eventSyncWorkerCount++;
					_config.setValue(KEYS.EventSyncWorkerCount, String.valueOf(eventSyncWorkerCount));
				}

				_logger.debug(LoggerType.Main, "Current EventSyncWorker pool size: " + eventSyncWorkerCount);

				if (!IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) && !IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT) && !_isPause && !_isCancel) {
					_syncTask();
				}
				// declare cancel's done
				if (_isCancel) {
					_isCancel = false;
				}

				_logger.info(LoggerType.Main, "Sync thread end, sleeping now...");
				for (int i = 0; i < MAX_SYNC_DURATION && !IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) && !IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT); i++) {
					Util.Sleep(1000);
				}
			}
		} catch (Throwable throwable) {
			_logger.error(LoggerType.Main, "SyncThread error: " + Util.getStackTrace(throwable));
		}

		_logger.info(LoggerType.Main, "Sync thread end.");
	}

	private void _updateOfflineEvents() {
		S3Event event = null;
		File localFile = null;
		int intCount = 0;

		_logger.info(LoggerType.Main, "SyncThread UpdateOfflineEvents starting...");

		try {
			_leqHandler.clear();
		} catch (EventException e) {
			_logger.error(LoggerType.Main, "_updateOfflineEvents() clear local events fail !!");
			e.printStackTrace();
		}

		_updateProgress("PROCESSING", "Detecting local changes...");
		Map<String, File> localFileMap = FileUtil.collectFiles(_config.getValue(KEYS.SafeBoxLocation));

		_logger.debug(LoggerType.Main, "SyncThread UpdateOfflineEvents Local files counts: " + localFileMap.size());
		// for each file in local disk to mapping local db
		for (String path : localFileMap.keySet()) {
			boolean addFlag = false;
			String objectKey = "/" + Util.translateObjectKey(path);

			event = null;
			localFile = new File(path);
			// 在一次檢查該檔案是否存在, 若不存在應該 JNotify 會偵測到而有 Local Event
			if (!localFile.exists())
				continue;

			FileObject serverObject = null;
			try {
				serverObject = _soHandler.get(objectKey);
			} catch (ObjectException e) {
				// Exception can be ignored here !!
			}

			if (null != serverObject) {
				String localFileMD5 = FileUtil.getMD5(localFile);
				if (localFile.isFile() && !serverObject.MD5.equals(localFileMD5)) {
					event = new S3Event(S3Event.FileAction.Modify, objectKey);
					event.fileObject.isFolder = false;
					event.timestamp = System.currentTimeMillis();
					addFlag = true;
				}
			} else {
				try {
					event = _leqHandler.queryEvent(objectKey);
				} catch (EventException e) {
					// Exception can be ignored here !!
				}

				if (null == event) {
					// add this new file
					event = new S3Event(S3Event.FileAction.Create, objectKey);
					event.fileObject.isFolder = localFile.isDirectory();
					event.timestamp = System.currentTimeMillis();
					addFlag = true;
				}
			}

			if (addFlag) {
				_logger.debug(LoggerType.Event, "Added offline event(" + event.fileAction + "): " + objectKey);

				try {
					_leqHandler.addEvent(event);
					intCount++;
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				}
			}
		}

		List<String> lstObjectKeys = null;
		try {
			lstObjectKeys = _soHandler.getObjectKeys();
		} catch (ObjectException e) {
			_logger.error(LoggerType.Main, e.getMessage());
		}

		_logger.debug(LoggerType.Main, "SyncThread UpdateOfflineEvents Remote table counts: " + lstObjectKeys.size());

		for (String objectKey : lstObjectKeys) {
			String path = Util.translateLocalPath(objectKey);
			localFile = new File(path);

			if (!localFile.exists()) {
				// need to delete these files
				_logger.debug(LoggerType.Event, "Added offline event(Delete): " + objectKey);

				try {
					event = new S3Event(S3Event.FileAction.Delete, objectKey);
					event.fileObject = _soHandler.get(objectKey);
					_leqHandler.addEvent(event);
					intCount++;
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				} catch (ObjectException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				}
			}
		}

		_logger.info(LoggerType.Main, "SyncThread UpdateOfflineEvents done & " + intCount + " events detected.");
	}

	/*
	 * This function is used only to separate Create event to Create or Modify
	 * event to clarify the event process flow. If the CSS server can produce
	 * Modify event, this function is useless.
	 */
	private void _validateEvents(List<S3Event> events) {
		for (S3Event event : events) {
			if (event.fileAction.equals(S3Event.FileAction.Create)) {
				S3Event eventBefore = null;

				try {
					eventBefore = _seqHandler.queryEvent(event.fileObject.objectKey, S3Event.EventState.New);
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				}

				if (eventBefore == null) {
					try {
						FileObject fbLocal = _soHandler.get(event.fileObject.objectKey);
						// Rule: If Local can find the object, then this event
						// must be Modify event.
						if (fbLocal != null && !fbLocal.isFolder)
							event.fileAction = S3Event.FileAction.Modify;
					} catch (ObjectException e) {
						_logger.error(LoggerType.Main, e.getMessage());
					}
				} else if (eventBefore.fileAction.equals(S3Event.FileAction.Create)) {
					event.fileAction = S3Event.FileAction.Modify;
				} else if (eventBefore.fileAction.equals(S3Event.FileAction.Rename)) {
					event.fileAction = S3Event.FileAction.Modify;
				} else if (eventBefore.fileAction.equals(S3Event.FileAction.Modify)) {
					event.fileAction = S3Event.FileAction.Modify;
				}
			}
		}
	}

	private boolean _eventBasedSync() throws InterruptedException, EventException {
		// /////////////////////////////////////////////////////////////////////
		// Step 1. Retrieve server events & grouping it by SyncID
		HashMap<Long, List<S3Event>> eventGroups = null;
		List<S3Event> events = null;

		try {
			eventGroups = _cssHandler.getServerEvents();
		} catch (SafeboxException se) {
			_logger.error(LoggerType.Main, se.getMessage());
			throw new InterruptedException();
		}
		// /////////////////////////////////////////////////////////////////////
		// Step 2. Check if there are any events retrieved from server.
		// The server events must be consolidated first, then retrieve
		// again from server queue for processing.
		if (!eventGroups.isEmpty()) {
			// Step 2.1. Sorting SyncIDs
			List<Long> lstSyncIDs = new ArrayList<Long>();
			lstSyncIDs.addAll(eventGroups.keySet());
			Collections.sort(lstSyncIDs);
			// Step 2.2. Adding to server event queue by SyncID's sequence with
			// consolidation.
			long lastsync = _seqHandler.getLastSyncID();

			for (int i = 0; i < lstSyncIDs.size(); i++) {
				if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) || _isCancel)
					return false;
				// If target SyncID is smaller than LastSyncID in SEQ, the
				// target SyncID's
				// server events will be ignored because of there are already in
				// SEQ.
				if (lstSyncIDs.get(i) <= lastsync)
					continue;

				events = eventGroups.get(lstSyncIDs.get(i));
				_validateEvents(events);

				try {
					_seqHandler.addEvents(events, lstSyncIDs.get(i).toString());
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				}

				_logger.info(LoggerType.Main, "SyncThread EventBasedSync Add target Server Events (SyncID:" + lstSyncIDs.get(i) + ") count: " + events.size());
			}
		}

		if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) || _isCancel) {
			return false;
		}
		// /////////////////////////////////////////////////////////////////////
		// Step 3. Retrieve Server/Local events for synchronization.
		EventSyncWorker.EVENT_TYPE eventType = EventSyncWorker.EVENT_TYPE.SERVER;

		try {
			events = _seqHandler.getEvents();

			if (events.size() == 0) {
				events = _leqHandler.getEvents();
				eventType = EventSyncWorker.EVENT_TYPE.LOCAL;
			}
		} catch (EventException e) {
			_logger.error(LoggerType.Main, e.getMessage());
		}

		if (events.isEmpty()) {
			_logger.info(LoggerType.Main, "SyncThread EventBasedSync No events found!");
			return true;
		}
		// /////////////////////////////////////////////////////////////////////
		// Step 4. Process events follows: DIR Create > Files > DIR Delete
		_logger.info(LoggerType.Main, "SyncThread EventBasedSync Process " + eventType.toString() + " events start...");
		_updateProgress("UPDATE", "Sync " + eventType.toString() + " events...");

		boolean isUpdateLastSyncId = false;
		List<S3Event> dir_events = new ArrayList<S3Event>();
		List<S3Event> dir_delete_events = new ArrayList<S3Event>();
		List<S3Event> file_events = new ArrayList<S3Event>();
		// Step 4.1. Separating events based on barrier event rules.
		for (S3Event event : events) {
			if (event.fileObject.isFolder) {
				if (event.fileAction.equals(S3Event.FileAction.Delete))
					dir_delete_events.add(event);
				else
					dir_events.add(event);
			} else
				file_events.add(event);
		}
		// Step 4.2. Barrier Event rule 1: 目錄操作必須要先作才能保證後續相關該目錄下的檔案操作能同時進行
		isUpdateLastSyncId = true;
		if (dir_events.size() > 0) {
			_logger.debug(LoggerType.Main, "Process folder events.");
			// Folder's Create & Rename events can't be processed in parallel,
			// must do it one by one.
			for (S3Event event : dir_events) {
				EventSyncWorker eventSyncWorker = new EventSyncWorker(event, _s3Assist, eventType);
				eventSyncWorker.run();

				if (!eventSyncWorker.isSuccess()) {
					isUpdateLastSyncId = false;
					break;
				}
			}
		}

		if (file_events.size() > 0 && isUpdateLastSyncId) {
			_logger.debug(LoggerType.Main, "Process file events.");
			isUpdateLastSyncId = _processEvents(file_events, eventType);
		}
		// Server Barrier Event rule 2: 目錄刪除必須要最後再做, 主要是因為要等該目錄的檔案都刪除完才能進行
		if (dir_delete_events.size() > 0 && isUpdateLastSyncId) {
			_logger.debug(LoggerType.Main, "Process folder delete events.");
			isUpdateLastSyncId = _processEvents(dir_delete_events, eventType);
		}

		_logger.info(LoggerType.Main, "SyncThread EventBasedSync end.");
		return isUpdateLastSyncId;
	}

	/*
	 * This function is used to sync all server objects to local. The local's
	 * file will be replaced if it's object key is the same as server's.
	 */
	private void _fullSync() throws EventException, InterruptedException {
		// During FullSync, jNotify will stop watching sync folder.
		_isFullSyncDone = -1;
		JNotifyHandler.getInstance().stopWatchEvent();
		_updateProgress("UPDATE", "Full syncing");

		try {
			_cssHandler.initSyncRequest();
		} catch (SafeboxException se) {
			_logger.error(LoggerType.Main, se.getMessage());
		}

		while (_cssHandler.getCurrentSyncId() == -1) {
			_logger.info(LoggerType.Main, "FullSync invoked, waiting for SyncID...");

			for (int i = 0; i < MAX_SYNC_DURATION && !IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) && !IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT) && !_isCancel; i++) {
				Util.Sleep(1000);
			}

			if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN)) {
				_logger.error(LoggerType.Main, "SyncThread FullSync abort because of shutdown !!");
				return;
			} else if (IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT)) {
				_logger.error(LoggerType.Main, "SyncThread FullSync abort because of CSS disconnect !!");
				return;
			}

			try {
				_cssHandler.initSyncRequest();
			} catch (SafeboxException se) {
				_logger.error(LoggerType.Main, se.getMessage());
			}
		}

		_logger.debug(LoggerType.Main, "FullSync with SyncID: " + _cssHandler.getCurrentSyncId() + " starting...");

		// Try to lock all files
		_lockAllFiles();
		final HashMap<String, FileObject> htServerObjects = _soHandler.getAll();
		// Re-init DB
		_seqHandler.clear();
		_soHandler.clear();

		List<FileObject> objList = new ArrayList<FileObject>();
		List<FileObject> objDIRList = new ArrayList<FileObject>();
		try {

			_cssHandler.ListObjectsDetails(objList, objDIRList);

			_logger.debug(LoggerType.Main, "FullSync start process folders:" + objDIRList.size());

			for (int i = 0; i < objDIRList.size(); i++) {
				
				if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of shutdown !!");
					_isFullSyncDone = -99;
					return;
				} else if (IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of CSS disconnect !!");
					_isFullSyncDone = -98;
					return;
				} else if (IGlobal.appState.equals(IGlobal.APP_STATE.PAUSED)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of pause !!");
					_isFullSyncDone = -97;
					return;
				}

				File file = new File(Util.translateLocalPath(objDIRList.get(i).objectKey));
				if (!file.exists()) {
					file.mkdir();
				}
				_soHandler.add(objDIRList.get(i));
			}

			_logger.debug(LoggerType.Main, "FullSync start process files:" + objList.size());

			for (int i = 0; i < objList.size(); i++) {
				
				if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of shutdown !");
					_isFullSyncDone = -99;
					return;
				} else if (IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of CSS disconnect !");
					_isFullSyncDone = -98;
					return;
				}  else if (IGlobal.appState.equals(IGlobal.APP_STATE.PAUSED)) {
					_logger.error(LoggerType.Main, "SyncThread FullSync abort because of pause !");
					_isFullSyncDone = -97;
					return;
				}

				FileObject obj = objList.get(i);
				if (!_processFullSyncFile(obj, htServerObjects.get(obj.objectKey))) {
					_isFullSyncDone = -2;
				}

			}
		} catch (SafeboxException e) {
			_logger.error(LoggerType.CSS, Util.getStackTrace(e));
		} finally {
//			_unlockAllFiles();
			_closeAllFileChannels();
//			_s3Assist.releaseFileLocks();
			_s3Assist.closeFileChannels();
		}

		if (_isFullSyncDone == -1)
			_isFullSyncDone = 1;

		_logger.debug(LoggerType.Main, "SyncThread FullSync done. Status: " + _isFullSyncDone);
		_updateProgress("NONE", "Full Sync done.");
		// Need to unlock syncID
		_finishSync(true);
		// initial full sync check count
		_fullSyncCheckCount = 0;
		JNotifyHandler.getInstance().startWatchEvent();
		// Sync local to server
		if (_isFullSyncDone == 1)
			_updateOfflineEvents();
	}

	private void _finishSync(boolean isUpdateLastSyncId) {
		_logger.info(LoggerType.Main, "Sync finishing...");
		// update last syncId
		if (isUpdateLastSyncId) {
			long lastSyncID = _config.getLastSyncId();
			long currentSyncID = _cssHandler.getCurrentSyncId();
			_logger.debug(LoggerType.Main, "Last sync id updated from " + lastSyncID + " move to " + currentSyncID);
			_config.setLastSyncId(currentSyncID);
			_config.storeProperties();
			// _config.load(null);
			_logger.debug(LoggerType.Main, "Newly Last sync id is " + _config.getLastSyncId());
		}
		// To release sync ID
		if (-1 != _cssHandler.getCurrentSyncId()) {
			_cssHandler.unlockSyncId(CSSHandler.UNLOCK_TYPE.SUCC);

			_s3Assist.initEventCount();
			_cssHandler.initEventCount();
		}

		// Update bucket info & UI.
		try {
			_cssHandler.getBucketInfo();
		} catch (SafeboxException e) {
		}
		TrayIconHandler.getInstance().updateSpaceRatio();

		_logger.info(LoggerType.Main, "Sync finished.");
	}

	private boolean _processEvents(final List<S3Event> events, final EventSyncWorker.EVENT_TYPE type) {
		_logger.debug(LoggerType.Main, "Processing number of " + type + " events: " + events.size() + " start...");

		int totalSize = events.size();
		boolean isSuccess = true;
		int currentCount = 0;

		_threadPool = Executors.newFixedThreadPool(Integer.parseInt(_config.getValue(KEYS.EventSyncWorkerCount)));
		workers = new ArrayList<Future<EventSyncWorker>>();
		_updateProgress("PROCESSING", "Processing " + type + " events starting...");

		// int threadNum = 1;
		for (S3Event event : events) {
			if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) || _isCancel) {
				_logger.debug(LoggerType.Main, "ProcessEvents() canceled.");
				break;
			}

			// _logger.debug(LoggerType.Event, "SyncThread processEvents(" +
			// type.toString() + "," + _cssHandler.getCurrentSyncId() + "): " +
			// event.fileObject.objectKey);
			_updateProgress("UPDATE", "Processing file " + (++currentCount) + " / " + totalSize);
			// _threadPool.execute(new EventSyncWorker(event, _s3Assist, type));
			EventSyncWorker eventSyncWorker = new EventSyncWorker(event, _s3Assist, type);
			// eventSyncWorker.setThreadNum(threadNum++);

			Future<EventSyncWorker> future = null;
			try {
				future = _threadPool.submit(eventSyncWorker, eventSyncWorker);
			} catch (RejectedExecutionException ree) {
				isSuccess = false;
				_logger.error(LoggerType.Main, Util.getStackTrace(ree));
			}

			workers.add(future);
		}

		_logger.debug(LoggerType.Main, "Thread Pool task count: " + ((ThreadPoolExecutor) _threadPool).getTaskCount());
		_threadPool.shutdown();

		try {
			if (_threadPool.awaitTermination(MAX_WAIT_SYNC_DURATION_HOUR, TimeUnit.HOURS)) {
				for (Future<EventSyncWorker> future : workers) {

					EventSyncWorker eventSyncWorker = null;
					try {
						if (_isCancel) {
							throw new InterruptedException();
						}

						eventSyncWorker = future.get();
					} catch (ExecutionException ee) {
						isSuccess = false;
						_logger.error(LoggerType.Main, Util.getStackTrace(ee));
					}

					if (eventSyncWorker != null && eventSyncWorker.isSuccess()) {
						continue;
					}

					isSuccess = false;

					break;
				}

				_logger.debug(LoggerType.Main, "SyncThread ProcessEvents(" + type.toString() + ") " + (isSuccess ? "success." : "failed."));
				_updateProgress("PROCESSING", "Sync work done.");
			} else {
				_logger.error(LoggerType.Main, "SyncThread ProcessEvents(" + type.toString() + ") timeout failed.");
				isSuccess = false;

				if (workers != null) {
					for (Future<EventSyncWorker> worker : workers) {
						worker.cancel(true);
					}
				}

				_threadPool.shutdownNow();

				if (!_threadPool.awaitTermination(MAX_WAIT_SYNC_DURATION_HOUR, TimeUnit.HOURS)) {
					_logger.error(LoggerType.Main, "ThreadPool did not terminated!");
				}
			}
		} catch (InterruptedException ie) {
			isSuccess = false;
			_threadPool.shutdownNow();
		}

		_threadPool = null;

//		_s3Assist.releaseFileLocks();
		_s3Assist.closeFileChannels();

		_logger.debug(LoggerType.Main, "Processing " + type + " events result was [" + isSuccess + "]");
		_logger.debug(LoggerType.Main, "Processing number of " + type + " events: " + totalSize + " end.");

		return isSuccess;
	}

	private synchronized void _updateProgress(String command, String progress) {
		if (IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) || IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT))
			return;

		if (command.equals("CONN") || command.equals("END")) {
			try {
				if (0 < _leqHandler.getCount()) {
					command = "CONTINUE";
					progress = "Checking local files";
				} else
					command = "NONE";
			} catch (EventException e) {
				_logger.error(LoggerType.Main, e.getMessage());
			}
		}

		if (command.equals("END")) {
			_logger.debug(LoggerType.Main, "Update Progress [End]");
			IGlobal.appState = IGlobal.APP_STATE.NORMAL;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals("START") || command.equals("UPDATE")) {
			// _logger.debug(LoggerType.Main,
			// "Update Progress [Start | Update]");
			IGlobal.appState = IGlobal.APP_STATE.SYNCING;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals("DISCONN")) {
			_logger.debug(LoggerType.Main, "Update Progress [Disconn]");
			IGlobal.appState = IGlobal.APP_STATE.DISCONNECT;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals("CONN") || command.equals("NONE")) {
			_logger.debug(LoggerType.Main, "Update Progress [Conn | None]");
			IGlobal.appState = IGlobal.APP_STATE.NORMAL;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals("PROCESSING") || command.equals("CONTINUE")) {
			_logger.debug(LoggerType.Main, "Update Progress [Continue]");
			IGlobal.appState = IGlobal.APP_STATE.PROCESSING;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals(IGlobal.APP_STATE.PAUSED.toString())) {
			_logger.debug(LoggerType.Main, "Update Progress [Pause]");
			IGlobal.appState = IGlobal.APP_STATE.PAUSED;
			setChanged();
			notifyObservers(progress);
		} else if (command.equals(IGlobal.APP_STATE.SHUTDOWN.toString())) {
			_logger.debug(LoggerType.Main, "Update Progress [Shutdown]");
			IGlobal.appState = IGlobal.APP_STATE.SHUTDOWN;
			setChanged();
			notifyObservers(progress);
		}

		try {
			if (_config.instantID != 0) {
				if (command.equals("START") || command.equals("UPDATE"))
					Main.setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, "SYNCING");
				else if (command.equals("DISCONN"))
					Main.setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, "DISCONN");
				else if (command.equals("CONN") || command.equals("END") || command.equals("CONTINUE"))
					Main.setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, command);
				else if (command.equals("IDLE"))
					Main.setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, "IDLE");
				else
					Main.setStautsInPort(_config.getValue(KEYS.HostIP), _config.getValue(KEYS.User), _config.instantID, command);
			}
		} catch (Exception e) {
			_logger.error(LoggerType.Main, Util.getStackTrace(e));
		}
	}

	private boolean _checkNewVersion() {
		boolean flag = false;
		_logger.debug(LoggerHandler.LoggerType.Main, "Checking Safebox new version start...");
		if (CLIENT_VERSION_CHECK_DURATION <= _clientVersionCheckCount) {
			_clientVersionCheckCount = 0;

			ComparableVersion serverVersion = _cssHandler.getNewClientVersion();
			ComparableVersion clientVersion = new ComparableVersion(IGlobal.APP_VER.substring(1));

			if (null != serverVersion && null != clientVersion && 0 > clientVersion.compareTo(serverVersion)) {
				_logger.debug(LoggerType.Main, "New version: " + serverVersion + ", Original version: " + clientVersion);
				String message = "New version is " + serverVersion + ", please click here to download the new version.";
				TrayIconHandler.getInstance().showMessage("There is a new version of Safebox available",
				        message + "||" + ServerConnectionUtil.getWebURL(_config.getValue(KEYS.HostIP)));
				flag = true;
			}
		}

		_clientVersionCheckCount++;
		_logger.debug(LoggerHandler.LoggerType.Main, "Checking Safebox new version end.");

		return flag;
	}

	private boolean _checkFullSyncNeed() {
		_logger.info(LoggerHandler.LoggerType.Main, "FullSync counting...");
		_fullSyncCheckCount++;

		if (FULL_SYNC_CHECK_DURATION <= _fullSyncCheckCount) {
			_fullSyncCheckCount = 0;

			try {
				_cssHandler.getBucketInfo();
			} catch (SafeboxException se) {
				_logger.error(LoggerType.Main, se.getMessage());
			}

			if (null != Command.BUCKET_INFO && Command.BUCKET_INFO.isValidate() && -1 != Command.BUCKET_INFO.getObjectCount()) {
				Collection<File> list = FileUtils.listFilesAndDirs(new File(_config.getValue(KEYS.SafeBoxLocation)), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
				
				int localFilesCount = 0;
				int localFoldersCount = -1; // list result include with the safebox syncfolder
				
				for (File file:list){
					if (file.isDirectory()) {
						localFoldersCount++;
					} else {
						localFilesCount++;
					}
				}				

				_logger.debug(LoggerType.Main, "local files count: " + localFilesCount + ", Server files count: " + Config.BUCKET_FILES_COUNT);
				_logger.debug(LoggerType.Main, "local folders count: " + localFoldersCount + ", Server folders count: " + Config.BUCKET_FOLDERS_COUNT);
				
				boolean hasErrorEvent = _leqHandler.hasErrorEvent();
				_logger.debug(LoggerType.Main, "local has error event: " + hasErrorEvent);
				
				if (localFilesCount != Config.BUCKET_FILES_COUNT || localFoldersCount != Config.BUCKET_FOLDERS_COUNT) {
					return true;
				} else if (hasErrorEvent){
					return true;
				}
			}
		}

		return false;
	}

    private FileLock _lockFile(File file) {
		// Require file lock
		FileLock lock = null;
		FileChannel channel = null;
		try {
			if (FileUtil.isFileReadOnly(file)) {
				channel = new RandomAccessFile(file, "r").getChannel();
				lock = channel.tryLock(0L, Long.MAX_VALUE, true);
			} else {
				channel = new RandomAccessFile(file, "rw").getChannel();
				lock = channel.tryLock(); // Same as tryLock(0L, Long.MAX_VALUE, false);
			}
			
//			fileChannels.add(channel);
			fileChannels.put(file.getAbsolutePath().replace(_config.getValue(KEYS.SafeBoxLocation), _config.getValue(KEYS.SafeBoxLocation) + "\\"), channel);
			
		} catch (FileNotFoundException fnfe) {
			_logger.error(LoggerType.Main, Util.getStackTrace(fnfe));
		} catch (IOException ioe) {
			_logger.error(LoggerType.Main, Util.getStackTrace(ioe));
		}
		
		return lock;
	}

	private int _listFiles(int cnt, String folder) {
		File directory = new File(folder);
		File[] contents = directory.listFiles();
		for (File f : contents) {
			if (f.isDirectory()) {
				cnt = _listFiles(cnt, f.getAbsolutePath());
			} else {
				_lockFile(f);
			}

			cnt++;
		}

		return cnt;
	}

	private void _lockAllFiles() {
		/*if (fileLocks == null) {
			fileLocks = new ArrayList<FileLock>();
		}*/
		
		if (fileChannels == null) {
//			fileChannels = new ArrayList<FileChannel>();
			fileChannels = new HashMap<String, FileChannel>();			
		}
		
		long start = System.currentTimeMillis();
		int cnt = _listFiles(0, _config.getValue(KEYS.SafeBoxLocation));
		long end = System.currentTimeMillis();
		
		_logger.debug(LoggerType.Main, "Nummber of Files lock: " + cnt + ",Spend time: " + (end-start) / 1000 + " sec");
	}
	
	private void _closeAllFileChannels() {

		_logger.debug(LoggerType.Main, "Nummber of file channels: " + fileChannels.size() + ", Close file channels start...");
		
		Iterator<Entry<String, FileChannel>> itr = fileChannels.entrySet().iterator();
		while (itr.hasNext()) {
			Entry<String, FileChannel> pairs = itr.next();
			
			FileChannel channel = (FileChannel) pairs.getValue();
			try {
				if (channel != null) {
					channel.close();
				}

			} catch (IOException ioe) {
				_logger.error(LoggerHandler.LoggerType.Main, Util.getStackTrace(ioe));
			}
			itr.remove(); // avoids a ConcurrentModificationException
		}

		fileChannels = null;
		
		/*for (FileChannel channel : fileChannels) {			
			try {
				if (channel != null) {
					channel.close();
				}
				
			} catch (IOException ioe) {
				_logger.error(LoggerType.Main, Util.getStackTrace(ioe));
			}
		}*/

//		fileChannels.clear();
		_logger.debug(LoggerType.Main, "Close file channels end.");
	}

	private void _syncTask() {
		_logger.debug(LoggerType.Main, "Sync Task starting...");

		IGlobal.SERVER_EVENT_TYPE serverEventType = _cssHandler.getExistingEvents(_config.getLastSyncId());

		_logger.debug(LoggerType.Main, "SERVER_EVENT_TYPE is " + serverEventType.name());

		if (serverEventType == IGlobal.SERVER_EVENT_TYPE.ERROR) {
			_logger.error(LoggerHandler.LoggerType.Main, "SyncThread EBS ExistingEvents() error.");
			return;
		}

		int intCount = 0;
		try {
			intCount = _leqHandler.getCount() + _seqHandler.getCount();
		} catch (EventException e) {
			_logger.error(LoggerType.Main, e.getMessage());
		}

		if (IGlobal.SERVER_EVENT_TYPE.NO_EVENT == serverEventType && 0 == intCount) {
			_logger.debug(LoggerType.Main, "SyncTaskWorker no server and local events");
			if (_checkFullSyncNeed()) {
				try {
					// try to lock sync folder
					// Util.lockDirectory(_config.getValue(KEYS.SafeBoxLocation),
					// System.getProperty("user.name"));
					// _lockFolder();
					_fullSync();
				} catch (InterruptedException e) {
					_logger.error(LoggerType.Main, "SyncTask full sync interrupted.");
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				} finally {
					// try to un-lock sync folder
					// Util.unlockDirectory(_config.getValue(KEYS.SafeBoxLocation),
					// System.getProperty("user.name"));
					// _unlockFolder();
				}
			}
		} else if (IGlobal.SERVER_EVENT_TYPE.RUNAWAY == serverEventType) {
			try {
				// try to lock sync folder
				// Util.lockDirectory(_config.getValue(KEYS.SafeBoxLocation),
				// System.getProperty("user.name"));
				// _lockFolder();
				_fullSync();
			} catch (InterruptedException e) {
				_logger.error(LoggerType.Main, "SyncTask full sync interrupted.");
			} catch (EventException e) {
				_logger.error(LoggerType.Main, e.getMessage());
			} finally {
				// try to un-lock sync folder
				// Util.unlockDirectory(_config.getValue(KEYS.SafeBoxLocation),
				// System.getProperty("user.name"));
				// _unlockFolder();
			}
		} else if (IGlobal.SERVER_EVENT_TYPE.HAS_EVENTS == serverEventType || 0 < intCount) {
			boolean isUpdateLastSyncId = false;
			// 只要有事件發生, 不管是 server/local, 都應該重設 FullSync 的計時器.
			_fullSyncCheckCount = 0;
			_updateProgress("START", "");
			// Request syncID from CSS
			try {
				_cssHandler.initSyncRequest();
			} catch (SafeboxException se) {
				_logger.error(LoggerType.Main, se.getMessage());
				return;
			}

			if (-1 == _cssHandler.getCurrentSyncId()) {
				_logger.info(LoggerType.Main, "Waitting for get Sync ID ...");
				return;
			}

			_s3Assist.initEventCount();
			_cssHandler.initEventCount();

			_logger.debug(LoggerType.Main, "Last Sync ID: " + _config.getLastSyncId() + ", Current Sync ID: " + _cssHandler.getCurrentSyncId());

			if (!IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) && !IGlobal.appState.equals(IGlobal.APP_STATE.LOGOUT) && !_isPause && !_isCancel) {
				try {
					isUpdateLastSyncId = _eventBasedSync();
				} catch (InterruptedException e) {
					_logger.error(LoggerType.Main, e.getMessage());
					cancelWork();
				} catch (EventException e) {
					_logger.error(LoggerType.Main, e.getMessage());
				}

				_finishSync(isUpdateLastSyncId);
			}
		}

		_updateProgress("END", "Last sync time: " + IGlobal.SDF.format(new Date()));
		_logger.debug(LoggerType.Main, "Sync Task end.");
	}

	private boolean _processFullSyncFile(FileObject targetobj, FileObject recordobj) {
		
		String localPath = Util.translateLocalPath(targetobj.objectKey);
		
		//unlock file for process
		FileChannel channel = fileChannels.get(localPath);
		
		try {
			if (channel != null) {
				channel.close();
			}
        } catch (IOException ioe) {
        	_logger.error(LoggerType.Main, Util.getStackTrace(ioe));
        }
		
		fileChannels.remove(localPath);

		File localFile = new File(localPath);
		String localMD5 = "";
		boolean isDone = false;
		// /////////////////////////////////////////////////////////////
		// Condition 1: If file is existing, it has to compare MD5 to
		// decide download or not.

		if (localFile.exists()) {
			// Optional: Use DB's snapshot can avoid MD5's calculation.
			if (null != recordobj) {
				if (localFile.lastModified() == recordobj.modifiedDate) {
					localMD5 = recordobj.MD5;
				} else {
					
					localMD5 = FileUtil.getMD5(localFile);
				}

			} else {
				localMD5 = FileUtil.getMD5(localFile);
			}
			// /////////////////////////////////////////////////////////
			// If file's MD5 is the same as server's, it has nothing to
			// do.
			// If not, file downloading from server must start to
			// replace the local one.
			if (!localMD5.equals(targetobj.MD5)) {
				if (localFile.lastModified() > targetobj.modifiedDate) {
					isDone = true;
				} else if (targetobj.modifiedDate <= localFile.lastModified() + MAX_MODIFIYTIME_DURATION) {
					FileUtil.fileBackup(targetobj.objectKey);
					isDone = _s3Assist.downloadingObjectForFullSync(targetobj.objectKey);
				} else {
					isDone = _s3Assist.downloadingObjectForFullSync(targetobj.objectKey);
				}
			} else {
				isDone = true;
			}
		}
		// /////////////////////////////////////////////////////////////
		// Condition 2: If file isn't existing, downloading it from
		// server.
		else {
			// Download file
			isDone = _s3Assist.downloadingObjectForFullSync(targetobj.objectKey);
		}

		if (isDone) {
			targetobj.size = localFile.length();
			_soHandler.add(targetobj);
			
			//lock file
			_lockFile(localFile);
			
		} else {
			return false;
		}

		return true;
	}
}
