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
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.bitcoin.core.Transaction;

public class BetWorldCup {
	static Logger logger = LoggerFactory.getLogger(BetWorldCup.class);
	public static Integer length = 8+2;
	public static Integer id = 42; //for WorldCup bet game
	
	public static HashMap<String , String> teamMap = null;
	
	public static void init(){
		teamMap=new HashMap<String , String>( );
		teamMap.put("1","Algeria/阿尔及利亚");
		teamMap.put("2","Argentina/阿根廷");
		teamMap.put("3","Australia/澳大利亚");
		teamMap.put("4","Belgium/比利时");
		teamMap.put("5","BosniaHerzegovina/波黑");
		teamMap.put("6","Brazil/巴西");
		teamMap.put("7","Cameroon/喀麦隆");
		teamMap.put("8","Chile/智利");
		teamMap.put("9","Colombia/哥伦比亚");
		teamMap.put("10","CostaRica/哥斯达黎加");
		teamMap.put("11","Coted'ivoire/科特迪瓦");
		teamMap.put("12","Croatia/克罗地亚");
		teamMap.put("13","Ecuador/厄瓜多尔");
		teamMap.put("14","England/英格兰");
		teamMap.put("15","France/法国");
		teamMap.put("16","Germany/德国");
		teamMap.put("17","Ghana/加纳");
		teamMap.put("18","Greece/希腊");
		teamMap.put("19","Honduras/洪都拉斯");
		teamMap.put("20","Iran/伊朗");
		teamMap.put("21","Italy/意大利");
		teamMap.put("22","Japan/日本");
		teamMap.put("23","KoreaRepublic/韩国");
		teamMap.put("24","Mexico/墨西哥");
		teamMap.put("25","Netherlands/荷兰");
		teamMap.put("26","Nigeria/尼日利亚");
		teamMap.put("27","Portugal/葡萄牙");
		teamMap.put("28","Russia/俄罗斯");
		teamMap.put("29","Spain/西班牙");
		teamMap.put("30","Switzerland/瑞士");
		teamMap.put("31","Uruguay/乌拉圭");
		teamMap.put("32","USA/美国");

		createTables(null);
	}
	
