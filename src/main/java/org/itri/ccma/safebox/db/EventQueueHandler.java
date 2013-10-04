package org.itri.ccma.safebox.db;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.Util;

class EventQueueHandler {
	public static final int MAX_RETRY = 10;
	protected static final long MAX_SYNC_SIZE = 134217728; // 128M
	protected static final int MAX_NUM_EVENT = 300;
	protected static final int MAX_INTERLEAVE_TIME = 1000;
	protected static final String LOCAL_TABLE = "LocalEvents";
	protected static final String SERVER_TABLE = "ServerEvents";

	protected volatile ReentrantReadWriteLock lock = null;
	protected String tableName = "";
	protected Connection connection = null;

	public EventQueueHandler() {
		lock = new ReentrantReadWriteLock(false);
	}

	public void initConnection() {
		connection = DataBaseHandler.getInstance().getConnection();
	}

	protected S3Event getBarrierEvent() throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			result = queryBarrierEvent();

			if (result == null)
				return result;

			PreparedStatement stmtUpdate = null;

			if (result.state.equals(S3Event.EventState.Fail)) {
				result.retry++;
			}

			if (tableName.equals(SERVER_TABLE)) {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and syncID=? and state in (?,?,?)");
				stmtUpdate.setString(1, S3Event.EventState.Process.toString());
				stmtUpdate.setLong(2, result.retry);
				stmtUpdate.setString(3, result.fileObject.objectKey);
				stmtUpdate.setLong(4, result.syncID);
				stmtUpdate.setString(5, S3Event.EventState.New.toString());
				stmtUpdate.setString(6, S3Event.EventState.Process.toString());
				stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
			} else {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and timestamp=? and state in (?,?,?)");
				stmtUpdate.setString(1, S3Event.EventState.Process.toString());
				stmtUpdate.setLong(2, result.retry);
				stmtUpdate.setString(3, result.fileObject.objectKey);
				stmtUpdate.setLong(4, result.timestamp);
				stmtUpdate.setString(5, S3Event.EventState.New.toString());
				stmtUpdate.setString(6, S3Event.EventState.Process.toString());
				stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
			}

