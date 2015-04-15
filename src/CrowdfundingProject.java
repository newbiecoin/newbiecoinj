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

public class CrowdfundingProject {
	static Logger logger = LoggerFactory.getLogger(CrowdfundingProject.class);
	public static Integer id = 50; //for crownfund project
	
	//public static HashMap<String , String> teamMap = null;
	
	public static void init(){
		//teamMap=new HashMap<String , String>( );
		//teamMap.put("1","Algeria/阿尔及利亚");
		

		createTables(null);		
	}
	
	public static void createTables(Database db){
		if(db==null) 
			db = Database.getInstance();
		try {
			db.executeUpdate("CREATE TABLE IF NOT EXISTS crowdfunding_projects (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, owner TEXT, project_set TEXT,expire_utc INTEGER,min_fund INTEGER,  backers INTEGER, nbc_funded INTEGER, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON crowdfunding_projects (block_index)");
			db.executeUpdate("CREATE TABLE IF NOT EXISTS crowdfunding_backers (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, backer TEXT, project_tx_index  INTEGER,back_price_nbc INTEGER,  email TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS project_tx_index ON crowdfunding_backers (project_tx_index)");
			//db.executeUpdate("CREATE TABLE IF NOT EXISTS crowdfunding_follows (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, follower TEXT, project_tx_hash TEXT,project_block_index INTEGER, project_owner TEXT,follow_set TEXT,  nbc_amount INTEGER, validity TEXT)");
			//db.executeUpdate("CREATE INDEX IF NOT EXISTS follow_index_idx ON crowdfunding_follows (block_index)");
			//db.executeUpdate("CREATE INDEX IF NOT EXISTS follow_index_project ON crowdfunding_follows (project_block_index)");
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}
	
	public static void parse(Integer txIndex, List<Byte> message) {
		logger.info( "\n=============================\n Parsing crowdfundingproject txIndex="+txIndex.toString()+"\n=====================\n");
		
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
					JSONObject project_set=new JSONObject( );
					int project_set_length = new Short(byteBuffer.getShort(0)).intValue();
					
					logger.info( "\n=============================\n message.size()="+message.size()+",project_set_length="+project_set_length+"\n=====================\n");
					
					if( !source.equals("") && message.size()==2+project_set_length )
					{
						validity = "valid";
						
						byte[] project_set_byte_array=new byte[project_set_length];
						
						for(int off=0;off<project_set_length;off++)
							project_set_byte_array[off]=byteBuffer.get(0+2+off);
						
						Integer expire_utc=0;
						Long min_fund=0L;
						try{
							project_set=new JSONObject(Util.uncompress(new String(project_set_byte_array,"ISO-8859-1")));
							logger.info( "\n=============================\n project_set="+project_set.toString()+"\n=====================\n");
							expire_utc=project_set.getInt("expire_utc");
							min_fund=project_set.getLong("min_fund");
						} catch (Exception e) {	
							logger.error(e.toString());
							return;
						}	
						
						PreparedStatement ps = db.connection.prepareStatement("insert into crowdfunding_projects(tx_index, tx_hash, block_index, owner, project_set,expire_utc,min_fund, backers, nbc_funded,validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+source+"',?,'"+expire_utc+"','"+min_fund+"','0','0','"+validity+"');");
						ps.setString(1, project_set.toString());
						ps.execute();
					}
				}				
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}
	}

