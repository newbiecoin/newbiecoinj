import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.Transaction;

public class Burn {
    static Logger logger = LoggerFactory.getLogger(Burn.class);

	public static void parse(Integer txIndex) {
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
                
                String statusMessage = "\n++++++++++++++++++++++++++++++++++\n Burn tx "+txIndex.toString()+"\n++++++++++++++++++++++++++++++++++\n";
                logger.error(statusMessage);
				
                //if(source.equals("") || blockIndex<Config.pobTrialStartBlock || blockIndex>Config.pobDownEndBlock)
                //    return;
                
				ResultSet rsCheck = db.executeQuery("select * from burns where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				ResultSet rsBurns = db.executeQuery("select sum(earned) as earned from burns where validity='valid';");
				BigInteger totalBurns = BigInteger.ZERO;
				BigInteger maxBurns = BigInteger.ZERO;
				if (rsBurns.next()) {
					totalBurns = BigInteger.valueOf(rsBurns.getInt("earned"));
				}
				maxBurns = BigInteger.valueOf(Config.maxBurn).multiply(BigInteger.valueOf(Config.nbc_unit));

                Double multiplier = getPobMultiplier(blockIndex);
                statusMessage = "\n++++++++++++++++++++++++++++++++++\n Block "+blockIndex.toString() +" 's multiplier is "+multiplier.toString()+"\n++++++++++++++++++++++++++++++++++\n";
                logger.error(statusMessage);
        
				Double earnedUnrounded = btcAmount.doubleValue() * multiplier;
				BigInteger earned = BigInteger.valueOf(earnedUnrounded.longValue());
				if ( !source.equals("") && blockIndex>=Config.pobTrialStartBlock && blockIndex<=Config.pobDownEndBlock  && totalBurns.add(earned).compareTo(maxBurns)<0) {
					db.executeUpdate("insert into burns(tx_index, tx_hash, block_index, source, burned, earned, validity,destination) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"','"+btcAmount.toString()+"','"+earned+"','valid','"+destination+"')");
					Util.credit(source, "NBC", earned, "BTC burned", txHash, blockIndex);
				}
			}
		} catch (SQLException e) {
		}
	}
    
    public static Double getPobMultiplier(Integer blockIndex){
        if( blockIndex>=Config.pobTrialStartBlock && blockIndex<=Config.pobTrialEndBlock )
            return Config.pobTrialMultiplier.doubleValue();
        else if( blockIndex>=Config.pobMaxStartBlock && blockIndex<=Config.pobMaxEndBlock )
            return Config.pobMaxMultiplier.doubleValue();
        else if( blockIndex>=Config.pobDownStartBlock && blockIndex<=Config.pobDownEndBlock ){
            Integer totalTime = Config.pobDownEndBlock - Config.pobDownStartBlock;
            Integer partialTime = Config.pobDownEndBlock - blockIndex;
                
            return  Config.pobDownEndMultiplier.doubleValue() + (partialTime.doubleValue()/totalTime.doubleValue()) * (Config.pobDownInitMultiplier.doubleValue() - Config.pobDownEndMultiplier.doubleValue());
        }else
            return new Integer(0).doubleValue();
    }
    
    public static Transaction create(String source, String destination, BigInteger btcAmount) throws Exception {
        String statusMessage = "\n***********************************\n Burn  "+btcAmount.toString()+" uBTC from " + source + " to "+ destination +"\n***********************************";
        logger.info(statusMessage);
            
		if (!source.equals("") 
          && (destination.equals(Config.burnAddressFund) || destination.equals(Config.burnAddressDark)) ) {
			BigInteger sourceBalance = Util.getBalance(source, "BTC");
			Integer assetId = Util.getAssetId("BTC");
            
            statusMessage = "sourceBalance = "+sourceBalance.toString()+" uBTC";
            logger.info(statusMessage);
            
			if ( sourceBalance.compareTo(btcAmount.add(BigInteger.valueOf(Config.minFee)) )>=0 ) {
				Blocks blocks = Blocks.getInstance();

				Transaction tx = blocks.transaction(source, destination, btcAmount, BigInteger.valueOf(Config.minFee), "");
                
                statusMessage = "Generated TX ";
                logger.info(statusMessage);
            
				return tx;
			} else {
				throw new Exception("Please send less than your balance.");
			}
		} else {
			throw new Exception("Please specify a source address and destination address, and only send NBC.");
		}
	}
}
