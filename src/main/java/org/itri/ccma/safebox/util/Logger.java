package org.itri.ccma.safebox.util;

import java.io.IOException;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

public class Logger
{
  public org.apache.log4j.Logger log4jLogger = null;
  
  private static final String _LOG_DIR = "./log/";
  
  private String _name   = "";
  private String _format = "%d{MM/dd HH:mm:ss} %-5p %x -> %m%n";
	
  public Logger(String name)
  {
    _name = name;
    log4jLogger = org.apache.log4j.Logger.getLogger(_name);
  }
	
  public void setFormat(String format)
  {
    _format = format;
  }
	
  public void setFilename(String dir, String filename)
  {
    setFilename(dir + "/" + filename);
  }
	
  public void setFilename(String filename)
  {
    try
    {
      Layout   layout       = new PatternLayout(_format);
      Appender fileAppender = new RollingFileAppender(layout, _LOG_DIR + filename);
      log4jLogger.addAppender(fileAppender);
      log4jLogger.setAdditivity(false);
      org.apache.log4j.BasicConfigurator.configure(fileAppender); 
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }
	
  public void debug(String message)
  {
    log4jLogger.debug(message);
  }
  
  public void info(String message)
  {
    log4jLogger.info(message);
  }
  
  public void warn(String message)
  {
    log4jLogger.warn(message);
  }
  
  public void error(String message)
  {
    log4jLogger.error(message); 	
  }

  public void fatal(String message)
  {
    log4jLogger.fatal(message);
  }
}
