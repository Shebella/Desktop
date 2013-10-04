package org.itri.ccma.safebox;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.itri.ccma.safebox.db.EventException;
import org.itri.ccma.safebox.db.FileObject;
import org.itri.ccma.safebox.db.LocalQueueHandler;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.db.ServerObjectHandler;
import org.itri.ccma.safebox.db.ServerQueueHandler;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.SafeboxException;
import org.itri.ccma.safebox.util.Util;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;

public class EventSyncWorker implements Runnable {
	public static enum EVENT_TYPE {
		SERVER, LOCAL, FULL_SYNC
	}

	private static final String _BACKUP_SUFFIX = "_Copy";

	private LoggerHandler _logger = LoggerHandler.getInstance();
	private LocalQueueHandler _leqHandler = LocalQueueHandler.getInstance();
	private ServerQueueHandler _seqHandler = ServerQueueHandler.getInstance();
	private ServerObjectHandler _soHandler = ServerObjectHandler.getInstance();
	private S3Assist _s3Assist = null;
	private CSSHandler _cssHandler = CSSHandler.getInstance();
	private EVENT_TYPE _eventType = null;
	private S3Event _s3Event = null;
	private File _localFile = null;
	private int threadNum = 0;
	private int _isSuccess = -1;
	private String executeMethod = "";

	public EventSyncWorker(S3Event event, S3Assist s3Assist, final EVENT_TYPE eventType) {
		_s3Event = event;
		_s3Assist = s3Assist;
		_eventType = eventType;
	}

	private boolean doDirChecking() {
		boolean isDone = true;
		File fileParent = _localFile.getParentFile();

		if (fileParent == null) {
			_logger.error(LoggerType.Event, "ESW Error path: " + Util.translateLocalPath(_s3Event.fileObject.objectKey));
			return false;
		}

		if (S3Event.FileAction.Delete != _s3Event.fileAction) {
			if (!fileParent.exists())
				isDone = false;
		}

		return isDone;
	}

	private void processEvent() throws ObjectException, SQLException, EventException {
		switch (_eventType) {
			case SERVER:
			case FULL_SYNC: // FullSync is also a kind of server event.
				if (_s3Event.fileObject.isFolder) {
					if (S3Event.FileAction.Delete == _s3Event.fileAction) {
						_isSuccess = processServerDirDelete();
						this.setExecuteMethod("processServerDirDelete");
					} else if (S3Event.FileAction.Rename == _s3Event.fileAction) {
						_isSuccess = processServerDirRename();
						this.setExecuteMethod("processServerDirRename");
					} else if (S3Event.FileAction.Create == _s3Event.fileAction) {
						_isSuccess = processServerDirCreate();
						this.setExecuteMethod("processServerDirCreate");
					} else {
						_logger.error(LoggerHandler.LoggerType.Event, "Undefined file action " + _s3Event.fileAction + " for objectKey: " + _s3Event.fileObject.objectKey);
					}
				} else {
					if (S3Event.FileAction.Delete == _s3Event.fileAction) {
						_isSuccess = processServerDelete();
						this.setExecuteMethod("processServerDelete");
					} else if (S3Event.FileAction.Rename == _s3Event.fileAction) {
						_isSuccess = processServerRename();
						this.setExecuteMethod("processServerRename");
					} else if (S3Event.FileAction.Create == _s3Event.fileAction || S3Event.FileAction.Modify == _s3Event.fileAction) {
						_isSuccess = processServerCreate();
						this.setExecuteMethod("processServerCreate");
					} else {
						_logger.error(LoggerHandler.LoggerType.Event, "Undefined file action " + _s3Event.fileAction + " for objectKey: " + _s3Event.fileObject.objectKey);
					}
				} // fullsync is also server event

				break;
			case LOCAL:
				if (S3Event.FileAction.Delete == _s3Event.fileAction) {
					_isSuccess = processLocalDelete();
					this.setExecuteMethod("processLocalDelete");
				} else if (S3Event.FileAction.Rename == _s3Event.fileAction) {
					_isSuccess = processLocalRename();
					this.setExecuteMethod("processLocalRename");
				} else if (S3Event.FileAction.Create == _s3Event.fileAction || S3Event.FileAction.Modify == _s3Event.fileAction) {
					_isSuccess = processLocalCreate();
					this.setExecuteMethod("processLocalCreate");
				} else {
					_logger.error(LoggerHandler.LoggerType.Event, "Undefined file action " + _s3Event.fileAction + " for objectKey: " + _s3Event.fileObject.objectKey);
				}

				break;
		}
	}

