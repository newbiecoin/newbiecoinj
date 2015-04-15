import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.bitcoin.core.Transaction;
import com.google.common.primitives.Ints;

public class Order {
    static Logger logger = LoggerFactory.getLogger(Order.class);
	public static Integer length = 8+8+8+8+2+8;
	public static Integer id = 10;
	
	public static void parse(Integer txIndex, List<Byte> message) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where tx_index="+txIndex.toString());
		try {
			if (rs.next()) {
				String source = rs.getString("source");
				String destination = rs.getString("destination");
				BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
				BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
				Integer blockIndex = rs.getInt("block_index");
				String txHash = rs.getString("tx_hash");
				
				ResultSet rsCheck = db.executeQuery("select * from orders where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				if (message.size() == length) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(length);
					for (byte b : message) {
						byteBuffer.put(b);
					}
					Integer giveId = Ints.checkedCast(byteBuffer.getLong(0));
					BigInteger giveAmount = BigInteger.valueOf(byteBuffer.getLong(8));
					Integer getId = Ints.checkedCast(byteBuffer.getLong(16));
					BigInteger getAmount = BigInteger.valueOf(byteBuffer.getLong(24));
					BigInteger expiration = BigInteger.valueOf(byteBuffer.getShort(32));
					BigInteger feeRequired = BigInteger.valueOf(byteBuffer.getLong(34));
					String giveAsset = Util.getAssetName(giveId);
					String getAsset = Util.getAssetName(getId);
					String validity = "invalid";
					BigInteger expirationIndex = expiration.add(BigInteger.valueOf(blockIndex));
					if (!source.equals("")) {
						BigInteger sourceBalance = Util.getBalance(source, giveAsset);
						Double price = getAmount.doubleValue() / giveAmount.doubleValue();
						if (!giveAsset.equals("BTC")) {
							giveAmount = giveAmount.min(sourceBalance);
							getAmount = new BigDecimal(price * giveAmount.doubleValue()).toBigInteger();
						}
						if (!giveAsset.equals(getAsset) && giveAmount.compareTo(BigInteger.ZERO)>=0 && getAmount.compareTo(BigInteger.ZERO)>=0 && expiration.compareTo(BigInteger.valueOf(Config.maxExpiration))<=0) {
							validity = "valid";
							if (!giveAsset.equals("BTC")) {
								Util.debit(source, giveAsset, giveAmount, "Debit to cover order", txHash, blockIndex);								
							}
						}
					}
					db.executeUpdate("insert into orders(tx_index, tx_hash, block_index, source, give_asset, give_amount, give_remaining, get_asset, get_amount, get_remaining, expiration, expire_index, fee_required, fee_provided, fee_remaining, validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"','"+giveAsset+"','"+giveAmount.toString()+"','"+giveAmount.toString()+"','"+getAsset+"','"+getAmount.toString()+"','"+getAmount.toString()+"','"+expiration.toString()+"','"+expirationIndex.toString()+"','"+feeRequired.toString()+"','"+fee.toString()+"','"+fee.toString()+"','"+validity+"')");
					expire(blockIndex);
					match(txIndex);
				}				
			}
		} catch (SQLException e) {	
		}
	}
	
