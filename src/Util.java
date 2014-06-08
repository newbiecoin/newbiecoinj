import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.wallet.WalletTransaction;


public class Util {
    static Logger logger = LoggerFactory.getLogger(Util.class);
	//private static String minVersionRequired = null;

	public static String getPage(String url_string) {
		return getPage(url_string, 1);

	}

	public static String getPage(String url_string, int retries) {
		URL url;
		String text = null;
		try {
			url = new URL(url_string);
			URLConnection urlc = url.openConnection();

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
				e.printStackTrace();
			}
		}
		return text;
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

	public static List<Map.Entry<String,String>> infoGetTransactions(String address) {
		List<Map.Entry<String,String>> txs = new ArrayList<Map.Entry<String,String>>();
		/*
		String result = getPage("https://blockexplorer.com/q/mytransactions/"+address);
		try {
			Map<String, Object> map = (new ObjectMapper()).readValue(result, new TypeReference<Map<String,Object>>() { });
			for (String key : map.keySet()) {
				Map<String, Object> txMap = (Map<String, Object>) map.get(key);
				txs.add(new AbstractMap.SimpleEntry(key, txMap.get("block").toString()));
			}
		} catch (Exception e) {
		}
		*/
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

	public static Map<String,Object> infoGetBlock(Integer blockNumber) {
		String result = getPage("https://blockchain.info/block-index/"+Integer.toString(blockNumber)+"?format=json");
		try {
			Map<String, Object> map = (new ObjectMapper()).readValue(result, new TypeReference<Map<String,Object>>() { });
			return map;
		} catch (Exception e) {
		}
		return null;
	}

	public static Map<String,Object> infoGetBlockByHash(String blockHash) {
		String result = getPage("https://blockchain.info/rawblock/"+blockHash);
		try {
			Map<String, Object> map = (new ObjectMapper()).readValue(result, new TypeReference<Map<String,Object>>() { });
			return map;
		} catch (Exception e) {
		}
		return null;
	}

	public static String format(Double input) {
		return format(input, "#.00");
	}

	public static String format(Double input, String format) {
		return (new DecimalFormat(format)).format(input);
	}

	public static String timeFormat(Integer timestamp) {
		Date date = new Date(timestamp*1000L); // *1000 is to convert seconds to milliseconds
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
		String formattedDate = sdf.format(date);
		return formattedDate;
	}

	static float roundOff(Double x, int position)
	{
		float a = x.floatValue();
		double temp = Math.pow(10.0, position);
		a *= temp;
		a = Math.round(a);
		return (a / (float)temp);
	}

	public static Integer getLastBlock() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks order by block_index desc limit 1;");
		try {
			while(rs.next()) {
				return rs.getInt("block_index");
			}
		} catch (SQLException e) {
		}	
		return 0;
	}
	
	public static Integer getLastBlockTime() {
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
	public static BigInteger getBalance(String address, String asset) {
		Database db = Database.getInstance();
		Blocks blocks = Blocks.getInstance();
		if (asset.equals("BTC")) {
			BigInteger totalBalance = BigInteger.ZERO;
			LinkedList<TransactionOutput> unspentOutputs = blocks.wallet.calculateAllSpendCandidates(true);
			Set<Transaction> txs = blocks.wallet.getTransactions(true);
			/*
			for (Transaction tx : txs) {
				System.out.println(tx);
				totalBalance = totalBalance.add(tx.getValueSentToMe(blocks.wallet));
				totalBalance = totalBalance.subtract(tx.getValueSentFromMe(blocks.wallet));
			}
			 */
			for (TransactionOutput out : unspentOutputs) {
				Script script = out.getScriptPubKey();
				if (script.getToAddress(blocks.params).toString().equals(address) && out.isAvailableForSpending()) {
					totalBalance = totalBalance.add(out.getValue());
				}
			}
			return totalBalance;
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
		/*if(minVersionRequired == null) {
			minVersionRequired = getPage(Config.minVersionPage).trim();
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
}