	public static List<CrowdfundingProjectInfo> getPending(String owner) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+owner+"' and destination='' and prefix_type=0 order by tx_index desc;");
		List<CrowdfundingProjectInfo> projects = new ArrayList<CrowdfundingProjectInfo>();
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

				ResultSet rsCheck = db.executeQuery("select * from crowdfunding_projects where tx_index='"+txIndex.toString()+"'");
				if (!rsCheck.next()) {
					List<Byte> messageType = blocks.getMessageTypeFromTransaction(dataString);
					List<Byte> message = blocks.getMessageFromTransaction(dataString);
					
					logger.info("messageType="+messageType.get(3)+"  message.size="+message.size());

					if (messageType.get(3)==CrowdfundingProject.id.byteValue() && message.size()>2) {
						ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
						for (byte b : message) {
							byteBuffer.put(b);
						}			
						
						int project_set_length = new Short(byteBuffer.getShort(0)).intValue();
						if( !owner.equals("") && message.size()==2+project_set_length )
						{
								byte[] project_set_byte_array=new byte[project_set_length];
								for(int off=0;off<project_set_length;off++)
									project_set_byte_array[off]=byteBuffer.get(0+2+off);
							
								JSONObject project_set;
								try{
									project_set=new JSONObject(Util.uncompress(new String(project_set_byte_array,"ISO-8859-1")));
								} catch (Exception e) {	
									logger.error(e.toString());
									return projects;
								}	
								CrowdfundingProjectInfo projectInfo = new CrowdfundingProjectInfo();
								projectInfo.owner = owner;
								projectInfo.projectSet = project_set;
								projectInfo.txIndex = txIndex;
								projectInfo.txHash = txHash;
								projectInfo.blockIndex = blockIndex;
								projectInfo.blockTime = blockTime;
								projectInfo.backers=0;
								projectInfo.nbcFunded=0L;
								projectInfo.validity="pending";
								projects.add(projectInfo);
						}
					}	
				}
			}
		} catch (SQLException e) {	
			logger.error(e.toString());
		}	
		return projects;
	}
	
	public static CrowdfundingProjectInfo getProjectInfo(String project_tx_index_or_hash) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select cp.owner ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.project_set, cp.backers, cp.nbc_funded,cp.validity from crowdfunding_projects cp,transactions where (cp.tx_index='"+project_tx_index_or_hash+"' or cp.tx_hash='"+project_tx_index_or_hash+"') and cp.tx_index=transactions.tx_index;");

		try {
			
			if(rs.next()) {
				CrowdfundingProjectInfo projectInfo = new CrowdfundingProjectInfo();
				projectInfo.owner = rs.getString("owner");
				projectInfo.txIndex = rs.getInt("tx_index");
				projectInfo.txHash = rs.getString("tx_hash");
				projectInfo.blockIndex = rs.getInt("block_index");
				projectInfo.blockTime = rs.getInt("block_time");
				projectInfo.validity = rs.getString("validity");
				projectInfo.backers = rs.getInt("backers");
				projectInfo.nbcFunded = rs.getLong("nbc_funded");
				
				projectInfo.backStat=getProjectBackStat(project_tx_index_or_hash);
				
				try{
					projectInfo.projectSet = new JSONObject(rs.getString("project_set"));
				}catch (Exception e) {
					logger.error(e.toString());
				}

				return projectInfo;
			}
		} catch (SQLException e) {
		}	

		return null;		
	}
	
	public static JSONObject getProjectBackStat(String project_tx_index_or_hash) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select bk.back_price_nbc,count(*) as backers,sum(back_price_nbc) as nbc_backed  from crowdfunding_projects cp,crowdfunding_backers bk where (cp.tx_index='"+project_tx_index_or_hash+"' or cp.tx_hash='"+project_tx_index_or_hash+"') and cp.tx_index=bk.project_tx_index and bk.validity='valid' group by back_price_nbc;");

		JSONObject  projectBackStat=new JSONObject();
		try {
			
			while(rs.next()) {
				JSONObject  itemStat=new JSONObject();
				itemStat.put("backers",rs.getInt("backers"));
				itemStat.put("nbc_backed",rs.getLong("nbc_backed"));
				
				projectBackStat.put(rs.getString("back_price_nbc"),itemStat);
			}
		} catch (Exception e) {

		}	
		return projectBackStat;		
			
	}
	
	public static JSONObject updateProjectStat(String project_tx_index_or_hash) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select count(*) as backers,sum(back_price_nbc) as nbc_funded  from crowdfunding_projects cp,crowdfunding_backers bk where (cp.tx_index='"+project_tx_index_or_hash+"' or cp.tx_hash='"+project_tx_index_or_hash+"') and (cp.validity='valid' or cp.validity='success' or cp.validity='failed') and cp.tx_index=bk.project_tx_index and bk.validity='valid';");

		JSONObject  projectStat=new JSONObject();
		try {
			if(rs.next()) {
				BigInteger nbcSupply = Util.nbcSupply();
				BigInteger nbc_funded = BigInteger.valueOf(rs.getLong("nbc_funded"));
				
				if(nbc_funded.compareTo(nbcSupply)<0){
					projectStat.put("backers",rs.getInt("backers"));
					projectStat.put("nbc_funded",rs.getLong("nbc_funded"));
					
					db.executeUpdate("update crowdfunding_projects set backers='"+rs.getInt("backers")+"',nbc_funded='"+rs.getLong("nbc_funded")+"' where tx_index='"+project_tx_index_or_hash+"' or tx_hash='"+project_tx_index_or_hash+"';");
				}else{
					logger.error("Exception: too big nbc_funded : "+nbc_funded.toString() +" for project:"+project_tx_index_or_hash);
					db.executeUpdate("update crowdfunding_projects set validity='exception(too big nbc_funded)' where tx_index='"+project_tx_index_or_hash+"' or tx_hash='"+project_tx_index_or_hash+"';");
				}
			}
		} catch (Exception e) {

		}	
		
		return projectStat;		
			
	}
	
	public static JSONObject getProjectMyBackedItems(String project_tx_index_or_hash,String address) {
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select bk.back_price_nbc,bk.tx_index from crowdfunding_projects cp,crowdfunding_backers bk where (cp.tx_index='"+project_tx_index_or_hash+"' or cp.tx_hash='"+project_tx_index_or_hash+"') and cp.tx_index=bk.project_tx_index and bk.backer='"+address+"' and bk.validity='valid' ;");

		JSONObject  myBackedItems=new JSONObject();
		try {
			while(rs.next()) {
				myBackedItems.put(rs.getString("back_price_nbc"),rs.getInt("tx_index"));
			}
		} catch (Exception e) {
		}	
		return myBackedItems;		
			
	}

	public static Transaction createProject(String owner,JSONObject project_set) throws Exception {
		if (owner.equals("")) throw new Exception("Please specify a owner address.");
		if (project_set==null) throw new Exception("Please specify valid project_set.");
		
		logger.info("project_set="+project_set.toString());
		
		//byte[] project_set_byte_array_original=project_set.toString().getBytes("ISO-8859-1");
		//Short project_set_length_original=Short.valueOf(new Integer(project_set_byte_array_original.length).toString());

		byte[] project_set_byte_array=Util.compress(project_set.toString()).getBytes("ISO-8859-1");
		Short project_set_length=Short.valueOf(new Integer(project_set_byte_array.length).toString());
		
		//logger.info("Compressed project_set's length="+project_set_length.toString()+". Original is "+project_set_length_original.toString());
		
		Blocks blocks = Blocks.getInstance();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4+2+project_set_length.intValue());
		byteBuffer.putInt(id);
		byteBuffer.putShort(project_set_length);
		byteBuffer.put(project_set_byte_array,0,project_set_length);
			
		List<Byte> dataArrayList = Util.toByteArrayList(byteBuffer.array());
		dataArrayList.addAll(0, Util.toByteArrayList(Config.newb_prefix.getBytes()));
		byte[] data = Util.toByteArray(dataArrayList);

		String dataString = "";
		try {
			dataString = new String(data,"ISO-8859-1");
			
		} catch (UnsupportedEncodingException e) {
		}

		Transaction tx = blocks.transaction(owner, "", BigInteger.ZERO, BigInteger.valueOf(Config.maxFee), dataString);

		//just for debug
		//logger.info("Test:createProject owner="+owner+", dataString.length="+dataString.length()+" dataString="+dataString);
		//blocks.importTransaction(tx, null, null);
		//System.exit(0);
		
		return tx;
	}
	
	//final
	public static void resolve(Integer lastBlock,Integer lastBlockTime) {
	
		String lastTxHash=Util.getBlockHash(lastBlock);
		if( lastTxHash==null ){
			logger.error("Invalid block hash");
			return;
		}
		
		Database db = Database.getInstance();
		
		ResultSet rs = db.executeQuery("select tx_index,owner,backers,nbc_funded,project_set from crowdfunding_projects where validity='valid' and expire_utc<'"+lastBlockTime+"' ;");

		try {
			while(rs.next()) {
				Integer project_tx_index=rs.getInt("tx_index");
				logger.info("Resolving expired crowdfunding_project : "+project_tx_index+"");
				
				//Integer backers = rs.getInt("backers");
				BigInteger nbc_funded = BigInteger.valueOf(rs.getLong("nbc_funded"));
				
				try{
					JSONObject project_set = new JSONObject(rs.getString("project_set"));
					BigInteger min_fund=BigInteger.valueOf(project_set.getLong("min_fund"));

					String final_result=null;
					if(nbc_funded.compareTo(min_fund)>=0){
						final_result="success";
						
						Util.credit(rs.getString("owner"), "NBC", nbc_funded, "crowdfunding fund", "", lastBlock);
						//db.executeUpdate("update crowdfunding_backers set valid='"+final_result+"' where project_tx_index='"+rs.getInt("tx_index")+"' and validity='valid';");
						
					} else {
						final_result="failed";
						
						ResultSet rs_back = db.executeQuery("select tx_index,backer,back_price_nbc from crowdfunding_backers where validity='valid' and project_tx_index='"+project_tx_index+"';");
						
						try {
							while(rs_back.next()) {
								Util.credit(rs_back.getString("backer"), "NBC", BigInteger.valueOf(rs_back.getLong("back_price_nbc")), "crowdfunding refund", "", lastBlock);
							}
						}catch (Exception e) {
							logger.error(e.toString());
						}
					}
					
					db.executeUpdate("update crowdfunding_projects set validity='"+final_result+"' where tx_index='"+project_tx_index+"';");
				}catch (Exception e) {
					logger.error(e.toString());
				}
			}
		} catch (Exception e) {
		}	
	}
	
	public static HashMap<String,Object> parseProjectSet(HashMap<String,Object> map,JSONObject project_set) throws Exception {
		map.put("title", HtmlRegexpUtil.filterHtml(project_set.getString("title")));
		if(project_set.has("logo_img_url")) {
			map.put("logo_img_url", HtmlRegexpUtil.filterHtml(project_set.getString("logo_img_url")));
			map.put("topic_img_url", HtmlRegexpUtil.filterHtml(project_set.getString("topic_img_url")));
			map.put("detail_img_url", HtmlRegexpUtil.filterHtml(project_set.getString("detail_img_url")));

			Integer expire_utc=project_set.getInt("expire_utc");
			map.put("expire_utc",expire_utc );

			String left_time_desc=Util.getLeftTimeDescStr(expire_utc);
			
			if(left_time_desc!=null)
				map.put("left_time_desc",left_time_desc );
				
			map.put("min_fund", BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()/Config.nbc_unit.doubleValue());  
		} else { //older version
			map.put("logo_img_url", "http://newbiecoin.org/images/logo.png");
			map.put("topic_img_url", "http://newbiecoin.org/images/topic_sample.jpg");
			map.put("detail_img_url", "http://newbiecoin.org/images/detail-sample.jpg");

			Integer expire_utc=1416714326;
			map.put("expire_utc",expire_utc );

			String left_time_desc=Util.getLeftTimeDescStr(expire_utc);
			
			if(left_time_desc!=null)
				map.put("left_time_desc",left_time_desc );
				
			map.put("min_fund", 1234567890);  
		}
		
		if(project_set.isNull("name"))
			map.put("name", "anonymous");
		else
			map.put("name", HtmlRegexpUtil.filterHtml(project_set.getString("name")));
			
		if(project_set.isNull("email"))
			map.put("email", "");
		else
			map.put("email", HtmlRegexpUtil.filterHtml(project_set.getString("email")));
			
		map.put("web", HtmlRegexpUtil.filterHtml(project_set.getString("web")));
		

		return map;
	}
}

class CrowdfundingProjectInfo {
	public Integer txIndex;
	public String owner;
	public String txHash;
    public Integer blockIndex;
    public Integer blockTime;
	public Integer backers;
	public Long nbcFunded;
	public String validity;
	
	public JSONObject projectSet;
	public JSONObject backStat;
}