	@Override
	public void run() {
		_localFile = new File(Util.translateLocalPath(_s3Event.fileObject.objectKey));

		if (!doDirChecking())
			_isSuccess = -100;

		try {
			if (_isSuccess != -100)
				processEvent();
		} catch (ObjectException e) {
		} catch (SQLException e) {
		} catch (EventException e) {
		}

		switch (_eventType) {
			case SERVER:
			case FULL_SYNC: // FullSync is also a kind of server event.
				// Sleep 1 seconds to let JNotify events triggered by server
				// event can be filtered by isEcho.
				// Server event will remain in Process state.
				Util.Sleep(1000);
				try {
					if (_isSuccess > 0) {
						_seqHandler.updateEvent(_s3Event, S3Event.EventState.Success);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW server event success with result code: " + _isSuccess);
					} else {
						if (_s3Event.retry >= ServerQueueHandler.MAX_RETRY)
							_seqHandler.updateEvent(_s3Event, S3Event.EventState.Error);
						else
							_seqHandler.updateEvent(_s3Event, S3Event.EventState.Fail);

						_logger.error(LoggerHandler.LoggerType.Event, "ESW server event fail with result code: " + _isSuccess);
					}
				} catch (EventException e) {
					_logger.error(LoggerHandler.LoggerType.Event, "ESW server event state update failed: " + _s3Event.fileObject.objectKey);
				}

				break;
			case LOCAL:
				if (_isSuccess < 0) {
					try {
						if (_s3Event.retry >= LocalQueueHandler.MAX_RETRY)
							_leqHandler.updateEvent(_s3Event, S3Event.EventState.Error);
						else
							_leqHandler.updateEvent(_s3Event, S3Event.EventState.Fail);
						
					} catch (EventException e) {
						_logger.error(LoggerHandler.LoggerType.Event, "local event mark fail error: " + e.getMessage());
					}

					_logger.error(LoggerHandler.LoggerType.Event, "ESW local event fail with result code: " + _isSuccess);
				}
				break;
		}
	}

	/*
	 * sC(dA)
	 */
	private int processServerDirCreate() throws ObjectException, SQLException {
		int intResult = -1;
		String localPath = Util.translateLocalPath(_s3Event.fileObject.objectKey);
		File localFile = new File(localPath);

		if (localFile.exists()) {
			// 如果 Local A 已經存在, 就將 Local A 變成 A_Copy, 然後在建立一個新的 A
			String localNewName = FileUtil.getBackupFolderName(localPath);			
			File localNewFile = new File(localNewName);

			try {
				FileUtils.moveDirectory(localFile, localNewFile);
			} catch (IOException e) {
				_logger.error(LoggerHandler.LoggerType.Event, "ESW SC(dA) move A to A_Copy(" + _s3Event.fileObject.objectKey + ") fail: " + e.getMessage());
				return -9;
			}

			if (FileUtil.makeDir(localPath)) {
				_soHandler.add(_s3Event.fileObject);
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW SC(dA) done with l(dA) existed, make local as a copy. " + _s3Event.fileObject.objectKey);
				intResult = 2;
			} else {
				intResult = -2;
				_logger.error(LoggerHandler.LoggerType.Event, "ESW SC(dA) error(" + intResult + "). " + _s3Event.fileObject.objectKey);
			}
		} else {
			// 直接建立該目錄
			if (FileUtil.makeDir(localPath)) {
				_soHandler.add(_s3Event.fileObject);
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW SC(dA) done. " + _s3Event.fileObject.objectKey);
				intResult = 1;
			} else {
				intResult = -1;
				_logger.error(LoggerHandler.LoggerType.Event, "ESW SC(dA) error(" + intResult + "). " + _s3Event.fileObject.objectKey);
			}
		}

		return intResult;
	}

