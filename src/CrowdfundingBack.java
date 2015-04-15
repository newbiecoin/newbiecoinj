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

public class CrowdfundingBack {
	static Logger logger = LoggerFactory.getLogger(CrowdfundingBack.class);
	public static Integer id = 51; //for crownfund back
	
	//public static HashMap<String , String> teamMap = null;
	
	public static void init(){
	
	}
	
	public static void parse(Integer txIndex, List<Byte> message) {
		logger.info( "\n=============================\n Parsing crowdfundingback txIndex="+txIndex.toString()+"\n=====================\n");
		
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

				ResultSet rsCheck = db.executeQuery("select * from crowdfunding_projects where tx_index='"+txIndex.toString()+"'");
				if (rsCheck.next()) return;

				if (message.size() >2) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
					for (byte b : message) {
						byteBuffer.put(b);
					}			
					
					String validity = "invalid";
					JSONObject back_data;
					int back_data_length = new Short(byteBuffer.getShort(0)).intValue();
					
					logger.info( "\n=============================\n message.size()="+message.size()+",back_data_length="+back_data_length+"\n=====================\n");
					
					if( !source.equals("") && message.size()==2+back_data_length )
					{
						byte[] back_data_byte_array=new byte[back_data_length];
						
						for(int off=0;off<back_data_length;off++)
							back_data_byte_array[off]=byteBuffer.get(0+2+off);
							
						try{
							back_data=new JSONObject(Util.uncompress(new String(back_data_byte_array,"ISO-8859-1")));
							
							logger.info( "\n=============================\n back_data="+back_data.toString()+"\n=====================\n");
							
							CrowdfundingProjectInfo projectInfo = CrowdfundingProject.getProjectInfo(back_data.getString("prj_tx_hash"));
							
							Integer tmp_project_tx_index=null;
							if(projectInfo==null){
								tmp_project_tx_index=0;
								logger.error( "\n=============================\n Invalid back data for invalid project tx hash:"+back_data.toString()+"\n=====================\n");
							} else if( !projectInfo.validity.equals("valid")) {
								tmp_project_tx_index=projectInfo.txIndex;
								logger.error( "\n=============================\n Invalid back data for invalid project:"+back_data.toString()+"\n=====================\n");
							} else  {	
								tmp_project_tx_index=projectInfo.txIndex;
								BigInteger back_price = BigInteger.valueOf(back_data.getLong("price"));
								if (back_price.compareTo(BigInteger.ZERO)>0 
									&& back_price.compareTo(Util.getBalance(source, "NBC"))<=0){
									
									JSONObject back_stat = projectInfo.backStat; 
									JSONArray  item_sets = projectInfo.projectSet.getJSONArray("item_sets");
									for(int tt=0;tt<item_sets.length();tt++){
										JSONObject item_obj=(JSONObject)item_sets.get(tt);
										Long item_price=item_obj.getLong("price");
										if(back_price.compareTo(BigInteger.valueOf(item_price))==0){
											Integer item_backers=0;
											if(back_stat!=null && back_stat.has(item_price.toString()))
												item_backers=back_stat.getJSONObject(item_price.toString()).getInt("backers");
											Integer leftChance=item_obj.getInt("max")-item_backers;
											if(leftChance<=0){
												validity = "filled";
												logger.info( "\n=============================\n The backed item had been filled:"+back_data.toString()+"\n=====================\n");
											}else{
												validity = "valid";
												Util.debit(source, "NBC", back_price, "Debit back amount", txHash, blockIndex);
											}
											break;
										}
									}
								} else {
									logger.error( "\n=============================\n Invalid back price or no enough balance:"+back_data.toString()+"\n=====================\n");
								}
							}
							
							PreparedStatement ps = db.connection.prepareStatement("insert into crowdfunding_backers(tx_index, tx_hash, block_index, backer, project_tx_index,  back_price_nbc, email, validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"','"+tmp_project_tx_index+"','"+back_data.getLong("price")+"',?,'"+validity+"');");
							ps.setString(1, back_data.getString("email"));
							ps.execute();
							
							if(projectInfo!=null)
								CrowdfundingProject.updateProjectStat(projectInfo.txIndex.toString());
						} catch (Exception e) {	
							logger.error(e.toString());
							return;
						}	
					}
				}				
			}
		} catch (SQLException e) {	
		}
	}

	public static List<CrowdfundingBackerInfo> getPending(String backer) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+backer+"' and destination='' and prefix_type=0 order by tx_index desc;");
		List<CrowdfundingBackerInfo> backers = new ArrayList<CrowdfundingBackerInfo>();
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

				ResultSet rsCheck = db.executeQuery("select * from crowdfunding_backers where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					List<Byte> messageType = blocks.getMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getMessageFromTransaction(dataString);
					
					logger.info("getPending(): messageType="+messageType.get(3)+"  message.size="+message.size());
					
					
					if (messageType.get(3)==CrowdfundingBack.id.byteValue() && message.size()>2) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
						
						int back_data_length = new Short(byteBuffer.getShort(0)).intValue();
						if( !backer.equals("") && message.size()==2+back_data_length )
						{
								byte[] back_data_byte_array=new byte[back_data_length];
								for(int off=0;off<back_data_length;off++)
									back_data_byte_array[off]=byteBuffer.get(0+2+off);
							
								JSONObject back_data;
								try{
									back_data=new JSONObject(Util.uncompress(new String(back_data_byte_array,"ISO-8859-1")));
									
									CrowdfundingBackerInfo backInfo = new CrowdfundingBackerInfo();
									backInfo.backer = backer;
									backInfo.txIndex = txIndex;
									backInfo.txHash = txHash;
									backInfo.blockIndex = blockIndex;
									backInfo.blockTime = blockTime;
									backInfo.projectTxHash = back_data.getString("prj_tx_hash");
									backInfo.price = back_data.getLong("price");
									backInfo.email = back_data.getString("email");
									backers.add(backInfo);
								} catch (Exception e) {	
									logger.error(e.toString());
									return backers;
								}	
								
						}
					}	
				}
			}
		} catch (SQLException e) {	
		}	
		return backers;
	}
	
	public static Transaction backProject(String backer,String project_tx_hash,  BigInteger back_price,String email) throws Exception {
		if (backer.equals("")) throw new Exception("Please specify a backer address.");
		if (project_tx_hash==null || project_tx_hash.equals("")) throw new Exception("Please specify valid project.");
		if (!(back_price.compareTo(BigInteger.ZERO)>0)) throw new Exception("Please back more than zero.");
		if (!(back_price.compareTo(Util.getBalance(backer, "NBC"))<=0)) throw new Exception("Please specify a back that is smaller than your NBC balance.");

		HashMap mapBackSet = new HashMap(); 
		mapBackSet.put("prj_tx_hash", project_tx_hash); 
		mapBackSet.put("price", back_price); 
		mapBackSet.put("email", email); 
		
		logger.info("backer: "+backer.toString() +" , mapBackSet="+mapBackSet.toString());
		 
		JSONObject back_set = new JSONObject(mapBackSet); 
		
		byte[] back_set_byte_array=Util.compress(back_set.toString()).getBytes("ISO-8859-1");
		
		Short back_data_length=Short.valueOf(new Integer(back_set_byte_array.length).toString());
		
		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4+2+back_data_length.intValue());
		byteBuffer.putInt(id);
		byteBuffer.putShort(back_data_length);
		byteBuffer.put(back_set_byte_array,0,back_data_length);
			
		List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
		dataArrayList.addAll(0, Util.toByteArrayList(Config.newb_prefix.getBytes()));
		byte[] data = Util.toByteArray(dataArrayList);

		String dataString = "";
		try {
			dataString = new String(data,"ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
		}
		
		logger.info("Test:backProject backer="+backer+",  dataString="+dataString);
		//System.exit(0);
		Transaction tx = blocks.transaction(backer, "", BigInteger.ZERO, BigInteger.valueOf(Config.maxFee), dataString);
		return tx;
	}
}

class CrowdfundingBackerInfo {
	public Integer txIndex;
	public String backer;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
	
	public String projectTxHash;
	public Long price;
	public String email;
}
