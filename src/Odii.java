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

public class Odii {
	static Logger logger = LoggerFactory.getLogger(Odii.class);
	public static Byte id = Config.FUNC_ID_ODII_REGIST; //for registing new ODII 
	
	//public static HashMap<String , String> teamMap = null;
	
	public static void init(){
		createTables(null);		
	}
	
	public static void createTables(Database db){
		if(db==null) 
			db = Database.getInstance();
		try {
			db.executeUpdate("CREATE TABLE IF NOT EXISTS odiis (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE,block_index INTEGER,full_odii TEXT UNIQUE,short_odii INTEGER UNIQUE ,  owner TEXT,register TEXT, odii_set TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON odiis (block_index)");
			//db.executeUpdate("CREATE TABLE IF NOT EXISTS tx_sn_in_block (tx_index INTEGER PRIMARY KEY,  block_index INTEGER, sn_in_block INTEGER)");
            db.executeUpdate("ALTER TABLE transactions ADD prefix_type INTEGER DEFAULT 0");
            db.executeUpdate("ALTER TABLE transactions ADD sn_in_block INTEGER DEFAULT 0;");
            
            db.executeUpdate("CREATE TABLE IF NOT EXISTS odii_update_logs (tx_index INTEGER PRIMARY KEY,block_index INTEGER, full_odii TEXT, updater TEXT,update_set TEXT, validity TEXT);");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS odii_index_idx ON odii_update_logs (full_odii);");
            
            /*
            //test chinese encode
            String oldStr="EncodeTest编码测试";
            String newStr=Util.uncompress(Util.compress(oldStr));
            db.executeUpdate("CREATE TABLE IF NOT EXISTS encodetest (old_str TEXT,new_str TEXT);");

            PreparedStatement ps = db.connection.prepareStatement("insert into encodetest(old_str, new_str) values(?,?);");

            ps.setString(1, oldStr);
            ps.setString(2, newStr);
			
            ps.execute();
            //test end
            */
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}
	
	public static void parse(Integer txIndex, List<Byte> message) {
		logger.info( "\n=============================\n Parsing ODII txIndex="+txIndex.toString()+"\n=====================\n");
		
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
				Integer txSnInBlock=rs.getInt("sn_in_block");
				String full_odii=blockIndex+"."+txSnInBlock;
                Integer short_odii=getLastShortOdii()+1;

				ResultSet rsCheck = db.executeQuery("select * from odiis where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

                String validity = "invalid";
                JSONObject odii_set=new JSONObject( );
                String odii_set_owner= destination.length()==0 ? source:destination;
                
				if (message.size() >2) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
					for (byte b : message) {
						byteBuffer.put(b);
					}			

                    Byte odii_set_data_type=byteBuffer.get(0); 
					int odii_set_length = new Short(byteBuffer.getShort(1)).intValue();
					
					logger.info( "\n=============================\n message.size()="+message.size()+",odii_set_length="+odii_set_length+"\n=====================\n");
					
					if( !source.equals("") && message.size()==1+2+odii_set_length )
					{
						validity = "valid";
						
						byte[] odii_set_byte_array=new byte[odii_set_length];
						
						for(int off=0;off<odii_set_length;off++)
							odii_set_byte_array[off]=byteBuffer.get(1+2+off);
						
						
						try{ 
                            if(odii_set_data_type==Config.DATA_BIN_GZIP)
                                odii_set=new JSONObject(Util.uncompress(new String(odii_set_byte_array,"ISO-8859-1")));
                            else
                                odii_set=new JSONObject(new String(odii_set_byte_array,"UTF-8"));
                            
							logger.info( "\n=============================\n odii_set="+odii_set.toString()+"\n=====================\n");
						} catch (Exception e) {	
                            odii_set=new JSONObject();
							logger.error(e.toString());
						}	

						
					}
				}		

                PreparedStatement ps = db.connection.prepareStatement("insert into odiis(tx_index, tx_hash, block_index, full_odii,short_odii,owner, register,odii_set,validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+full_odii+"','"+short_odii.toString()+"',?,'"+source+"',?,'"+validity+"');");

                ps.setString(1, odii_set_owner);
                ps.setString(2, odii_set.toString());
                ps.execute();
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}
	}

	public static List<OdiiInfo> getPending(String register) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+register+"' and prefix_type=1 order by tx_index desc;");
		List<OdiiInfo> odiis = new ArrayList<OdiiInfo>();
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