	/*
	 * sR(dA->dB)
	 */
	private int processServerDirRename() {
		int intResult = -1;
		File localFileOld = new File(Util.translateLocalPath(_s3Event.oldObjectKey));
		File localFileNew = new File(Util.translateLocalPath(_s3Event.fileObject.objectKey));
		try {
			// Has dB, need to rename local dB -> dB_Copy
			if (localFileNew.exists()) {
				File localFileNewCopy = new File(localFileNew.getAbsolutePath() + _BACKUP_SUFFIX);
				FileUtils.moveDirectory(localFileNew, localFileNewCopy);
			}
			// Rename dA -> dB or making dB
			if (localFileOld.exists()){
				FileUtils.moveDirectory(localFileOld, localFileNew);
				_leqHandler.renameEvents(_s3Event.oldObjectKey, _s3Event.fileObject.objectKey, System.currentTimeMillis());
			}				
			else
				FileUtil.makeDir(localFileNew.getAbsolutePath());
			
			_soHandler.renameObjects(_s3Event.oldObjectKey, _s3Event.fileObject.objectKey);
			_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR(dA->dB) done for " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
			intResult = 1;
		} catch (IOException e) {
			_logger.error(LoggerHandler.LoggerType.Event, "Remove existed event fail: " + e.getMessage());
			intResult = -9;
		} catch (ObjectException e) {
			_logger.error(LoggerHandler.LoggerType.Event, "Rename server object fail: " + e.getMessage());
			intResult = -1;
		} catch (EventException e) {
			_logger.error(LoggerHandler.LoggerType.Event, "Rename local event fail: " + e.getMessage());
			intResult = -1;
		}

		return intResult;
	}

	/*
	 * sD(dA)
	 */
	private int processServerDirDelete() throws ObjectException, SQLException {
		int intResult = 1;
		File localFile = new File(Util.translateLocalPath(_s3Event.fileObject.objectKey));
		// /////////////////////////////////////////////////////////////////////
		// Has A
		if (localFile.exists()) {
			try {		
//				FileUtils.forceDelete(localFile);
				FileUtils.deleteDirectory(localFile);
//				_soHandler.remove(_s3Event.fileObject.objectKey);
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(dA) done: " + _s3Event.fileObject.objectKey);
				intResult = 2;
			} catch (IOException e) {
				_logger.error(LoggerHandler.LoggerType.Event, "ESW sD(dA) fail: " + e.getMessage());
				intResult = -2;
			}
		} else {
			_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(dA) with no dA done:" + _s3Event.fileObject.objectKey);
		}

		//Make sure db has no this folder
		if (intResult > 0) {
			_soHandler.remove(_s3Event.fileObject.objectKey);
		}
		
		return intResult;
	}