	public static void createTables(Database db){
		if(db==null) 
			db = Database.getInstance();
		try {
			db.executeUpdate("CREATE TABLE IF NOT EXISTS bets_worldcup (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, bet INTEGER, bet_set INTEGER,  profit INTEGER, nbc_supply INTEGER, roll INTEGER, resolved TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON bets_worldcup (block_index)");
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}
	
	public static String getTeamLabel(Short teamId){
		String teamLabel=teamMap.get(teamId.toString());
		return teamLabel;
	}	
	
	public static String getBetSetLabel(Short bet_set){
		Short teamId1=bet_set.compareTo(new Short("1000"))<0?Short.parseShort(bet_set.toString().substring(0,1)):Short.parseShort(bet_set.toString().substring(0,2));
		Short teamId2=bet_set.compareTo(new Short("1000"))<0?Short.parseShort(bet_set.toString().substring(1)):Short.parseShort(bet_set.toString().substring(2));

		return getTeamLabel(teamId1)+" & "+getTeamLabel(teamId2);
	}	

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
				Integer blockTime = rs.getInt("block_time");
				String txHash = rs.getString("tx_hash");

				ResultSet rsCheck = db.executeQuery("select * from bets_worldcup where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				if (message.size() == length) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(length);
					for (byte b : message) {
						byteBuffer.put(b);
					}			
					BigInteger bet = BigInteger.valueOf(byteBuffer.getLong(0));
					Short bet_set = byteBuffer.getShort(8);
					
					//Double houseEdge = Config.houseEdge;
					BigInteger nbcSupply = Util.nbcSupply();
					String validity = "invalid";
					if (!source.equals("") && (bet_set>100 || bet_set<10000) ) {
						if(bet.compareTo(BigInteger.ZERO)==0 && source.equals(Config.houseAddressFund)
   						   && blockTime>=Config.WORLDCUP2014_RESOLVE_SCHEME_UTC-3600 ){
							validity = "valid"; //Resolved broadcast from the house
						}else if (bet.compareTo(BigInteger.ZERO)>0 
						    && blockTime>Config.WORLDCUP2014_BETTING_START_UTC-3600
							&& blockTime<Config.WORLDCUP2014_BETTING_END_UTC+3600
							&& bet.compareTo(Util.getBalance(source, "NBC"))<=0) {
							validity = "valid";
							Util.debit(source, "NBC", bet, "Debit bet amount", txHash, blockIndex);
						}
					}
					db.executeUpdate("insert into bets_worldcup(tx_index, tx_hash, block_index, source, bet, bet_set, profit, nbc_supply, validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"','"+bet.toString()+"','"+bet_set.toString()+"','0','"+nbcSupply.toString()+"','"+validity+"')");					
				}				
			}
		} catch (SQLException e) {	
		}
	}

	public static List<BetWorldCupInfo> getPending(String source) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+source+"' and destination='' and prefix_type=0 order by tx_index desc;");
		List<BetWorldCupInfo> bets = new ArrayList<BetWorldCupInfo>();
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

				ResultSet rsCheck = db.executeQuery("select * from bets_worldcup where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					List<Byte> messageType = blocks.getMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getMessageFromTransaction(dataString);
					if (messageType.get(3)==BetWorldCup.id.byteValue() && message.size() == length ) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
						BigInteger bet = BigInteger.valueOf(byteBuffer.getLong(0));
						Short bet_set = byteBuffer.getShort(8);
						//Double houseEdge = Config.houseEdge;
						//BigInteger nbcSupply = Util.nbcSupply();
						//String validity = "invalid";
						if (!source.equals("") && bet.compareTo(BigInteger.ZERO)>0 
							&& (bet_set>100 ||  bet_set<10000)
						    ) {
							if (bet.compareTo(Util.getBalance(source, "NBC"))<=0) {
									BetWorldCupInfo betInfo = new BetWorldCupInfo();
									betInfo.bet = bet;
									betInfo.bet_set = bet_set;
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

	public static Transaction create(String source,BigInteger bet, Short championTeamId,Short secondTeamId) throws Exception {
		BigInteger nbcSupply = Util.nbcSupply();
		if (source.equals("")) throw new Exception("Please specify a source address.");
		if (!(bet.compareTo(BigInteger.ZERO)>0)) throw new Exception("Please bet more than zero.");
		if ( championTeamId <1 ||  championTeamId>32 || secondTeamId <1 ||  secondTeamId>32 || championTeamId==secondTeamId) 
			throw new Exception("Please specify two valid teams: the champion and the second place.");
		if (!(bet.compareTo(Util.getBalance(source, "NBC"))<=0)) throw new Exception("Please specify a bet that is smaller than your NBC balance.");

		Short bet_set=Short.valueOf(new Integer(championTeamId.intValue()*100+secondTeamId.intValue()).toString());
		
		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(length+4);
		byteBuffer.putInt(0, id);
		byteBuffer.putLong(0+4, bet.longValue());
		byteBuffer.putShort(4+8, bet_set);
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
	
	public static Transaction createResolvedBroadcast(Short championTeamId,Short secondTeamId) throws Exception {
		BigInteger nbcSupply = Util.nbcSupply();
		String source=Config.houseAddressFund;
		Integer bet=0;
		if (source.equals("")) throw new Exception("Please specify a houseAddressFund .");
		if ( championTeamId <1 ||  championTeamId>32 || secondTeamId <1 ||  secondTeamId>32 || championTeamId==secondTeamId) 
			throw new Exception("Please specify two valid teams: the champion and the second place.");

		Short bet_set=Short.valueOf(new Integer(championTeamId.intValue()*100+secondTeamId.intValue()).toString());
		
		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(length+4);
		byteBuffer.putInt(0, id);
		byteBuffer.putLong(0+4, bet.longValue());
		byteBuffer.putShort(4+8, bet_set);
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

	//resolve worldcup bets
	public static void resolve(Integer lastBlock,Integer lastBlockTime) {
		if( lastBlock > 311939 ){
			//logger.info("Feature closed");
			return;
		}
		
		if( lastBlockTime<Config.WORLDCUP2014_RESOLVE_SCHEME_UTC ){
			logger.info("Waiting for resolving worldcup2014 bets");
			return;
		}
		
		String lastTxHash=Util.getBlockHash(lastBlock);
		if( lastTxHash==null ){
			logger.error("Invalid block hash");
			return;
		}
		
		Short rolled_result=getResolvedResult();
		if(rolled_result==null){
			logger.info("Pending the worldcup2014 resolved broadcast");
			return;
		}
		
		logger.info("Resolving worldcup (Block="+lastBlock.toString()+")");

		BigInteger  win_bet_sum=BigInteger.ZERO;
		BigInteger  lost_bet_sum=BigInteger.ZERO;
		
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select bet_set,sum(bet) as bet_sum from bets_worldcup where bet>0 and validity='valid'  group by bet_set;");
		try {
			while (rs.next()) {
				Short bet_set = rs.getShort("bet_set");
				BigInteger bet_sum = BigInteger.valueOf(rs.getLong("bet_sum"));
				if(bet_set.compareTo(rolled_result)==0){
					win_bet_sum=bet_sum;
				} else {
					lost_bet_sum=lost_bet_sum.add(bet_sum);
				}
			}
		} catch (SQLException e) {
			logger.error(e.toString());
			return;
		}
		logger.info(">>>>>>       rolled_result="+rolled_result.toString()+" win_bet_sum="+win_bet_sum.toString()+"  lost_bet_sum="+lost_bet_sum.toString());
		
		rs = db.executeQuery("select block_time,blocks.block_index as block_index,tx_index,tx_hash,source,bet,bet_set,nbc_supply from bets_worldcup,blocks where bets_worldcup.block_index=blocks.block_index and bets_worldcup.bet>0 and bets_worldcup.validity='valid' and bets_worldcup.resolved IS NOT 'true' order by block_index asc;");
		
		try {
			while (rs.next()) {
				String source = rs.getString("source");
				String txHash = rs.getString("tx_hash");
				Integer txIndex = rs.getInt("tx_index");
				Integer blockIndex = rs.getInt("block_index");
				BigInteger bet = BigInteger.valueOf(rs.getLong("bet"));
				Short bet_set = rs.getShort("bet_set");
				BigInteger nbcSupply = BigInteger.valueOf(rs.getLong("nbc_supply"));
				Date blockTime = new Date((long)rs.getLong("block_time")*1000);

				logger.info("Attempting to resolve bet "+txHash+" at block "+blockIndex.toString());
				logger.info("    bet= "+ bet.toString()+" uNBC  bet_set= "+bet_set.toString());
				
				BigInteger profit = BigInteger.ZERO;
				BigInteger houseFee = BigInteger.ZERO;
				
				if(win_bet_sum.compareTo(BigInteger.ZERO)==0 || lost_bet_sum.compareTo(BigInteger.ZERO)==0){
					profit = BigInteger.ZERO;
				}else{
					profit = (bet_set.compareTo(rolled_result)==0)   
							? new BigDecimal(bet.doubleValue()*(lost_bet_sum.doubleValue()/win_bet_sum.doubleValue())).toBigInteger( )
							: bet.multiply(BigInteger.valueOf(-1));
				} 
				
				if(profit.compareTo(BigInteger.ZERO)>0 ){
					houseFee=new BigDecimal(profit.doubleValue()*Config.houseEdge).toBigInteger( );
					profit= profit.subtract(houseFee);
				}
				logger.info("------["+source+"]    profit="+profit.toString()+" uNBC  houseFee="+houseFee.toString()+" uNBC");
				
				db.executeUpdate("update bets_worldcup set profit='"+profit.toString()+"',roll='"+rolled_result+"', resolved='true' where tx_index='"+txIndex+"';");
			
				BigInteger betReturn = bet.add(profit);
				if(betReturn.compareTo(BigInteger.ZERO)>0)
					Util.credit(source, "NBC", betReturn, "bet return", lastTxHash, lastBlock);
					
				if(houseFee.compareTo(BigInteger.ZERO)>0 )
					Util.credit(Config.houseAddressFund, "NBC", houseFee, Config.houseWorldCupFunctionName, lastTxHash, lastBlock);
			}
		} catch (SQLException e) {
			logger.error(e.toString());
		}
	}
	
	public static Short getResolvedResult(){
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from bets_worldcup where source='"+Config.houseAddressFund+"' and bet=0 order by block_index DESC limit 1;");
		try {
			if(rs.next()) {
				return  rs.getShort("bet_set");
			} else {
				logger.info("Pending house resolved broadcast");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
}

class BetWorldCupInfo {
	public String source;
	public BigInteger bet;
	public Short  bet_set;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
}