	public static List<OrderInfo> getPending(String address) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+address+"' and destination='' and prefix_type=0 order by tx_index desc;");
		List<OrderInfo> orders = new ArrayList<OrderInfo>();
		Blocks blocks = Blocks.getInstance();
		try {
			while (rs.next()) {
				String destination = rs.getString("destination");
				BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
				BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
				Integer blockIndex = rs.getInt("block_index");
                Integer blockTime = rs.getInt("block_time");
				String txHash = rs.getString("tx_hash");
				Integer txIndex = rs.getInt("tx_index");
				String dataString = rs.getString("data");

				ResultSet rsCheck = db.executeQuery("select * from orders where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					List<Byte> messageType = blocks.getMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getMessageFromTransaction(dataString);
					
					logger.info("getPending(): messageType="+messageType.get(3)+"  message.size="+message.size());
					
					
					if (messageType.get(3)==Order.id.byteValue() && message.size()>2) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}	
						
						OrderInfo orderInfo = new OrderInfo();

						orderInfo.source = address;
						orderInfo.txIndex = txIndex;
						orderInfo.txHash = txHash;
						orderInfo.blockIndex = blockIndex;
						orderInfo.blockTime = blockTime;
									
						Integer giveId = Ints.checkedCast(byteBuffer.getLong(0));
						orderInfo.giveAsset = Util.getAssetName(giveId);
						orderInfo.giveAmount = BigInteger.valueOf(byteBuffer.getLong(8));
						Integer getId = Ints.checkedCast(byteBuffer.getLong(16));
						orderInfo.getAsset = Util.getAssetName(getId);
						orderInfo.getAmount = BigInteger.valueOf(byteBuffer.getLong(24));
						orderInfo.expiration = BigInteger.valueOf(byteBuffer.getShort(32));
						orderInfo.feeRequired = BigInteger.valueOf(byteBuffer.getLong(34));

						orderInfo.validity = "pending";
						//BigInteger expirationIndex = expiration.add(BigInteger.valueOf(blockIndex));
					
						orders.add(orderInfo);
					}	
				}
			}
		} catch (SQLException e) {	
		}	
		return orders;
	}
	
	public static Transaction create(String source, String giveAsset, BigInteger giveAmount, String getAsset, BigInteger getAmount, BigInteger expiration, BigInteger feeRequired, BigInteger feeProvided) throws Exception {
		if (!source.equals("")) {
			BigInteger sourceBalance = Util.getBalance(source, giveAsset);
			Integer giveId = Util.getAssetId(giveAsset);
			Integer getId = Util.getAssetId(getAsset);
			if (sourceBalance.compareTo(giveAmount)>=0) {
				Blocks blocks = Blocks.getInstance();
				ByteBuffer byteBuffer = ByteBuffer.allocate(length+4);
				byteBuffer.putInt(0, id);
				byteBuffer.putLong(0+4, giveId);
				byteBuffer.putLong(8+4, giveAmount.longValue());
				byteBuffer.putLong(16+4, getId);
				byteBuffer.putLong(24+4, getAmount.longValue());
				byteBuffer.putShort(32+4, expiration.shortValue());
				byteBuffer.putLong(34+4, feeRequired.longValue());
				List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
				dataArrayList.addAll(0, Util.toByteArrayList(Config.newb_prefix.getBytes()));
				byte[] data = Util.toByteArray(dataArrayList);

				String dataString = "";
				try {
					dataString = new String(data,"ISO-8859-1");
				} catch (UnsupportedEncodingException e) {
				}
				if (feeProvided.compareTo(BigInteger.valueOf(Config.minFee))<0) {
					feeProvided = BigInteger.valueOf(Config.minFee);
				}
				Transaction tx = blocks.transaction(source, "", BigInteger.ZERO, feeProvided, dataString);
				return tx;
			} else {
				throw new Exception("Please make your order smaller than your balance.");
			}
		} else {
			throw new Exception("Please specify a source address.");
		}
	}
	public static void match(Integer txIndex) {
		logger.info("###############  Matching orders for txIndex="+txIndex);
		
		Database db = Database.getInstance();
		ResultSet rstx1 = db.executeQuery("select * from orders where validity='valid' and tx_index="+txIndex.toString());
		try {
			if (rstx1.next()) {
				Integer tx1Index = txIndex;
				BigInteger tx1ExpireIndex = BigInteger.valueOf(rstx1.getLong("expire_index"));
				Integer tx1BlockIndex = rstx1.getInt("block_index");
				String tx1GiveAsset = rstx1.getString("give_asset");
				String tx1GetAsset = rstx1.getString("get_asset");
				String tx1Hash = rstx1.getString("tx_hash");
				String tx1Source = rstx1.getString("source");
				ResultSet rstx0 = db.executeQuery("select 1.0*get_amount/give_amount as price_index,* from orders where give_asset='"+tx1GetAsset+"' and get_asset='"+tx1GiveAsset+"' and validity='valid' order by price_index, tx_index;");
				BigInteger tx1FeeRemaining = BigInteger.valueOf(rstx1.getLong("fee_remaining"));
				BigInteger tx1FeeRequired = BigInteger.valueOf(rstx1.getLong("fee_required"));
				BigInteger tx1GiveRemaining = BigInteger.valueOf(rstx1.getLong("give_remaining"));
				BigInteger tx1GetRemaining = BigInteger.valueOf(rstx1.getLong("get_remaining"));
				BigInteger tx1GiveAmount = BigInteger.valueOf(rstx1.getLong("give_amount"));
				BigInteger tx1GetAmount = BigInteger.valueOf(rstx1.getLong("get_amount"));
				while (rstx0.next()) {
					Integer tx0Index = rstx0.getInt("tx_index");
					logger.info("---> Checking tx0Index="+tx0Index);
					BigInteger tx0ExpireIndex = BigInteger.valueOf(rstx0.getLong("expire_index"));
					BigInteger tx0BlockIndex = BigInteger.valueOf(rstx0.getLong("block_index"));
					String tx0GiveAsset = rstx0.getString("give_asset");
					String tx0GetAsset = rstx0.getString("get_asset");
					String tx0Hash = rstx0.getString("tx_hash");
					String tx0Source = rstx0.getString("source");
					BigInteger tx0FeeRemaining = BigInteger.valueOf(rstx0.getLong("fee_remaining"));
					BigInteger tx0FeeRequired = BigInteger.valueOf(rstx0.getLong("fee_required"));
					BigInteger tx0GiveRemaining = BigInteger.valueOf(rstx0.getLong("give_remaining"));
					BigInteger tx0GetRemaining = BigInteger.valueOf(rstx0.getLong("get_remaining"));
					BigInteger tx0GiveAmount = BigInteger.valueOf(rstx0.getLong("give_amount"));
					BigInteger tx0GetAmount = BigInteger.valueOf(rstx0.getLong("get_amount"));
					if (tx1GiveRemaining.compareTo(BigInteger.ZERO)>0 && tx0GiveRemaining.compareTo(BigInteger.ZERO)>0) {
						Double tx0Price = tx0GetAmount.doubleValue() / tx0GiveAmount.doubleValue();
						Double tx1Price = tx1GetAmount.doubleValue() / tx1GiveAmount.doubleValue();
						Double tx1InversePrice = tx1GiveAmount.doubleValue() / tx1GetAmount.doubleValue();
						if (tx0Price.compareTo(tx1InversePrice)<=0) {
							BigInteger forwardAmount = BigInteger.ZERO;
							//PROTOCOL CHANGE
							if (tx0BlockIndex.compareTo(BigInteger.valueOf(299200))<0) {
								forwardAmount = tx0GiveRemaining.min(new BigDecimal(tx1GiveRemaining).divideToIntegralValue(new BigDecimal(tx0Price)).toBigInteger());								
							}else{
								forwardAmount = tx0GiveRemaining.min(new BigDecimal(tx1GiveRemaining.doubleValue() / tx0Price.doubleValue()).toBigInteger());																
							}
							BigInteger backwardAmount = new BigDecimal(forwardAmount.doubleValue() * tx0Price).toBigInteger();
							BigInteger fee = BigInteger.ZERO;
							if (tx1GetAsset.equals("BTC")) {
								fee = new BigDecimal(tx1FeeRequired.doubleValue() * forwardAmount.doubleValue() / tx1GetRemaining.doubleValue()).toBigInteger();
								if (tx0FeeRemaining.compareTo(fee)<0) {
									continue;
								} else {
									tx0FeeRemaining = tx0FeeRemaining.subtract(fee);
								}
							} else if (tx1GiveAsset.equals("BTC")) {
								fee = new BigDecimal(tx0FeeRequired.doubleValue() * backwardAmount.doubleValue() / tx0GetRemaining.doubleValue()).toBigInteger();
								if (tx1FeeRemaining.compareTo(fee)<0) {
									continue;
								} else {
									tx1FeeRemaining = tx1FeeRemaining.subtract(fee);
								}								
							}
							String forwardAsset = tx1GetAsset;
							String backwardAsset = tx1GiveAsset;
							
							if( (forwardAsset.equals("BTC") && forwardAmount.compareTo(BigInteger.valueOf(Config.minOrderMatchBTC))<0)
								||  (backwardAsset.equals("BTC") && backwardAmount.compareTo(BigInteger.valueOf(Config.minOrderMatchBTC))<0)
							){
								logger.error("Mismatched order for BTC amount less than "+(Config.minOrderMatchBTC)+": "+forwardAsset+"="+forwardAmount+","+backwardAsset+"="+backwardAmount+" [tx0="+tx0Hash +",tx1="+ tx1Hash+"]");
							} else {
								String orderMatchId = tx0Hash + tx1Hash;
								String validity = "pending";
								if (!tx1GiveAsset.equals("BTC") && !tx1GetAsset.equals("BTC")) {
									validity = "valid";
									Util.credit(tx1Source, tx1GetAsset, forwardAmount, "Order match", tx1Hash, tx1BlockIndex);
									Util.credit(tx0Source, tx0GetAsset, backwardAmount, "Order match", tx1Hash, tx1BlockIndex);
								}
								tx0GiveRemaining = tx0GiveRemaining.subtract(forwardAmount);
								tx0GetRemaining = tx0GetRemaining.subtract(backwardAmount);
								tx1GiveRemaining = tx1GiveRemaining.subtract(backwardAmount);
								tx1GetRemaining = tx1GetRemaining.subtract(forwardAmount);
								db.executeUpdate("update orders set give_remaining='"+tx0GiveRemaining.toString()+"', get_remaining='"+tx0GetRemaining.toString()+"', fee_remaining='"+tx0FeeRemaining.toString()+"' where tx_index='"+tx0Index+"';");
								db.executeUpdate("update orders set give_remaining='"+tx1GiveRemaining.toString()+"', get_remaining='"+tx1GetRemaining.toString()+"', fee_remaining='"+tx1FeeRemaining.toString()+"' where tx_index='"+tx1Index+"';");
								BigInteger matchExpireIndex = tx0ExpireIndex.min(tx1ExpireIndex);
								db.executeUpdate("insert into order_matches(id, tx0_index, tx0_hash, tx0_address, tx1_index, tx1_hash, tx1_address, forward_asset, forward_amount, backward_asset, backward_amount, tx0_block_index, tx1_block_index, tx0_expiration, tx1_expiration, match_expire_index, validity) values('"+orderMatchId+"', '"+tx0Index.toString()+"', '"+tx0Hash+"', '"+tx0Source+"', '"+tx1Index.toString()+"', '"+tx1Hash+"', '"+tx1Source+"', '"+forwardAsset+"', '"+forwardAmount.toString()+"', '"+backwardAsset+"', '"+backwardAmount.toString()+"', '"+tx0BlockIndex.toString()+"', '"+tx1BlockIndex.toString()+"', '"+tx0ExpireIndex.toString()+"', '"+tx1ExpireIndex.toString()+"', '"+matchExpireIndex.toString()+"', '"+validity+"');");
							}
						}
					}
				}
			}
		} catch (SQLException e) {	
		}
	}
	
	public static void expire() {
		expire(Util.getLastBlock());
	}
	
	public static void expire(Integer lastBlock) {
		logger.info("Expiring orders");

		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from orders where validity='valid' and expire_index<"+lastBlock.toString());
		try {
			while (rs.next()) {
				String txIndex = rs.getString("tx_index");
				Integer blockIndex = rs.getInt("block_index");
				String txHash = rs.getString("tx_hash");
				String giveAsset = rs.getString("give_asset");
				String source = rs.getString("source");
				BigInteger giveRemaining = BigInteger.valueOf(rs.getLong("give_remaining"));
				db.executeUpdate("update orders set validity = 'invalid: expired' where tx_index = '"+txIndex+"'");
				if (!giveAsset.equals("BTC")) {
					Util.credit(source, giveAsset, giveRemaining, "Expired order credit", txHash, blockIndex);
				}
				db.executeUpdate("insert into order_expirations(order_index, order_hash, block_index) values('"+txIndex+"','"+txHash+"','"+blockIndex.toString()+"');");
			}
		} catch (SQLException e) {	
		}		
		rs = db.executeQuery("select * from order_matches where validity='pending' and match_expire_index<"+lastBlock.toString());
		try {
			while (rs.next()) {
				String id = rs.getString("id");
				String orderMatchId = rs.getString("tx0_hash") + rs.getString("tx1_hash");
				Integer tx1BlockIndex = rs.getInt("tx1_block_index");
				String tx0Index = rs.getString("tx0_index");
				String tx1Index = rs.getString("tx1_index");
				String tx0Address = rs.getString("tx0_address");
				String tx1Address = rs.getString("tx1_address");
				String forwardAsset = rs.getString("forward_asset");
				String backwardAsset = rs.getString("backward_asset");
				BigInteger forwardAmount = BigInteger.valueOf(rs.getLong("forward_amount"));
				BigInteger backwardAmount = BigInteger.valueOf(rs.getLong("backward_amount"));
				db.executeUpdate("update order_matches set validity = 'invalid: expired awaiting payment' where id = '"+id+"'");
				db.executeUpdate("insert into order_match_expirations(order_match_id, tx0_address, tx1_address, block_index) values('"+orderMatchId+"','"+tx0Address+"','"+tx1Address+"','"+tx1BlockIndex.toString()+"');");
				ResultSet rstx0Order = db.executeQuery("select * from orders where tx_index='"+tx0Index+"';");
				if (rstx0Order.next()) {
					Integer tx0OrderTimeLeft = rstx0Order.getInt("expire_index") - Util.getLastBlock();
					BigInteger tx0OrderGiveRemaining = BigInteger.valueOf(rstx0Order.getLong("give_remaining"));
					BigInteger tx0OrderGetRemaining = BigInteger.valueOf(rstx0Order.getLong("get_remaining"));
					if (tx0OrderTimeLeft.compareTo(0)>=0) {
						BigInteger giveRemaining = tx0OrderGiveRemaining.add(forwardAmount);
						BigInteger getRemaining = tx0OrderGetRemaining.add(backwardAmount);
						db.executeUpdate("update orders set give_remaining = '"+giveRemaining.toString()+"', get_remaining = '"+getRemaining.toString()+"' where tx_index = '"+tx0Index+"';");
					} else if (!forwardAsset.equals("BTC")) {
						Util.credit(tx0Address, forwardAsset, forwardAmount, "Expired order credit", "", tx1BlockIndex);
					}
				}
				ResultSet rstx1Order = db.executeQuery("select * from orders where tx_index='"+tx1Index+"';");
				if (rstx1Order.next()) {
					Integer tx1OrderTimeLeft = rstx1Order.getInt("expire_index") - Util.getLastBlock();
					BigInteger tx1OrderGiveRemaining = BigInteger.valueOf(rstx1Order.getLong("give_remaining"));
					BigInteger tx1OrderGetRemaining = BigInteger.valueOf(rstx1Order.getLong("get_remaining"));
					if (tx1OrderTimeLeft.compareTo(0)>=0) {
						BigInteger giveRemaining = tx1OrderGiveRemaining.add(backwardAmount);
						BigInteger getRemaining = tx1OrderGetRemaining.add(forwardAmount);
						db.executeUpdate("update orders set give_remaining = '"+giveRemaining.toString()+"', get_remaining = '"+getRemaining.toString()+"' where tx_index = '"+tx1Index+"';");
					} else if (!backwardAsset.equals("BTC")) {
						Util.credit(tx1Address, backwardAsset, backwardAmount, "Expired order credit", "", tx1BlockIndex);
					}
				}
			}
		} catch (SQLException e) {	
		}		
	}
}

class OrderInfo {
	public Integer txIndex;
	public String source;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
	public String validity;
	
	public String giveAsset;
	public BigInteger giveAmount;
	public String getAsset;
	public BigInteger getAmount;
	public BigInteger expiration;
	public BigInteger feeRequired;
}