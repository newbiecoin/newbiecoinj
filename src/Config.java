import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


public class Config {
	//name
	public static String appName = "Newbiecoin";

	public static String log = appName+".log";
	//public static String minVersionPage = "http://newbiecoin.org/min_version.txt";
	public static String minVersion="2.0";
	public static String dbPath = "resources/db/";
	public static String newsUrl = "http://newbiecoin.org/downloads/news.html";
	public static String downloadUrl = "http://newbiecoin.org/downloads/";
	public static String downloadZipUrl = "http://newbiecoin.org/download.txt"; //Only update package
	public static Integer RPCPort = 44944;
	public static String RPCUsername = "";
	public static String RPCPassword = "";
	public static String ConfigFile = "./resources/newbiecoin.conf";
	
	//version
	public static Integer majorVersion = 2;
	public static Integer minorVersion = 0;
	public static String version = Integer.toString(majorVersion)+"."+Integer.toString(minorVersion);
	public static Integer majorVersionDB = 1;
	public static Integer minorVersionDB = 2;
	public static String versionDB = Integer.toString(majorVersionDB)+"."+Integer.toString(minorVersionDB);	
	
    public static Boolean testNet = false;
    public static String burnAddressFund = "1NEWBfeEiyM5HC2ERf7ZWHN6DEdjbVXvEW";  //POB to fund
    public static String burnAddressDark = "1NewbiecoinXXXXXXXXXXXXXXXXDN67UA8";  //POB to dark hole
    public static String prefix = "NEWBCOIN";
    
	//burn
	public static Integer firstBlock = 303600;
    
	public static Integer pobTrialStartBlock = firstBlock;
	public static Integer pobTrialEndBlock = pobTrialStartBlock+999;
    public static Integer pobTrialMultiplier = 1000;
    
    public static Integer pobMaxStartBlock = pobTrialEndBlock+1;
	public static Integer pobMaxEndBlock = pobMaxStartBlock+999;
    public static Integer pobMaxMultiplier = 2048;
    
    public static Integer pobDownStartBlock = pobMaxEndBlock+1;
	public static Integer pobDownEndBlock = pobDownStartBlock+1999;
	public static Integer pobDownInitMultiplier = 2000;
    public static Integer pobDownEndMultiplier = 1000;
    
	public static Integer maxBurn = 1000000000;
	public static long burnCreationTime = 1401605006-1;  //UTC 2014-6-1 6:43:25
	
    //POS
    public static Integer posFirstBlock = pobDownEndBlock+1001;
    public static Integer posEndBlock = posFirstBlock+6*24*365*10; //About 10 years
    public static Integer posWaitBlocks = 1000; //About 7 days
    public static Double  posInterest = 0.001;
	
	//casino
	public static String houseFunctionName = "bet house fee";
	public static Double houseEdge = 0.01; 
	public static String houseAddressFund = burnAddressFund;  //House fund
	public static Short BET_BIGGER = 1;
	public static Short BET_SMALLER = 0;
	public static Integer betStartBlock=firstBlock+1;
	public static Integer betPeriodBlocks = 100;
	public static Integer betResolveWaitBlocks = 5;
	
	//bitcoin
	public static Integer dustSize = 5430*2;
	public static Integer minFee = 10000;
	public static Integer dataValue = 0;
	
	//protocol
	public static String txTypeFormat = ">I";
	public static Integer unit = 100000000;
	
	//etc.
	public static Integer maxExpiration = 4*2016;
	public static Integer maxInt = ((int) Math.pow(2.0,63.0))-1;
	
	public static void loadUserDefined() {
		FileInputStream input;
		try {
			input = new FileInputStream(ConfigFile);
			Properties prop = new Properties();
			prop.load(input);
			RPCUsername = prop.getProperty("RPCUsername");
			RPCPassword = prop.getProperty("RPCPassword");
		} catch (IOException e) {
		}		
	}
}
