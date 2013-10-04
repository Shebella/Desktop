package org.itri.ccma.safebox.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.s3.S3Event;
import org.itri.ccma.safebox.util.Util;

public class LocalQueueHandler extends EventQueueHandler {

	private static LocalQueueHandler instance = null;

	private LocalQueueHandler() {
		super();
		tableName = LOCAL_TABLE;
	}

	public static LocalQueueHandler getInstance() {
		if (null == instance) {
			instance = new LocalQueueHandler();
		}
		return instance;
	}

	/**
	 * <pre>
	 *  Used to check the specific objectKey if any event existed in server event list or local event in database
	 *  Note: require read lock
	 * </pre>
	 * 
	 * @param objectKey
	 *            the event objectKey
	 * @param serverSyncId
	 *            the current syncid
	 * @return process result
	 * @throws EventException
	 */
	public boolean isEchoEvent(S3Event.FileAction fileAction, String objectKey, long serverSyncId, long eventTimeStamp) throws EventException {
		boolean result = false;

		Debug("Check echo event from server.");

		// /do server queue instance query
		// if (ServerQueueHandler.getInstance().isEchoEvent(objectKey,
		// serverSyncId))
		if (ServerQueueHandler.getInstance().isEchoEvent(objectKey))
			return true;

		Debug("Check echo event from local.");

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			if (fileAction.equals(S3Event.FileAction.Modify)) {
				PreparedStatement stmtSelect = null;

				long targetTime = eventTimeStamp - MAX_INTERLEAVE_TIME;

				stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and state in (?,?)");
				stmtSelect.setString(1, objectKey);
				stmtSelect.setString(2, S3Event.EventState.New.toString());
				stmtSelect.setString(3, S3Event.EventState.Fail.toString());

				ResultSet rs = stmtSelect.executeQuery();

				if (rs.next()) {
					// check create
					if (rs.getString("fileAction").equals(S3Event.FileAction.Create.toString())) {
						result = true;
						// check rename
					} else if (rs.getString("fileAction").equals(S3Event.FileAction.Rename.toString())) {
						if (rs.getLong("timestamp") >= targetTime) {
							result = true;
						} else {
							Debug("Modify event is too late, not a echo event");
						}

						// check modify
					} else if (rs.getString("fileAction").equals(S3Event.FileAction.Modify.toString())) {
						result = true;
					}
				}

				if (null != rs)
					rs.close();
				rs = null;

				if (stmtSelect != null)
					stmtSelect.close();
				stmtSelect = null;
			}
		} catch (SQLException e) {
			Error("Check echo event failed:" + objectKey + " by SQLException:" + e.getMessage());
			// throw new EventException ("Check Echo Event failed:" + objectKey
			// + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}

	/**
	 * <pre>
	 *  Used to get number of events in local queue by sync engine.
	 *  Note: require write lock
	 * </pre>
	 * 
	 * @return number of events
	 * @throws EventException
	 */
	public int getCount() throws EventException {
		int result = -1;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			ResultSet rs = null;

			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT COUNT(*) AS rowcount FROM " + tableName + " where state in (?,?,?)");
			stmtSelect.setString(1, S3Event.EventState.New.toString());
			stmtSelect.setString(2, S3Event.EventState.Process.toString());
			stmtSelect.setString(3, S3Event.EventState.Fail.toString());
			rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = rs.getInt("rowcount");
			}

			if (stmtSelect != null)
				stmtSelect.close();
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Get local event count failed by SQLException:" + e.getMessage());
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to add raw localevent to local event list in database after checking the echo event.
	 *  Note: require write lock
	 * </pre>
	 * 
	 * @param event
	 *            the S3Event event related to the file.
	 * @return process result
	 * @throws EventException
	 */
	public boolean addEvent(S3Event event) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();
		try {
			if (event.fileObject.isFolder)
				consolidateFolderEvent(event);
			else
				consolidateEvent(event);

			// printDB();
		} finally {
			lock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * <pre>
	 *  Used to get events from server event list or local event list
	 *  All returned events would set state to process
	 *  Note: states of all returned events were in NEW, PROCESS, FAIL
	 *  Note: Don't require lock , the lower level would do lock.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @return
	 * @throws EventException
	 */
	public List<S3Event> getEvents() throws EventException {
		List<S3Event> result = new ArrayList<S3Event>();

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			DataBaseHandler.getInstance().beginTransaction();

			result = getEventsBeforeBarrier();

			if (result.size() == 0) {
				S3Event event = getBarrierEvent();
				if (event != null) {
					result.add(event);
					return result;
				}
			} else {
				return result;
			}

			PreparedStatement stmtUpdate = null;
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " where state in (?,?,?) order by isFolder DESC,timestamp ASC,objectKey ASC limit ?");
			stmtSelect.setString(1, S3Event.EventState.New.toString());
			stmtSelect.setString(2, S3Event.EventState.Process.toString());
			stmtSelect.setString(3, S3Event.EventState.Fail.toString());
			stmtSelect.setInt(4, MAX_NUM_EVENT);
			
			ResultSet rs = stmtSelect.executeQuery();

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

					stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and timestamp=? and state in (?,?,?)");
					stmtUpdate.setString(1, S3Event.EventState.Process.toString());
					stmtUpdate.setInt(2, event.retry);
					stmtUpdate.setString(3, event.fileObject.objectKey);
					stmtUpdate.setLong(4, event.timestamp);
					stmtUpdate.setString(5, S3Event.EventState.New.toString());
					stmtUpdate.setString(6, S3Event.EventState.Process.toString());
					stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
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
			Error("Get local events failed by SQLException:" + Util.getStackTrace(e));
		} finally {
			DataBaseHandler.getInstance().endTransaction(true);

			lock.writeLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to query local rename events from database by sync engine 
	 *  Note: require readlock lock
	 * </pre>
	 * 
	 * @param oldObjectKey
	 *            the rename event oldObjectKey
	 * @return S3Event
	 * @throws EventException
	 */
	public S3Event queryRenameEvent(String oldObjectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE oldObjectKey=? and fileAction=? and state in (?,?,?)");
			stmtSelect.setString(1, oldObjectKey);
			stmtSelect.setString(2, S3Event.FileAction.Rename.toString());
			stmtSelect.setString(3, S3Event.EventState.New.toString());
			stmtSelect.setString(4, S3Event.EventState.Process.toString());
			stmtSelect.setString(5, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();
			if (rs.next()) {
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");
				result.timestamp = rs.getLong("timestamp");
			}
			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query local rename event failed: " + oldObjectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to query local events from database by sync engine
	 *  Note: require readlock lock
	 * </pre>
	 * 
	 * @param objectKey
	 *            the event objectKey
	 * @return S3Event
	 * @throws EventException
	 */
	public S3Event queryEvent(String objectKey, S3Event.FileAction action) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and fileAction=? and state in (?,?,?)");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, action.toString());
			stmtSelect.setString(3, S3Event.EventState.New.toString());
			stmtSelect.setString(4, S3Event.EventState.Process.toString());
			stmtSelect.setString(5, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();
			if (rs.next()) {
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");
				result.timestamp = rs.getLong("timestamp");
			}
			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query local event failed: " + objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to query local events from database by sync engine
	 *  Note: require readlock lock
	 * </pre>
	 * 
	 * @param objectKey
	 *            the event objectKey
	 * @return S3Event
	 * @throws EventException
	 */
	public S3Event queryEvent(String objectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and state in (?,?,?)");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, S3Event.EventState.New.toString());
			stmtSelect.setString(3, S3Event.EventState.Process.toString());
			stmtSelect.setString(4, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();
			if (rs.next()) {
				result = new S3Event(S3Event.FileAction.valueOf(rs.getString("fileAction")), rs.getString("objectKey"));
				result.timestamp = rs.getLong("timestamp");
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");
			}
			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Query local event failed: " + objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 * Used to update event process status , usually used to mark event failed by sync engine.
	 * </pre>
	 * 
	 * @param objectKey
	 *            the event objectKey
	 * @param state
	 *            the new status , usually is FAIL
	 * @return process result
	 * @exception EventException
	 */

	public boolean updateEvent(S3Event event, S3Event.EventState state) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();
		try {
			PreparedStatement stmtUpdate = null;

			if (IGlobal.appState.equals(IGlobal.APP_STATE.PAUSED) && state.equals(S3Event.EventState.Fail)) {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and timestamp=? and state in (?,?,?)");
				stmtUpdate.setString(1, S3Event.EventState.Fail.toString());
				stmtUpdate.setInt(2, --event.retry);
				stmtUpdate.setString(3, event.fileObject.objectKey);
				stmtUpdate.setLong(4, event.timestamp);
				stmtUpdate.setString(5, S3Event.EventState.New.toString());
				stmtUpdate.setString(6, S3Event.EventState.Process.toString());
				stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
				
			} else {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=? WHERE objectKey=? and timestamp=? and state in (?,?,?)");
				stmtUpdate.setString(1, state.toString());
				stmtUpdate.setString(2, event.fileObject.objectKey);
				stmtUpdate.setLong(3, event.timestamp);
				stmtUpdate.setString(4, S3Event.EventState.New.toString());
				stmtUpdate.setString(5, S3Event.EventState.Process.toString());
				stmtUpdate.setString(6, S3Event.EventState.Fail.toString());
			}

			int count = stmtUpdate.executeUpdate();

			if (count == 1)
				result = true;

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

		} catch (SQLException e) {
			Error("Query local event failed: " + event.fileObject.objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to delete a specific event by sync engine
	 *  Note: Don't require lock , the lower level would do lock.
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @param objectKey
	 *            the event objectKey
	 * @return process result
	 * @exception EventException
	 *                , SQLException
	 * 
	 */
	public boolean removeEvent(String objectKey, long timestamp) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtDelete = null;

			stmtDelete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE objectKey=? and timestamp=? and state!=?");
			stmtDelete.setString(1, objectKey);
			stmtDelete.setLong(2, timestamp);
			stmtDelete.setString(3, S3Event.EventState.Error.toString());

			int count = stmtDelete.executeUpdate();

			if (count == 1)
				result = true;

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;

			//Soft delete
			/*PreparedStatement stmtUpdate = null;
			
			stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=? WHERE objectKey=? and timestamp=? and state in (?,?,?)");
			stmtUpdate.setString(1, S3Event.EventState.Delete.toString());
			stmtUpdate.setString(2, objectKey);
			stmtUpdate.setLong(3, timestamp);
			stmtUpdate.setString(4, S3Event.EventState.New.toString());
			stmtUpdate.setString(5, S3Event.EventState.Process.toString());
			stmtUpdate.setString(6, S3Event.EventState.Fail.toString());
			
			stmtUpdate.executeUpdate();
			
			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;*/
			
		} catch (SQLException e) {
			Error("LocalQueueHandler Remove event failed:" + objectKey + "," + timestamp + " by SQLException:" + e.getMessage());			
		} finally {
			lock.writeLock().unlock();
		}
		return result;
	}

	public boolean removeEvents(String objectKey, long timestamp) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			
			PreparedStatement stmtDelete = null;

			stmtDelete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE objectKey like ? and timestamp<=? and state!=?");
			stmtDelete.setString(1, objectKey + "/%");
			stmtDelete.setLong(2, timestamp);
			stmtDelete.setString(3, S3Event.EventState.Error.toString());

			stmtDelete.executeUpdate();

			result = true;

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;
						
			/*PreparedStatement stmtUpdate = null;
			
			stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=? WHERE objectKey like ? and timestamp<=? and state in (?,?,?)");
			stmtUpdate.setString(1, S3Event.EventState.Delete.toString());
			stmtUpdate.setString(2, objectKey + "/%");
			stmtUpdate.setLong(3, timestamp);
			stmtUpdate.setString(4, S3Event.EventState.New.toString());
			stmtUpdate.setString(5, S3Event.EventState.Process.toString());
			stmtUpdate.setString(6, S3Event.EventState.Fail.toString());
			
			stmtUpdate.executeUpdate();
			
			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;*/
			
		} catch (SQLException e) {
			Error("LocalQueueHandler Remove events failed:" + objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
		}
		return result;
	}

}