import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.Transaction;

public class JsonRpcServiceImpl implements JsonRpcService {
    static Logger logger = LoggerFactory.getLogger(JsonRpcServiceImpl.class);
	
	public String getBalance(String address) {
		BigInteger balance = Util.getBalance(address, "NBC");
		return Double.toString(balance.doubleValue() / Config.nbc_unit);
	}
	
	public String send(String source, String destination, Double amount) {
		Blocks blocks = Blocks.getInstance();
		BigInteger quantity = new BigDecimal(amount*Config.nbc_unit).toBigInteger();
		try {
			Transaction tx = Send.create(source, destination, "NBC", quantity);
			blocks.sendTransaction(source, tx);
			logger.info("Success! You sent "+amount+" NBC to "+destination+".");
			return tx.getHashAsString();
		} catch (Exception e) {
			logger.info("Error! There was a problem with your transaction: "+e.getMessage());						
			return "Error: "+e.getMessage();
		}
	}
	
	public String getSends(String address) {
		Database db = Database.getInstance();
		Blocks blocks = Blocks.getInstance();
		//get my sends
		ResultSet rs = db.executeQuery("select * from sends where (source='"+address+"') and asset='NBC' and validity='valid' order by block_index desc, tx_index desc;");
		JSONArray jsonArray = new JSONArray();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("amount", String.format(Config.nbc_display_format, BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.nbc_unit.doubleValue()));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				map.put("destination", rs.getString("destination"));
				map.put("confirmations", Math.max(blocks.bitcoinBlock - rs.getInt("block_index") + 1,0));
				jsonArray.put(map);
			}
		} catch (SQLException e) {
		}
		return jsonArray.toString();								
	}
	
	public String getReceives(String address) {
		Database db = Database.getInstance();
		Blocks blocks = Blocks.getInstance();
		//get my receives
		ResultSet rs = db.executeQuery("select * from sends where (destination='"+address+"') and asset='NBC' and validity='valid' order by block_index desc, tx_index desc;");
		JSONArray jsonArray = new JSONArray();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("amount", String.format(Config.nbc_display_format, BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.nbc_unit.doubleValue()));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				map.put("destination", rs.getString("destination"));
				map.put("confirmations", Math.max(blocks.bitcoinBlock - rs.getInt("block_index") + 1,0));
				jsonArray.put(map);
				
			}
		} catch (SQLException e) {
		}
		return jsonArray.toString();									
	}
	
	public String importPrivKey(String privateKey) {
		return importPrivateKey(privateKey);
	}
	public String importPrivateKey(String privateKey) {
		Blocks blocks = Blocks.getInstance();
		String address;
		try {
			address = blocks.importPrivateKey(privateKey);
			BigInteger balanceBTC = Util.getBalance(address, "BTC");
			return "\""+address+"\""+":"+String.format("%.8f",balanceBTC.doubleValue() / Config.btc_unit.doubleValue());
		} catch (Exception e) {
			return "Error: "+e.getMessage();
		}
	}

	public void reparse() {
    	Blocks blocks = Blocks.getInstance();
    	blocks.reparse();
    }
	
}