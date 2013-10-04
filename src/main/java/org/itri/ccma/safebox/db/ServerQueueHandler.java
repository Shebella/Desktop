package org.itri.ccma.safebox.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.s3.S3Event;

public class ServerQueueHandler extends EventQueueHandler {

	private static ServerQueueHandler instance = null;

	private ServerQueueHandler() {
		super();
		tableName = SERVER_TABLE;
	}

	public static ServerQueueHandler getInstance() {
		if (null == instance) {
			instance = new ServerQueueHandler();
		}
		return instance;
	}

	/**
	 * <pre>
	 *  Used to get events from server event list or local event list
	 *  the state of return events would be NEW, PROCESS, or FAIL 
	 *  Note: No need lock
	 * </pre>
	 * 
	 * @param name
	 *            database name
	 * @return
	 * @throws EventException
	 */
	public int getCount() throws EventException {
		int result = -1;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT COUNT(*) AS rowcount FROM " + tableName + " where state in (?,?,?)");
			stmtSelect.setString(1, S3Event.EventState.New.toString());
			stmtSelect.setString(2, S3Event.EventState.Process.toString());
			stmtSelect.setString(3, S3Event.EventState.Fail.toString());

			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = rs.getInt("rowcount");
			}
		} catch (SQLException e) {
			Error("Get server event count failed by SQLException:" + e.getMessage());
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to add server events grouping by syncid to server event list in database.
	 *  Note: require write lock
	 * </pre>
	 * 
	 * @param events
	 *            a list of events grouping by the same syncId.
	 * @param syncid
	 *            used to grouping events
	 * @return process result
	 * @throws EventException
	 */
	public boolean addEvents(List<S3Event> events, String syncid) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();
		try {
			// do something
			for (int i = 0; i < events.size(); i++) {
				if (events.get(i).fileObject.isFolder)
					consolidateFolderEvent(events.get(i));
				else
					consolidateEvent(events.get(i));
			}

			// result = updateServerEventId(syncid);
			// if (!result)
			// throw new
			// EventException("Update server event sycId failed:"+syncid);

		} finally {
			lock.writeLock().unlock();
		}

		return result;
	}

	public List<S3Event> getEvents() throws EventException {
		List<S3Event> result = null;

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
					Debug("return barrier event.");
					return result;
				}
			} else {
				Debug("return events before barrier event.");
				return result;
			}

			PreparedStatement stmtUpdate = null;
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " where state in (?,?,?) order by isFolder DESC,syncID ASC,objectKey ASC limit ?");
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

					stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and syncID=? and state in (?,?,?)");
					stmtUpdate.setString(1, S3Event.EventState.Process.toString());
					stmtUpdate.setInt(2, event.retry);
					stmtUpdate.setString(3, event.fileObject.objectKey);
					stmtUpdate.setLong(4, event.syncID);
					stmtUpdate.setString(5, S3Event.EventState.New.toString());
					stmtUpdate.setString(6, S3Event.EventState.Fail.toString());
					stmtUpdate.setString(7, S3Event.EventState.Process.toString());
					stmtUpdate.executeUpdate();
				}
			}

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

		} catch (SQLException e) {
			Error("Get server events failed by SQLException:" + e.getMessage());
		} finally {
			DataBaseHandler.getInstance().endTransaction(true);

			lock.writeLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Only used by handle server events by CSSHandler, ex: modify event or create event
	 * </pre>
	 * 
	 * @param objectKey
	 *            the related event objectKey
	 * @param state
	 *            state of the query event
	 * @return S3Event
	 * @throws EventException
	 */
	public S3Event queryEvent(String objectKey, S3Event.EventState state) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and state=?");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, state.toString());

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
				result.syncID = rs.getLong("syncID");
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

			if (null != rs)
				rs.close();

		} catch (SQLException e) {
			Error("Query server event failed:" + objectKey + " by SQLException:" + e.getMessage());
		}

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
	 */
	public S3Event queryEvent(String objectKey) throws EventException {
		S3Event result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

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
				result.fileObject.objectKey = rs.getString("objectKey");
				result.fileObject.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.fileObject.MD5 = rs.getString("MD5");
				result.fileObject.modifiedDate = rs.getLong("modifiedDate");
				result.fileObject.sequence = rs.getInt("sequence");
				result.oldObjectKey = rs.getString("oldObjectKey");
				result.state = S3Event.EventState.valueOf(rs.getString("state"));
				result.retry = rs.getInt("retry");
				result.syncID = rs.getLong("syncID");
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

			if (null != rs)
				rs.close();

		} catch (SQLException e) {
			Error("Query server event failed:" + objectKey + " by SQLException:" + e.getMessage());
		}

		return result;
	}

	// public boolean isEchoEvent(String objectKey, long serverSyncId) throws
	// EventException, SQLException{
	public boolean isEchoEvent(String objectKey) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();
		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE objectKey=? and state=?");
			stmtSelect.setString(1, objectKey);
			stmtSelect.setString(2, S3Event.EventState.Process.toString());

			ResultSet rs = stmtSelect.executeQuery();
			if (rs.next()) {
				result = true;
			}
			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

			if (null != rs)
				rs.close();

		} catch (SQLException e) {
			Debug("Check server echo event failed:" + objectKey + " by SQLException:" + e.getMessage());
			// throw new EventException ("Check server echo event failed:" +
			// objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}

	/**
	 * <pre>
	 * Used to update event process status , used to mark event failed or success by sync engine.
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

			if ((IGlobal.appState.equals(IGlobal.APP_STATE.SHUTDOWN) || IGlobal.appState.equals(IGlobal.APP_STATE.PAUSED)) && state.equals(S3Event.EventState.Fail)) {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=?,retry=? WHERE objectKey=? and syncID=? and state in (?,?,?)");
				stmtUpdate.setString(1, S3Event.EventState.Fail.toString());
				stmtUpdate.setInt(2, --event.retry);
				stmtUpdate.setString(3, event.fileObject.objectKey);
				stmtUpdate.setLong(4, event.syncID);

				stmtUpdate.setString(5, S3Event.EventState.New.toString());
				stmtUpdate.setString(6, S3Event.EventState.Process.toString());
				stmtUpdate.setString(7, S3Event.EventState.Fail.toString());
			} else {
				stmtUpdate = connection.prepareStatement("UPDATE " + tableName + " SET state=? WHERE objectKey=? and syncID=? and state in (?,?,?)");
				stmtUpdate.setString(1, state.toString());
				stmtUpdate.setString(2, event.fileObject.objectKey);
				stmtUpdate.setLong(3, event.syncID);
				stmtUpdate.setString(4, S3Event.EventState.New.toString());
				stmtUpdate.setString(5, S3Event.EventState.Process.toString());
				stmtUpdate.setString(6, S3Event.EventState.Fail.toString());
			}

			int count = stmtUpdate.executeUpdate();

			if (count == 1) {
				result = true;
			} else {
				Debug("Update server events failed with count:" + count + ", event:" + event.fileObject.objectKey + "," + event.syncID + "," + event.state.toString());
			}

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;

		} catch (SQLException e) {
			Debug("Update server events failed:" + event.fileObject.objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * <pre>
	 *  Used to get last syncID of server event. 
	 *  To prevent repeatedly add server events.
	 *  Note: Don't require lock , because this is a simple select query.
	 * </pre>
	 * 
	 * @param
	 * @return syncid
	 * @throws SQLException
	 * @exception EventException
	 * 
	 */
	public long getLastSyncID() throws EventException {
		long result = -1;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT MAX(syncID) AS syncID FROM " + tableName);

			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = rs.getLong("syncID");
			}

			if (null != rs) {
				rs.close();
			}
			rs = null;

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Get server last syncID failed by SQLException:" + e.getMessage());
			throw new EventException("Get server last syncID failed by SQLException:" + e.getMessage());
		}

		return result;
	}

	/**
	 * <pre>
	 *  Used to add full sync event. 
	 *  Note: Don't require lock , because this is not processing in multi-thread.
	 * </pre>
	 * 
	 * @param event
	 * @param syncID
	 * @return boolean
	 * @throws SQLException
	 * @exception EventException
	 * 
	 */
	public boolean addEvent(S3Event event, long syncID) throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();
		try {
			PreparedStatement stmtInsert = null;

			String statement = "INSERT INTO " + tableName
			        + "(syncID,objectKey,oldObjectKey,sequence,MD5,isFolder,fileAction,modifiedDate,state,retry) VALUES (?,?,?,?,?,?,?,?,?,?)";

			stmtInsert = connection.prepareStatement(statement);
			stmtInsert.setLong(1, syncID);
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

		} catch (SQLException e) {
			Error("Add server event failed:" + event.fileObject.objectKey + " by SQLException:" + e.getMessage());
			throw new EventException("Add server event failed:" + event.fileObject.objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
		}

		return result;
	}
}