import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Newbiecoin {
	public static void main(String[] args) {
		Blocks blocks = Blocks.getInstanceSkipVersionCheck();
		blocks.init();
		//blocks.versionCheck();
		blocks.follow();
		Thread blocksThread = new Thread(blocks);
		blocksThread.setDaemon(true);
		blocksThread.start(); 
		Config.loadUserDefined();
		JsonRpcServletEngine engine = new JsonRpcServletEngine();
		try {
			engine.startup();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}