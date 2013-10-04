package org.itri.ccma.safebox.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.itri.ccma.safebox.CSSHandler;
import org.itri.ccma.safebox.Command;
import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.Config.KEYS;
import org.itri.ccma.safebox.IGlobal;
import org.itri.ccma.safebox.JNotifyHandler;
import org.itri.ccma.safebox.Main;
import org.itri.ccma.safebox.SyncThread;
import org.itri.ccma.safebox.util.DownloadUtil;
import org.itri.ccma.safebox.util.LoggerHandler;
import org.itri.ccma.safebox.util.LoggerHandler.LoggerType;
import org.itri.ccma.safebox.util.ServerConnectionUtil;
import org.itri.ccma.safebox.util.Util;

public class ConfigDialog extends JFrame implements ActionListener, Observer {
	private static final long serialVersionUID = 1L;
	private static final String _AC_LOGIN = "login";
	private static final String _AC_CLOSE = "close";
	private static final String _AC_SELECT = "select";
	private static final String _AC_DOWNLOAD = "download";
	private static final String ENV_REG_PATH = "HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
	private static final String PROCESSOR_ARCHITECTURE = "PROCESSOR_ARCHITECTURE";
//	private static final String RUN_REG_PATH = "HKEY_LOCAL_MACHINE\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
	
	private static ConfigDialog _instance = new ConfigDialog();

	private static final int TREE_ROW_HEIGHT = 20;

	private Config _config = Config.getInstance();
	
	private Border empty;
	private JTextField idField, statusField, reasonField, rootPathField;
	private JPasswordField passField;
	private JLabel appLabel, webLabel, noticeLabel, ipLabel;
	private DefaultMutableTreeNode treeTop = null;
	private JButton loginButton, closeButton;
	private JButton rootPathButton = null;
	private JTabbedPane tabbedPane;
	private String password = "";
	private Color normalColor = new Color(0, 128, 0);
	private Color enhancedColor = new Color(160, 64, 64);
	private Color grayColor = new Color(140, 140, 140);
	private JTree folderTree = null;
	private Boolean wizardMode = false;
	private Boolean hasLogin = false;
	private Boolean hasPaused = false;
	private Boolean hasPackGui = false;
	// private Boolean hasFolderPropertyChange = false;
	public Boolean dlgOpened = false;
	private int curTab = -1;
	private int watchID = -1;
	private JPanel attrPanelMain = null;
	private JPanel attrPanelSub1 = null;
	private JSplitPane attrPanelSub2 = null;
	private JButton downloadBtn = null;
	private JLabel checkLabel = null;
	private LoggerHandler _logger = LoggerHandler.getInstance();

	public static ConfigDialog getInstance() {
		return _instance;
	}