			stmtUpdate.executeUpdate();

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

		} catch (SQLException e) {
			Error("Get barrier event failed by SQLException:" + e.getMessage());
		}

		return result;
	}

	protected S3Event queryBarrierEvent() throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			if (tableName.equals(SERVER_TABLE)) {
				stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " where fileAction=? and isFolder=? and state in (?,?,?) order by syncID ASC limit 1");
			} else {
				stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " where fileAction=? and isFolder=? and state in (?,?,?) order by timestamp ASC limit 1");
			}
			stmtSelect.setString(1, S3Event.FileAction.Rename.toString());
			stmtSelect.setInt(2, 1);
			stmtSelect.setString(3, S3Event.EventState.New.toString());
			stmtSelect.setString(4, S3Event.EventState.Process.toString());
			stmtSelect.setString(5, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = true;
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");

				if (tableName.equals(SERVER_TABLE)) {
					result.syncID = rs.getLong("syncID");
				} else {
					result.timestamp = rs.getLong("timestamp");
				}
			}

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query barrier event failed by SQLException:" + e.getMessage());
		}

		return result;
	}

	protected List<S3Event> getEventsBeforeBarrier() throws EventException {
		List<S3Event> result = new ArrayList<S3Event>();

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtUpdate = null;
			PreparedStatement stmtSelect = null;

			S3Event barrierEvent = queryBarrierEvent();
			if (barrierEvent == null)
				return result;

			if (tableName.equals(SERVER_TABLE)) {
				stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName
				        + " where syncID<? and state in (?,?,?) order by isFolder DESC,syncID ASC,objectKey ASC limit ?");
				stmtSelect.setLong(1, barrierEvent.syncID);
				stmtSelect.setString(2, S3Event.EventState.New.toString());
				stmtSelect.setString(3, S3Event.EventState.Fail.toString());
				stmtSelect.setString(4, S3Event.EventState.Process.toString());
				stmtSelect.setInt(5, MAX_NUM_EVENT);
			} else {
				stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName
				        + " where timestamp<? and state in (?,?,?) order by isFolder DESC,timestamp ASC,objectKey ASC limit ?");
				stmtSelect.setLong(1, barrierEvent.timestamp);
				stmtSelect.setString(2, S3Event.EventState.New.toString());
				stmtSelect.setString(3, S3Event.EventState.Fail.toString());
				stmtSelect.setString(4, S3Event.EventState.Process.toString());
				stmtSelect.setInt(5, MAX_NUM_EVENT);
			}

			ResultSet rs = stmtSelect.executeQuery();

			/*
			 * long currentSize = 0;
			 * 
			 * while (rs.next() && currentSize < MAX_SYNC_SIZE) { S3Event event
			 * = new
			 * S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")),
			 * rs.getString("objectKey")); event.fileObject.objectKey =
			 * rs.getString("objectKey"); event.fileObject.isFolder = (1 ==
			 * rs.getInt("isFolder")) ? true : false; event.fileObject.MD5 =
			 * rs.getString("MD5"); event.fileObject.modifiedDate =
			 * rs.getLong("modifiedDate"); event.fileObject.sequence =
			 * rs.getInt("sequence"); event.oldObjectKey =
			 * rs.getString("oldObjectKey"); event.state =
			 * S3Event.EventState.valueOf(rs.getString("state")); event.retry =
			 * rs.getInt("retry");
			 * 
			 * if (tableName.equals(SERVER_TABLE)) { event.syncID =
			 * rs.getLong("syncID"); } else { event.timestamp =
			 * rs.getLong("timestamp"); event.fileObject.size =
			 * rs.getLong("fileSize");
			 * 
			 * if (!event.fileObject.isFolder &&
			 * (event.fileAction.equals(S3Event.FileAction.Create) ||
			 * event.fileAction.equals(S3Event.FileAction.Modify))){ currentSize
			 * += event.fileObject.size; }
			 * 
			 * }
			 * 
			 * result.add(event);
			 * 
			 * }
			 * 
			 * 
			 * Debug("Current sync event filesize reached:"+currentSize/1048576+"MB"
			 * );
			 */

			makeEventList(rs, result);

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

			if (0 < result.size()) {
				for (S3Event event : result) {
					if (event.state.toString().equals(S3Event.EventState.Fail.toString())) {
						event.retry++;
					}

					if (tableName.equals(SERVER_TABLE)) {
						stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and syncID=? and state=?");
						stmtUpdate.setString(1, S3Event.EventState.Process.toString());
						stmtUpdate.setLong(2, event.retry);
						stmtUpdate.setString(3, event.fileObject.objectKey);
						stmtUpdate.setLong(4, event.syncID);
						stmtUpdate.setString(5, event.state.toString());
					} else {
						stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and timestamp=? and state=?");
						stmtUpdate.setString(1, S3Event.EventState.Process.toString());
						stmtUpdate.setLong(2, event.retry);
						stmtUpdate.setString(3, event.fileObject.objectKey);
						stmtUpdate.setLong(4, event.timestamp);
						stmtUpdate.setString(5, event.state.toString());
					}
					stmtUpdate.executeUpdate();
				}
			}

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

			if (null != rs) {
				rs.close();
			}

		} catch (SQLException e) {
			Error("Get events before barrier event failed by SQLException:" + e.getMessage());
		}

		return result;
	}

	private S3Event queryBarrierEvent(String objectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and fileAction=? and state in (?,?,?)");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, S3Event.FileAction.Rename.toString());
			stmtSelect.setString(3, S3Event.EventState.New.toString());
			stmtSelect.setString(4, S3Event.EventState.Process.toString());
			stmtSelect.setString(5, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = true;
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");

				if (tableName.equals(SERVER_TABLE)) {
					result.syncID = rs.getLong("syncID");
				} else {
					result.timestamp = rs.getLong("timestamp");
				}
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Private query barrier event failed:" + objectKey + " by SQLException:" + e.getMessage());
		}
		return result;
	}

	private S3Event queryFolderEvent(String objectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;
			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE (objectKey=? or oldObjectKey=?) and fileAction!=? and state in (?,?,?)");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, objectKey);
			stmtSelect.setString(3, S3Event.FileAction.Rename.toString());
			stmtSelect.setString(4, S3Event.EventState.New.toString());
			stmtSelect.setString(5, S3Event.EventState.Process.toString());
			stmtSelect.setString(6, S3Event.EventState.Fail.toString());
			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				// result = new S3Event(fileObject);
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));

				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");

				if (tableName.equals(SERVER_TABLE)) {
					result.syncID = rs.getLong("syncID");
				} else {
					result.timestamp = rs.getLong("timestamp");
				}
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query folder event failed:" + objectKey + " by SQLException:" + e.getMessage());
		}
		return result;
	}

	protected boolean consolidateFolderEvent(S3Event event) throws EventException {
		boolean result = false;
		S3Event relatedEvent = null;

		// consolidate related events without rename events
		if (event.fileAction.equals(S3Event.FileAction.Rename)) {
			relatedEvent = queryFolderEvent(event.oldObjectKey);
		} else {
			relatedEvent = queryFolderEvent(event.fileObject.objectKey);
		}

		if (null == relatedEvent) {
			// consolidate rename event with rename event
			if (event.fileAction.equals(S3Event.FileAction.Rename)) {
				relatedEvent = queryBarrierEvent(event.oldObjectKey);
				String oldKey = event.oldObjectKey;
				String newKey = event.fileObject.objectKey;

				if (relatedEvent != null) {

					Debug("Rule3 R(X-A) & R(A-B):" + event.fileObject.objectKey);

					removeEventNoLock(relatedEvent);
					event.oldObjectKey = relatedEvent.oldObjectKey;
					result = consolidateFolderEvent(event);

					if (tableName.equals(SERVER_TABLE))
						renameServerEvents(oldKey, newKey, event.syncID + 1);
					else
						renameEvents(oldKey, newKey, event.timestamp);
				} else {
					if (tableName.equals(SERVER_TABLE))
						renameServerEvents(oldKey, newKey, event.syncID + 1);
					else
						renameEvents(oldKey, newKey, event.timestamp);

					result = addConsolidatedEvent(event);
				}
			} else {
				result = addConsolidatedEvent(event);
			}
		} else if (null != relatedEvent) {
			if (event.fileAction.equals(S3Event.FileAction.Create)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Delete)) {

					Debug("Rule1 D(A) & C(A):" + event.fileObject.objectKey);

					removeEventNoLock(relatedEvent);
					result = true;
				} else {
					Error("Case1, no rule match:" + relatedEvent.toString());
				}
			} else if (event.fileAction.equals(S3Event.FileAction.Rename)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Create)) {
					String oldkey = event.oldObjectKey;
					String newKey = event.fileObject.objectKey;

					Debug("Rule3 C(A) & R(A-B):" + event.fileObject.objectKey);

					removeEventNoLock(relatedEvent);
					event.oldObjectKey = "";
					event.fileAction = S3Event.FileAction.Create;
					result = consolidateFolderEvent(event);

					if (tableName.equals(SERVER_TABLE))
						renameServerEvents(oldkey, newKey, event.syncID + 1);
					else
						renameEvents(oldkey, newKey, event.timestamp);

				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Delete)) {
					Debug("wait for barrier event and rename path:" + relatedEvent.toString());
					result = addConsolidatedEvent(event);
				}
			} else if (event.fileAction.equals(S3Event.FileAction.Delete)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Create)) {

					Debug("Rule6 C(A) & D(A):" + event.fileObject.objectKey);

					removeEventNoLock(relatedEvent);
					result = true;
				}
			} else {
				Error("Should not go in here:" + event.toString());
			}
		}
		return result;
	}

	/**
	 * <pre>
	 *  Only used by add events for applying consolidation rules 
	 *  Note: Don't require lock , the higher level would do lock.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @param event
	 *            server raw event or local raw event
	 * @return success or not as result of adding event
	 * @throws EventException
	 *             , SQLException
	 */
	protected boolean consolidateEvent(S3Event event) throws EventException {
		boolean result = false;
		S3Event relatedEvent = null;

		if (event.fileAction.equals(S3Event.FileAction.Rename)) {
			relatedEvent = queryEvent(event.oldObjectKey);
		} else {
			relatedEvent = queryEvent(event.fileObject.objectKey);
		}

		// Process relatedEvent and currentEvent
		if (null != relatedEvent) {
			if (event.fileAction.equals(S3Event.FileAction.Create)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Delete)) {

					Debug("Rule1 D(A) & C(A):" + event.fileObject.objectKey);
					Debug("Remove event:" + relatedEvent.toString());

					removeEventNoLock(relatedEvent);
					event.fileAction = S3Event.FileAction.Modify;
					result = consolidateEvent(event);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Rename)) {
					if (relatedEvent.oldObjectKey.equals(event.fileObject.objectKey)) {

						Debug("Rule2 R(A-X) & C(A):" + event.fileObject.objectKey);
						Debug("Remove event:" + relatedEvent.toString());

						removeEventNoLock(relatedEvent);
						relatedEvent.fileAction = S3Event.FileAction.Create;
						relatedEvent.oldObjectKey = "";
						relatedEvent.syncID = event.syncID;
						relatedEvent.timestamp = event.timestamp;

						consolidateEvent(relatedEvent);
						event.fileAction = S3Event.FileAction.Modify;
						result = consolidateEvent(event);
					} else {
						// result = addConsolidatedEvent(event);
						Error("Error Event:Create A , but early event not Delete A: " + relatedEvent.toString());
					}
				} else {
					// result = addConsolidatedEvent(event);
					Error("Error Event:Create A , but early event is not in rules:" + relatedEvent.toString());
				}
			} else if (event.fileAction.equals(S3Event.FileAction.Modify)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Create)) {

					Debug("Rule3 C(A) & M(A):" + event.fileObject.objectKey);
					// Debug("Remove event:" + event.toString());

					// removeEventNoLock(event.fileObject.objectKey);
					// event.fileAction = S3Event.FileAction.Create;
					// result = consolidateEvent(event);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Rename)) {
					if (relatedEvent.fileObject.objectKey.equals(event.fileObject.objectKey)) {

						Debug("Rule4 R(X-A) & M(A):" + event.fileObject.objectKey);
						Debug("Remove event:" + relatedEvent.toString());

						removeEventNoLock(relatedEvent);

						// Rename X->A --> Delete X
						relatedEvent.fileObject.objectKey = relatedEvent.oldObjectKey;
						relatedEvent.oldObjectKey = "";
						relatedEvent.fileAction = S3Event.FileAction.Delete;
						relatedEvent.syncID = event.syncID;
						relatedEvent.timestamp = event.timestamp;
						consolidateEvent(relatedEvent);

						// Modify A --> Create A
						event.fileAction = S3Event.FileAction.Create;
						result = consolidateEvent(event);
					} else {
						// result = addConsolidatedEvent(event);
						Error("Error Event:Modify A , but early event is Rename A->X: " + relatedEvent.toString());
					}
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Modify)) {

					Debug("Rule5 M(A) & M(A):" + event.fileObject.objectKey);
					// Debug("Remove event:" + relatedEvent.toString());

					// removeEventNoLock( event.fileObject.objectKey);
					// result = consolidateEvent( event);
				} else {
					// result = addConsolidatedEvent(event);
					Error("Error Event:Modify A , but early event is not in rules: " + relatedEvent.toString());
				}
			} else if (event.fileAction.equals(S3Event.FileAction.Rename)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Create)) {

					Debug("Rule6 C(A) & R(A-B): from" + event.oldObjectKey + " to" + event.fileObject.objectKey);
					Debug("Remove event:" + relatedEvent.toString());

					removeEventNoLock(relatedEvent);

					event.oldObjectKey = "";
					event.fileAction = S3Event.FileAction.Modify;
					result = consolidateEvent(event);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Modify)) {
					Debug("Rule7 M(A) & R(A-B): from" + event.oldObjectKey + " to" + event.fileObject.objectKey);
					Debug("Remove event:" + relatedEvent.toString());

					removeEventNoLock(relatedEvent);

					// Modify A --> Delete A
					relatedEvent.fileAction = S3Event.FileAction.Delete;
					relatedEvent.syncID = event.syncID;
					relatedEvent.timestamp = event.timestamp;
					result = consolidateEvent(relatedEvent);

					// Rename A->B --> Create B
					event.fileAction = S3Event.FileAction.Create;
					event.oldObjectKey = "";
					result = consolidateEvent(event);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Rename)) {
					if (relatedEvent.fileObject.objectKey.equals(event.oldObjectKey)) {
						Debug("Rule8 R(X-A) & R(A-B): from" + event.oldObjectKey + " to" + event.fileObject.objectKey);
						Debug("Remove event:" + relatedEvent.toString());

						removeEventNoLock(relatedEvent);

						event.oldObjectKey = relatedEvent.oldObjectKey;
						result = consolidateEvent(event);
					} else {
						// result = addConsolidatedEvent(event);
						Error("Error Event:R(A-B) , but early Rename event is not X->A: " + relatedEvent.toString());
					}
				} else {
					// result = addConsolidatedEvent(event);
					Error("Error Event:R(A-B) , but early event is not in rules: " + relatedEvent.toString());
				}
			} else if (event.fileAction.equals(S3Event.FileAction.Delete)) {
				if (relatedEvent.fileAction.equals(S3Event.FileAction.Create)) {

					Debug("Rule10 C(A) & D(A):" + event.fileObject.objectKey);
					Debug("Remove event:" + relatedEvent.toString());

					removeEventNoLock(relatedEvent);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Modify)) {

					Debug("Rule11 M(A) & D(A):" + event.fileObject.objectKey);
					Debug("Remove event:" + relatedEvent.toString());

					removeEventNoLock(relatedEvent);
					result = consolidateEvent(event);
				} else if (relatedEvent.fileAction.equals(S3Event.FileAction.Rename)) {
					if (relatedEvent.fileObject.objectKey.equals(event.fileObject.objectKey)) {

						Debug("Rule12 R(X-A) & D(A):" + event.fileObject.objectKey);
						Debug("Remove event:" + relatedEvent.toString());

						removeEventNoLock(relatedEvent);

						event.fileObject.objectKey = relatedEvent.oldObjectKey;
						result = consolidateEvent(event);
					} else {
						// result = addConsolidatedEvent(event);
						Error("Error Event:Delete A , but early Rename event is not Rename X->A: " + relatedEvent.toString());
					}
				} else {
					// result = addConsolidatedEvent(event);
					Error("Error Event:Delete A , but early event is not in rules: " + relatedEvent.toString());
				}
			}
		} else {
			// Rename Event need check another objectkey
			if (event.fileAction.equals(S3Event.FileAction.Rename)) {
				relatedEvent = queryEvent(event.fileObject.objectKey);
				if (null != relatedEvent) {
					if (relatedEvent.fileAction.equals(S3Event.FileAction.Delete)) {

						Debug("Rule9 D(B) & R(A-B):" + event.fileObject.objectKey);
						Debug("Remove event:" + relatedEvent.toString());

						removeEventNoLock(relatedEvent);

						// Delete B --> Delete A
						relatedEvent.fileObject.objectKey = event.oldObjectKey;
						relatedEvent.fileObject.MD5 = event.fileObject.MD5;
						relatedEvent.syncID = event.syncID;
						relatedEvent.timestamp = event.timestamp;
						consolidateEvent(relatedEvent);

						// Rename A->B --> Modify B
						event.fileAction = S3Event.FileAction.Modify;
						event.oldObjectKey = "";

						result = consolidateEvent(event);
					} else {
						// result = addConsolidatedEvent(event);
						Error("Error Event:Rename A->B , but early Delete event is not B");
					}
				} else {
					result = addConsolidatedEvent(event);
				}
			} else {
				result = addConsolidatedEvent(event);
			}
		}

		return result;
	}

	/**
	 * <pre>
	 *  Only used by consolidate method for add event to database.
	 *  Note: Don't require lock , because the higher level already required.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @param event
	 *            consolidated event
	 * @return boolean
	 * @throws EventException
	 *             , SQLException
	 */
	private boolean addConsolidatedEvent(S3Event event) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtInsert = null;
			// PreparedStatement stmtSelect = null;

			// need ot check objectkey existed or not??

			// LOCAL =
			// "timestamp, objectKey , oldtKey , filesequence , fileMd5 , isFolder , fileAction , modifyDate , state , retry";
			// SERVER =
			// "syncID, objectKey, oldObjectKey, filesequence, fileMd5, isFolder, fileAction, modifyDate, state, retry, PRIMARY KEY (syncID, objectKey)";

			String statement = null;
			if (tableName.equals(SERVER_TABLE)) {
				statement = "INSERT INTO " + tableName
				        + "(syncID,objectKey,oldObjectKey,sequence,MD5,isFolder,fileAction,modifiedDate,state,retry,fileSize) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
			} else {
				statement = "INSERT INTO " + tableName
				        + "(timestamp,objectKey,oldObjectKey,sequence,MD5,isFolder,fileAction,modifiedDate,state,retry) VALUES (?,?,?,?,?,?,?,?,?,?)";
			}

			stmtInsert = connection.prepareStatement(statement);
			if (tableName.equals(SERVER_TABLE)) {
				stmtInsert.setLong(1, event.syncID);
				stmtInsert.setLong(11, event.fileObject.size);
			} else {
				stmtInsert.setLong(1, event.timestamp);
			}

			stmtInsert.setString(2, event.fileObject.objectKey);
			stmtInsert.setString(3, event.oldObjectKey);
			stmtInsert.setInt(4, event.fileObject.sequence);
			stmtInsert.setString(5, event.fileObject.MD5);
			stmtInsert.setInt(6, event.fileObject.isFolder ? 1 : 0);
			stmtInsert.setString(7, event.fileAction.toString());
			stmtInsert.setLong(8, event.fileObject.modifiedDate);
			stmtInsert.setString(9, event.state.toString());
			stmtInsert.setInt(10, event.retry);

			int count = stmtInsert.executeUpdate();
			if (count != 1)
				result = false;
			else
				result = true;

			if (null != stmtInsert) {
				stmtInsert.close();
			}
			stmtInsert = null;

			Debug("Add consolidated event to SyncID(" + event.syncID + ")" + tableName + " :" + event.toString());
			/*
			 * if (tableName.equals(SERVER_TABLE))
			 * Debug("Add consolidated event to SyncID(" + event.syncID + ")" +
			 * tableName + " :" + event.toString());
			 */
		} catch (SQLException e) {
			Error("Consolidate event failed:" + event.toString() + " by SQLException:" + e.getMessage());
		}

		/*
		 * if (event.fileAction.equals(S3Event.FileAction.Rename) &&
		 * event.fileObject.isFolder){
		 * 
		 * Debug("Do rename events in " + tableName + " from " +
		 * event.oldObjectKey + "," + event.fileObject.objectKey);
		 * renameEvents(event.oldObjectKey, event.fileObject.objectKey,
		 * event.timestamp); }
		 */

		return result;
	}

	/**
	 * <pre>
	 *  Only used by consolidate method for query related event from database.
	 *  Note: Don't require lock , because the higher level already required.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @param objectKey
	 *            the related event objectKey
	 * @return S3Event
	 * @throws EventException
	 *             , SQLException
	 */
	private S3Event queryEvent(String objectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			// stmtSelect = connection.prepareStatement("SELECT * FROM " +
			// tableName +
			// " WHERE (objectKey = ? or oldObjectKey = ?) and state in (?, ?, ?)");
			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE (objectKey=? or oldObjectKey=?) and state in (?,?)");

			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, objectKey);
			stmtSelect.setString(3, S3Event.EventState.New.toString());
			// stmtSelect.setString(4, S3Event.EventState.Process.toString());
			// stmtSelect.setString(5, S3Event.EventState.Fail.toString());
			stmtSelect.setString(4, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				// result = new S3Event(fileObject);
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));

				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");

				if (tableName.equals(SERVER_TABLE)) {
					result.syncID = rs.getLong("syncID");
				} else {
					result.timestamp = rs.getLong("timestamp");
				}
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query event failed:" + objectKey + " by SQLException:" + e.getMessage());
		}
		return result;
	}

	private boolean renameServerEvents(String oldPath, String newPath, long syncID) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtUpdate = null;

			DataBaseHandler.getInstance().beginTransaction();

			stmtUpdate = connection.prepareStatement("UPDATE " + tableName
			        + " SET objectKey=replace(objectKey,?,?),syncID=? WHERE objectKey LIKE ? and state in (?,?,?) and syncID<=?");

			stmtUpdate.setString(1, oldPath);
			stmtUpdate.setString(2, newPath);
			stmtUpdate.setLong(3, syncID);
			stmtUpdate.setString(4, oldPath + "/%");
			stmtUpdate.setString(5, S3Event.EventState.New.toString());
			stmtUpdate.setString(6, S3Event.EventState.Process.toString());
			stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
			stmtUpdate.setLong(8, syncID);
			stmtUpdate.executeUpdate();

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

			result = true;
		} catch (SQLException e) {
			Error("Rename events failed server from " + oldPath + " to " + newPath + " by SQLException:" + e.getMessage());

		} finally {
			DataBaseHandler.getInstance().endTransaction(true);
			lock.writeLock().unlock();
		}

		return result;
	}

	public boolean renameEvents(String oldPath, String newPath, long timestamp) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtUpdate = null;

			DataBaseHandler.getInstance().beginTransaction();

			stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET objectKey=replace(objectKey,?,?),timestamp=? WHERE objectKey LIKE ? and state in (?,?,?)");

			stmtUpdate.setString(1, oldPath);
			stmtUpdate.setString(2, newPath);
			stmtUpdate.setLong(3, timestamp + MAX_INTERLEAVE_TIME * 2);
			stmtUpdate.setString(4, oldPath + "/%");
			stmtUpdate.setString(5, S3Event.EventState.New.toString());
			stmtUpdate.setString(6, S3Event.EventState.Process.toString());
			stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
			stmtUpdate.executeUpdate();

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

			result = true;
		} catch (SQLException e) {
			Error("Rename events failed local from " + oldPath + " to " + newPath + " by SQLException:" + e.getMessage());
		} catch (Exception e) {
			Error("Rename events failed local from " + oldPath + " to " + newPath + " by SQLException:" + e.getMessage());

		} finally {
			DataBaseHandler.getInstance().endTransaction(true);

			lock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * <pre>
	 *  Only used by consolidation method to modify path for all relative events
	 *  if update is happened at local event, this also update the event timestamp for delay used.
	 *  Note: require write lock
	 * </pre>
	 * 
	 * @param oldPath
	 *            event oldObjectKey
	 * @param newPath
	 *            event objectKey
	 * @return isSuccess
	 * @throws EventException
	 */
	/*
	 * private boolean renameEvents(String oldPath, String newPath) throws
	 * EventException{ boolean result = false;
	 * 
	 * try{ PreparedStatement stmtUpdate = null; PreparedStatement stmtSelect =
	 * null;
	 * 
	 * DataBaseHandler.getInstance().beginTransaction();
	 * 
	 * List<String> updateObjKeyList = new ArrayList<String>(); stmtSelect =
	 * connection.prepareStatement("SELECT objectKey from " + tableName +
	 * " WHERE objectKey like ? and state in (?, ?, ?) ");
	 * 
	 * stmtSelect.setString(1, oldPath + "/%"); stmtSelect.setString(2,
	 * S3Event.EventState.New.toString()); stmtSelect.setString(3,
	 * S3Event.EventState.Process.toString()); stmtSelect.setString(4,
	 * S3Event.EventState.Fail.toString()); ResultSet rs =
	 * stmtSelect.executeQuery();
	 * 
	 * while (rs.next()) { updateObjKeyList.add(rs.getString("objectKey")); }
	 * 
	 * if (0 < updateObjKeyList.size()) { for (String oldObjkey :
	 * updateObjKeyList) { String newObjkey =
	 * oldObjkey.replaceFirst(Pattern.quote(oldPath), newPath);
	 * 
	 * if (tableName.equals(SERVER_TABLE)){ stmtUpdate =
	 * connection.prepareStatement("UPDATE " + tableName +
	 * " SET objectKey=? WHERE objectKey=? "); stmtUpdate.setString(1,
	 * newObjkey); stmtUpdate.setString(2, oldObjkey); }else{ stmtUpdate =
	 * connection.prepareStatement("UPDATE " + tableName +
	 * " SET objectKey=?, timestamp=? WHERE objectKey=? ");
	 * stmtUpdate.setString(1, newObjkey); stmtUpdate.setLong(2,
	 * System.currentTimeMillis()); stmtUpdate.setString(3, oldObjkey); }
	 * 
	 * stmtUpdate.executeUpdate(); } }
	 * 
	 * if (null != rs) { rs.close(); } rs = null;
	 * 
	 * if (null != stmtSelect) { stmtSelect.close(); } stmtSelect = null;
	 * 
	 * if (null != stmtUpdate) { stmtUpdate.close(); } stmtUpdate = null;
	 * 
	 * result = true; } catch (SQLException e) { throw new EventException
	 * ("renameEvents failed:" + oldPath + " to " + newPath +
	 * " by SQLException:" + e.getMessage()); }finally{
	 * DataBaseHandler.getInstance().endTransaction(true);
	 * 
	 * } return result; }
	 */
	/**
	 * <pre>
	 *  Only used by consolidated method for doing remove event with the objectKey
	 *  Note: Don't require lock , because the higher level already required.
	 * </pre>
	 * 
	 * @param objectKey
	 *            the event objectKey
	 * @return S3Event
	 * @throws EventException
	 *             , SQLException
	 */
	public boolean removeEventNoLock(S3Event event) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {

			PreparedStatement stmtDelete = null;

			if (tableName.equals(SERVER_TABLE)) {
				stmtDelete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE objectKey=? and syncID=?");
				stmtDelete.setString(1, event.fileObject.objectKey);
				stmtDelete.setLong(2, event.syncID);
			} else {
				stmtDelete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE objectKey=? and timestamp=?");
				stmtDelete.setString(1, event.fileObject.objectKey);
				stmtDelete.setLong(2, event.timestamp);
			}

			int count = stmtDelete.executeUpdate();

			if (count == 1)
				result = true;

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;

			// Soft delete
			/*
			 * PreparedStatement stmtUpdate = null;
			 * 
			 * if (tableName.equals(SERVER_TABLE)) { stmtUpdate =
			 * connection.prepareStatement("UPDATE " + tableName +
			 * " SET state=? WHERE objectKey=? and syncID=? and state in (?,?,?)"
			 * ); stmtUpdate.setString(1, S3Event.EventState.Delete.toString());
			 * stmtUpdate.setString(2, event.fileObject.objectKey);
			 * stmtUpdate.setLong(3, event.syncID); stmtUpdate.setString(4,
			 * S3Event.EventState.New.toString()); stmtUpdate.setString(5,
			 * S3Event.EventState.Process.toString()); stmtUpdate.setString(6,
			 * S3Event.EventState.Fail.toString()); } else { stmtUpdate =
			 * connection.prepareStatement("UPDATE " + tableName +
			 * " SET state=? WHERE objectKey=? and timestamp=? and state in (?,?,?)"
			 * ); stmtUpdate.setString(1, S3Event.EventState.Delete.toString());
			 * stmtUpdate.setString(2, event.fileObject.objectKey);
			 * stmtUpdate.setLong(3, event.timestamp); stmtUpdate.setString(4,
			 * S3Event.EventState.New.toString()); stmtUpdate.setString(5,
			 * S3Event.EventState.Process.toString()); stmtUpdate.setString(6,
			 * S3Event.EventState.Fail.toString()); }
			 * 
			 * stmtUpdate.executeUpdate();
			 * 
			 * if (null != stmtUpdate) { stmtUpdate.close(); } stmtUpdate =
			 * null;
			 */
		} catch (SQLException e) {
			if (tableName.equals(SERVER_TABLE))
				Error("Remove event (" + tableName + ") failed:" + event.fileObject.objectKey + "," + event.syncID + " by SQLException:" + e.getMessage());
			else
				Error("Remove event (" + tableName + ") failed:" + event.fileObject.objectKey + "," + event.timestamp + " by SQLException:" + e.getMessage());
		}

		return result;
	}

	/**
	 * <pre>
	 *  Used to delete all events by sync engine
	 *  Note: Don't require lock , the lower level would do lock.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @return process result
	 * @throws SQLException
	 * @exception EventException
	 *                , SQLException
	 * 
	 */
	public boolean clear() throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {

			PreparedStatement stmtDelete = null;

			stmtDelete = connection.prepareStatement("DELETE FROM " + tableName);
			stmtDelete.executeUpdate();

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;
			

			//Soft delete
			/*PreparedStatement stmtUpdate = null;

			stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?");
			stmtUpdate.setString(1, S3Event.EventState.Delete.toString());

			stmtUpdate.executeUpdate();
			*/
			
			result = true;
		} catch (SQLException e) {
			Error("clear table failed:" + tableName + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
		}
		return result;
	}

	protected void printDB() {
		if (DataBaseHandler.getInstance().isDisconneced())
			return;

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName);

			ResultSet rs = stmtSelect.executeQuery();

			Debug(" ===start printDB:" + tableName);

			while (rs.next()) {
				StringBuffer buff = new StringBuffer();
				buff.append(rs.getString("fileAction"));
				buff.append(",objectKey: ");
				buff.append(rs.getString("objectKey"));
				buff.append(",state : ");
				buff.append(rs.getString("state"));
				if (tableName.equals(SERVER_TABLE)) {
					buff.append(",syncid : ");
					buff.append(rs.getLong("syncID"));
				} else {
					buff.append(",timestamp : ");
					buff.append(rs.getLong("timestamp"));
				}

				Debug(buff.toString() + "\n");
			}

			Debug(" ===end printDB");

			if (null != stmtSelect)
				stmtSelect.close();

			rs = null;
		} catch (SQLException e) {
			Error(e.getMessage());
		}
	}

	protected void makeEventList(ResultSet rs, List<S3Event> result) throws SQLException {
		long currentSize = 0;

		while (rs.next() && currentSize < MAX_SYNC_SIZE) {
			String objectKey = rs.getString("objectKey");

			S3Event event = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), objectKey);

			if (tableName.equals(SERVER_TABLE)) {
				event.syncID = rs.getLong("syncID");
				event.fileObject.size = rs.getLong("fileSize");
				event.fileObject.MD5 = rs.getString("MD5");
				event.fileObject.modifiedDate = rs.getLong("modifiedDate");
			} else {
				String localPath = Util.translateLocalPath(objectKey);
				File localFile = new File(localPath);

				if (FileUtil.isFileLocked(localFile)) {
					continue;
				}

				event.timestamp = rs.getLong("timestamp");
				event.fileObject.size = localFile.length();
				event.fileObject.MD5 = FileUtil.getMD5(localFile);
				event.fileObject.modifiedDate = localFile.lastModified();
			}

			event.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
			event.fileObject.sequence = rs.getInt("sequence");
			event.oldObjectKey = rs.getString("oldObjectKey");
			event.state = S3Event.EventState.valueOf(rs.getString("state"));
			event.retry = rs.getInt("retry");

			result.add(event);

			// Create , Modify or Rename all could be resulted to do put object
			if (!event.fileObject.isFolder && !event.fileAction.equals(S3Event.FileAction.Delete)) {
				// Debug(event.fileObject.objectKey+" File size:"+event.fileObject.size);
				currentSize += event.fileObject.size;
			}
		}

//		Debug("Current ( " + tableName + " ) events total filesize:" + String.format("%.3f", currentSize / 1048576.0) + "MB");
		Debug("Current ( " + tableName + " ) events total filesize:" + String.format("%.1f", currentSize / 1024.0) + " KB");
	}

	public boolean hasErrorEvent() {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("Select Count(*) from " + tableName + " where state='Error'");

			ResultSet rs = stmtSelect.executeQuery();

			int intCount = rs.getInt(1);

			if (intCount > 0)
				result = true;
			else
				result = false;

		} catch (SQLException e) {
			Error("query error event failed:" + tableName + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	public void Info(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Event, LoggerHandler.INFO, msg);
	}

	public void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Event, LoggerHandler.DBG, msg);
	}

	public void Error(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Event, LoggerHandler.ERR, msg);
	}

}