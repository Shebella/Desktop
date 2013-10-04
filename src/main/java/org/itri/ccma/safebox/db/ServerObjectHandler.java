package org.itri.ccma.safebox.db;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import org.itri.ccma.safebox.util.FileUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.Util;

public class ServerObjectHandler {
	public static final String SERVER_OBJECTS_TABLE = "ServerFiles";

	private static ServerObjectHandler instance = null;

	private ReentrantReadWriteLock lock = null;

	protected Connection connection = null;

	public void initConnection() {
		connection = DataBaseHandler.getInstance().getConnection();
	}

	private ServerObjectHandler() {
		lock = new ReentrantReadWriteLock(false);
	}

	public static ServerObjectHandler getInstance() {
		if (null == instance) {
			instance = new ServerObjectHandler();
		}
		return instance;
	}

	public boolean renameObject(String oldKey, String newKey) throws ObjectException {
		boolean result = false;
		lock.writeLock().lock();

		try {
			PreparedStatement stmtUpdate = null;
			Connection connection = DataBaseHandler.getInstance().getConnection();

			stmtUpdate = connection.prepareStatement("UPDATE " + SERVER_OBJECTS_TABLE + " SET objectKey=? WHERE objectKey = ? ");
			stmtUpdate.setString(1, newKey);
			stmtUpdate.setString(2, oldKey);
			int count = stmtUpdate.executeUpdate();

			if (count == 1)
				result = true;

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;
			result = true;
		} catch (SQLException e) {
			Error("renameObject failed: " + oldKey + " To " + newKey + " by SQLException:" + e.getMessage());
			throw new ObjectException("renameObject failed: " + oldKey + " To " + newKey + " by SQLException:" + e.getMessage());
		} finally {

			lock.writeLock().unlock();
		}

		return result;
	}

	public boolean renameObjects(String oldPath, String newPath) throws ObjectException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return false;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtUpdate = null;
			PreparedStatement stmtSelect = null;

			DataBaseHandler.getInstance().beginTransaction();

			ResultSet rs = null;
			List<String> updateObjKeyList = new ArrayList<String>();
			stmtSelect = connection.prepareStatement("SELECT objectKey from " + SERVER_OBJECTS_TABLE + " WHERE objectKey=? or objectKey like ?");
			stmtSelect.setString(1, oldPath);
			stmtSelect.setString(2, oldPath + "%");
			rs = stmtSelect.executeQuery();

			while (rs.next()) {
				updateObjKeyList.add(rs.getString("objectKey"));
			}

			if (0 < updateObjKeyList.size()) {
				for (String oldObjkey : updateObjKeyList) {
					String newObjkey = oldObjkey.replaceFirst(Pattern.quote(oldPath), newPath);
					stmtUpdate = connection.prepareStatement("UPDATE " + SERVER_OBJECTS_TABLE + " SET objectKey=? WHERE objectKey=? ");
					stmtUpdate.setString(1, newObjkey);
					stmtUpdate.setString(2, oldObjkey);
					stmtUpdate.executeUpdate();
				}
			}

			if (null != rs) {
				rs.close();
			}
			rs = null;

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