	private ConfigDialog() {
		super("Safebox");

		TitledBorder titled;
		String noticeTitle;

		noticeTitle = "IP Policy Statement<BR>";
		noticeTitle += "GUIDELINES ON THE MANAGEMENT AND IMPLEMENTATION<BR>";
		noticeTitle += "OF INTELLECTUAL PROPERTY RIGHTS";

		// set icon
		try {
			BufferedImage image = null;
			image = ImageIO.read(new File(IGlobal.APP_PATH + IGlobal.APP_ICON));
			setIconImage(image);
		} catch (IOException e) {
		}

		// disable resize
		setResizable(false);

		// init variables
		wizardMode = true;
		empty = BorderFactory.createEmptyBorder();

		// init data fields
		idField = new JTextField(10);
		passField = new JPasswordField(20);
		statusField = new JTextField(10);
		reasonField = new JTextField(100);
		statusField.setBorder(empty);
		statusField.setEditable(false);
		statusField.setBackground(getBackground());
		statusField.setAutoscrolls(true);
		reasonField.setBorder(empty);
		reasonField.setEditable(false);
		reasonField.setBackground(getBackground());
		reasonField.setForeground(grayColor);
		reasonField.setAutoscrolls(true);
		appLabel = new JLabel("Safebox", SwingConstants.CENTER);
		webLabel = new JLabel("http://", SwingConstants.CENTER);
		noticeLabel = new JLabel("http://", SwingConstants.LEFT);
		ipLabel = new JLabel("", SwingConstants.LEFT);
		loginButton = new JButton("Login");
		closeButton = new JButton("Close");
		treeTop = new DefaultMutableTreeNode(new TreeNodeInfo("Sync Folder", false));
		folderTree = new JTree(treeTop);
		rootPathField = new JTextField(28);
		tabbedPane = new JTabbedPane();

		appLabel.setText(IGlobal.APP_FULL_NAME + " - 2012.09.03");
		webLabel.setText("<html><font color=\"#0000CF\"><u>" + "Help Center" + "</u></font></html>");
		webLabel.setToolTipText(IGlobal.APP_HELP_WEB_ADDR);
		noticeLabel.setText("<html><font color=\"#3c72C9\"><u>" + noticeTitle + "</u></font></html>");
		noticeLabel.setToolTipText(IGlobal.APP_NOTICE_WEB_ADDR);

		// Tab panels:
		JPanel accountPanel = createAccountPanel();
		JPanel attrPanel = createAttrPanel();
		JPanel aboutPanel = createAboutPanel();
		tabbedPane.addTab("Account", new ImageIcon(IGlobal.APP_PATH + "tab_account.png"), accountPanel, null);
//		tabbedPane.addTab("Folders", new ImageIcon(IGlobal.APP_PATH + "tab_folder.png"), attrPanel, null);
		tabbedPane.addTab("About", new ImageIcon(IGlobal.APP_PATH + "tab_about.png"), aboutPanel, null);
		attrPanelMain = attrPanel;

		// Control panel
		JPanel buttonPanel = new JPanel();
		JPanel subPanel = new JPanel(new GridLayout(1, 5, 16, 16));
		BoxLayout layout = new BoxLayout(buttonPanel, BoxLayout.Y_AXIS);

		buttonPanel.setLayout(layout);
		subPanel.add(webLabel);
		subPanel.add(new JLabel(""));
		subPanel.add(new JLabel(""));
		subPanel.add(loginButton);
		subPanel.add(closeButton);
		titled = BorderFactory.createTitledBorder(empty, "");
		addCompForBorder(titled, subPanel, buttonPanel);

		curTab = 0;
		tabbedPane.setSelectedIndex(curTab);
		getContentPane().add(tabbedPane, BorderLayout.NORTH);
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		// Listen to events from buttons.
		loginButton.setActionCommand(_AC_LOGIN);
		loginButton.addActionListener(this);
		closeButton.setActionCommand(_AC_CLOSE);
		closeButton.addActionListener(this);

		ChangeListener cl = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent evt) {

				JTabbedPane pane = (JTabbedPane) evt.getSource();
				curTab = pane.getSelectedIndex();
				idField.selectAll();
				rootPathField.selectAll();
			}
		};

		tabbedPane.addChangeListener(cl);

		KeyListener kl = new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				int keyCode = e.getKeyCode();
				if (curTab == 0 && keyCode == KeyEvent.VK_ENTER) {
					if (idField.hasFocus() && !idField.getText().isEmpty()) {
						passField.requestFocus();
					} else if (passField.hasFocus() && passField.getPassword().length > 0) {
						loginButton.requestFocus();
						ActionEvent e0 = new ActionEvent(this, 0, _AC_LOGIN);
						_instance.actionPerformed(e0);
					}
				}
			}
		};
		idField.addKeyListener(kl);
		passField.addKeyListener(kl);

		MouseAdapter ma = new MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				JLabel l = (JLabel) evt.getSource();
				String addr = l.getToolTipText();
				try {
					Desktop desktop = java.awt.Desktop.getDesktop();
					URI uri = new java.net.URI(addr);
					desktop.browse(uri);
				} catch (URISyntaxException use) {
				} catch (IOException ioe) {
				}
			}
		};
		webLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		webLabel.addMouseListener(ma);
		noticeLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		noticeLabel.addMouseListener(ma);

		ipLabel.setText(_config.getValue(KEYS.HostIP));
		idField.setText(_config.getValue(KEYS.User));
		rootPathField.setText(_config.getValue(KEYS.SafeBoxLocation));

		updateStatusField();
		CreateTreeNodes(folderTree, treeTop);
	}

	private JPanel createAccountPanel() {
		JPanel mainPanel = new JPanel();
		TitledBorder titled = BorderFactory.createTitledBorder("");
		JTextField brkLine;
		JLabel txtLabel;
		int x, y;

		mainPanel.setPreferredSize(new Dimension(450, 250));
		mainPanel.setLayout(null);

		// Login area
		x = 16;
		y = 10;
		txtLabel = new JLabel("Login");
		txtLabel.setBounds(x, y, 100, 24);
		mainPanel.add(txtLabel);

		brkLine = new JTextField();
		brkLine.setBorder(titled);
		brkLine.setBounds(x + 36, y + 10, 378, 1);
		mainPanel.add(brkLine);

		txtLabel = new JLabel("User Name : ", JLabel.RIGHT);
		txtLabel.setBounds(x, y + 30, 86, 24);
		mainPanel.add(txtLabel);

		idField.setBounds(x + 90, y + 30, 248, 24);
		mainPanel.add(idField);
		/*idText = new JLabel();
		idText.setBounds(x + 90, y + 30, 248, 24);
		idText.setText(System.getProperty("user.name"));
		mainPanel.add(idText);
		 */
		txtLabel = new JLabel("Password : ", JLabel.RIGHT);
		txtLabel.setBounds(x, y + 56, 86, 24);
		mainPanel.add(txtLabel);

		passField.setBounds(x + 90, y + 56, 248, 24);
		mainPanel.add(passField);

		// Folder area
		y = 110;
		txtLabel = new JLabel("Sync Folder");
		txtLabel.setBounds(x, y, 100, 24);
		mainPanel.add(txtLabel);

		brkLine = new JTextField();
		brkLine.setBorder(titled);
		brkLine.setBounds(x + 72, y + 10, 342, 1);
		mainPanel.add(brkLine);

		txtLabel = new JLabel("Folder Name : ", JLabel.RIGHT);
		txtLabel.setBounds(x, y + 30, 86, 24);
		mainPanel.add(txtLabel);

		rootPathField.setBounds(x + 90, y + 30, 248, 24);
		rootPathField.setEditable(false);
		mainPanel.add(rootPathField);

		rootPathButton = new JButton("Move...");
		rootPathButton.setBounds(x + 348, y + 30, 74, 23);
		mainPanel.add(rootPathButton);

		// Status area
		y = 184;
		txtLabel = new JLabel("Server Status");
		txtLabel.setBounds(x, y, 100, 24);
		mainPanel.add(txtLabel);

		brkLine = new JTextField();
		brkLine.setBorder(titled);
		brkLine.setBounds(x + 82, y + 10, 334, 1);
		mainPanel.add(brkLine);

		txtLabel = new JLabel("Service Point : ", JLabel.RIGHT);
		txtLabel.setBounds(x, y + 30, 86, 24);
		mainPanel.add(txtLabel);

		ipLabel.setText("");
		ipLabel.setBounds(x + 90, y + 30, 140, 24);
		mainPanel.add(ipLabel);

		statusField.setText("Disconnected");
		statusField.setBounds(x + 230, y + 30, 100, 22);
		mainPanel.add(statusField);

		reasonField.setText("Not logged in");
		reasonField.setBounds(x + 230, y + 48, 300, 24);
		mainPanel.add(reasonField);

		rootPathButton.setActionCommand(_AC_SELECT);
		rootPathButton.addActionListener(this);

		return mainPanel;
	}

	private class TreeNodeInfo {
		public String name;
		public Boolean encrypted;

		public TreeNodeInfo(String n, Boolean en) {
			name = n;
			encrypted = en;
		}

		public String toString() {
			return name;
		}
	}

	private class CellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;
		Icon checkIcon, uncheckIcon;

		public CellRenderer(Icon icon1, Icon icon2) {
			checkIcon = icon1;
			uncheckIcon = icon2;
		}

		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			if (isEncrypted(value)) {
				setIcon(checkIcon);
				setToolTipText("Encrypted folder");
			} else {
				setIcon(uncheckIcon);
				setToolTipText(null);
			}

			return this;
		}

		protected boolean isEncrypted(Object value) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			TreeNodeInfo nodeInfo = (TreeNodeInfo) (node.getUserObject());

			if (nodeInfo != null && nodeInfo.encrypted) {
				return true;
			}
			return false;
		}
	}

	private void TreeNodeRename(String oldName, String newName) {
		if (_config == null || _config.getValue(KEYS.SafeBoxLocation).isEmpty() || oldName.isEmpty() || newName.isEmpty()) {
			return;
		}

		JTree tree = folderTree;
		DefaultMutableTreeNode top = treeTop;
		DefaultMutableTreeNode node;
		TreeNodeInfo nodeInfo;

		for (node = (DefaultMutableTreeNode) top.getFirstChild(); node != null; node = node.getNextNode()) {
			nodeInfo = (TreeNodeInfo) node.getUserObject();
			if (nodeInfo.name.equals(oldName)) {
				nodeInfo.name = newName;
				tree.updateUI();
				break;
			}
		}
	}

	private void TreeNodeDelete(String name) {
		if (_config == null || _config.getValue(KEYS.SafeBoxLocation).isEmpty() || name.isEmpty()) {
			return;
		}

		JTree tree = folderTree;
		DefaultMutableTreeNode top = treeTop;
		DefaultMutableTreeNode node;
		TreeNodeInfo nodeInfo;

		for (node = (DefaultMutableTreeNode) top.getFirstChild(); node != null; node = node.getNextNode()) {
			nodeInfo = (TreeNodeInfo) node.getUserObject();
			if (nodeInfo.name.equals(name)) {
				top.remove(node);
				tree.updateUI();
				break;
			}
		}
	}

	private void TreeNodeCreate(String dirName) {
		if (_config == null || _config.getValue(KEYS.SafeBoxLocation).isEmpty() || dirName.isEmpty()) {
			return;
		}

		JTree tree = folderTree;
		DefaultMutableTreeNode top = treeTop;
		TreeNodeInfo nodeInfo;
		DefaultMutableTreeNode node;

		nodeInfo = new TreeNodeInfo(dirName, _config.IsEncryptDir(dirName));
		node = new DefaultMutableTreeNode(nodeInfo);
		top.add(node);

		if (tree != null) {
			tree.updateUI();
		}
	}

	private void CreateTreeNodes(JTree tree, DefaultMutableTreeNode top) {
		DefaultMutableTreeNode node = null;
		TreeNodeInfo nodeInfo;
		String dirName;

		if (tree == null || top == null) {
			return;
		}

		top.removeAllChildren();

		if (_config != null && StringUtils.isNotEmpty(_config.getValue(KEYS.SafeBoxLocation))) {
			int i;
			File dir = new File(_config.getValue(KEYS.SafeBoxLocation));
			File[] files = dir.listFiles();
			if (files != null) {
				for (i = 0; i < files.length; i++) {
					if (files[i].isDirectory() == false)
						continue;

					dirName = files[i].getName();
					nodeInfo = new TreeNodeInfo(dirName, _config.IsEncryptDir(dirName));
					node = new DefaultMutableTreeNode(nodeInfo);
					top.add(node);
				}
				if (tree != null)
					tree.expandRow(0);
			}
		}
	}

	private void ChangeTreeNodeProperty(JLabel infoLabel, JTree tree, DefaultMutableTreeNode node) {
		TreeNodeInfo nodeInfo;

		if (node != null) {
			nodeInfo = (TreeNodeInfo) node.getUserObject();
			nodeInfo.encrypted = (nodeInfo.encrypted ? false : true);

			if (tree != null) {
				tree.repaint();
			}

			if (infoLabel != null) {
				UpdateInfoLabel(infoLabel, node);
			}

			// special set _config value here..
			if (_config != null) {
				_config.SetEncryptDir(nodeInfo.name, nodeInfo.encrypted);
			}
		}
	}

	private JPanel createAttrPanel() {
		JPanel mainPanel = new JPanel();
		JPanel subPanel = new JPanel(new GridLayout(1, 2));
		BoxLayout layout = new BoxLayout(mainPanel, BoxLayout.Y_AXIS);

		mainPanel.setLayout(layout);

		final JTree tree = folderTree;
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.putClientProperty("JTree.lineStyle", "Horizontal");
		tree.setRowHeight(TREE_ROW_HEIGHT);

		ImageIcon checkIcon = new ImageIcon("folder_enc.png");
		ImageIcon uncheckIcon = new ImageIcon("folder.png");
		if (checkIcon != null) {
			CellRenderer renderer = new CellRenderer(checkIcon, uncheckIcon);
			tree.setCellRenderer(renderer);
		}
		ToolTipManager.sharedInstance().registerComponent(tree);

		// Create the scroll pane and add the tree to it.
		JScrollPane treeView = new JScrollPane(tree);

		// Create the info viewing pane.
		final JLabel infoLabel = new JLabel();
		subPanel.add(infoLabel);
		subPanel.add(new JLabel(""));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setTopComponent(treeView);
		splitPane.setBottomComponent(subPanel);

		Dimension minimumSize = new Dimension(100, 60);
		subPanel.setMinimumSize(minimumSize);
		treeView.setMinimumSize(minimumSize);
		splitPane.setDividerLocation(192);
		splitPane.setPreferredSize(new Dimension(360, 240));

		// Add the split pane to this panel.
		// mainPanel.add(splitPane);
		JPanel textPane = new JPanel();
		JLabel txtLabel = new JLabel("Sorry, you need to login first.");
		Font f = txtLabel.getFont();
		// textPane.setLocale(null);
		txtLabel.setFont(new Font(f.getFontName(), f.getStyle(), 16));
		txtLabel.setBounds(100, 40, 300, 64);
		textPane.add(txtLabel);

		attrPanelSub1 = textPane;
		attrPanelSub2 = splitPane;

		tree.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				Point pt = arg0.getPoint();
				Rectangle rc;
				int row;
				JTree t = (JTree) arg0.getSource();
				DefaultMutableTreeNode n;
				// change property if icon is hit
				if (pt.y > 16 && pt.x >= 16 /* && pt.x < 32 */&& t != null) {
					row = pt.y / TREE_ROW_HEIGHT;
					rc = t.getRowBounds(row);
					if (rc != null && pt.x < (rc.x + rc.width)) {
						n = (DefaultMutableTreeNode) t.getLastSelectedPathComponent();
						ChangeTreeNodeProperty(infoLabel, t, n);
						// hasFolderPropertyChange = true;
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent arg0) {
			}

			@Override
			public void mouseExited(MouseEvent arg0) {
			}

			@Override
			public void mousePressed(MouseEvent arg0) {
			}

			@Override
			public void mouseReleased(MouseEvent arg0) {
			}
		});

		tree.addTreeSelectionListener(new TreeSelectionListener() {

			@Override
			public void valueChanged(TreeSelectionEvent arg0) {
				DefaultMutableTreeNode n = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
				UpdateInfoLabel(infoLabel, n);
			}
		});

		return mainPanel;
	}

	private JPanel createAboutPanel() {
		JPanel mainPanel = new JPanel();
		TitledBorder titled = BorderFactory.createTitledBorder("");
		JTextField brkLine;
		JLabel txtLabel;
		Font f;
		int x, y;

		mainPanel.setPreferredSize(new Dimension(450, 250));
		mainPanel.setLayout(null);

		x = 24;
		y = 20;
		txtLabel = new JLabel("Safebox version " + IGlobal.APP_VER);
		f = txtLabel.getFont();
		txtLabel.setFont(new Font(f.getFontName(), f.getStyle(), 16));
		txtLabel.setBounds(x, y, 400, 24);
		mainPanel.add(txtLabel);

		brkLine = new JTextField();
		brkLine.setBorder(titled);
		brkLine.setBounds(x, y + 30, 374, 1);
		mainPanel.add(brkLine);

		x = 28;
		y = 60;
		f = noticeLabel.getFont();
		noticeLabel.setFont(new Font(f.getFontName(), f.getStyle(), 14));
		noticeLabel.setBounds(x, y, 480, 100);
		mainPanel.add(noticeLabel);

		downloadBtn = new JButton("Download and Install");
		downloadBtn.setEnabled(false);
		downloadBtn.setVisible(false);
		downloadBtn.setBounds(x, y + 120, 180, 23);

		checkLabel = new JLabel("Check new version", SwingConstants.LEFT);
		checkLabel.setText("<html><font color=\"#3c72C9\">check new version</font></html>");
		f = checkLabel.getFont();
		checkLabel.setFont(new Font(f.getFontName(), f.getStyle(), 12));
		checkLabel.setBounds(x, y + 100, 400, 24);
		checkLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

		downloadBtn.setActionCommand(_AC_DOWNLOAD);
		downloadBtn.addActionListener(this);

		checkLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final java.awt.event.MouseEvent evt) {

				new Timer().schedule(new TimerTask() {
					public void run() {
						try {
							downloadBtn.setVisible(false);
							checkLabel.setText("Checking new version, please wait a while...");

							ComparableVersion serverVersion = CSSHandler.getInstance().getNewClientVersion();
							ComparableVersion clientVersion = new ComparableVersion(IGlobal.APP_VER.substring(1));

							if (null != serverVersion && null != clientVersion && 0 > clientVersion.compareTo(serverVersion)) {
								checkLabel.setText("New version is v" + serverVersion + "\r\n, please click to download the new version.");

								downloadBtn.setEnabled(true);
								downloadBtn.setVisible(true);
							} else {
								checkLabel.setText("<html><font color=\"#3c72C9\">Current version is the latest, check new version again</font></html>");
							}

							setCursor(Cursor.getDefaultCursor());
						} catch (Exception e) {
							System.err.println("=== check new version on preference dialog error: " + e);
						}
					}
				}, 500);
			}
		});

		mainPanel.add(checkLabel);
		mainPanel.add(downloadBtn);

		return mainPanel;
	}

	void addCompForTitledBorder(TitledBorder border, String description, int justification, int position, Container container) {
		border.setTitleJustification(justification);
		border.setTitlePosition(position);
		addCompForBorder(border, description, container);
	}

	void addCompForBorder(Border border, String description, Container container) {
		JPanel comp = new JPanel(false);
		JLabel label = new JLabel(description, JLabel.CENTER);
		comp.setLayout(new GridLayout(1, 1));
		comp.add(label);
		comp.setBorder(border);

		container.add(Box.createRigidArea(new Dimension(0, 10)));
		container.add(comp);
	}

	void addCompForBorder(Border border, Component sub, Container container) {
		JPanel comp = new JPanel(false);
		comp.setLayout(new GridLayout(1, 1));
		comp.add(sub);
		comp.setBorder(border);

		container.add(Box.createRigidArea(new Dimension(0, 10)));
		container.add(comp);
	}

	private void WatchSyncFolderEnd(int id) {

		try {
			if (id >= 0)
				JNotify.removeWatch(id);
		} catch (JNotifyException e) {
			e.printStackTrace();
		}
	}

	private int WatchSyncFolder() {
		boolean watchSubtree = false;
		int mask = JNotify.FILE_CREATED | JNotify.FILE_DELETED | JNotify.FILE_RENAMED;
		int id = -1;
		final char slash = File.separatorChar;

		try {
			id = JNotify.addWatch(_config.getValue(KEYS.SafeBoxLocation), mask, watchSubtree, new JNotifyListener() {

				public void fileRenamed(int wd, String rootPath, String oldName, String newName) {

					if (_instance.dlgOpened == false)
						return;

					if (new File(rootPath + slash + oldName).isDirectory() || new File(rootPath + slash + newName).isDirectory()) {
						// System.out.println("event renamed " + " : " + oldName
						// + " -> " + newName);
						_instance.TreeNodeRename(oldName, newName);
					}
				}

				public void fileModified(int wd, String rootPath, String name) {

				}

				public void fileDeleted(int wd, String rootPath, String name) {

					if (_instance.dlgOpened == true) {
						// System.out.println("event deleted " + " : " + name);
						_instance.TreeNodeDelete(name);
					}
				}

				public void fileCreated(int wd, String rootPath, String name) {

					if (_instance.dlgOpened == true && new File(rootPath + slash + name).isDirectory()) {
						// System.out.println("event created " + " : " + name);
						_instance.TreeNodeCreate(name);
					}
				}
			});
		} catch (JNotifyException e) {
			e.printStackTrace();
		}

		return id;
	}

	private void changeSyncFolder(String path) {
		String s, curDir = _config.getValue(KEYS.SafeBoxLocation);
		String slashStr = "";

		slashStr += File.separatorChar;

		if (!curDir.endsWith(slashStr)) {
			curDir += File.separatorChar;
		}

		if (path.equalsIgnoreCase(_config.getValue(KEYS.SafeBoxLocation))) {
			s = "Target Folder is your current Safebox.";
			Util.MsgBox(this, s);
		} else if (path.startsWith(curDir)) {
			s = "Target Folder is inside your current Safebox.";
			Util.MsgBox(this, s);
		} else {
			s = "This will move the Safebox folder and all the files inside\n";
			s += "from its current location to ";
			s += path;
			s += ".";
			if (JOptionPane.YES_OPTION == Util.ConfirmBox(this, s)) {
				final String strPreStatus = statusField.getText();
				final String strCurrentPath = rootPathField.getText();
				final String strTargetPath = path;
				// Safe first and move later, do nothing if the DIR moving is
				// interrupted.
				SyncThread.getInstance().setPause(true);
				rootPathButton.setEnabled(false);

				updateStatusField("Moving folder...");
				setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						JNotifyHandler.getInstance().stopWatchEvent();
						Util.moveDirectory(new File(strCurrentPath), new File(strTargetPath));
						rootPathField.setText(strTargetPath);
						_config.setValue(KEYS.SafeBoxLocation, strTargetPath);
						_config.storeProperties();
						SyncThread.getInstance().setRootPath(strTargetPath);
						JNotifyHandler.getInstance().startWatchEvent();
						SyncThread.getInstance().setPause(false);						
						updateStatusField(strPreStatus);
						rootPathButton.setEnabled(true);
						setCursor(Cursor.getDefaultCursor());
					}
				});
			}
		}
		
		String processor = WindowsReqistry.readRegistry(ENV_REG_PATH, PROCESSOR_ARCHITECTURE);
		String safeboxExe;
		String exeCommand;
		
		if (processor.equals("x86")) {
			safeboxExe = "\"" + IGlobal.APP_PATH + "Safebox.exe" + "\"";
		} else {
			safeboxExe = "\"" + IGlobal.APP_PATH + "Safebox64.exe" + "\"";
		}
		
		exeCommand = safeboxExe + " -move " + path;
		
		try {
			Runtime.getRuntime().exec("cmd /c " + exeCommand);
		} catch (Exception e) {

		}

		_logger.debug(LoggerType.Main, exeCommand);
	}

	private void updateStatusField(String text) {
		if (statusField != null) {
			statusField.setForeground(normalColor);
			statusField.setText(" " + text);
		}

		if (reasonField != null) {
			reasonField.setText("");
		}
	}

	public void updateStatusField() {
		String statusText, reasonText = "";

		if (hasLogin == false && _config.connStatus) {
			statusText = "Not logged in";
			if (!_config.connStatus && !_config.connText.equalsIgnoreCase(statusText))
				reasonText = _config.connText;
		} else if (hasPaused == true) {
			statusText = "Paused";
		} else {
			statusText = _config.connStatus ? "Connected" : "Disconnected";
			reasonText = _config.connStatus ? "" : _config.connText;
		}

		if (statusField != null) {
			statusField.setForeground(_config.connStatus ? normalColor : enhancedColor);
			statusField.setText(statusText);
		}

		if (reasonField != null) {
			reasonField.setText(reasonText);
		}
	}

	public void setPauseStatus(Boolean p) {
		hasPaused = p;
	}

	private void updateLoginStatus() {
		if (_config.connStatus == true) {
			hasLogin = true;
			idField.setEnabled(false);
			passField.setEnabled(false);
			passField.setText("********");
			
			rootPathButton.setEnabled(true);
			loginButton.setText("Logout");
			attrPanelMain.removeAll();
			attrPanelMain.add(attrPanelSub2);
			attrPanelMain.updateUI();
		} else {
			hasLogin = false;
			idField.setEnabled(true);
			passField.setEnabled(true);
			rootPathButton.setEnabled(false);
			loginButton.setText("Login");
			attrPanelMain.removeAll();
			attrPanelMain.add(attrPanelSub1);
			attrPanelMain.updateUI();
		}
	}

	private void UpdateInfoLabel(JLabel infoLabel, DefaultMutableTreeNode node) {
		TreeNodeInfo info = (TreeNodeInfo) node.getUserObject();

		if (infoLabel != null && info != null) {
			infoLabel.setText(info.encrypted ? "Encrypted" : "Unencrypted");
		}
	}

	private void updateView() {
		updateLoginStatus();
		updateStatusField();
		CreateTreeNodes(folderTree, treeTop);
		folderTree.updateUI();
	}

	private void CloseDialog() {

		// if (hasFolderPropertyChange == true) {
		// hasFolderPropertyChange = false;
		// SendCommand("OK");
		// }

		this.setVisible(false);
		this.dlgOpened = false;
	}

	public void ShowGUI(boolean do_check) {
		final ConfigDialog frame = (ConfigDialog) this;

		if (hasPackGui == false) {
			hasPackGui = true;

			frame.addWindowListener(new WindowAdapter() {
				public void windowActivated(WindowEvent e) {
					frame.pack();
				}

				public void windowClosing(WindowEvent e) {
					CloseDialog();
				}
			});

			frame.pack();

			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			int w = this.getSize().width;
			int h = this.getSize().height;
			int x = (dim.width - w) / 2;
			int y = (dim.height - h) / 2;
			this.setLocation(x, y);
		}

		updateView();
		frame.setVisible(true);

		if (do_check) {
			MouseEvent clickEvent = new MouseEvent(checkLabel, 0, 0, 0, 69, 12, 1, false);
			MouseListener[] mListenerArr = checkLabel.getMouseListeners();
			for (MouseListener listener : mListenerArr) {
				listener.mouseClicked(clickEvent);
			}
		}
	}

	public void Open(int open_tab, boolean do_check) {
		curTab = open_tab;
		tabbedPane.setSelectedIndex(curTab);

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				ShowGUI(true);
			}
		});

		if (watchID == -1 && wizardMode == false)
			watchID = WatchSyncFolder();

	}

	public void Close() {

		if (watchID != -1)
			WatchSyncFolderEnd(watchID);
	}

	// public void UpdateFolderAttr(Config c) {
	//
	// _config.SetEncryptDir(c.encryptDir);
	//
	// if (folderTree != null) {
	// CreateTreeNodes(folderTree, treeTop);
	// folderTree.updateUI();
	// }
	// }

	private void CheckInputPane() {
		if (curTab != 0) {
			curTab = 0;
			tabbedPane.setSelectedIndex(curTab);
		}
	}

	private void login() {
		if (idField == null || passField == null) {
			return;
		}

//		config.setValue(KEYS.User, idField.getText().toLowerCase());
		
		String userName = idField.getText().toLowerCase();
		_config.setValue(KEYS.User, userName);
		_config.setValue(KEYS.DefaultBucket, "bkt-" + userName);
		_logger.debug(LoggerType.Main, "Login user name :=" + userName);
		
		password = String.valueOf(passField.getPassword());

		if (_config.getValue(KEYS.HostIP).isEmpty()) {
			Util.MsgBox(this, "Please input server IP.");
		} else if (_config.getValue(KEYS.User).isEmpty()) {
			CheckInputPane();
			Util.MsgBox(this, "Please input user ID.");
			idField.requestFocus();
		} else if (password.isEmpty()) {
			CheckInputPane();
			Util.MsgBox(this, "Please input password.");
			passField.requestFocus();
		} else {
			loginButton.setEnabled(false);
			updateStatusField("Connecting...");
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					Main.login(password);

					updateLoginStatus();
					updateStatusField();

					if (_config.connStatus == false) {
						passField.selectAll();
						passField.requestFocus();
					}

					loginButton.setEnabled(true);
					setCursor(Cursor.getDefaultCursor());

					Frame frame = new Frame(IGlobal.APP_NAME + " " + IGlobal.APP_VER);
					JOptionPane.showMessageDialog(frame, _config.connText);
					frame.toFront();
					frame.setAlwaysOnTop(true);
				}
			});
		}
	}
	
	private void logout() {
		passField.setText("");
		loginButton.setEnabled(false);
		updateStatusField("Stopping...");
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		Main.logout();
				
		updateLoginStatus();
		updateStatusField();

		loginButton.setEnabled(true);
		setCursor(Cursor.getDefaultCursor());
	}

	@Override
	// Interface: ActionListener for Buttons
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		// JOptionPane.showMessageDialog(this,cmd);

		if (cmd.equals(_AC_CLOSE)) {
			CloseDialog();
		} else if (cmd.equals(_AC_LOGIN)) {
			_config.setEmptyAccessKey();
			
			if (hasLogin == false)
				login();
			else
				logout();
		} else if (cmd.equals(_AC_SELECT)) {
			JFileChooser fc = new JFileChooser();
			fc.setCurrentDirectory(new File(_config.getValue(KEYS.SafeBoxLocation)));
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fc.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				String path = fc.getSelectedFile().getPath();
				if (!path.endsWith(File.separator)) {
					path = path + File.separatorChar;
				}

				changeSyncFolder(path + "Safebox");
			}
		} else if (cmd.equals(_AC_DOWNLOAD)) {
			downloadBtn.setEnabled(false);

			if (JOptionPane.YES_OPTION == Util.ConfirmBox(this, "The client will be shutdown and download/install the new version, do it?")) {
				new Timer().schedule(new TimerTask() {
					public void run() {
						try {
							setCursor(new Cursor(Cursor.WAIT_CURSOR));

							downloadBtn.setText("Downloading, please wait...");
							String downloadFilePath = DownloadUtil.downloadFile(ServerConnectionUtil.getWebURL(_config.getValue(KEYS.HostIP)) + IGlobal.CLIENT_DOWNLOAD_LINK);

							if (StringUtils.isNotEmpty(downloadFilePath)) {
								downloadBtn.setText("Shutdown client now...");
								Runtime.getRuntime().exec("cmd /c " + downloadFilePath);

								Command.getInstance().sendCommand(new String[] { "-exit" });
							}
						} catch (Exception e) {
							e.printStackTrace();
							System.err.println("=== download file error: " + e);
						}
					}
				}, 100);
			} else {
				downloadBtn.setVisible(false);
				checkLabel.setText("<html><font color=\"#3c72C9\">check new version</font></html>");
			}
		}
	}

	@Override
	// Interface: Observer
	public void update(Observable o, Object arg) {
		statusField.setText(IGlobal.appState.toString());
	}
}