	/*
	 * sC(A) / sM(A)
	 */
	private int processServerCreate() throws ObjectException, SQLException {
		int intResult = -1;
		String localPath = Util.translateLocalPath(_s3Event.fileObject.objectKey);
		File localFile = new File(localPath);

		intResult = getObject(localFile, _s3Event.fileObject.objectKey);
		// getObject 失敗就表示這個 server event 失敗了.
		if (intResult < 0)
			return intResult;

		// /////////////////////////////////////////////////////////////////////
		// getObject 成功還要再處理 local 會有 conflict 的情形發生.
		// Conflict Handling, 主要檢查項目有以下兩點:
		// Check 1. 檢查是否有任何 local event, 有表示有 conflict 要處理.
		// Rule: Delete any local Create/Modify/Delete event
		S3Event event = null;

		try {
			event = _leqHandler.queryEvent(_s3Event.fileObject.objectKey);
		} catch (EventException e) {
			// Exception can be ignore here.
		}
		// If find any local event, conflict happened.
		if (event != null) {
			try {
				_leqHandler.removeEvent(event.fileObject.objectKey, event.timestamp);
			} catch (EventException e) {
				_logger.error(LoggerHandler.LoggerType.Event, "Remove existed event fail: " + e.getMessage());
			}

			_logger.debug(LoggerHandler.LoggerType.Event, "ESW sC/M(A) done with local event(" + event.fileAction + ") removed. " + _s3Event.fileObject.objectKey);
		}
		// /////////////////////////////////////////////////////////////////////
		// Check 2. local rename event 必須另外檢查
		// Rule: lR(A->B) need to change to lC(B)
		try {
			event = _leqHandler.queryRenameEvent(_s3Event.fileObject.objectKey);
		} catch (EventException e) {
			// Exception can be ignore here.
		}
		// If find any rename event, conflict happened.
		if (event != null) {
			try {
				_leqHandler.removeEvent(event.fileObject.objectKey, event.timestamp);

				event.oldObjectKey = "";
				event.fileAction = S3Event.FileAction.Create;

				_leqHandler.addEvent(event);
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW sC/M(A) done with lR(A->B) change to lC(B). " + _s3Event.fileObject.objectKey);
			} catch (EventException e) {
				_logger.error(LoggerHandler.LoggerType.Event, "Remove existed event fail: " + e.getMessage());
			}
		}

		return intResult;
	}

