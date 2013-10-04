package org.itri.ccma.safebox.db;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.db.ConnectionFactory.SourceType;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.Util;

/**
 * LOCAL_TABLE 並不 Backup 到 HardDisk, 也就是說, 下次程式重起, 只做 updateOfflineEvents(), 並不 Restore from HardDisk.
 * SERVER_TABLE & SERVER_OBJECTS_TABLE 會需要作 HardDisk's Backup & Restore.
 **/
public class DataBaseHandler {
	private static final String SQL_LOCAL_STMT = "timestamp BIGINT NOT NULL, objectKey TEXT NOT NULL, oldObjectKey TEXT, sequence INTEGER NOT NULL, MD5 TEXT NOT NULL, isFolder INTEGER NOT NULL, fileAction TEXT NOT NULL, modifiedDate BIGINT NOT NULL, state TEXT NOT NULL, retry INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (timestamp,objectKey,state)";
	private static final String SQL_SERVER_STMT = "syncID BIGINT NOT NULL, objectKey TEXT NOT NULL, oldObjectKey TEXT,  sequence INTEGER NOT NULL, MD5 TEXT NOT NULL, isFolder INTEGER NOT NULL, fileAction TEXT NOT NULL, modifiedDate BIGINT NOT NULL, state TEXT NOT NULL, retry INTEGER NOT NULL DEFAULT 0, fileSize BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (syncID,objectKey,state)";
	private static final String SQL_OBJECT_STMT = "objectKey TEXT PRIMARY KEY NOT NULL UNIQUE, sequence INTEGER NOT NULL, MD5 TEXT NOT NULL, isFolder INTEGER NOT NULL, modifiedDate BIGINT NOT NULL";

	private static DataBaseHandler instance = null;
	private Connection connection = null;

	private DataBaseHandler() {
	}

	public static DataBaseHandler getInstance() {
		if (null == instance) {
			instance = new DataBaseHandler();
		}
		return instance;
	}

	public Connection getConnection() {
		return connection;
	}

	public boolean init() {
		boolean isDone = false;

		// Connection connectionDisk =
		// ConnectionFactory.getInstance().createConnection(ON_HARDDISK);
		Connection connectionDisk = ConnectionFactory.getConnection(SourceType.HARDDISK);
		Statement stmt = null;

		// Create Disk tables
		try {
			stmt = connectionDisk.createStatement();

			StringBuilder createSql1 = new StringBuilder("CREATE TABLE IF NOT EXISTS " + EventQueueHandler.LOCAL_TABLE + " (").append(SQL_LOCAL_STMT).append(")");
			stmt.executeUpdate(createSql1.toString());

			StringBuilder createSql2 = new StringBuilder("CREATE TABLE IF NOT EXISTS " + EventQueueHandler.SERVER_TABLE + " (").append(SQL_SERVER_STMT).append(")");
			stmt.executeUpdate(createSql2.toString());

			StringBuilder createSql3 = new StringBuilder("CREATE TABLE IF NOT EXISTS " + ServerObjectHandler.SERVER_OBJECTS_TABLE + " (").append(SQL_OBJECT_STMT).append(")");
			stmt.executeUpdate(createSql3.toString());

			if (null != stmt)
				stmt.close();
			stmt = null;

			if (null != connectionDisk)
				connectionDisk.close();
			connectionDisk = null;

		} catch (SQLException e) {
			Error(Util.getStackTrace(e));
		}

		// Create Memory tables
		try {
			if (null != connection)
				return isDone;

			// connection =
			// ConnectionFactory.getInstance().createConnection(IN_MEMORY);
			connection = ConnectionFactory.getConnection(SourceType.IN_MEMORY);

			stmt = connection.createStatement();

			stmt.execute("PRAGMA journal_mode = OFF");
			// statement.execute("PRAGMA temp_store = MEMORY");
			// statement.execute("PRAGMA cache_size = 8000");
			stmt.execute("PRAGMA synchronous = OFF");
			stmt.execute("PRAGMA case_sensitive_like = TRUE");

			StringBuilder createSql1 = new StringBuilder("CREATE TABLE " + EventQueueHandler.LOCAL_TABLE + " (").append(SQL_LOCAL_STMT).append(")");
			stmt.executeUpdate(createSql1.toString());

			StringBuilder createSql2 = new StringBuilder("CREATE TABLE " + EventQueueHandler.SERVER_TABLE + " (").append(SQL_SERVER_STMT).append(")");
			stmt.executeUpdate(createSql2.toString());

			StringBuilder createSql3 = new StringBuilder("CREATE TABLE " + ServerObjectHandler.SERVER_OBJECTS_TABLE + " (").append(SQL_OBJECT_STMT).append(")");
			stmt.executeUpdate(createSql3.toString());

			StringBuilder sql = new StringBuilder();
			sql.append("ATTACH DATABASE \"").append(Config.dataRootPath).append(File.separator).append(Config.DB_NAME).append("\" AS tempDB");

			stmt.execute(sql.toString());

			// int intCount1 = stmt.executeUpdate("INSERT INTO " +
			// EventQueueHandler.LOCAL_TABLE + " SELECT * FROM tempDB." +
			// EventQueueHandler.LOCAL_TABLE);
			int intCount2 = stmt.executeUpdate("INSERT INTO " + EventQueueHandler.SERVER_TABLE + " SELECT * FROM tempDB." + EventQueueHandler.SERVER_TABLE);
			int intCount3 = stmt.executeUpdate("INSERT INTO " + ServerObjectHandler.SERVER_OBJECTS_TABLE + " SELECT * FROM tempDB." + ServerObjectHandler.SERVER_OBJECTS_TABLE);

			isDone = true;
			// Debug("Restore Harddisk DB to Memory DB done for LocalEvents(" +
			// intCount1 + ") & ServerEvents(" + intCount2 + ") & ServerFiles("
			// +intCount3 +").");
			Debug("Restore Harddisk DB to Memory DB done for ServerEvents(" + intCount2 + ") & ServerFiles(" + intCount3 + ").");

			if (null != stmt)
				stmt.close();
			stmt = null;

		} catch (SQLException e) {
			Error(Util.getStackTrace(e));
		}

		LocalQueueHandler.getInstance().initConnection();
		ServerQueueHandler.getInstance().initConnection();
		ServerObjectHandler.getInstance().initConnection();

		return isDone;
	}

