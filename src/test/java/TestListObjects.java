import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.itri.ccma.safebox.Config;
import org.itri.ccma.safebox.db.ObjectException;
import org.itri.ccma.safebox.s3.S3Assist;
import org.itri.ccma.safebox.s3.S3CallBack;
import org.jets3t.service.model.S3Object;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


public class TestListObjects {
	private static final String APP_CFG_FILE = "safebox.cfg";
	private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss:SSS");

	private S3Assist s3Assist;
	private Config appConfig;

	private S3CallBack listObjCallBack;

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
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	@Ignore
	public void testConnection() {
		//Assert.assertTrue(s3Assist.testConn(appConfig));
	}

	@Test
	public void testListObject() throws ObjectException, SQLException {
		System.out.println("==== before test: " + SDF.format(new Date()));
		List<S3Object> objList = s3Assist.listObjects("", true, null);
		System.out.println("==== after test: " + SDF.format(new Date()));

		Assert.assertNotNull(objList);

		if (null != objList) {
			System.out.println("=== objectList size: " + objList.size());
		}

	}

}
