package org.itri.ccma.safebox;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

import org.itri.ccma.safebox.db.EventException;
import org.itri.ccma.safebox.db.FileObject;
import org.itri.ccma.safebox.db.LocalQueueHandler;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.db.ServerObjectHandler;
import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.Util;

public class JNotifyHandler implements JNotifyListener {
	private static final boolean _WATCH_SUB_TREE = true;
	private static final int _JNOTIFY_MASK = JNotify.FILE_CREATED | JNotify.FILE_DELETED | JNotify.FILE_MODIFIED | JNotify.FILE_RENAMED;

	private static final JNotifyHandler _instance = new JNotifyHandler();

	private String _rootPath = "";
	private int _watchID = -1;

	private LoggerHandler _logger = LoggerHandler.getInstance();
	private CSSHandler _cssHandler = CSSHandler.getInstance();
	private LocalQueueHandler _leqHandler = LocalQueueHandler.getInstance();
	private ServerObjectHandler _soHandler = ServerObjectHandler.getInstance();

	private JNotifyHandler() {
	}

	public static JNotifyHandler getInstance() {
		return _instance;
	}

	public void setRootPath(String path) {
		// Init path
		_rootPath = path;
	}

	public void stopWatchEvent() {
		if (_watchID >= 0)
			try {
				JNotify.removeWatch(_watchID);
			} catch (JNotifyException e) {
				e.printStackTrace();
			}

		_watchID = -1;
	}

	public void startWatchEvent() {
		_watchID = -1;

		try {
			_watchID = JNotify.addWatch(_rootPath, _JNOTIFY_MASK, _WATCH_SUB_TREE, this);
		} catch (Exception e) {
			_logger.error(LoggerType.Event, e.getMessage());
			stopWatchEvent();
		}

		return;
	}

