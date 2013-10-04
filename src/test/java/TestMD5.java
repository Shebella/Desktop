import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.Assert;

import org.itri.ccma.safebox.FileNode;
import org.itri.ccma.safebox.util.FileUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestMD5 {
	// private static final String FILE_PATH =
	// "D:/01.Workshop/03. ISOs/CentOS-5.6-x86_64-bin-DVD-1of2.iso";
	private static final String FILE_PATH = "C:/SafeboxAutoTest/SrcFolder/100/MV.avi";
	private static final String ROOT_PATH = "C:/SafeboxAutoTest/SyncFolder";

	private long startTime = System.nanoTime();
	private long endTime = System.nanoTime();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	private void printTime(String prefix_str) {
		System.out.println("=== " + prefix_str + ": " + ((endTime - startTime) / 1000.0 / 1000.0 / 1000.0));
	}

	@Test
	public void testMd5ForFile() {
		startTime = System.nanoTime();
		String md5Old = FileUtil.getMD5(FILE_PATH);
		endTime = System.nanoTime();
		printTime("old md5");

		startTime = System.nanoTime();
		String md5New = FileUtil.getMD5(FILE_PATH);
		endTime = System.nanoTime();
		printTime("new md5");

		Assert.assertEquals(md5Old, md5New);

		System.out.println("=== " + FILE_PATH + ": " + md5Old);
	}

	@Test
	public void testMd5ForDir() {
		startTime = System.nanoTime();
		int fileCountOld = updateOfflineEventsOld(ROOT_PATH);
		endTime = System.nanoTime();
		printTime("old md5(folder)");

		startTime = System.nanoTime();
		int fileCountNew = updateOfflineEventsNew(ROOT_PATH);
		endTime = System.nanoTime();
		printTime("new md5(folder)");

		Assert.assertEquals(fileCountOld, fileCountNew);

		System.out.println("=== " + ROOT_PATH + ": " + fileCountOld);
	}

	private int updateOfflineEventsOld(String start_path) {
		Map<String, FileNode> localFileMap = CollectCurFiles(start_path);
		if (null != localFileMap) {
			for (String objKey : localFileMap.keySet()) {
				FileUtil.getMD5(localFileMap.get(objKey).getPath());
			}
		}

		return localFileMap.size();
	}

	private int updateOfflineEventsNew(String start_path) {
		Map<String, FileNode> localFileMap = CollectCurFiles(start_path);
		if (null != localFileMap) {
			for (String objKey : localFileMap.keySet()) {
				FileUtil.getMD5(localFileMap.get(objKey).getPath());
			}
		}

		return localFileMap.size();
	}

	private Map<String, FileNode> CollectCurFiles(String dirPath) {
		Map<String, FileNode> fileMap = new TreeMap<String, FileNode>();
		CollectFiles(fileMap, dirPath);
		return fileMap;
	}

	private void CollectFiles(Map<String, FileNode> fileMap, String dirPath) {
		File dir = new File(dirPath);
		FileNode node;
		int i;

		File[] files = dir.listFiles();
		if (files != null) {
			for (i = 0; i < files.length; i++) {
				if (!files[i].canRead())
					continue;
				node = new FileNode(files[i]);
				fileMap.put(files[i].getPath(), node);
				if (files[i].isDirectory())
					CollectFiles(fileMap, files[i].getPath());
			}
		}
	}

}
