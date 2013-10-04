package org.itri.ccma.safebox.s3;

import org.itri.ccma.safebox.util.LoggerHandler;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.multi.event.CreateObjectsEvent;
import org.jets3t.service.multi.event.DownloadObjectsEvent;
import org.jets3t.service.multi.event.ServiceEvent;
import org.jets3t.service.multi.s3.S3ServiceEventAdaptor;

public class S3EventAdaptor extends S3ServiceEventAdaptor {

	private boolean cancelFlag = false;
	private StorageObject currentObject = null;

	public S3EventAdaptor() {
	}

	public void setObject(final StorageObject event_obj) {
		currentObject = event_obj;
	}

	private void displayIgnoredErrors(ServiceEvent event) {
		if (ServiceEvent.EVENT_IGNORED_ERRORS == event.getEventCode()) {
			Throwable[] throwables = event.getIgnoredErrors();
			for (int i = 0; i < throwables.length; i++) {
				Info("Ignoring error: " + throwables[i].getMessage());
			}
		}
	}

	public void cancelTask() {
		cancelFlag = true;
	}

	public boolean isCanceled() {
		return cancelFlag;
	}

	public int ConverNum(char ch) {
		int n = 0;

		if (ch >= '0' && ch <= '9')
			n = ch - '0';
		else if (ch >= 'A' && ch <= 'F')
			n = 10 + ch - 'A';
		else if (ch >= 'a' && ch <= 'f')
			n = 10 + ch - 'a';
		return n;
	}

	private void closeObject() {
		if (null != currentObject) {
			try {
				currentObject.closeDataInputStream();
			} catch (Exception e) {
				System.err.println("=== close object's input stream error: " + e.getMessage());
				e.printStackTrace();
			}
		}

		currentObject = null;
	}

	@Override
	public void event(CreateObjectsEvent event) {
		super.event(event);
		displayIgnoredErrors(event);

		if (ServiceEvent.EVENT_STARTED == event.getEventCode()) {
			if (cancelFlag) {
				event.getThreadWatcher().cancelTask();
			}
		}

		if (ServiceEvent.EVENT_IN_PROGRESS == event.getEventCode()) {
			if (cancelFlag) {
				event.getThreadWatcher().cancelTask();
			}
		}

		if (ServiceEvent.EVENT_COMPLETED == event.getEventCode()) {
			closeObject();
		}

		if (ServiceEvent.EVENT_ERROR == event.getEventCode()) {
			Info("PutObject error: " + event.getErrorCause().getMessage());
		}
	}

	@Override
	public void event(DownloadObjectsEvent event) {
		super.event(event);
		displayIgnoredErrors(event);

		if (ServiceEvent.EVENT_STARTED == event.getEventCode()) {
			if (cancelFlag) {
				event.getThreadWatcher().cancelTask();
			}
		}

		if (ServiceEvent.EVENT_IN_PROGRESS == event.getEventCode()) {
			if (cancelFlag) {
				event.getThreadWatcher().cancelTask();
			}
		}

		if (ServiceEvent.EVENT_COMPLETED == event.getEventCode()) {
			closeObject();
		}

		if (ServiceEvent.EVENT_ERROR == event.getEventCode()) {
			Info("GetObject error: " + event.getErrorCause().getMessage());
		}
	}
	
	public void Error(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.ERR, msg);
	}
	public void Info(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.INFO, msg);
	}
	public void Debug(String msg){
		LoggerHandler.getInstance().log(LoggerHandler.LoggerType.CSS, LoggerHandler.DBG, msg);
	}
}
