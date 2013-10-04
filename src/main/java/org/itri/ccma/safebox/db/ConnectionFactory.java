package org.itri.ccma.safebox.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.itri.ccma.safebox.Config;
import org.sqlite.SQLiteConfig;

public class ConnectionFactory {
	public static enum SourceType {IN_MEMORY, HARDDISK}
	
	private static final String JDBC_NAME = "org.sqlite.JDBC";
	private static final String PREFIX = "jdbc:sqlite:";
	private static final String URI = "jdbc:sqlite::memory:";
		
	public static Connection getConnection(SourceType sourceType) {
		try {
			Class.forName(JDBC_NAME);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		if (SourceType.IN_MEMORY.equals(sourceType)) {
			return getConnectionInMemory();
		} else if (SourceType.HARDDISK.equals(sourceType)) {		
			return getConnectionOnHarddisk();
		}
		
		return null;
	}
	
	private static Connection getConnectionInMemory() {
		Connection connection = null;
		
		try {
			SQLiteConfig config = new SQLiteConfig();
			config.setSharedCache(true);
			
			connection = DriverManager.getConnection(URI, config.toProperties());
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return connection;
	}
	
	private static Connection getConnectionOnHarddisk() {
		Connection connection = null;
		
		try {
			StringBuilder tmpStr = new StringBuilder(PREFIX);
			tmpStr.append(Config.dataRootPath).append(File.separator).append(Config.DB_NAME);

			connection = DriverManager.getConnection(tmpStr.toString());
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return connection;
	}
}
