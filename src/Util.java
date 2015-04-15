import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.text.ParsePosition;
import java.util.TimeZone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.OutputStream; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.wallet.WalletTransaction;

public class Util {
	static Logger logger = LoggerFactory.getLogger(Util.class);

	public static String getPage(String urlString) {
		return getPage(urlString, 1);

	}

	public static String getPage(String urlString, int retries) {
		try {
			logger.info("Getting URL: "+urlString);
			doTrustCertificates();
			URL url = new URL(urlString);
			HttpURLConnection connection = null;
			connection = (HttpURLConnection)url.openConnection();
			connection.setUseCaches(false);
			connection.addRequestProperty("User-Agent", Config.appName+" "+Config.version); 
			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setReadTimeout(10000);
			connection.connect();

			BufferedReader rd  = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			
			while ((line = rd.readLine()) != null)
			{
				sb.append(line + '\n');
			}
			//System.out.println (sb.toString());

			return sb.toString();
		} catch (Exception e) {
			logger.error("Fetch URL error: "+e.toString());
		}
		return "";
		/*
		URL url;
		String text = null;
		try {
			doTrustCertificates();
			url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			urlc.setRequestProperty("User-Agent", "NewbieCoin "+Config.version);
			urlc.setDoOutput(false);
			urlc.connect();

			BufferedInputStream buffer = new BufferedInputStream(urlc.getInputStream());

			StringBuilder builder = new StringBuilder();

			int byteRead;

			while ((byteRead = buffer.read()) != -1) {
				builder.append((char) byteRead);
			}

			buffer.close();

			text=builder.toString();

		} catch (Exception e) {
			if (retries != 0) {
				return getPage(url_string, retries-1);	
			} else {
				logger.error(e.toString());
			}
		}
		return text;
		 */
	}	

