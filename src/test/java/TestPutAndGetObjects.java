import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.itri.ccma.safebox.CSSHandler;
import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.util.SafeboxException;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


public class TestPutAndGetObjects {
	private static final String APP_CFG_FILE = "safebox.cfg";
	private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss:SSS");

	private static final String UPLOAD_PATH = "C:\\Users\\A10138\\My Documents\\Safebox\\aaa.java";

	private S3Assist s3Assist;
	private CSSHandler cssHandler;
	private Config appConfig;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		appConfig = Config.getInstance();
		appConfig.load(APP_CFG_FILE);
		//s3Assist = new S3Assist();

		//s3Assist.setConfig(appConfig, true);

		//cssHandler = new CSSHandler(appConfig);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetObject() throws ObjectException, SQLException {
		System.out.println("==== before test: " + SDF.format(new Date()));
		List<S3Object> objList = s3Assist.listObjects("/", false, null);
		System.out.println("==== after test: " + SDF.format(new Date()));

		Assert.assertNotNull(objList);

		if (null != objList) {
			System.out.println("=== objectList size: " + objList.size());

			for (S3Object obj : objList) {
				//System.out.print("=== name: " + obj.getName() + ", obj-seq: "
						//+ ((StorageObjectExtend) obj).getObjSeq());

				Map<String, Object> metadataMap = obj.getMetadataMap();
				for (String key : metadataMap.keySet()) {
					System.out.print(", key: " + key + ", value: " + metadataMap.get(key));
				}
				System.out.println("");

				StorageObject objDetail = null;
                try {
	                objDetail = s3Assist.downloadingObjectDetails(obj.getName());
                } catch (SafeboxException e) {
                }
				if (null != objDetail) {
					System.out.println("=== objKey: " + objDetail.getKey());
				}

				System.out.println("=== result: " + s3Assist.isObjectExist(obj.getName()));

				System.out.println();
			}
		}
	}

	public void testGetObjectByThread() {

	}

	@Test
	@Ignore
	public void testRenameObj() {

		// try {
		// s3Assist.moveObject(appConfig.defaultBucket, "/01.mp3",
		// appConfig.defaultBucket,
		// new StorageObject("/02.mp3"), false);
		// } catch (ServiceException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
	}

	@Test
	public void testPutObject() {
		try {
	        Map syncMap = cssHandler.initSyncRequest();
        } catch (SafeboxException e1) {
        }

		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();
		(new TestWorker(s3Assist, "/aaa.java")).run();

		try {
			cssHandler.unlockSyncId(CSSHandler.UNLOCK_TYPE.SUCC);
			Thread.sleep(300000000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private class TestWorker extends Thread {
		private S3Assist s3AssistThread;
		private String filePath;

		public TestWorker(final S3Assist s3_assist, String file_path) {
			s3AssistThread = s3_assist;
			filePath = file_path;
		}

		@Override
		public void run() {
			// System.out.println(this.getId() + ": " +
			// s3AssistThread.putObject("", UPLOAD_PATH));

		}
	}
}
