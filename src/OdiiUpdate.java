import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
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

import org.json.JSONArray;
import org.json.JSONObject;

public class OdiiUpdate {
	static Logger logger = LoggerFactory.getLogger(OdiiUpdate.class);
	public static Byte id = Config.FUNC_ID_ODII_UPDATE; //for updateing an exist ODII 
    public static int UPDATE_ODII_PREFIX_LENGTH=31;
	
	public static void init(){
			
	}

	public static void parse(Integer txIndex, List<Byte> message) {
		logger.info( "\n=============================\n Parsing OdiiUpdate txIndex="+txIndex.toString()+"\n=====================\n");
		
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("SELECT * FROM transactions tx  WHERE tx.tx_index="+txIndex.toString());
		try {
			if (rs.next()) {
				String source = rs.getString("source");
				String destination = rs.getString("destination");
				BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
				BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
				Integer blockIndex = rs.getInt("block_index");
				Integer blockTime = rs.getInt("block_time");
				String txHash = rs.getString("tx_hash");
                
                if(destination.length()==0) destination=source;

				ResultSet rsCheck = db.executeQuery("select * from odii_update_logs where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				if (message.size() >2) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
					for (byte b : message) {
						byteBuffer.put(b);
					}			
					
                    String validity = "invalid";
                    JSONObject update_set=new JSONObject( );
                    
                    String full_odii=getFullOdiiFromUpdateMessage(byteBuffer); //需要完善对full_odii的取值安全检测
                    OdiiInfo oldOdiiInfo=Odii.getOdiiInfo(full_odii);

                    if(oldOdiiInfo!=null && !source.equals("") ){
                        if(message.size()==UPDATE_ODII_PREFIX_LENGTH){ //transfer register
                            if(source.equals(oldOdiiInfo.register)){//only the register can submit transfer 
                                PreparedStatement ps = db.connection.prepareStatement("UPDATE odiis SET register='"+destination+"' WHERE full_odii=?;");

                                ps.setString(1, full_odii);
                                ps.execute();
                                
                                validity = "valid";
                            }
                            try{ 
                                update_set.put("register",destination);
                            }catch(Exception e){
                            }
                        }else{ //update owner's set
                            String authSet="";
                            try{
                                authSet=oldOdiiInfo.odiiSet.getString("auth");
                            }catch(Exception e){
                            }
                            if( Odii.checkUpdatable(authSet,source,oldOdiiInfo.register,oldOdiiInfo.owner) ){
                                Byte update_set_data_type=byteBuffer.get(UPDATE_ODII_PREFIX_LENGTH); 
                                int update_set_length = new Short(byteBuffer.getShort(UPDATE_ODII_PREFIX_LENGTH+1)).intValue();
                                
                                logger.info( "\n=============================\n message.size()="+message.size()+",update_set_length="+update_set_length+"\n=====================\n");
                                
                                if( !source.equals("") && message.size()==UPDATE_ODII_PREFIX_LENGTH+1+2+update_set_length )
                                {
                                    validity = "valid";
                                    
                                    byte[] update_set_byte_array=new byte[update_set_length];
                                    
                                    for(int off=0;off<update_set_length;off++)
                                        update_set_byte_array[off]=byteBuffer.get(UPDATE_ODII_PREFIX_LENGTH+1+2+off);
                                    
                                    try{ 
                                        if(update_set_data_type==Config.DATA_BIN_GZIP)
                                            update_set=new JSONObject(Util.uncompress(new String(update_set_byte_array,"ISO-8859-1")));
                                        else
                                            update_set=new JSONObject(new String(update_set_byte_array,"UTF-8"));
                                        
                                        logger.info( "\n=============================\n update_set="+update_set.toString()+"\n=====================\n");
                                        
                                        JSONObject  real_odii_set=update_set;
                                        boolean needSecondComfirm=true;
                                        if(authSet.equals("2")){ //need update by register and owner together
                                            if(!update_set.isNull("confirm_tx_hash")){
                                                String confirm_tx_hash=update_set.getString("confirm_tx_hash");
                                                OdiiUpdateInfo odiiUpdateInfo=OdiiUpdate.getOdiiUpdateInfo(confirm_tx_hash);
                                                
                                                if(odiiUpdateInfo!=null && !source.equals(odiiUpdateInfo.updater)){     //confirm another's update
                                                    real_odii_set=odiiUpdateInfo.updateSet;
                                                    if(!real_odii_set.isNull("owner"))
                                                        real_odii_set.remove("owner");
                                                    db.executeUpdate("UPDATE odii_update_logs SET validity='valid' WHERE tx_index='"+odiiUpdateInfo.txIndex+"';");
                                                    needSecondComfirm=false;
                                                }
                                            }
                                        }else{
                                            needSecondComfirm=false;
                                        }
                                        
                                        if(!needSecondComfirm){
                                            PreparedStatement ps = db.connection.prepareStatement("UPDATE odiis SET owner='"+destination+"',odii_set=? WHERE full_odii=?;");

                                            ps.setString(1, real_odii_set.toString());
                                            ps.setString(2, full_odii);
                                            ps.execute();
                                            
                                            validity = "valid";
                                        }else{
                                            validity = "awaiting";
                                        }
                                        
                                        if(!destination.equals(oldOdiiInfo.owner))
                                            update_set.put("owner",destination);
                                        
                                    } catch (Exception e) {	
                                        update_set=new JSONObject();
                                        logger.error(e.toString());
                                    }	
                                }
                            }
                        }
                    }
                    
                    if(validity.equals("awaiting")){ //cancel the existed awaiting updates
                        PreparedStatement ps = db.connection.prepareStatement("update odii_update_logs set validity='overwrited' where full_odii=? and validity='awaiting' and updater='"+source+"';");

                        ps.setString(1, full_odii);
                        ps.execute();
                    }
                    
                    PreparedStatement ps = db.connection.prepareStatement("insert into odii_update_logs(tx_index, block_index,full_odii,updater, update_set,validity) values('"+txIndex.toString()+"','"+blockIndex.toString()+"',?,'"+source+"',?,'"+validity+"');");

                    ps.setString(1, full_odii);
                    ps.setString(2, update_set.toString());
                    ps.execute();
				}				
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}
	}

	public static List<OdiiUpdateInfo> getPending(String updater) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+updater+"' and prefix_type=1 order by tx_index desc;");
		List<OdiiUpdateInfo> odiiUpdateLogs = new ArrayList<OdiiUpdateInfo>();
		Blocks blocks = Blocks.getInstance();
		try {
			while (rs.next()) {
				String destination = rs.getString("destination");
				//BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
				//BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
				Integer blockIndex = rs.getInt("block_index");
                Integer blockTime = rs.getInt("block_time");
				String txHash = rs.getString("tx_hash");
				Integer txIndex = rs.getInt("tx_index");
				String dataString = rs.getString("data");

				ResultSet rsCheck = db.executeQuery("select * from odii_update_logs where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					Byte messageType = blocks.getPPkMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getPPkMessageFromTransaction(dataString);
					
					logger.info("messageType="+messageType.toString()+"  message.size="+message.size());

					if (messageType==OdiiUpdate.id && message.size()>2) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
                        
                        String full_odii=getFullOdiiFromUpdateMessage(byteBuffer);
                        
                        if(message.size()==UPDATE_ODII_PREFIX_LENGTH){ //transfer register
                            OdiiUpdateInfo odiiUpdateInfo = new OdiiUpdateInfo();

                            odiiUpdateInfo.updater = updater;
                            odiiUpdateInfo.fullOdii=full_odii;
                            odiiUpdateInfo.shortOdii=Odii.getShortOdii(full_odii);
                            odiiUpdateInfo.updateSet = new JSONObject();
                            try{
                                odiiUpdateInfo.updateSet.put("register",destination);
                            }catch(Exception e){                              
                            }
                            
                            odiiUpdateInfo.txIndex = txIndex;
                            odiiUpdateInfo.txHash = txHash;
                            odiiUpdateInfo.blockIndex = blockIndex;
                            odiiUpdateInfo.blockTime = blockTime;
                            odiiUpdateInfo.validity="pending";
                            odiiUpdateLogs.add(odiiUpdateInfo);
                        } else{ //update owner's set
                            Byte update_set_data_type=byteBuffer.get(UPDATE_ODII_PREFIX_LENGTH); 
                            int update_set_length = new Short(byteBuffer.getShort(UPDATE_ODII_PREFIX_LENGTH+1)).intValue();
                            if( !updater.equals("") && message.size()==UPDATE_ODII_PREFIX_LENGTH+1+2+update_set_length )
                            {
                                byte[] update_set_byte_array=new byte[update_set_length];
                                for(int off=0;off<update_set_length;off++)
                                    update_set_byte_array[off]=byteBuffer.get(UPDATE_ODII_PREFIX_LENGTH+1+2+off);
                            
                                JSONObject update_set;
                                    
                                try{
                                    if(update_set_data_type==Config.DATA_BIN_GZIP)
                                        update_set=new JSONObject(Util.uncompress(new String(update_set_byte_array,"ISO-8859-1")));
                                    else
                                        update_set=new JSONObject(new String(update_set_byte_array,"UTF-8"));
                                    
                                } catch (Exception e) {	
                                    logger.error(e.toString());
                                    return odiiUpdateLogs;
                                }	
                                OdiiUpdateInfo odiiUpdateInfo;
                                
                                if(update_set.isNull("confirm_tx_hash")){
                                    odiiUpdateInfo = new OdiiUpdateInfo();
                                    odiiUpdateInfo.updater = updater;
                                    odiiUpdateInfo.fullOdii=full_odii;
                                    odiiUpdateInfo.shortOdii=Odii.getShortOdii(full_odii);
                                    odiiUpdateInfo.updateSet = update_set;
                                    odiiUpdateInfo.txIndex = txIndex;
                                    odiiUpdateInfo.txHash = txHash;
                                    odiiUpdateInfo.blockIndex = blockIndex;
                                    odiiUpdateInfo.blockTime = blockTime;
                                    odiiUpdateInfo.validity="pending";
                                    
                                    odiiUpdateLogs.add(odiiUpdateInfo);
                                }else{
                                    try{
                                        String confirm_tx_hash=update_set.getString("confirm_tx_hash");
                                        odiiUpdateInfo=getOdiiUpdateInfo(confirm_tx_hash);
                                        
                                        if(odiiUpdateInfo!=null){
                                            odiiUpdateInfo.confirm_tx_hash=confirm_tx_hash;
                                            odiiUpdateLogs.add(odiiUpdateInfo);
                                        }
                                    }catch(Exception e){
                                    }
                                }
                                
                            }
                        }
					}	
				}
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}	
		return odiiUpdateLogs;
	}
    
    public static String getFullOdiiFromUpdateMessage(ByteBuffer byteBufferMessage){
        byte[] full_odii_byte_array=new byte[UPDATE_ODII_PREFIX_LENGTH];
        for(int off=0;off<UPDATE_ODII_PREFIX_LENGTH;off++)
            full_odii_byte_array[off]=byteBufferMessage.get(off);
        String full_odii=new String(full_odii_byte_array);
        full_odii=full_odii.trim();
        return full_odii;
    }
    
	public static OdiiUpdateInfo getOdiiUpdateInfo(String update_tx_index_or_hash) { 
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select l.tx_index, l.block_index,l.updater, l.update_set,l.validity,cp.full_odii,cp.short_odii,cp.register,cp.owner,transactions.block_time,transactions.tx_hash from odiis cp,odii_update_logs l,transactions where (l.tx_index='"+update_tx_index_or_hash+"' and l.full_odii=cp.full_odii and  l.tx_index=transactions.tx_index) or ( transactions.tx_hash='"+update_tx_index_or_hash+"' and l.tx_index=transactions.tx_index and  l.full_odii=cp.full_odii ) ;");

		try {
			
			if(rs.next()) {
				OdiiUpdateInfo odiiUpdateInfo = new OdiiUpdateInfo();
				odiiUpdateInfo.fullOdii = rs.getString("full_odii");
                odiiUpdateInfo.shortOdii = rs.getInt("short_odii");
				odiiUpdateInfo.updater = rs.getString("updater");
				odiiUpdateInfo.txIndex = rs.getInt("tx_index");
				odiiUpdateInfo.txHash = rs.getString("tx_hash");
				odiiUpdateInfo.blockIndex = rs.getInt("block_index");
				odiiUpdateInfo.blockTime = rs.getInt("block_time");
				odiiUpdateInfo.validity = rs.getString("validity");
				
				try{
					odiiUpdateInfo.updateSet = new JSONObject(rs.getString("update_set"));
				}catch (Exception e) {
					logger.error(e.toString());
				}

				return odiiUpdateInfo;
			}
		} catch (SQLException e) {
		}	

		return null;		
	}

    public static Transaction updateOdiiOwnerSet(String fullOdii,String updater,String owner,JSONObject update_set) throws Exception {
        return updateOdii(fullOdii,updater,null,owner,update_set);
    }
    
    public static Transaction transOdiiRegister(String fullOdii,String updater,String new_register) throws Exception {
        if (updater.equals(new_register)) throw new Exception("Please specify another register address which is not same to the updater.");
        return updateOdii(fullOdii,updater,new_register,null,null);
    }
    
	private static Transaction updateOdii(String fullOdii,String updater,String new_register,String owner,JSONObject update_set) throws Exception {
		if (updater.equals("")) throw new Exception("Please specify a updater address.");
        
        if(fullOdii.length()<UPDATE_ODII_PREFIX_LENGTH){
            while(fullOdii.length()<UPDATE_ODII_PREFIX_LENGTH){
                fullOdii=fullOdii+" "; //Append BLANK char
            }
        }
        byte[] full_odii_byte_array=fullOdii.toString().getBytes("ISO-8859-1");
        logger.info("Test:updateOdii fullOdii="+fullOdii+", full_odii_byte_array.length="+full_odii_byte_array.length);

        String  destination;
        ByteBuffer byteBuffer;
        
        if(new_register!=null){
            byteBuffer = ByteBuffer.allocate(UPDATE_ODII_PREFIX_LENGTH+1);
            byteBuffer.put(id);
            byteBuffer.put(full_odii_byte_array,0,UPDATE_ODII_PREFIX_LENGTH);
            
            destination=new_register;
        } else{
            if (update_set==null) throw new Exception("Please specify valid update_set.");
            logger.info("updateOdii update_set="+update_set.toString());
        
            Byte update_set_data_type=Config.DATA_TEXT_UTF8; 
            byte[] update_set_byte_array=update_set.toString().getBytes("UTF-8");
            byte[] update_set_byte_array_gzip=Util.compress(update_set.toString()).getBytes("ISO-8859-1");
            
            if(update_set_byte_array.length>update_set_byte_array_gzip.length){ //need compress the long data
               update_set_byte_array=update_set_byte_array_gzip;
               update_set_data_type=Config.DATA_BIN_GZIP;
            }    

            Short update_set_length=Short.valueOf(new Integer(update_set_byte_array.length).toString());
            if (update_set_length>65535) throw new Exception("Too big setting data.(Should be less than 65535 bytes)");
            
            byteBuffer = ByteBuffer.allocate(UPDATE_ODII_PREFIX_LENGTH+1+1+2+update_set_length.intValue());
            byteBuffer.put(id);
            byteBuffer.put(full_odii_byte_array,0,UPDATE_ODII_PREFIX_LENGTH);
            byteBuffer.put(update_set_data_type);
            byteBuffer.putShort(update_set_length);
            byteBuffer.put(update_set_byte_array,0,update_set_length);
                
            destination = updater.equals(owner) ? "" : owner ; 
        }
        
        List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
        dataArrayList.addAll(0, Util.toByteArrayList(Config.ppk_prefix.getBytes()));
        byte[] data = Util.toByteArray(dataArrayList);
            
		String dataString = "";
		try {
			dataString = new String(data,"ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
		}

        Blocks blocks = Blocks.getInstance();
		Transaction tx = blocks.transaction(updater, destination, BigInteger.valueOf(Config.dustSize), BigInteger.valueOf(Config.ppkStandardDataFee), dataString);

		/*
        //just for debug
		logger.info("Test:updateOdii updater="+updater+", dataString.length="+dataString.length()+" dataString="+dataString);
		blocks.importTransaction(tx, null, null, null);
		System.exit(0);
		*/
		return tx;
	}

	public static HashMap<String,Object> parseOdiiUpdateSet(HashMap<String,Object> map,JSONObject update_set) throws Exception {
        String update_desc="<ul>";
        if(update_set.isNull("register")){
            if(!update_set.isNull("confirm_tx_hash"))
                update_desc+="<li>Confirm below updates (TX_HASH:"+HtmlRegexpUtil.filterHtml(update_set.getString("confirm_tx_hash"))+")</li>\n";
            
            if(!update_set.isNull("owner"))
                update_desc+="<li>Transfer owner to "+HtmlRegexpUtil.filterHtml(update_set.getString("owner"))+"</li>\n";
            
            if(!update_set.isNull("title"))
                update_desc+="<li>Title:"+HtmlRegexpUtil.filterHtml(update_set.getString("title"))+"</li>\n";
            
            if(!update_set.isNull("email"))
                update_desc+="<li>Email:"+HtmlRegexpUtil.filterHtml(update_set.getString("email"))+"</li>\n";

            update_desc+="<li>Access points</li>\n<ul>\n";
            if(!update_set.isNull("ap_list")){
                JSONArray  ap_list = update_set.getJSONArray("ap_list");
                for(int tt=0;tt<ap_list.length();tt++){
                    update_desc+="<li>"+HtmlRegexpUtil.filterHtml((String)ap_list.get(tt))+"</li>\n";
                }
            }
            update_desc+="</ul>";
		}else{
            update_desc+="<li>Transfer register to "+HtmlRegexpUtil.filterHtml(update_set.getString("register"))+"</li>";
        }
        update_desc+="</ul>";
        map.put("update_desc", update_desc);
		return map;
	}

}

class OdiiUpdateInfo {
	public String fullOdii;
    public Integer shortOdii;
	public Integer txIndex;
	public String updater;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
	public String  validity;
    
    public String  confirm_tx_hash;

	public JSONObject updateSet;
}