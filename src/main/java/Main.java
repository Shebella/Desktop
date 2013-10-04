import org.itri.ccma.safebox.util.LoggerHandler;

public class Main {

	public static void main(String[] args) {
		LoggerHandler.getInstance().setReferece(null);
		org.itri.ccma.safebox.Main safeboxMain = new org.itri.ccma.safebox.Main();
		safeboxMain.mainEntrance(args);
	}

}
