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
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.bitcoin.core.Transaction;

public class Bet {
	static Logger logger = LoggerFactory.getLogger(Bet.class);
	public static Integer length = 8+2;
	public static Integer id = 41; //modified for newbiecoin bet game

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

				ResultSet rsCheck = db.executeQuery("select * from bets where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				if (message.size() == length) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(length);
					for (byte b : message) {
						byteBuffer.put(b);
					}			
					BigInteger bet = BigInteger.valueOf(byteBuffer.getLong(0));
					Short bet_bs = byteBuffer.getShort(8);
					
					//Double houseEdge = Config.houseEdge;
					BigInteger nbcSupply = Util.nbcSupply();
					String validity = "invalid";
					if (!source.equals("") && bet.compareTo(BigInteger.ZERO)>0
						&& (bet_bs==Config.BET_BIGGER ||  bet_bs==Config.BET_SMALLER)
						) {
						if (bet.compareTo(Util.getBalance(source, "NBC"))<=0) {
								validity = "valid";
								Util.debit(source, "NBC", bet, "Debit bet amount", txHash, blockIndex);
						}
					}
					db.executeUpdate("insert into bets(tx_index, tx_hash, block_index, source, bet, bet_bs, profit, nbc_supply, validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"','"+bet.toString()+"','"+bet_bs.toString()+"','0','"+nbcSupply.toString()+"','"+validity+"')");					
				}				
			}
		} catch (SQLException e) {	
		}
	}

	public static List<BetInfo> getPending(String source) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+source+"' and destination='' and prefix_type=0 order by tx_index desc;");
		List<BetInfo> bets = new ArrayList<BetInfo>();
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

				ResultSet rsCheck = db.executeQuery("select * from bets where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					List<Byte> messageType = blocks.getMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getMessageFromTransaction(dataString);
					if (messageType.get(3)==Bet.id.byteValue() && message.size() == length ) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
						BigInteger bet = BigInteger.valueOf(byteBuffer.getLong(0));
						Short bet_bs = byteBuffer.getShort(8);
						//Double houseEdge = Config.houseEdge;
						//BigInteger nbcSupply = Util.nbcSupply();
						//String validity = "invalid";
						if (!source.equals("") && bet.compareTo(BigInteger.ZERO)>0 
							&& (bet_bs==Config.BET_BIGGER ||  bet_bs==Config.BET_SMALLER)
						    ) {
							if (bet.compareTo(Util.getBalance(source, "NBC"))<=0) {
									BetInfo betInfo = new BetInfo();
									betInfo.bet = bet;
									betInfo.bet_bs = bet_bs;
									betInfo.source = source;
									betInfo.txHash = txHash;
                                    betInfo.blockIndex = blockIndex;
                                    betInfo.blockTime = blockTime;
									bets.add(betInfo);
							}
						}
					}	
				}
			}
		} catch (SQLException e) {	
		}	
		return bets;
	}

	public static Transaction create(String source,BigInteger bet, Short bet_bs) throws Exception {
		BigInteger nbcSupply = Util.nbcSupply();
		if (source.equals("")) throw new Exception("Please specify a source address.");
		if (!(bet.compareTo(BigInteger.ZERO)>0)) throw new Exception("Please bet more than zero.");
		if ( bet_bs !=Config.BET_BIGGER &&  bet_bs !=Config.BET_SMALLER  ) 
			throw new Exception("Please specify a valid bet: bigger or smaller.");

		if (!(bet.compareTo(Util.getBalance(source, "NBC"))<=0)) throw new Exception("Please specify a bet that is smaller than your NBC balance.");

		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(length+4);
		byteBuffer.putInt(0, id);
		byteBuffer.putLong(0+4, bet.longValue());
		byteBuffer.putShort(4+8, bet_bs);
		List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
		dataArrayList.addAll(0, Util.toByteArrayList(Config.newb_prefix.getBytes()));
		byte[] data = Util.toByteArray(dataArrayList);

		String dataString = "";
		try {
			dataString = new String(data,"ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
		}

		Transaction tx = blocks.transaction(source, "", BigInteger.ZERO, BigInteger.valueOf(Config.minFee), dataString);
		return tx;
	}

	public static void resolve(Integer lastBlock) {
		//resolve bets
		if(lastBlock<Config.betStartBlock+Config.betPeriodBlocks 
			|| (lastBlock-Config.betStartBlock)%Config.betPeriodBlocks!= Config.betResolveWaitBlocks ){
			logger.info("Wait next block for resolving bets");
			return;
		}
		
		String lastTxHash=Util.getBlockHash(lastBlock);
		if( lastTxHash==null ){
			logger.error("Invalid block hash");
			return;
		}
		
		Short rolled_bs=null;
		
		logger.info("Resolving bets (Block="+lastBlock.toString()+")");
		Integer  randomBlock = lastBlock-Config.betResolveWaitBlocks;
		
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks where block_index="+randomBlock.toString()+";");
		try {
			Long sumRandom=Long.parseLong("0");
			Integer randomTimes=0;
			if(rs.next()) {
				Integer blockIndex = rs.getInt("block_index");
				Long blockNonce = rs.getLong("block_nonce");
				sumRandom += blockNonce % 10;
				randomTimes++;
				
				logger.info(">>>>>>   ["+randomTimes.toString()+"] Block="+blockIndex.toString()+"  nonce="+blockNonce.toString()+"  sumRandom="+sumRandom.toString());
			} else {
				logger.error("Invalid random block["+randomBlock.toString()+"]");
				return;
			}

			if(sumRandom<5)
				rolled_bs=Config.BET_SMALLER;
			else
				rolled_bs=Config.BET_BIGGER;
				
			logger.info("<<<<<<   rolled_bs="+rolled_bs.toString()+" for "+randomTimes.toString()+" blocks  finalRandomNumber="+sumRandom.toString());
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		Integer  resolveStartBlock = lastBlock-Config.betResolveWaitBlocks-Config.betPeriodBlocks;
		Integer  resolveEndBlock   = lastBlock-Config.betResolveWaitBlocks-1;
		BigInteger  win_bet_sum=BigInteger.ZERO;
		BigInteger  lost_bet_sum=BigInteger.ZERO;
		//Double   win_profit_percent=0;
		//Double   lost_profit_percent=0;
		
		rs = db.executeQuery("select bet_bs,sum(bet) as bet_sum from bets where bets.validity='valid' AND bets.block_index>="+resolveStartBlock.toString()+" AND bets.block_index<="+resolveEndBlock.toString()+"  group by bet_bs;");
		try {
			while (rs.next()) {
				Short bet_bs = rs.getShort("bet_bs");
				BigInteger bet_sum = BigInteger.valueOf(rs.getLong("bet_sum"));
				if(bet_bs==rolled_bs){
					win_bet_sum=bet_sum;
				} else {
					lost_bet_sum=bet_sum;
				}
			}
		} catch (SQLException e) {
			logger.error(e.toString());
			return;
		}
		
		rs = db.executeQuery("select block_time,blocks.block_index as block_index,tx_index,tx_hash,source,bet,bet_bs,nbc_supply from bets,blocks where bets.block_index=blocks.block_index and bets.validity='valid' and bets.resolved IS NOT 'true' AND bets.block_index>="+resolveStartBlock.toString()+" AND bets.block_index<="+resolveEndBlock.toString()+"  order by block_index asc;");
		
		try {
			while (rs.next()) {
				String source = rs.getString("source");
				String txHash = rs.getString("tx_hash");
				Integer txIndex = rs.getInt("tx_index");
				Integer blockIndex = rs.getInt("block_index");
				BigInteger bet = BigInteger.valueOf(rs.getLong("bet"));
				Short bet_bs = rs.getShort("bet_bs");
				BigInteger nbcSupply = BigInteger.valueOf(rs.getLong("nbc_supply"));
				Date blockTime = new Date((long)rs.getLong("block_time")*1000);

				logger.info("Attempting to resolve bet "+txHash+" at block "+blockIndex.toString());
				logger.info("    bet= "+ bet.toString()+" uNBC  bet_bs= "+bet_bs.toString());
				
				BigInteger profit = BigInteger.ZERO;
				BigInteger houseFee = BigInteger.ZERO;
				
				if(win_bet_sum.compareTo(BigInteger.ZERO)==0 || lost_bet_sum.compareTo(BigInteger.ZERO)==0){
					profit = BigInteger.ZERO;
				}else if(win_bet_sum.compareTo(lost_bet_sum)==0){
					profit = (bet_bs==rolled_bs) ?  bet : bet.multiply(BigInteger.valueOf(-1));
				}else if(win_bet_sum.compareTo(lost_bet_sum)>0){
					profit = (bet_bs==rolled_bs)   
							? new BigDecimal(bet.doubleValue()*(lost_bet_sum.doubleValue()/win_bet_sum.doubleValue())).toBigInteger( )
							: bet.multiply(BigInteger.valueOf(-1));
				} else {
					profit = (bet_bs==rolled_bs)   
							? bet
							: new BigDecimal(bet.doubleValue()*( win_bet_sum.doubleValue()/lost_bet_sum.doubleValue())).toBigInteger().multiply(BigInteger.valueOf(-1));
				}
				
				if(profit.compareTo(BigInteger.ZERO)>0 ){
					houseFee=new BigDecimal(profit.doubleValue()*Config.houseEdge).toBigInteger( );
					profit= profit.subtract(houseFee);
				}
				logger.info("------["+source+"]    profit="+profit.toString()+" uNBC  houseFee="+houseFee.toString()+" uNBC");
				
				db.executeUpdate("update bets set profit='"+profit.toString()+"',roll='"+rolled_bs+"', resolved='true' where tx_index='"+txIndex+"';");
			
				BigInteger betReturn = bet.add(profit);
				if(betReturn.compareTo(BigInteger.ZERO)>0)
					Util.credit(source, "NBC", betReturn, "bet return", lastTxHash, lastBlock);
				if(houseFee.compareTo(BigInteger.ZERO)>0 )
					Util.credit(Config.houseAddressFund, "NBC", houseFee, Config.houseFunctionName, lastTxHash, lastBlock);
			}
		} catch (SQLException e) {
			logger.error(e.toString());
		}
	}
}

class LottoResult {
	public List<LottoDraw> draw;
	public String jsonSearch;
}
class LottoDraw {
	public String date;
	public List<BigInteger> numbersDrawn;
}

class BetInfo {
	public String source;
	public BigInteger bet;
	public Short  bet_bs;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
}