package org.itri.ccma.safebox.ui;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Observable;
import java.util.Observer;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.itri.ccma.safebox.Command;
import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.Main;
import org.itri.ccma.safebox.SyncThread;
import org.itri.ccma.safebox.Config.KEYS;

public class TrayIconHandler extends TrayIcon implements MouseListener, ActionListener, Observer {
	private static ImageIcon _icon = new ImageIcon(IGlobal.APP_PATH + IGlobal.APP_ICON);
	private static TrayIconHandler _instance = new TrayIconHandler();

	private static final String _AC_OPEN_FOLDER = "openFolder";
	private static final String _AC_OPEN_WEB = "openWeb";
	private static final String _AC_PAUSE_RESUME = "pauseResume";
	private static final String _AC_PREFERENCE = "preference";
	private static final String _AC_HELP = "help";
	private static final String _AC_EXIT = "exit";

	private PopupMenu _popupMenu = new PopupMenu();
	private BufferedImage[] _imageArray = null;
	private ConfigDialog _configDlg = null;

	private TrayIconHandler() {
		super(_icon.getImage(), IGlobal.APP_FULL_NAME);
		readImage();
		configGUI();
	}

	public static TrayIconHandler getInstance() {
		return _instance;
	}

	public boolean register() {
		// Init Dialogs
		_configDlg = ConfigDialog.getInstance();
		// Event registration
		SyncThread syncThread = SyncThread.getInstance();
		syncThread.addObserver(this);
		syncThread.addObserver(_configDlg);

		try {
			SystemTray systemTray = SystemTray.getSystemTray();
			systemTray.add(this);
		} catch (AWTException e) {
			e.printStackTrace();
			System.out.println("TrayIcon could not be added.");
			return false;
		}

		return true;
	}

	public void remove() {
		_configDlg.Close();
		_configDlg.dispose();

		SystemTray systemTray = SystemTray.getSystemTray();
		systemTray.remove(this);
	}

	private void readImage() {
		BufferedImage image = null;
		String[] fileNames = new String[5];

		_imageArray = new BufferedImage[5];

		fileNames[0] = "safebox.png";
		fileNames[1] = "safebox_disconnect.png";
		fileNames[2] = "safebox_syncing.png";
		fileNames[3] = "safebox_stopped.png";
		fileNames[4] = "safebox_processing.png";

		for (int i = 0; i < 5; i++) {
			try {
				image = ImageIO.read(new File(IGlobal.APP_PATH + fileNames[i]));
				_imageArray[i] = image;
			} catch (IOException e) {
				e.printStackTrace();
				_imageArray[i] = null;
			}
		}
	}

	private void configGUI() {
		// Create a popup menu components
		MenuItem item1 = new MenuItem("Open Safebox Folder");
		MenuItem item2 = new MenuItem("Launch Safebox Website");
		MenuItem item3 = new MenuItem("Pause Syncing");
		MenuItem item8 = new MenuItem("20 GB user space");
		MenuItem item4 = new MenuItem("Connecting..."); // "Sync Status"
		MenuItem item5 = new MenuItem("Preferences...");
		MenuItem item6 = new MenuItem("Help Center");
		MenuItem item7 = new MenuItem("Exit");

		item1.setActionCommand(_AC_OPEN_FOLDER);
		item2.setActionCommand(_AC_OPEN_WEB);
		item3.setActionCommand(_AC_PAUSE_RESUME);
		item5.setActionCommand(_AC_PREFERENCE);
		item6.setActionCommand(_AC_HELP);
		item7.setActionCommand(_AC_EXIT);

		item1.addActionListener(this);
		item2.addActionListener(this);
		item3.addActionListener(this);
		item5.addActionListener(this);
		item6.addActionListener(this);
		item7.addActionListener(this);

		item4.setEnabled(false);
		// item8.setEnabled(false);

		_popupMenu.removeAll();

		_popupMenu.add(item1); // Index: 0
		_popupMenu.add(item2);
		_popupMenu.add(item3);
		_popupMenu.addSeparator();
		_popupMenu.add(item8);
		_popupMenu.addSeparator(); // Index: 5
		_popupMenu.add(item4);
		_popupMenu.addSeparator();
		_popupMenu.add(item5);
		_popupMenu.add(item6);
		_popupMenu.addSeparator(); // Index: 10
		_popupMenu.add(item7);

		this.setPopupMenu(_popupMenu);
	}

