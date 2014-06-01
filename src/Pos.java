import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pos {
    static Logger logger = LoggerFactory.getLogger(Pos.class);

	public static void doPosBlock(Integer blockIndex,Integer blockTime) {
        String statusMessage = "Do POS block "+blockIndex.toString();
		logger.info(statusMessage);
        
        if( blockIndex <= Config.posFirstBlock || blockIndex > Config.posEndBlock 
            || ( blockIndex - Config.posFirstBlock ) % Config.posWaitBlocks != 0 ){
            logger.info("Wait next block.");
            return;
        }
        
		Database db = Database.getInstance();

        ResultSet rs = db.executeQuery("select address,sum(amount) as amount from balances where asset='NBC' group by address;");
        try {
            while (rs.next()) {
                String  address=rs.getString("address");
                BigInteger nbcAmount = BigInteger.valueOf(rs.getLong("amount"));
                statusMessage = "Do address: " + address + "  balance: "+nbcAmount.toString();
                logger.info(statusMessage);
                
                Double nbcPosEarnedUnrounded =  nbcAmount.doubleValue() * Config.posInterest ;
                BigInteger nbcPosEarned = BigInteger.valueOf(nbcPosEarnedUnrounded.longValue());

                statusMessage = "   Earned : " + nbcPosEarned.toString() ;
                logger.info(statusMessage);

                Integer txIndex=Util.getLastTxIndex()+1;
                String txHash=address+"POS"+blockIndex.toString();
                db.executeUpdate("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data) VALUES ('"+txIndex.toString()+"','"+txHash+"','"+ blockIndex.toString() +"','"+blockTime.toString()+"','','"+address+"','0','0','')");
                
                db.executeUpdate("insert into sends(tx_index, tx_hash, block_index, source, destination, asset, amount, validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','POS','"+address+"','NBC','"+nbcPosEarned.toString()+"','valid')");	
                
                Util.credit(address, "NBC", nbcPosEarned, "POS ", "", blockIndex);
            }
        } catch (SQLException e) {
        }

	}
}