				ResultSet rsCheck = db.executeQuery("select * from odiis where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					Byte messageType = blocks.getPPkMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getPPkMessageFromTransaction(dataString);
					
					logger.info("messageType="+messageType.toString()+"  message.size="+message.size());

					if (messageType==Odii.id && message.size()>2) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
                        Byte odii_set_data_type=byteBuffer.get(0); 
						int odii_set_length = new Short(byteBuffer.getShort(1)).intValue();
						if( !register.equals("") && message.size()==1+2+odii_set_length )
						{
								byte[] odii_set_byte_array=new byte[odii_set_length];
								for(int off=0;off<odii_set_length;off++)
									odii_set_byte_array[off]=byteBuffer.get(1+2+off);
							
								JSONObject odii_set;
								String odii_set_owner = destination; //HtmlRegexpUtil.filterHtml( destination.length()==0 ? register : destination );
                                    
								try{
                                    if(odii_set_data_type==Config.DATA_BIN_GZIP)
                                        odii_set=new JSONObject(Util.uncompress(new String(odii_set_byte_array,"ISO-8859-1")));
									else
                                        odii_set=new JSONObject(new String(odii_set_byte_array,"UTF-8"));
									
								} catch (Exception e) {	
									logger.error(e.toString());
									return odiis;
								}	
								OdiiInfo odiiInfo = new OdiiInfo();

								odiiInfo.register = register;
								
								odiiInfo.fullOdii="";
                                odiiInfo.shortOdii=-1;
								odiiInfo.txSnInBlock=-1;
								odiiInfo.owner=odii_set_owner;
								odiiInfo.odiiSet = odii_set;
								odiiInfo.txIndex = txIndex;
								odiiInfo.txHash = txHash;
								odiiInfo.blockIndex = blockIndex;
								odiiInfo.blockTime = blockTime;
								odiiInfo.validity="pending";
								odiis.add(odiiInfo);
						}
					}	
				}
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}	
		return odiis;
	}
	
	public static OdiiInfo getOdiiInfo(String odii) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select cp.full_odii,cp.short_odii,cp.register,cp.owner ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odii_set, cp.validity from odiis cp,transactions where (cp.full_odii='"+odii+"' or cp.short_odii='"+odii+"') and cp.tx_index=transactions.tx_index;");

		try {
			
			if(rs.next()) {
				OdiiInfo odiiInfo = new OdiiInfo();
				odiiInfo.fullOdii = rs.getString("full_odii");
                odiiInfo.shortOdii = rs.getInt("short_odii");
				odiiInfo.register = rs.getString("register");
				odiiInfo.owner = rs.getString("owner");
				odiiInfo.txIndex = rs.getInt("tx_index");
				odiiInfo.txHash = rs.getString("tx_hash");
				odiiInfo.blockIndex = rs.getInt("block_index");
				odiiInfo.blockTime = rs.getInt("block_time");
				odiiInfo.validity = rs.getString("validity");
				
				try{
					odiiInfo.odiiSet = new JSONObject(rs.getString("odii_set"));
				}catch (Exception e) {
					logger.error(e.toString());
				}

				return odiiInfo;
			}
		} catch (SQLException e) {
		}	

		return null;		
	}
    
    public static Integer getShortOdii(String full_odii) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select full_odii,short_odii from odiis  where full_odii='"+full_odii+"';");

		try {
			if(rs.next()) {
				return rs.getInt("short_odii");
			}
		} catch (SQLException e) {
		}	

		return -1;		
	}
		
	public static Transaction createOdii(String register,String owner,JSONObject odii_set) throws Exception {
		if (register.equals("")) throw new Exception("Please specify a register address.");
		if (odii_set==null) throw new Exception("Please specify valid odii_set.");
		
		logger.info("createOdii odii_set="+odii_set.toString());
		
        Byte odii_set_data_type=Config.DATA_TEXT_UTF8; 
        byte[] odii_set_byte_array=odii_set.toString().getBytes("UTF-8");
        byte[] odii_set_byte_array_gzip=Util.compress(odii_set.toString()).getBytes("ISO-8859-1");
        
        if(odii_set_byte_array.length>odii_set_byte_array_gzip.length){ //need compress the long data
           odii_set_byte_array=odii_set_byte_array_gzip;
           odii_set_data_type=Config.DATA_BIN_GZIP;
        }    

		Short odii_set_length=Short.valueOf(new Integer(odii_set_byte_array.length).toString());
        if (odii_set_length>65535) throw new Exception("Too big setting data.(Should be less than 65535 bytes)");
		
		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(1+1+2+odii_set_length.intValue());
		byteBuffer.put(id);
        byteBuffer.put(odii_set_data_type);
		byteBuffer.putShort(odii_set_length);
		byteBuffer.put(odii_set_byte_array,0,odii_set_length);
			
		List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
		dataArrayList.addAll(0, Util.toByteArrayList(Config.ppk_prefix.getBytes()));
		byte[] data = Util.toByteArray(dataArrayList);

		String dataString = "";
		try {
			dataString = new String(data,"ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
		}
		Transaction tx = blocks.transaction(register, owner, BigInteger.valueOf(Config.dustSize), BigInteger.valueOf(Config.ppkStandardDataFee), dataString);

		//just for debug
		//logger.info("Test:createOdii register="+register+", dataString.length="+dataString.length()+" dataString="+dataString);
		//blocks.importTransaction(tx, null, null);
		//System.exit(0);
		
		return tx;
	}

	public static HashMap<String,Object> parseOdiiSet(HashMap<String,Object> map,JSONObject odii_set,String myaddress,String register,String owner ,String awaiting_update_tx_index ) throws Exception {
		/*if(map.containsKey("owner")){
			if(odii_set.isNull("owner"))
				map.put("owner", "");
			else
				map.put("owner", HtmlRegexpUtil.filterHtml(odii_set.getString("owner")));
		}*/
        String authSet="";
        try{
            authSet=odii_set.getString("auth");
        }catch(Exception e){
        }
        
        if( checkUpdatable(authSet,myaddress,register,owner)){
            map.put("me_updatable", true);
        }
        
        if( myaddress.equals(register) ){
            map.put("me_transable",true);
        }
        
        if(awaiting_update_tx_index!=null){
            OdiiUpdateInfo odiiUpdateInfo=OdiiUpdate.getOdiiUpdateInfo(awaiting_update_tx_index);
            
            if(odiiUpdateInfo!=null && odiiUpdateInfo.updateSet!=null){
                odii_set=odiiUpdateInfo.updateSet;  
                map.put("awaiting_update_tx_hash", odiiUpdateInfo.txHash);
                if(!odii_set.isNull("owner"))
                    map.put("owner", odii_set.getString("owner"));
            }
        }
        
        if(odii_set.isNull("title"))
			map.put("title", "");
		else
            map.put("title", HtmlRegexpUtil.filterHtml(odii_set.getString("title")));
		
		if(odii_set.isNull("email"))
			map.put("email", "");
		else
			map.put("email", HtmlRegexpUtil.filterHtml(odii_set.getString("email")));

        String ap_list_debug="<ul>";
        if(!odii_set.isNull("ap_list")){
            JSONArray  ap_list = odii_set.getJSONArray("ap_list");
            
            for(int tt=0;tt<ap_list.length();tt++){
                String ap_url=(String)ap_list.get(tt);
                map.put("ap"+(tt+1)+"_url", ap_url);
                ap_list_debug+="<li>"+HtmlRegexpUtil.filterHtml(ap_url)+"</li>\n";
            }
        }
        ap_list_debug+="</ul>";

		map.put("ap_list_debug", ap_list_debug);

        map.put("auth", authSet);

		return map;
	}
    
    public static boolean checkUpdatable(String authSet,String updater,String register,String owner  ) {
        if(
            ( ( authSet==null || authSet.length()==0 || authSet.equals("1") ) && updater.equals(register) )
          ||( ( authSet.equals("0")||authSet.equals("2") ) && ( updater.equals(register) || updater.equals(owner) ) )
        ){
            return true;
        } else {
            return false;
        }
    }

    
    public static Integer getLastShortOdii() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("SELECT short_odii from odiis order by short_odii DESC LIMIT 1;");
		try {
			while(rs.next()) {
				return rs.getInt("short_odii");
			}
		} catch (SQLException e) {
		}	
		return -1;
	}	
}

class OdiiInfo {
	public String fullOdii;
    public Integer shortOdii;
	public Integer txIndex;
	public String register;
	public String owner;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
	public Integer txSnInBlock;
	public String  validity;

	public JSONObject odiiSet;
}