	public void changeIcon(IGlobal.APP_STATE currentState) {
		try {
			this.setImage(_imageArray[currentState.ordinal()]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	// Interface: MouseListener
	public void mouseClicked(MouseEvent e) {
		if (3 == e.getButton()) {
			updateSpaceRatio();
		}
	}

	@Override
	// Interface: MouseListener
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	// Interface: MouseListener
	public void mouseExited(MouseEvent e) {
	}

	@Override
	// Interface: MouseListener
	public void mousePressed(MouseEvent e) {
	}

	@Override
	// Interface: MouseListener
	public void mouseReleased(MouseEvent e) {
	}

	@Override
	// Interface: ActionListener
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		Config config = Config.getInstance();
		// JOptionPane.showMessageDialog(null, command);

		if (command.equals(_AC_OPEN_FOLDER))
			openFolder(config.getValue(KEYS.SafeBoxLocation));
		else if (command.equals(_AC_OPEN_WEB))
			openWeb(config.GetWebURL());
		else if (command.equals(_AC_PAUSE_RESUME)) {
			boolean pauseStatus = SyncThread.getInstance().isPaused();
			pauseStatus = !pauseStatus;

			_popupMenu.getItem(2).setLabel(pauseStatus ? "Resume Syncing" : "Pause Syncing");
			changeIcon(pauseStatus ? IGlobal.APP_STATE.PAUSED : IGlobal.APP_STATE.NORMAL);

			ConfigDialog configDlg = ConfigDialog.getInstance();
			configDlg.setPauseStatus(pauseStatus);
			SyncThread.getInstance().setPause(pauseStatus);
		} else if (command.equals(_AC_PREFERENCE)) {
			ConfigDialog configDlg = ConfigDialog.getInstance();

			configDlg.dlgOpened = true;
			configDlg.updateStatusField();
			configDlg.Open(0, true);
			
			/*if (!configDlg.dlgOpened) {
				configDlg.dlgOpened = true;
				configDlg.SetConfig(_config);
				configDlg.updateStatusField(_config);
				configDlg.Open(0, true);
			}*/
		} else if (command.equals(_AC_HELP))
			openWeb(IGlobal.APP_HELP_WEB_ADDR);
		else if (command.equals(_AC_EXIT))
			Main.shutdownLatch.countDown();
	}

	private void openFolder(String path) {
		Boolean launched = false;
		File f;

		if (Desktop.isDesktopSupported() == false)
			return;

		if (Config.IsWinOS()) {
			try {
				Runtime.getRuntime().exec("explorer.exe " + path);
				launched = true;
			} catch (IOException e) {
			}
		}

		if (launched == true) {
			return;
		}

		Desktop desktop = Desktop.getDesktop();

		if (desktop != null) {
			try {
				f = new File(path);
				if (f.exists() && f.isDirectory()) {
					desktop.open(f);
				}
			} catch (IOException e) {
			}
		}
	}

	private void openWeb(String addr) {
		if (Desktop.isDesktopSupported() == false)
			return;
		
		Desktop desktop = Desktop.getDesktop();
		
		if (desktop != null) {
			try {
				desktop.browse(new URI(addr));
			} catch (URISyntaxException e) {
			} catch (IOException e) {
			}
		}
	}

	public void updateSpaceRatio() {
		String strMsg = Command.BUCKET_INFO.GetUsedRateString();
		_popupMenu.getItem(4).setLabel(strMsg.isEmpty() ? "20 GB user space" : strMsg);
	}

	public void showMessage(String caption, String message) {
		this.displayMessage(caption, message, MessageType.INFO);
	}

	@Override
	// Interface: Observer
	public void update(Observable o, Object arg) {
		changeIcon(IGlobal.appState);

		if (IGlobal.appState.equals(IGlobal.APP_STATE.PAUSED))
			_popupMenu.getItem(2).setLabel("Resume Syncing");
		else if (IGlobal.appState.equals(IGlobal.APP_STATE.NORMAL)) {
			_popupMenu.getItem(2).setEnabled(true);
			_popupMenu.getItem(2).setLabel("Pause Syncing");
			_popupMenu.getItem(6).setLabel("Connected");
		} else if (IGlobal.appState.equals(IGlobal.APP_STATE.DISCONNECT)) {
			_popupMenu.getItem(2).setEnabled(false);
			_popupMenu.getItem(6).setLabel("Disconnected");
		}

		_popupMenu.getItem(6).setLabel((String) arg);
		this.setToolTip(IGlobal.APP_FULL_NAME + "\n" + (String) arg);
	}
}