			if (null != stmtUpdate) {
				stmtUpdate.close();
			}
			stmtUpdate = null;
			result = true;
		} catch (SQLException e) {
			Error("renameObjects failed: " + oldPath + " To " + newPath + " by SQLException:" + e.getMessage());
		} finally {
			DataBaseHandler.getInstance().endTransaction(true);

			lock.writeLock().unlock();

			Debug("Rename server objects success from " + oldPath + " to " + newPath + ".");
		}
		return result;
	}

	/*
	 * private boolean modify(FileObject object) throws ObjectException{ boolean
	 * result = false;
	 * 
	 * if (DataBaseHandler.getInstance().isDisconneced()) return false;
	 * 
	 * lock.writeLock().lock();
	 * 
	 * try{ PreparedStatement stmtUpdate = null; PreparedStatement stmtSelect =
	 * null;
	 * 
	 * stmtSelect = connection.prepareStatement("SELECT * FROM " +
	 * SERVER_OBJECTS_TABLE + " WHERE objectKey=? order by objectKey");
	 * stmtSelect.setString(1, object.objectKey); ResultSet rs =
	 * stmtSelect.executeQuery();
	 * 
	 * if (rs.next()){ stmtUpdate = connection.prepareStatement("UPDATE " +
	 * SERVER_OBJECTS_TABLE +
	 * " SET isFolder=?,modifiedDate=?,MD5=?,sequence=? WHERE objectKey=? ");
	 * 
	 * stmtUpdate.setInt(1, object.isFolder ? 1 : 0); stmtUpdate.setLong(2,
	 * object.modifiedDate); stmtUpdate.setString(3, object.MD5);
	 * stmtUpdate.setInt(4, object.sequence); stmtUpdate.setString(5,
	 * object.objectKey);
	 * 
	 * int count = stmtUpdate.executeUpdate(); if (count != 1) result = false;
	 * else result = true;
	 * 
	 * }else{ Debug("No server object record:"+object.objectKey); }
	 * 
	 * if (null != stmtSelect) { stmtSelect.close(); } stmtSelect = null;
	 * 
	 * 
	 * if (null != stmtUpdate) { stmtUpdate.close(); } stmtUpdate = null;
	 * 
	 * } catch (SQLException e) { Error ("Modify server object failed: " +
	 * object.objectKey + " by SQLException:" + e.getMessage()); }finally{
	 * lock.writeLock().unlock(); }
	 * 
	 * return result; }
	 */
	public FileObject get(String objectKey) throws ObjectException {
		FileObject result = null;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + SERVER_OBJECTS_TABLE + " WHERE objectKey=?");
			stmtSelect.setString(1, objectKey);
			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = new FileObject();
				result.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				result.modifiedDate = rs.getLong("modifiedDate");
				result.objectKey = objectKey;
				result.MD5 = rs.getString("MD5");
				result.sequence = rs.getInt("sequence");
			} else {
				Debug("No server object record:" + objectKey);
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

			if (null != rs)
				rs.close();
			rs = null;
		} catch (SQLException e) {
			Error("Get server object failed: " + objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}

	public boolean add(FileObject object) {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtInsert = null;

			File objFile = new File(Util.translateLocalPath(object.objectKey));

			stmtInsert = connection.prepareStatement("REPLACE INTO " + SERVER_OBJECTS_TABLE + " (objectKey,sequence,MD5,isFolder,modifiedDate) VALUES (?,?,?,?,?)");

			stmtInsert.setString(1, object.objectKey);
			stmtInsert.setInt(2, object.sequence);

			if (null == object.MD5 || object.MD5.equals(""))
				stmtInsert.setString(3, FileUtil.getMD5(objFile));
			else
				stmtInsert.setString(3, object.MD5);

			stmtInsert.setInt(4, object.isFolder ? 1 : 0);
			stmtInsert.setLong(5, objFile.lastModified());

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
			Error("Add server object failed: " + object.objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
			if (result)
				Debug("Add server object success: " + object.objectKey + ".");
			else
				Debug("Add server object failed: " + object.objectKey + ".");
		}

		// LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Event,
		// LoggerHandler.DBG, "add server object :"+object.objectKey+", md5:"+
		// object.MD5);

		return result;
	}

	public boolean remove(String objectKey) throws ObjectException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return false;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtDelete = null;

			stmtDelete = connection.prepareStatement("DELETE FROM " + SERVER_OBJECTS_TABLE + " WHERE objectKey=?");
			stmtDelete.setString(1, objectKey);

			int count = stmtDelete.executeUpdate();

			if (count == 1)
				result = true;

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;
		} catch (SQLException e) {
			Error("Delete server object failed: " + objectKey + " by SQLException:" + e.getMessage());
			throw new ObjectException("Delete server object failed: " + objectKey + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();

			Debug("Delete server object success: " + objectKey + ".");
		}
		return result;
	}

	public int getCount() {
		int result = 0;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT COUNT(*) AS rowcount FROM " + SERVER_OBJECTS_TABLE);
			ResultSet rs = stmtSelect.executeQuery();

			if (rs.next()) {
				result = rs.getInt("rowcount");
			}
		} catch (SQLException e) {
			Error("Get server object count failed by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	/**
	 * <pre>
	 *  Used to get all object keys for Full sync or update offline by sync engine 
	 *  Note: Don't require lock , no multi-thread accessing.
	 * </pre>
	 * 
	 * @return a list of object keys in ServerFiles
	 * @throws ObjectException
	 */
	public List<String> getObjectKeys() throws ObjectException {
		List<String> result = new ArrayList<String>();

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT objectKey FROM " + SERVER_OBJECTS_TABLE);
			ResultSet rs = stmtSelect.executeQuery();

			while (rs.next()) {
				result.add(rs.getString("objectKey"));
			}

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Get server object count failed by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}
	
	public List<FileObject> getObjectKeys(String folderName) throws ObjectException {
		List<FileObject> result = new ArrayList<FileObject>();

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT objectKey,isFolder FROM " + SERVER_OBJECTS_TABLE + " where objectKey like ?");
			stmtSelect.setString(1, folderName + "/%");
			
			ResultSet rs = stmtSelect.executeQuery();

			while (rs.next()) {
				FileObject obj = new FileObject();
				obj.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				obj.objectKey = rs.getString("objectKey");
				result.add(obj);
			}

			if (null != stmtSelect) {
				stmtSelect.close();
			}
			stmtSelect = null;

		} catch (SQLException e) {
			Error("Get server object count failed by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}

	public HashMap<String,FileObject> getAll() {
		HashMap<String,FileObject> result = new HashMap<String,FileObject>();

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.readLock().lock();

		try {
			PreparedStatement stmtSelect = null;

			stmtSelect = connection.prepareStatement("SELECT * FROM " + SERVER_OBJECTS_TABLE);
			ResultSet rs = stmtSelect.executeQuery();

			while(rs.next()) {
				FileObject fo = new FileObject();
				fo.isFolder = (1 == rs.getInt("isFolder")) ? true : false;
				fo.modifiedDate = rs.getLong("modifiedDate");
				fo.objectKey = rs.getString("objectKey");
				fo.MD5 = rs.getString("MD5");
				fo.sequence = rs.getInt("sequence");
				result.put(fo.objectKey, fo);
			}

			if (null != stmtSelect)
				stmtSelect.close();
			stmtSelect = null;

			if (null != rs)
				rs.close();
			rs = null;
		} catch (SQLException e) {
			Error("Get all server object failed by SQLException:" + e.getMessage());
		} finally {
			lock.readLock().unlock();
		}

		return result;
	}
	
	public boolean clear() throws EventException {
		boolean result = false;

		if (DataBaseHandler.getInstance().isDisconneced())
			return result;

		lock.writeLock().lock();

		try {
			PreparedStatement stmtDelete = null;

			stmtDelete = connection.prepareStatement("DELETE FROM " + SERVER_OBJECTS_TABLE);
			stmtDelete.executeUpdate();

			if (null != stmtDelete) {
				stmtDelete.close();
			}
			stmtDelete = null;
			result = true;
		} catch (SQLException e) {
			Error("clear table failed:" + SERVER_OBJECTS_TABLE + " by SQLException:" + e.getMessage());
		} finally {
			lock.writeLock().unlock();
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
