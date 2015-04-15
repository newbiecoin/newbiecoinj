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
    public static String newsUrlCN = "http://newbiecoin.org/downloads/news_cn.html";
	public static String downloadUrl = "http://newbiecoin.org/downloads/";
	public static String downloadZipUrl = "http://newbiecoin.org/download.txt"; //Only update package
	public static Integer RPCPort = 44944;
	public static String RPCUsername = "";
	public static String RPCPassword = "";
	public static String ConfigFile = "./resources/newbiecoin.conf";
	
	//version
	public static Integer majorVersion = 2;
	public static Integer minorVersion = 7;
	public static String version = Integer.toString(majorVersion)+"."+Integer.toString(minorVersion);
	public static Integer majorVersionDB = 1;
	public static Integer minorVersionDB = 2;
	public static String versionDB = Integer.toString(majorVersionDB)+"."+Integer.toString(minorVersionDB);	
	
    public static Boolean testNet = false;
    public static String burnAddressFund = "1NEWBfeEiyM5HC2ERf7ZWHN6DEdjbVXvEW";  //POB to fund
    public static String burnAddressDark = "1NewbiecoinXXXXXXXXXXXXXXXXDN67UA8";  //POB to dark hole
    public static String newb_prefix = "NEWBCOIN";
    
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
	public static String houseAddressFund = "1NEWBhDUzacwdat1KcKDnYdUjQL15DLNyP";  //House fund
	public static Short BET_BIGGER = 1;
	public static Short BET_SMALLER = 0;
	public static Integer betStartBlock=firstBlock+1;
	public static Integer betPeriodBlocks = 100;
	public static Integer betResolveWaitBlocks = 5;
	
	//Bet worldcup
	public static String houseWorldCupFunctionName = "bet worldcup fee";
	
	public static Integer WORLDCUP2014_BETTING_START_UTC = 1402272000; //UTC 2014-06-09 00:00:00
	public static Integer WORLDCUP2014_BETTING_END_UTC = 1403913600;  //UTC 2014-06-28 00:00:00
	public static Integer WORLDCUP2014_RESOLVE_SCHEME_UTC = 1405285200; //UTC 2014-07-13 21:00:00
	
	//protocol
	public static String txTypeFormat = ">I";
	public static Integer btc_unit = 100000000;
	public static Integer nbc_unit = 10000;//100000000 changed to 10000 , 2014-08-05
	public static String nbc_display_format = "%.2f";
	
	//bitcoin
	public static Integer dustSize = 780;
	public static Integer minOrderMatchBTC = 100000;
	public static Integer minFee = 3000;
	public static Integer maxFee = 10000;
	public static Integer dataValue = 0;
    
    //PPk
    public static Integer ppkStandardDataFee = 10000;
    
    public static String ppk_prefix = "P2P is future! ppkpub.org->ppk:0";  //1BwiPbYoSW7oBiJW9dLsf1uue2N2gXPXJE

    public static Byte FUNC_ID_ODII_REGIST='R'; 
    public static Byte FUNC_ID_ODII_UPDATE='U'; 
    
    public static Byte DATA_TEXT_UTF8= 'T'; //normal text in UTF-8
    public static Byte DATA_BIN_GZIP = 'G'; //Compressed by gzip
    
    public static Byte DATA_CATALOG_UNKNOWN= 0; //Unkown Data
    
	
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