	/*
	 * Implements interface: JNotifyListener
	 * 
	 * @see net.contentobjects.jnotify.JNotifyListener#fileRenamed(int,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	public void fileRenamed(int wd, String rootPath, String oldName, String newName) {
		String oldPath = rootPath + File.separatorChar + oldName;
		String newPath = rootPath + File.separatorChar + newName;
		String oldKey = "/" + Util.translateObjectKey(oldPath);
		String newKey = "/" + Util.translateObjectKey(newPath);
		long eventTimeStamp = System.currentTimeMillis();

		_logger.debug(LoggerType.Event, "check rename echo event:" + newKey + ", currentTime:" + eventTimeStamp);

		try {
			if (_leqHandler.isEchoEvent(S3Event.FileAction.Rename, newKey, _cssHandler.getCurrentSyncId(), eventTimeStamp)) {
				_logger.debug(LoggerType.Event, "Jnotify Rename is ignored because of Echo: " + oldKey + " -> " + newKey);
				return;
			}
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		File newFile = new File(newPath);
		S3Event event = new S3Event(S3Event.FileAction.Rename, newKey);

		event.oldObjectKey = oldKey;
		event.fileObject.isFolder = newFile.isDirectory();
		event.timestamp = eventTimeStamp;

		_logger.debug(LoggerType.Event, "Jnotify Rename:" + oldKey + "->" + newKey);

		try {
			_leqHandler.addEvent(event);
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}
	}

	/*
	 * Implements interface: JNotifyListener
	 * 
	 * @see net.contentobjects.jnotify.JNotifyListener#fileModified(int,
	 * java.lang.String, java.lang.String)
	 */
	public void fileModified(int wd, String rootPath, String name) {

		String filePath = rootPath + File.separatorChar + name;
		String objectKey = "/" + Util.translateObjectKey(filePath);
		File file = new File(filePath);
		long eventTimeStamp = System.currentTimeMillis();

		if (false == file.exists() || file.isDirectory()) {
			_logger.debug(LoggerType.Event, "Jnotify Modify ignored because file doesn't exixt or is directory: " + objectKey);
			return;
		}

		_logger.debug(LoggerType.Event, "check modify echo event:" + objectKey + ", currentTime:" + eventTimeStamp);

		try {
			if (_leqHandler.isEchoEvent(S3Event.FileAction.Modify, objectKey, _cssHandler.getCurrentSyncId(), eventTimeStamp)) {
				_logger.debug(LoggerType.Event, "Jnotify Modify is ignored because of Echo: " + objectKey);
				return;
			}
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		S3Event event = new S3Event(S3Event.FileAction.Modify, objectKey);
		event.fileObject.isFolder = file.isDirectory();
		event.timestamp = eventTimeStamp;

		try {
			_leqHandler.addEvent(event);
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}
	}

	/*
	 * Implements interface: JNotifyListener
	 * 
	 * @see net.contentobjects.jnotify.JNotifyListener#fileDeleted(int,
	 * java.lang.String, java.lang.String)
	 */
	public void fileDeleted(int wd, String rootPath, String name) {
		String filePath = rootPath + File.separatorChar + name;
		String objectKey = "/" + Util.translateObjectKey(filePath);
		long eventTimeStamp = System.currentTimeMillis();

		_logger.debug(LoggerType.Event, "check delete echo event:" + objectKey + ", currentTime:" + eventTimeStamp);

		try {
			if (_leqHandler.isEchoEvent(S3Event.FileAction.Delete, objectKey, _cssHandler.getCurrentSyncId(), eventTimeStamp)) {
				_logger.debug(LoggerType.Event, "Jnotify Delete is ignored because of Echo: " + objectKey);
				return;
			}
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		S3Event event = new S3Event(S3Event.FileAction.Delete, objectKey);

		try {
			S3Event oldevent = _leqHandler.queryEvent(objectKey);
			if (oldevent == null) {
				FileObject obj = _soHandler.get(objectKey);
				if (obj != null) {
					event.fileObject = obj;
				}
			} else {
				event.fileObject = oldevent.fileObject;
			}
		} catch (EventException e1) {
			e1.printStackTrace();
		} catch (ObjectException e) {
			e.printStackTrace();
		}

		event.timestamp = eventTimeStamp;

		_logger.debug(LoggerType.Event, "Jnotify Delete:" + objectKey);

		try {
			_leqHandler.addEvent(event);
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		File file = new File(filePath);
		// Resolve move folder but no delete files events, event time need to
		// before folder event
		if (!file.exists() && event.fileObject.isFolder) {

			// remove events from local event queue
			try {
				_logger.debug(LoggerType.Event, "Auto-remove events due to delete folder:" + objectKey);
				_leqHandler.removeEvents(objectKey, eventTimeStamp);
			} catch (EventException e) {
				_logger.error(LoggerType.Event, e.getMessage());
			}

			// Then, add delete events for server objects to local event queue
			try {
				List<FileObject> objectKeys = _soHandler.getObjectKeys(objectKey);

				for (int i = 0; i < objectKeys.size(); i++) {
					S3Event deleteEvent = new S3Event(S3Event.FileAction.Delete, objectKey);
					deleteEvent.fileObject = objectKeys.get(i);
					deleteEvent.timestamp = eventTimeStamp - 100;

					try {
						_logger.debug(LoggerType.Event, "Auto-add delete events due to delete folder:" + objectKey);
						_leqHandler.addEvent(deleteEvent);
					} catch (EventException e) {
						_logger.error(LoggerType.Event, e.getMessage());
					}
				}
			} catch (ObjectException e) {
				_logger.error(LoggerType.Event, e.getMessage());
			}

		}
	}

	/*
	 * Implements interface: JNotifyListener
	 * 
	 * @see net.contentobjects.jnotify.JNotifyListener#fileCreated(int,
	 * java.lang.String, java.lang.String)
	 */
	public void fileCreated(int wd, String rootPath, String name) {
		String filePath = rootPath + File.separatorChar + name;
		String objectKey = "/" + Util.translateObjectKey(filePath);
		long eventTimeStamp = System.currentTimeMillis();

		_logger.debug(LoggerType.Event, "check create echo event:" + objectKey + ", currentTime:" + eventTimeStamp);

		try {
			if (_leqHandler.isEchoEvent(S3Event.FileAction.Create, objectKey, _cssHandler.getCurrentSyncId(), eventTimeStamp)) {
				_logger.debug(LoggerType.Event, "Jnotify Create is ignored because of Echo: " + objectKey);
				return;
			}
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		File file = new File(filePath);
		S3Event event = new S3Event(S3Event.FileAction.Create, objectKey);

		event.fileObject.isFolder = file.isDirectory();
		event.timestamp = eventTimeStamp;

		try {
			_leqHandler.addEvent(event);
			_logger.debug(LoggerType.Event, "Jnotify Create: " + objectKey + " SyncID: " + _cssHandler.getCurrentSyncId() + " for " + filePath);
		} catch (EventException e) {
			_logger.error(LoggerType.Event, e.getMessage());
		}

		// Resolve read only folder would not give create events
		if (file.exists() && file.isDirectory()) {
			Map<String, File> subFolderMap = FileUtil.collectFiles(filePath);

			for (String path : subFolderMap.keySet()) {
				String eventkey = "/" + Util.translateObjectKey(path);
				try {
					S3Event oldEvent = _leqHandler.queryEvent(eventkey);
					if (oldEvent == null) {
						File subFile = new File(path);

						S3Event subEvent = new S3Event(S3Event.FileAction.Create, eventkey);
						subEvent.fileObject.isFolder = subFile.isDirectory();
						subEvent.timestamp = eventTimeStamp;

						_logger.debug(LoggerType.Event, "Jnotify Auto-Create:" + eventkey);
						_leqHandler.addEvent(subEvent);
					}
				} catch (EventException ex) {
					_logger.error(LoggerType.Event, "JNotify create folder's sub event failed:" + eventkey);
				}
			}
		}
	}
}