	/*
	 * sR(A->B) |No A |Has A 
	 * --------- No B | 1 | 3
	 * -------- Has B | 2 & 5 | 4 
	 * -------- Has B' | 6
	 */
	private int processServerRename() throws ObjectException, SQLException {
		int intResult = -1;
		File localFileOld = new File(Util.translateLocalPath(_s3Event.oldObjectKey));
		File localFileNew = new File(Util.translateLocalPath(_s3Event.fileObject.objectKey));
		FileObject fileObjectOld = _soHandler.get(_s3Event.oldObjectKey);
		// /////////////////////////////////////////////////////////////////////
		// Has B
		if (localFileNew.exists()) {
			int intGetResult = getObject(localFileNew, _s3Event.fileObject.objectKey);
			// Is B
			if (intGetResult == 1) {
				// Has A
				if (localFileOld.exists() && fileObjectOld != null) {
					// Is A
					if (fileObjectOld.MD5.equals(FileUtil.getMD5(localFileOld))) {
						deleteLocal(localFileOld);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[A,B] & delete A. " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
					}
					// Is A'
					else {
						_soHandler.remove(_s3Event.oldObjectKey);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[A',B] & delete dbA. " + _s3Event.oldObjectKey + " -> "
						        + _s3Event.fileObject.objectKey);
					}

					intResult = 4;
				}
				// No A
				else if (!localFileOld.exists() && fileObjectOld != null) {
					_soHandler.remove(_s3Event.oldObjectKey);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[,B] & delete dbA. " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
					intResult = 2;
				} else {
					_logger.error(LoggerHandler.LoggerType.Event, "ESW sR[A,B] fail with l[,B] & dbA not found. " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
					intResult = 5;
				}
			}
			// Is B'
			else if (intGetResult == 2) {
				try {
					S3Event event = _leqHandler.queryEvent(_s3Event.fileObject.objectKey);

					if (event != null) {
						_leqHandler.removeEvent(event.fileObject.objectKey, event.timestamp);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[X,B'] & delete B event(" + event.fileAction + "). " + _s3Event.oldObjectKey + " -> "
						        + _s3Event.fileObject.objectKey);
					} else
						_logger.error(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[X,B'] but no B event found. " + _s3Event.oldObjectKey + " -> "
						        + _s3Event.fileObject.objectKey);

					deleteLocal(localFileOld);
				} catch (EventException e) {
					_logger.error(LoggerHandler.LoggerType.Event, "Remove existed event fail: " + e.getMessage());
				}

				_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[X,B'] & delete A. " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
				intResult = 6;
			} else
				intResult = -3;
		}
		// /////////////////////////////////////////////////////////////////////
		// No B
		else {
			// Has A
			if (localFileOld.exists()) {
				String localFileMD5 = FileUtil.getMD5(localFileOld);
				// Same A, do Rename(A -> B)
				if (fileObjectOld.MD5.equals(localFileMD5)) {
					// 代表兩邊內容一模一樣，所以跟著做 RENAME 即可
					try {
						FileUtils.moveFile(localFileOld, localFileNew);
						_soHandler.renameObject(_s3Event.oldObjectKey, _s3Event.fileObject.objectKey);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[A,] & lR(A->B). " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
						intResult = 3;
					} catch (IOException e) {
						_logger.error(LoggerHandler.LoggerType.Event, "ESW sR[A,B] & l[A,] rename failed: " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
						intResult = -3;
					}
				}
				// A', do Get(B)
				else {
					if (getObject(null, _s3Event.fileObject.objectKey) > 0) {
						_soHandler.remove(_s3Event.oldObjectKey);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[A',] & Get(B) & delete dbA. " + _s3Event.oldObjectKey + " -> "
						        + _s3Event.fileObject.objectKey);
						intResult = 3;
					} else {
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] fail with l[A',] & Get(B). " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
						intResult = -3;
					}
				}
			}
			// No A
			else {
				if (getObject(null, _s3Event.fileObject.objectKey) > 0) {
					_soHandler.remove(_s3Event.oldObjectKey);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] done with l[,] & Get(B) & delete dbA. " + _s3Event.oldObjectKey + " -> "
					        + _s3Event.fileObject.objectKey);
					intResult = 1;
				} else {
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW sR[A,B] fail with l[,] & Get(B). " + _s3Event.oldObjectKey + " -> " + _s3Event.fileObject.objectKey);
					intResult = -1;
				}
			}
		}

		return intResult;
	}

	/*
	 * sD(A)
	 */
	private int processServerDelete() throws ObjectException, SQLException {
		int intResult = -1;
		File localFile = new File(Util.translateLocalPath(_s3Event.fileObject.objectKey));
		// /////////////////////////////////////////////////////////////////////
		// Has A
		if (localFile.exists()) {
			// Local A 存在, 比對 MD5 確認是 A or A', 只有是 A 才能作 Delete
			FileObject fileObject = _soHandler.get(_s3Event.fileObject.objectKey);

			if (null == fileObject) {
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(A) error with no dbA. " + _s3Event.fileObject.objectKey);
				intResult = -2;
			} else {
				String localFileMd5 = FileUtil.getMD5(localFile);
				if (localFileMd5.equals(fileObject.MD5)) {
					// local的檔案內容與前次remote記錄的內容一樣, 所以直接刪除檔案
					deleteLocal(localFile);
					_soHandler.remove(_s3Event.fileObject.objectKey);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(A) done with lA. " + _s3Event.fileObject.objectKey);
					intResult = 1;
				} else {
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(A) ignore with lA'. " + _s3Event.fileObject.objectKey);
					intResult = 3;
				}
			}
		}
		// /////////////////////////////////////////////////////////////////////
		// No A
		else {
			S3Event event = null;
			try {
				event = _leqHandler.queryEvent(_s3Event.fileObject.objectKey);
			} catch (EventException e) {
				// Exception can be ignore here.
			}

			if (event == null)
				_logger.error(LoggerHandler.LoggerType.Event, "ESW sD(A) fail with no local event. " + _s3Event.fileObject.objectKey);
			else if (event.fileAction.equals(S3Event.FileAction.Delete)) {
				try {
					_leqHandler.removeEvent(event.fileObject.objectKey, event.timestamp);
				} catch (EventException e) {
					_logger.error(LoggerHandler.LoggerType.Event, "Remove existed event fail: " + e.getMessage());
				}

				_logger.debug(LoggerHandler.LoggerType.Event, "ESW sD(A) done with local event deleted. " + _s3Event.fileObject.objectKey);
			} else {
				_logger.error(LoggerHandler.LoggerType.Event, "ESW sD(A) fail with local event (" + event.fileAction + ") found. " + _s3Event.fileObject.objectKey);
			}
			// local沒有這個檔案了，所以只需要把 snapshot 的紀錄刪除即可
			_soHandler.remove(_s3Event.fileObject.objectKey);
			intResult = 1;
		}

		return intResult;
	}

	/*
	 * lC(A)
	 */
	private int processLocalCreate() throws ObjectException, SQLException, EventException {
		int intResult = -1;

		if (putObject(_localFile, _s3Event.fileObject.objectKey)) {
			_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
			intResult = 1;
		} else {
			_logger.error(LoggerHandler.LoggerType.Event, "put object failed: " + _s3Event.fileObject.objectKey);
			intResult = -2;
		}

		return intResult;
	}

	/*
	 * lR(A->B) |No dbA |Has dbA ------------------------- No dbB | 1 | 3
	 * ------------------------- Has dbB | 2 | 4 & 5
	 */
	private int processLocalRename() throws SQLException, EventException, ObjectException {
		int intResult = -1;
		FileObject fileObjectOld = _soHandler.get(_s3Event.oldObjectKey);
		FileObject fileObjectNew = _soHandler.get(_s3Event.fileObject.objectKey);
		// /////////////////////////////////////////////////////////////////////
		// No dbA
		if (fileObjectOld == null) {
			// No dbB
			if (fileObjectNew == null) {
				if (putObject(_localFile, _s3Event.fileObject.objectKey)) {
					_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW lR[,] put(B) done: " + _s3Event.fileObject.objectKey);
					intResult = 1;
				} else {
					_logger.error(LoggerHandler.LoggerType.Event, "ESW lR[,] put(B) fail: " + _s3Event.fileObject.objectKey);
					intResult = -1;
				}
			}
			// Has dbB
			else {
				_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
				_logger.debug(LoggerHandler.LoggerType.Event, "ESW lR[,B] already has (B): " + _s3Event.fileObject.objectKey);
				intResult = 2;
			}
		}
		// /////////////////////////////////////////////////////////////////////
		// Has dbA
		else {
			// No dbB
			if (fileObjectNew == null) {
				// Normal rename
				try {
					if (_cssHandler.renameObject(_cssHandler.getCurrentSyncId(), _s3Event.oldObjectKey, _s3Event.fileObject.objectKey)) {

						if (fileObjectOld.isFolder)
							_soHandler.renameObjects(_s3Event.oldObjectKey, _s3Event.fileObject.objectKey);
						else
							_soHandler.renameObject(_s3Event.oldObjectKey, _s3Event.fileObject.objectKey);

						_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
						_logger.debug(LoggerHandler.LoggerType.Event, "ESW lR[dbA,] lR(A->B) done: " + _s3Event.fileObject.objectKey);
						intResult = 3;
					} else {
						_logger.error(LoggerHandler.LoggerType.Event, "ESW lR[dbA,] lR(A->B) fail: " + _s3Event.fileObject.objectKey);
						intResult = -3;
					}
				} catch (SafeboxException se) {
					Error(se.getMessage());
				}
			}
			// Has dbB
			else {
				File localFileOld = new File(Util.translateLocalPath(_s3Event.oldObjectKey));
				// Delete local file A & event in LEQ
				// dbA 不移除是因為要交給要交給 lD(A) event 去處理.
				if (deleteLocal(localFileOld)) {
					_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW lR[dbA,dbB] done with delete A: " + _s3Event.fileObject.objectKey);
					intResult = 4;
				}
				// Delete local file A fail, but A is gone in the end.
				else if (!localFileOld.exists()) {
					_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
					_logger.debug(LoggerHandler.LoggerType.Event, "ESW lR[dbA,dbB] done with lA not found: " + _s3Event.fileObject.objectKey);
					intResult = 5;
				} else {
					intResult = -4;
				}
			}
		}

		return intResult;
	}

	/*
	 * lD(A)
	 */
	private int processLocalDelete() throws ObjectException, SQLException, EventException {
		FileObject fileObject = _soHandler.get(_s3Event.fileObject.objectKey);

		if (null != fileObject) {
			// 檔案都不在了，所以如實刪除 EVENT 包括 SERVER 上的
			if (_s3Assist.deletingObject(_s3Event.fileObject.objectKey)) {
				_soHandler.remove(_s3Event.fileObject.objectKey);
				_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);

				return 1;
			}
		}
		// Added by Ireta at 2013/06/11
		// Local 檔案不存在, 而且 DB 也沒有, 表示 Server Event 處理時已經將該檔案砍掉,
		// Local Event 還是會存在表示 JNotify Delete Event 是在 Server Event Sync "完成很久"
		// 才觸發,
		// 而這個觸發的 Delete Event 沒有被判斷成 Echo Event 而留在 LEQ !!
		// 這應該要避免不要讓他發生 (目前有機率會發生, 很久的原因暫時不明).
		else if (!_localFile.exists() && null == fileObject) {
			_leqHandler.removeEvent(_s3Event.fileObject.objectKey, _s3Event.timestamp);
			return 2;
		}

		return -1;
	}

	/*
	 * 當 Local 決定要進行 putObject, 應該重新抓一次新的 Server 檔案資訊，再比一次 MD5, 確定 Local 與
	 * Server 檔案內容不一致，再把 Local 檔案送上去.
	 */
	private boolean putObject(File localFile, String objectKey) throws ObjectException {
		boolean isDone = false;
		String localFileMD5 = FileUtil.getMD5(localFile);
		StorageObject serverObject = null;
		try {
			serverObject = _s3Assist.downloadingObjectDetails(Util.urlEncode(objectKey));
		} catch (SafeboxException se) {
			Error(se.getMessage());
			return isDone;
		}

		FileObject fileObject = new FileObject();
		fileObject.objectKey = objectKey;
		fileObject.MD5 = localFileMD5;
		fileObject.isFolder = localFile.isDirectory();
		fileObject.size = localFile.length();
		fileObject.modifiedDate = localFile.lastModified();

		// /////////////////////////////////////////////////////////////////////
		// Step 1. 先檢查 server 有沒有相同檔案
		if (null != serverObject && null != serverObject.getETag())
			if (serverObject.getETag().equals(localFileMD5)) {
				// Server Object 存在, 而且跟 local 檔案 MD5 一模一樣, 為保險起見,
				// 仍然更新 DB Server Object, 避免因為程式突然 kill 造成前一次 sync 資料來不及備份到
				// HardDisk.
				_soHandler.add(fileObject);
				_logger.debug(LoggerHandler.LoggerType.Event, "putObject() already has " + objectKey);
				return true; // It means server already has this file object.
			}
		// /////////////////////////////////////////////////////////////////////
		// Step 2. 嘗試 putObject
		if (!FileUtil.isFileLocked(localFile)) {
			isDone = _s3Assist.uploadingObject("", Util.translateLocalPath(objectKey));
			_logger.debug(LoggerHandler.LoggerType.Event, "putObject() isDone: " + isDone + " for " + objectKey);
		}
		// /////////////////////////////////////////////////////////////////////
		// Step 3. 如果 putObject 成功, 即更新 Local DB's Snapshot
		if (isDone)
			_soHandler.add(fileObject);

		return isDone;
	}

	/*
	 * Get object from server. return 1 : Local has the same server object. 2 :
	 * Get server object done. -1 : S3 getObject fail. -2 : Don't have server
	 * object.
	 */
	private int getObject(File localFile, String objectKey) throws ObjectException {
		StorageObject serverObject = null;
		try {
			serverObject = _s3Assist.downloadingObjectDetails(Util.urlEncode(objectKey));
		} catch (SafeboxException se) {
			Error(se.getMessage());
		}

		if (null == serverObject) {
			_logger.error(LoggerHandler.LoggerType.Event, "getObject() server object not found: " + objectKey);
			return -2;
		}

		FileObject fileObject = new FileObject();
		fileObject.objectKey = objectKey;
		fileObject.MD5 = serverObject.getETag();
		fileObject.isFolder = _s3Event.fileObject.isFolder;
		fileObject.size = serverObject.getContentLength();
		try {
			fileObject.modifiedDate = _s3Assist.getObjectTime((S3Object) serverObject);
		} catch (SafeboxException e) {
			_logger.error(LoggerHandler.LoggerType.CSS, "getObjectTime() is failed:" + objectKey);
		}

		if (null != localFile && null != serverObject.getETag()) {
			String localFileMD5 = FileUtil.getMD5(localFile);

			if (serverObject.getETag().equals(localFileMD5)) {
				// 代表 LOCAL 的新檔案內容恰恰好就是 SERVER 的新檔案內容，所以什麼事都不用做只要更新 LOCAL
				// SNAPSHOT
				_soHandler.add(fileObject);
				_logger.debug(LoggerHandler.LoggerType.Event, "getObject() already has " + objectKey);
				return 1;
			}
		}

		if (_s3Assist.downloadingObject(objectKey)) {
			_soHandler.add(fileObject);
			_logger.debug(LoggerHandler.LoggerType.Event, "getObject() Success isFolder: " + _s3Event.fileObject.isFolder + " for " + objectKey);
			return 2;
		}

		_logger.debug(LoggerHandler.LoggerType.Event, "getObject() Fail isFolder: " + _s3Event.fileObject.isFolder + " for " + objectKey);
		return -1;
	}

	private boolean deleteLocal(File deleteFile) {
		boolean isDone = false;

		try {
			// Deletes files and folders. If file/folder does not exist then no
			// exception is thrown.
			FileUtil.deleteFile(deleteFile);
			if (deleteFile.exists()) {
				deleteFile.setWritable(true);
				if (!deleteFile.delete()) {
					return deleteLocal(deleteFile);
				}
			}

			_logger.debug(LoggerHandler.LoggerType.Event, "deleted file: " + deleteFile.getPath());
			isDone = true;
		} catch (Exception e) {
			_logger.error(LoggerHandler.LoggerType.Event, "delete file error: " + e.getMessage());
		}

		return isDone;
	}

	public int getThreadNum() {
		return threadNum;
	}

	public void setThreadNum(int threadNo) {
		this.threadNum = threadNo;
	}

	public S3Event getS3Event() {
		return _s3Event;
	}

	public boolean isSuccess() {
		return _isSuccess < 0 ? false : true;
	}

	public String getExecuteMethod() {
		return executeMethod;
	}

	public void setExecuteMethod(String executeMethod) {
		this.executeMethod = executeMethod;
	}

	public void Error(Throwable t) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.ERR, Util.getStackTrace(t));
	}

	public void Error(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.ERR, msg);
	}

	public void Info(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.INFO, msg);
	}

	public void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, msg);
	}
}
