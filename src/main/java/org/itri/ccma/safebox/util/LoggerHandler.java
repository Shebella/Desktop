package org.itri.ccma.safebox.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class LoggerHandler {
	public static final int INFO = 0;
	public static final int DBG = 1;
	public static final int WARN = 2;
	public static final int ERR = 3;
	public static final int WARN_LEVEL_MAX = ERR;
	public static final String warnText[] = { "INFO", "DBG", "WARN", "ERR" };
	
	public enum LoggerType {Event, Main, CSS, Root};
	
	private String logPath = "";

	private static Logger _eventLogger = null;
	private static Logger _mainLogger  = null;
	private static Logger _cssLogger   = null;
	
	private static LoggerHandler instance = null;
	
	public static LoggerHandler getInstance() {
		if (instance == null)
			instance = new LoggerHandler();
		return instance;
	}
	
	public void log(LoggerType type, int level, String msg) {		
		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		String className = caller.getClassName();
		int lastDot = className.lastIndexOf(".");
		if (0 < lastDot)
			className = className.substring(++lastDot); 
	
		msg = className+": "+msg;
		write(type, level, msg);
	}
	
	private void write(LoggerType type,int level, String msg) {
		Logger logger = getLogger(type);
				
		switch(level){
			case INFO:
				logger.info(msg);
			break;
			case DBG:
				logger.debug(msg);
			break;
			case ERR:
				logger.error(msg);
			break;
			case WARN:
				logger.warn(msg);
			break;
			default:
				logger.debug(msg);
			break;
		}	
	}
	
	public void setReferece(String path){
		
		if (path == null){
			_eventLogger = Logger.getLogger(LoggerType.Event.toString());
			_mainLogger = Logger.getLogger(LoggerType.Main.toString());
			_cssLogger = Logger.getLogger(LoggerType.CSS.toString());
			return;
		}
		
		String current = null;
		try {
			current = new java.io.File( "." ).getCanonicalPath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(current+"\\log4j.properties"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		props.setProperty("log4j.appender.EventAppender.File", path+".event.log");
		props.setProperty("log4j.appender.MainAppender.File", path+".main.log");
		props.setProperty("log4j.appender.CSSAppender.File", path+".css.log");
		
		PropertyConfigurator.configure(props);
		
		_eventLogger = Logger.getLogger(LoggerType.Event.toString());
		_mainLogger = Logger.getLogger(LoggerType.Main.toString());
		_cssLogger = Logger.getLogger(LoggerType.CSS.toString());
	}

	private LoggerHandler() {
		// clean old logs
		Logger rootLogger = Logger.getRootLogger();
		FileAppender appender = (FileAppender) rootLogger.getAppender("fileAppender");
		if (appender != null) {
			logPath = appender.getFile().replace('/', '\\');
			// Truncate log file
			FileUtil.emptyFile(logPath);
		}
	}
	
	public Logger getLogger(LoggerType type) {
		Logger logger = null;
		
		if (type.equals(LoggerType.Main))
			logger = _mainLogger;
		else if (type.equals(LoggerType.Event))
			logger = _eventLogger;
		else if (type.equals(LoggerType.CSS))
			logger = _cssLogger;
		else
			logger = Logger.getRootLogger();
		
		if (logger == null)
			logger = Logger.getRootLogger();
		
		return logger;
	}
	
	public void debug(LoggerType type, String msg) {
		Logger logger = getLogger(type);		
		logger.debug(msg);
	}
	
	public void info(LoggerType type, String msg) {
		Logger logger = getLogger(type);		
		logger.info(msg);
	}
	
	public void error(LoggerType type, String msg) {
		Logger logger = getLogger(type);		
		logger.error(msg);
	}
}