	public boolean shutdown() {
		boolean result = false;

		Statement stmt = null;
		try {
			stmt = connection.createStatement();

			stmt.executeUpdate("DELETE FROM tempDB. " + EventQueueHandler.LOCAL_TABLE);
			stmt.executeUpdate("DELETE FROM tempDB. " + EventQueueHandler.SERVER_TABLE);

			stmt.executeUpdate("DELETE FROM tempDB. " + ServerObjectHandler.SERVER_OBJECTS_TABLE);

			int intCount1 = stmt.executeUpdate("INSERT INTO tempDB. " + EventQueueHandler.LOCAL_TABLE + " SELECT * FROM  " + EventQueueHandler.LOCAL_TABLE);
			int intCount2 = stmt.executeUpdate("INSERT INTO tempDB. " + EventQueueHandler.SERVER_TABLE + " SELECT * FROM  " + EventQueueHandler.SERVER_TABLE);
			int intCount3 = stmt.executeUpdate("INSERT INTO tempDB. " + ServerObjectHandler.SERVER_OBJECTS_TABLE + " SELECT * FROM  " + ServerObjectHandler.SERVER_OBJECTS_TABLE);

			ResultSet rs = stmt.executeQuery("Select Count(*) from " + EventQueueHandler.SERVER_TABLE + " where state='Error'");
			int intCount4 = rs.getInt(1);

			rs = stmt.executeQuery("Select Count(*) from " + EventQueueHandler.LOCAL_TABLE + " where state='Error'");
			int intCount5 = rs.getInt(1);

			if (null != rs)
				rs.close();
			rs = null;

			Debug("Backup memory DB to Harddisk DB done for LocalErrorEvents(" + intCount5 + ") & LocalEvents(" + intCount1 + ") & ServerErrorEvents(" + intCount4
			        + ") & ServerEvents(" + intCount2 + ") & ServerFiles(" + intCount3 + ").");
			// Debug("Backup memory DB to Harddisk DB done for ServerEvents(" +
			// intCount2 + ") & ServerFiles(" +intCount3 +").");

			result = true;

			if (null != stmt)
				stmt.close();
			stmt = null;

			if (connection != null)
				connection.close();

			connection = null;

		} catch (SQLException e) {
			Error(Util.getStackTrace(e));
		}

		return result;
	}

	public boolean beginTransaction() {
		boolean isDone = false;
		if (isDisconneced())
			return isDone;

		PreparedStatement pstmt = null;
		try {
			pstmt = connection.prepareStatement("BEGIN TRANSACTION");
			isDone = pstmt.execute();

			if (null != pstmt)
				pstmt.close();
			pstmt = null;

		} catch (SQLException e) {
			Error(Util.getStackTrace(e));
		}

		return isDone;
	}

	public boolean endTransaction(boolean isCommit) {
		boolean isDone = false;
		if (isDisconneced())
			return isDone;

		PreparedStatement pstmt = null;
		try {
			if (isCommit)
				pstmt = connection.prepareStatement("COMMIT TRANSACTION");
			else
				pstmt = connection.prepareStatement("ROLLBACK TRANSACTION");

			isDone = pstmt.execute();

			if (null != pstmt)
				pstmt.close();
			pstmt = null;
		} catch (SQLException e) {
			Error(Util.getStackTrace(e));
		}
		return isDone;
	}

	public boolean isDisconneced() {
		boolean result = false;
		if (null != connection) {
			try {
				if (connection.isClosed()) {
					Debug("DB connection is closed, retry to create new connection");
					result = true;
				}
			} catch (SQLException e) {
				Error(Util.getStackTrace(e));
			}
		} else {
			result = true;
			Debug("DB connection is null, retry to create new connection");
		}
		return result;
	}

	/*
	 * private boolean vacuumDB() throws SQLException{ boolean result = false;
	 * PreparedStatement pstmt = null;
	 * 
	 * if (!isConnected()){
	 * LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main,
	 * LoggerHandler.DBG, "isConnected failed"); return result; }
	 * 
	 * try { pstmt = connection.prepareStatement("VACUUM " +
	 * EventQueueHandler.LOCAL_TABLE); pstmt.execute();
	 * 
	 * pstmt = connection.prepareStatement("VACUUM " +
	 * EventQueueHandler.SERVER_TABLE); pstmt.execute();
	 * 
	 * pstmt = connection.prepareStatement("VACUUM " +
	 * ServerObjectHandler.SERVER_OBJECTS_TABLE); pstmt.execute();
	 * 
	 * result = true; } finally { if (null != pstmt) pstmt.close(); pstmt =
	 * null; } return result; }
	 */

	public void Info(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.INFO, msg);
	}

	public void Debug(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.DBG, msg);
	}

	public void Error(String msg) {
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.Main, LoggerHandler.ERR, msg);
	}
}