	public static void doTrustCertificates() throws Exception {
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers()
					{
						return null;
					}
					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
					{
					}
					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
					{
					}
				}
		};
		try 
		{
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} 
		catch (Exception e) 
		{
			System.out.println(e);
		}
	}	

	private static boolean isRedirected( Map<String, List<String>> header ) {
		for(String hv : header.get(null)) {
			if(hv.contains(" 301 ") || hv.contains(" 302 ")) return true;
		}
		return false;
	}
	public static void downloadToFile(String link, String fileName) {
		try {
			URL url  = new URL(link);
			HttpURLConnection http = (HttpURLConnection)url.openConnection();
			Map<String, List<String>> header = http.getHeaderFields();
			while(isRedirected(header)) {
				link = header.get("Location").get(0);
				url = new URL(link);
				http = (HttpURLConnection)url.openConnection();
				header = http.getHeaderFields();
			}
			InputStream input = http.getInputStream();
			byte[] buffer = new byte[4096];
			int n = -1;
			OutputStream output = new FileOutputStream( new File( fileName ));
			while ((n = input.read(buffer)) != -1) {
				output.write(buffer, 0, n);
			}
			output.close();
		} catch (Exception e) {
			logger.info(e.toString());
		}
	}

	public static String unspentAddress(String address) {
		return "http://api.bitwatch.co/listunspent/"+address+"?verbose=1&minconf=0";
	}

	public static List<UnspentOutput> getUnspents(String address) {
		String result = getPage(unspentAddress(address));
		List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			UnspentOutputs unspentOutputs = objectMapper.readValue(result, new TypeReference<UnspentOutputs>() {});
			unspents = unspentOutputs.result;
		} catch (Exception e) {
			logger.error(e.toString());
		}
		return unspents;
	}

	/*
	public static List<Map.Entry<String,String>> getTransactions(String address) {
		List<Map.Entry<String,String>> txs = new ArrayList<Map.Entry<String,String>>();
		String result = getPage("https://blockexplorer.com/address/"+address);
		Pattern p = Pattern.compile("href=\"/tx/(.*?)#.*?/block/(.*?)\"", Pattern.DOTALL);
		Matcher m = p.matcher(result);
		while (m.find()) {
			logger.info(m.group(1));
			SimpleEntry tx = new AbstractMap.SimpleEntry(m.group(1), m.group(2));
			if (!txs.contains(tx)) {
				txs.add(tx);		
			}
		}		
		return txs;
	}
	 */

	public static String format(Double input) {
		return format(input, "#.00");
	}

	public static String format(Double input, String format) {
		return (new DecimalFormat(format)).format(input);
	}

	public static String timeFormat(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
		String formattedDate = sdf.format(date);
		return formattedDate;
	}

	public static String timeFormat(Integer timestamp) {
		Date date = new Date(timestamp*1000L); // *1000 is to convert seconds to milliseconds
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
		String formattedDate = sdf.format(date);
		return formattedDate;
	}

	static float roundOff(Double x, int position) {
		float a = x.floatValue();
		double temp = Math.pow(10.0, position);
		a *= temp;
		a = Math.round(a);
		return (a / (float)temp);
	}

	public static Integer getLastBlock() {
		Blocks blocks = Blocks.getInstance();
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks order by block_index desc limit 1;");
		try {
			while(rs.next()) {
				return rs.getInt("block_index");
			}
		} catch (SQLException e) {
		}	
		return blocks.newbiecoinBlock;
	}

	public static Integer getLastTxIndex() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("SELECT * FROM transactions WHERE tx_index = (SELECT MAX(tx_index) from transactions);");
		try {
			while(rs.next()) {
				return rs.getInt("tx_index");
			}
		} catch (SQLException e) {
		}	
		return 0;
	}	
	
	public static void updateLastParsedBlock(Integer block_index) {
		Database db = Database.getInstance();
		db.executeUpdate("REPLACE INTO sys_parameters (para_name,para_value) values ('last_parsed_block','"+block_index.toString()+"');");
	}	
	
	public static Integer getLastParsedBlock() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("SELECT para_value FROM sys_parameters WHERE para_name='last_parsed_block'");
		try {
			while(rs.next()) {
				return rs.getInt("para_value");
			}
		} catch (SQLException e) {
		}	
		return 0;
	}	

	public static void debit(String address, String asset, BigInteger amount, String callingFunction, String event, Integer blockIndex) {
		Database db = Database.getInstance();
		if (hasBalance(address, asset)) {
			BigInteger existingAmount = getBalance(address,asset);
			BigInteger newAmount = existingAmount.subtract(amount);
			if (newAmount.compareTo(BigInteger.ZERO)>=0) {
				db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
				db.executeUpdate("insert into debits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
			}
		}
	}

	public static void credit(String address, String asset, BigInteger amount, String callingFunction, String event, Integer blockIndex) {
		Database db = Database.getInstance();
		if (hasBalance(address, asset)) {
			BigInteger existingAmount = getBalance(address,asset);
			BigInteger newAmount = existingAmount.add(amount);
			db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
		} else {
			db.executeUpdate("insert into balances(address, asset, amount) values('"+address+"','"+asset+"','"+amount.toString()+"');");				
		}
		db.executeUpdate("insert into credits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
	}

	public static Boolean hasBalance(String address, String asset) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select amount from balances where address='"+address+"' and asset='"+asset+"';");
		try {
			if (rs.next()) {
				return true;
			}
		} catch (SQLException e) {
		}
		return false;
	}

	public static String transactionAddress(String txHash) {
		return "https://api.biteasy.com/blockchain/v1/transactions/"+txHash;
	}

	public static TransactionInfo getTransaction(String txHash) {
		String result = getPage(transactionAddress(txHash));
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			TransactionInfo transactionInfo = objectMapper.readValue(result, new TypeReference<TransactionInfo>() {});
			return transactionInfo;
		} catch (Exception e) {
			logger.error(e.toString());
			return null;
		}
	}

	public static String BTCBalanceAddress(String address) {
		return "http://api.bitwatch.co/getbalance/"+address+"?minconf=1&maxreqsigs=1";
	}

	public static BigInteger getBTCBalance(String address) {
		String result = getPage(BTCBalanceAddress(address));
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			AddressInfo addressInfo = objectMapper.readValue(result, new TypeReference<AddressInfo>() {});
			return BigDecimal.valueOf(addressInfo.result*Config.btc_unit).toBigInteger();
		} catch (Exception e) {
			logger.error(e.toString());
			return BigInteger.ZERO;
		}
	}

	public static BigInteger getBalance(String address, String asset) {
		Database db = Database.getInstance();
		Blocks blocks = Blocks.getInstance();
		if (asset.equals("BTC")) {
			/*
			BigInteger totalBalance = BigInteger.ZERO;
			LinkedList<TransactionOutput> unspentOutputs = blocks.wallet.calculateAllSpendCandidates(true);
			Set<Transaction> txs = blocks.wallet.getTransactions(true);
			for (TransactionOutput out : unspentOutputs) {
				Script script = out.getScriptPubKey();
				if (script.getToAddress(blocks.params).toString().equals(address) && out.isAvailableForSpending()) {
					totalBalance = totalBalance.add(out.getValue());
				}
			}
			return totalBalance;
			 */
			return getBTCBalance(address);
		} else {
			ResultSet rs = db.executeQuery("select sum(amount) as amount from balances where address='"+address+"' and asset='"+asset+"';");
			try {
				if (rs.next()) {
					return BigInteger.valueOf(rs.getLong("amount"));
				}
			} catch (SQLException e) {
			}
		}
		return BigInteger.ZERO;
	}
	
	public static BigInteger getReserved(String address, String asset) {
		Database db = Database.getInstance();
		Blocks blocks = Blocks.getInstance();

		ResultSet rs = db.executeQuery("select sum(give_amount) as amount from orders  where source='"+address+"' and give_asset='"+asset+"' and validity='valid'");
		try {
			if (rs.next()) {
				return BigInteger.valueOf(rs.getLong("amount"));
			}
		} catch (SQLException e) {
		}

		return BigInteger.ZERO;
	}
	
	public static BigInteger nbcSupply() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select sum(amount) as amount from balances where asset='NBC';");
		try {
			if (rs.next()) {
				return BigInteger.valueOf(rs.getLong("amount"));
			}
		} catch (SQLException e) {
		}
		return BigInteger.ZERO;
	}
	public static BigInteger nbcBurned(String destination) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select sum(earned) as amount from burns "+( destination==null ? "":" where destination='"+destination+"'" )+";");
		try {
			if (rs.next()) {
				return BigInteger.valueOf(rs.getLong("amount"));
			}
		} catch (SQLException e) {
		}
		return BigInteger.ZERO;
	}
	public static BigInteger btcBurned(String destination) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select sum(burned) as amount from burns "+( destination==null ? "":" where destination='"+destination+"'" )+";");
		try {
			if (rs.next()) {
				return BigInteger.valueOf(rs.getLong("amount"));
			}
		} catch (SQLException e) {
		}
		return BigInteger.ZERO;
	}

	public static List<String> getAddresses() {
		Blocks blocks = Blocks.getInstance();
		List<ECKey> keys = blocks.wallet.getKeys();
		List<String> addresses = new ArrayList<String>();
		for(ECKey key : keys) {
			addresses.add(key.toAddress(blocks.params).toString());
		}
		return addresses;
	}

	public static Integer getAssetId(String asset) {
		if (asset.equals("BTC")) {
			return 0;
		} else if (asset.equals("NBC")) {
			return 1;
		} else {
			return null;
		}
	}
	public static String getAssetName(Integer assetId) {
		if (assetId==0) {
			return "BTC";
		} else if (assetId==1) {
			return "NBC";
		} else {
			return null;
		}
	}

	public static byte[] toByteArray(List<Byte> in) {
		final int n = in.size();
		byte ret[] = new byte[n];
		for (int i = 0; i < n; i++) {
			ret[i] = in.get(i);
		}
		return ret;
	}	
	public static List<Byte> toByteArrayList(byte[] in) {
		List<Byte> arrayList = new ArrayList<Byte>();

		for (byte b : in) {
			arrayList.add(b);
		}
		return arrayList;
	}	

	public static String getMinVersion() {
		/*
		String minVersion = getPage(Config.minVersionPage);
		if (minVersion == null || minVersion.equals("")) {
			minVersion = getPage(Config.minVersionPage2).trim();
			if (minVersion == null || minVersion.equals("")) {
				return Config.version;
			}
		}
		minVersion = minVersion.trim();
		return minVersion;
		} */
		return Config.minVersion;
	}
	public static Integer getMinMajorVersion() {
		String minVersion = getMinVersion();
		String[] pieces = minVersion.split("\\.");
		return Integer.parseInt(pieces[0].trim());
	}
	public static Integer getMinMinorVersion() {
		String minVersion = getMinVersion();
		String[] pieces = minVersion.split("\\.");
		return Integer.parseInt(pieces[1].trim());
	}
	
	public static String getBlockHash(Integer blockIndex) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select block_hash from blocks where block_index='"+blockIndex.toString()+"';");
		try {
			if(rs.next()) {
				return rs.getString("block_hash");
			}
		} catch (SQLException e) {
		}	
		return null;
	}
	
	public static Integer getLastBlockTimestamp() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks order by block_index desc limit 1;");
		try {
			while(rs.next()) {
				return rs.getInt("block_time");
			}
		} catch (SQLException e) {
		}	
		return 0;
	}
	
	//the_date format is YYYY-MM-DD
	//timeZone=null for local time,  timeZone="GMT+0:00" for UTC
	public static Integer getDateTimestamp(String the_date, String timeZone){
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		if(timeZone!=null){
			TimeZone tz = TimeZone.getTimeZone(timeZone); 
			formatter.setTimeZone(tz);
		}
		
		ParsePosition pos = new ParsePosition(0);
		Date strtodate = formatter.parse(the_date+" 00:00:00", pos);
		
		Long tsInSecond=strtodate.getTime()/1000L;
		
		return Integer.parseInt(tsInSecond.toString());
	}
	
	public static String getLeftTimeDescStr(Integer expireUTC){
		Calendar nowtime=Calendar.getInstance();
		
		Long leftSeconds=expireUTC-nowtime.getTimeInMillis()/1000L;
		
		if(leftSeconds<0L)
			return null;
		
		String   aboutLeftTimeDesc="";
		Integer  aboutDays=new Double(java.lang.Math.floor(leftSeconds.doubleValue()/new Double(60*60*24).doubleValue())).intValue();
		if(aboutDays>0)
			aboutLeftTimeDesc=aboutDays.toString() + " " + Language.getLangLabel("days");
		
		leftSeconds=leftSeconds%(60*60*24);
		Integer  aboutHours=new Double( java.lang.Math.floor(leftSeconds.doubleValue() /new Double(60*60).doubleValue())).intValue();
		if(aboutHours>0)
			aboutLeftTimeDesc=aboutLeftTimeDesc+" "+aboutHours.toString() + " " + Language.getLangLabel("hours");
		else {
			leftSeconds=leftSeconds%(60*60);
			
			Integer  aboutMins=new Double( java.lang.Math.floor(leftSeconds.doubleValue() /new Double(60).doubleValue())).intValue();
			if(aboutMins>0)
				aboutLeftTimeDesc=aboutLeftTimeDesc+" "+aboutMins.toString() + " " + Language.getLangLabel("minutes");
		}
		
		if(aboutLeftTimeDesc.equals(""))
			aboutLeftTimeDesc=" < 1 "+Language.getLangLabel("minute");
		
		return aboutLeftTimeDesc;//+" (DEBUG: now="+nowtime.getTimeInMillis()+"  dest="+strtodate.getTime()+")  ";
	}
	
	/*
	public static String getLeftTimeDescStr(String expire_date){
		//logger.info("expire_date="+expire_date);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		TimeZone tz = TimeZone.getTimeZone("GMT+0:00"); //for UTC time
		formatter.setTimeZone(tz);
		ParsePosition pos = new ParsePosition(0);
		Date strtodate = formatter.parse(expire_date+" 00:00:00", pos);
		//logger.info("strtodate="+strtodate.toString());
  
		Calendar nowtime=Calendar.getInstance();
		
		Long leftMillis=strtodate.getTime()-nowtime.getTimeInMillis();
		//logger.info("leftMillis="+leftMillis);
		
		if(leftMillis<0L)
			return null;
		
		String   aboutLeftTimeDesc="";
		Integer  aboutDays=new Double(java.lang.Math.floor(leftMillis.doubleValue()/new Double(1000*60*60*24).doubleValue())).intValue();
		if(aboutDays>0)
			aboutLeftTimeDesc=aboutDays.toString() + " " + Language.getLangLabel("days");
		
		leftMillis=leftMillis%(1000*60*60*24);
		Integer  aboutHours=new Double( java.lang.Math.floor(leftMillis.doubleValue() /new Double(1000*60*60).doubleValue())).intValue();
		if(aboutHours>0)
			aboutLeftTimeDesc=aboutLeftTimeDesc+" "+aboutHours.toString() + " hours";
			
		return aboutLeftTimeDesc;//+" (DEBUG: now="+nowtime.getTimeInMillis()+"  dest="+strtodate.getTime()+")  ";
	}
	*/
	
	//Compress string
	public static String compress(String str) throws Exception {
		if (str == null || str.length() == 0) {
		  return str;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(str.getBytes("UTF-8"));
		gzip.close();
		return out.toString("ISO-8859-1");
	}

	//Uncompress string
	public static String uncompress(String str) throws Exception {
		if (str == null || str.length() == 0) {
		  return str;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayInputStream in = new ByteArrayInputStream(str.getBytes("ISO-8859-1"));
		GZIPInputStream gunzip = new GZIPInputStream(in);
		byte[] buffer = new byte[256];
		int n;
		while ((n = gunzip.read(buffer)) >= 0) {
		  out.write(buffer, 0, n);
		}
		return out.toString("UTF-8");
	}
	
	public static boolean exportTextToFile(String text, String fileName) {
		try {
			FileWriter fw = new FileWriter(fileName);  
			fw.write(text,0,text.length());  
			fw.flush();  
			return true;
		} catch (Exception e) {
			logger.info(e.toString());
			return false;
		}
	}
	
	public static String readTextFile(String fileName){  
		return readTextFile(fileName,"ISO-8859-1");
	} 
    
    public static String readTextFile(String fileName,String encode){  
        try {
            InputStreamReader read = new InputStreamReader (new FileInputStream(fileName),encode);
            BufferedReader reader=new BufferedReader(read);
            String str="";
            String line;
            while ((line = reader.readLine()) != null) {
                str+=line;
            }
            reader.close();
            read.close();
            return str;
        }catch(Exception e){
			logger.error(e.toString());
			return null;
		}
    }
	
    public static JSONObject getRSAKeys(String address,boolean auto_generate) throws Exception{  
		String  privateKeyFilename="resources/db/ck-"+address;
					
		if (!(new File(privateKeyFilename)).exists() ){
			if(!auto_generate)
				return null;
			
			//init a rsa key file for current address
			JSONObject keyMap = RSACoder.initKey();  
			logger.info("Generated new keys: " + keyMap.toString());  
		
			if(!exportTextToFile(keyMap.toString(),privateKeyFilename)){
				logger.error("Failed to save generated RSA keys to "+privateKeyFilename);
				return null;
			}
		}
		String  tmpKeyStr=Util.readTextFile(privateKeyFilename);
		if(tmpKeyStr==null){
			logger.error("Failed to get RSA keys from "+privateKeyFilename);
			return null;					
		}
		
		JSONObject keyMap=new JSONObject(tmpKeyStr);
		
		return keyMap;
	}
	
	public static boolean exportOriginalBackContact(String back_tx_hash,String contact){
		try{
			String  backContactFilename="resources/db/back-contact";
			
			JSONObject keyMap = null;	
			if (!(new File(backContactFilename)).exists() ){
				keyMap=new JSONObject();
				keyMap.put(back_tx_hash,contact);
			} else {
				String  tmpKeyStr=Util.readTextFile(backContactFilename);
				if(tmpKeyStr==null){
					logger.error("Failed to get back contact from "+backContactFilename);
					return false;					
				}
				
				keyMap=new JSONObject(tmpKeyStr);
				keyMap.put(back_tx_hash,contact);
			}
			
			if(exportTextToFile(keyMap.toString(),backContactFilename)){
				return true;
			}
			
		}catch(Exception e){
			logger.error(e.toString());
		}
		
		return false;
	}
	
	public static String getOriginalBackContact(String back_tx_hash){  
		try{
			String  backContactFilename="resources/db/back-contact";
			
			if ((new File(backContactFilename)).exists() ){
				String  tmpKeyStr=Util.readTextFile(backContactFilename);
				if(tmpKeyStr==null){
					logger.error("Failed to get back contact from "+backContactFilename);
					return null;					
				}
				
				JSONObject keyMap=new JSONObject(tmpKeyStr);
				if(keyMap.has(back_tx_hash))
					return keyMap.getString(back_tx_hash);
			}
		}catch(Exception e){
			logger.error(e.toString());
		}
		
		return null;
	}
	
}

class AddressInfo {
	public Double result;
}

class TransactionInfo {
	public Data data;

	public static class Data {
		public Integer confirmations;
	}
}

class UnspentOutputs {
	public List<UnspentOutput> result;
}
