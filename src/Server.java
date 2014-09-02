import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.setPort;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.freemarker.FreeMarkerRoute;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.common.collect.Lists;

import freemarker.template.Configuration;

public class Server implements Runnable {
	public Logger logger = LoggerFactory.getLogger(Server.class);

	public static void main(String[] args){
		Server server = new Server();
		server.run();
	}

	public void run() { 
		init(); 
	} 
	
	public Map<String, Object> updateCommonStatus(Request request, Map<String, Object> attributes) {
		Blocks blocks = Blocks.getInstance();
		attributes.put("supply", Util.nbcSupply().floatValue() / Config.nbc_unit.floatValue());
		attributes.put("blocksBTC", blocks.bitcoinBlock);
		attributes.put("blocksNBC", blocks.newbiecoinBlock);
		attributes.put("version", Config.version);
		attributes.put("min_version", Util.getMinVersion());
		attributes.put("min_version_major", Util.getMinMajorVersion());
		attributes.put("min_version_minor", Util.getMinMinorVersion());
		attributes.put("version_major", Config.majorVersion);
		attributes.put("version_minor", Config.minorVersion);
		Blocks.getInstance().versionCheck();
		if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
		
		String address = Util.getAddresses().get(0);
		if (request.session().attributes().contains("address")) {
			address = request.session().attribute("address");
		}
		if (request.queryParams().contains("address")) {
			address = request.queryParams("address");
			request.session().attribute("address", address);
		}
		ArrayList<HashMap<String, Object>> addresses = new ArrayList<HashMap<String, Object>>();
		for (String addr : Util.getAddresses()) {
			HashMap<String,Object> map = new HashMap<String,Object>();	
			map.put("address", addr);
			map.put("balance_NBC", Util.getBalance(addr, "NBC").floatValue() / Config.nbc_unit.floatValue());
			addresses.add(map);
		}
		attributes.put("address", address);				
		attributes.put("addresses", addresses);
		for (ECKey key : blocks.wallet.getKeys()) {
			if (key.toAddress(blocks.params).toString().equals(address)) {
				attributes.put("own", true);
			}
		}
        
        attributes.put("LANG_NEWBIECOIN", Language.getLangLabel("Newbiecoin"));
        attributes.put("LANG_CROWDFUNDING", Language.getLangLabel("Crowdfunding"));
        attributes.put("LANG_CASINO", Language.getLangLabel("Casino"));
        attributes.put("LANG_EXCHANGE", Language.getLangLabel("Exchange"));
        attributes.put("LANG_WALLET", Language.getLangLabel("Wallet"));
        attributes.put("LANG_TECHNICAL", Language.getLangLabel("Technical"));
        attributes.put("LANG_COMMUNITY", Language.getLangLabel("Community"));
        
        attributes.put("LANG_BLOCKS", Language.getLangLabel("blocks"));
        attributes.put("LANG_VERSION", Language.getLangLabel("Version"));
        
        attributes.put("LANG_VIEWING_OTHER_ADDRESS", Language.getLangLabel("Viewing other address"));
        attributes.put("LANG_VIEWING_OTHER_ADDRESS_NOTICE", Language.getLangLabel("Notice: Click your address listed on the right side to go back your wallet."));
        attributes.put("LANG_REPARSE_TRANSACTIONS", Language.getLangLabel("Reparse transactions"));
        attributes.put("LANG_VERSION_OUT_OF_DATE", Language.getLangLabel("You must update to the latest version of Newbiecoin. Your version is out of date."));
        attributes.put("LANG_PARSING_TRANSACTIONS", Language.getLangLabel("Newbiecoin is parsing transactions. You can still use the software, but the information you see will be out of date."));
        
        attributes.put("LANG_ERROR", Language.getLangLabel("Error"));
        
		return attributes;
	}
	
	public void init() {
		//start Blocks thread
		Blocks blocks = Blocks.getInstance();
		Thread blocksThread = new Thread(blocks);
		blocksThread.setDaemon(true);
		blocksThread.start(); 
		
		boolean inJar = false;
		try {
			CodeSource cs = this.getClass().getProtectionDomain().getCodeSource();
			inJar = cs.getLocation().toURI().getPath().endsWith(".jar");
		}
		catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		setPort(8087);    
		
		final Configuration configuration = new Configuration();
		try {
			if (inJar) {
				Spark.externalStaticFileLocation("resources/static/");
				configuration.setClassForTemplateLoading(this.getClass(), "resources/templates/");
			} else {
				Spark.externalStaticFileLocation("./resources/static/");
				configuration.setDirectoryForTemplateLoading(new File("./resources/templates/"));	
			}
		} catch (Exception e) {
		}

		get(new Route("/supply") {
			@Override
			public Object handle(Request request, Response response) {
				return String.format(Config.nbc_display_format, Util.nbcSupply().doubleValue() / Config.nbc_unit);
			}
		});
		get(new Route("/chat_status_update") {
			@Override
			public Object handle(Request request, Response response) {
				request.session(true);
				if (request.queryParams().contains("chat_open")) {
					request.session().attribute("chat_open", request.queryParams("chat_open"));	
				}
				return request.session().attribute("chat_open");
			}
		});
		get(new FreeMarkerRoute("/") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				
				Blocks blocks = Blocks.getInstance();
				
				if (request.queryParams().contains("reparse")) {
					blocks.reparse();
				}
				
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "A coin focusing on decentralized applications");
                
                if(Language.getCurrentLang().equals("CN"))
                    attributes.put("news_url", Config.newsUrlCN);
                else
                    attributes.put("news_url", Config.newsUrl);
				
				//Only for test
				if(Config.testNet && Config.appName.equals("NewbiecoinBeta") &&  Config.prefix.equals("NEWBIECO") ){
					logger.info(" === giveaway for test === ");
					//Init a giveaway 10000000NEWB only for beta test
					for (String addr : Util.getAddresses()) {
						BigInteger existingAmount=Util.getBalance(addr, "NBC");
						if (existingAmount.compareTo(BigInteger.ZERO)==0) {
							logger.info(" === existingAmount:"+existingAmount.toString()+" === ");
							Util.credit(addr, "NBC", BigInteger.valueOf(100000000000L), "test giveaway", "Test Giveaway", 1);
						}
					}
				}

				Database db = Database.getInstance();
				ResultSet rs = db.executeQuery("select address,amount as balance,amount*100.0/(select sum(amount) from balances) as share from balances where asset='NBC' group by address order by amount desc limit 10;");
				ArrayList<HashMap<String, Object>> balances = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("address", rs.getString("address"));
						map.put("balance", BigInteger.valueOf(rs.getLong("balance")).doubleValue()/Config.btc_unit.doubleValue());
						map.put("share", rs.getDouble("share"));
						balances.add(map);
					}
				} catch (SQLException e) {
				}
				attributes.put("balances", balances);
				
				//get newest project
				rs = db.executeQuery("select cp.owner ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.project_set, cp.backers, cp.nbc_funded,cp.validity from crowdfunding_projects cp,transactions where cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc limit 1;");
				try {
					if( rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("owner", rs.getString("owner"));
						map.put("tx_index", rs.getString("tx_index"));
						map.put("tx_hash", rs.getString("tx_hash"));
						map.put("validity", rs.getString("validity"));
						map.put("block_index", rs.getString("block_index"));
						map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
						map.put("backers", rs.getInt("backers"));
						map.put("nbc_funded", BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/Config.nbc_unit.doubleValue());
						
						try{
							JSONObject project_set = new JSONObject(rs.getString("project_set")); 
							map=CrowdfundingProject.parseProjectSet(map,project_set);
							
							map.put("percent", new BigDecimal(100*BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()).toBigInteger( )); 
							
							attributes.put("recommand_project", map);
						}catch (Exception e) {
							logger.error(e.toString());
						}
					}
				} catch (SQLException e) {
				}

				/*
				rs = db.executeQuery("select bets.source as source,bet,bet_bs,profit,bets.tx_hash as tx_hash,rolla,rollb,roll,resolved,bets.tx_index as tx_index,bets.block_index,transactions.block_time from bets,transactions where bets.validity='valid' and bets.tx_index=transactions.tx_index order by bets.block_index desc, bets.tx_index desc limit 10;");
				ArrayList<HashMap<String, Object>> bets = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("source", rs.getString("source"));
						map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
						map.put("bet_bs", rs.getShort("bet_bs"));
						map.put("tx_hash", rs.getString("tx_hash"));
						map.put("roll", rs.getDouble("roll"));
						map.put("resolved", rs.getString("resolved"));
                        map.put("block_index", rs.getString("block_index"));
						map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
						map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
						bets.add(map);
					}
				} catch (SQLException e) {
				}
				attributes.put("bets", bets);
				*/
                
                attributes.put("LANG_A_UNIQUE_COIN", Language.getLangLabel("a unique coin that focus on decentralized applications such as decentralized crowdfunding,decentralized casino."));
                attributes.put("LANG_MADE_FOR_A_LITTLE_JOY", Language.getLangLabel("Newbiecoin is a coin and a decentralized platform. It's made for a little joy."));
                attributes.put("LANG_DOWNLOAD", Language.getLangLabel("Download"));
                attributes.put("LANG_SOFTWARE_INCLUDING", Language.getLangLabel("the Newbiecoin software -- including wallet , decentralized crowdfunding, decentralized casino, and decentralized exchange . Start playing today!"));
                attributes.put("LANG_CREATED_BY_BURNING", Language.getLangLabel("Coins were created by burning Bitcoins during a creative Twin-POB period. Now there are total"));
                attributes.put("LANG_BUILT_ON_BITCOIN_BLOCKCHAIN", Language.getLangLabel("Built on top of the Bitcoin blockchain, Newbiecoin is a platform that is truly decentralized. There is no central point of failure. The owner can't run away with the bankroll. It's owned by the people."));
                attributes.put("LANG_LEARN_MORE", Language.getLangLabel("Learn more"));
                attributes.put("LANG_NEWS", Language.getLangLabel("News"));

                attributes.put("LANG_CREATE_NEW_PROJECT", Language.getLangLabel("Create a new project"));
                attributes.put("LANG_LEFT", Language.getLangLabel("Left"));
                attributes.put("LANG_VIEW", Language.getLangLabel("View"));
                attributes.put("LANG_PROJECT_EXPIRED", Language.getLangLabel("project expired and pending resolved."));
                attributes.put("LANG_SUCCESSFULLY_FUNDED", Language.getLangLabel("Successfully funded!"));
                attributes.put("LANG_FAILED", Language.getLangLabel("Failed!"));
                attributes.put("LANG_CANCELED", Language.getLangLabel("Canceled"));
                attributes.put("LANG_TOTAL", Language.getLangLabel("Total"));
                attributes.put("LANG_OF", Language.getLangLabel("of"));                
                attributes.put("LANG_BACKERS", Language.getLangLabel("Backers"));
                attributes.put("LANG_PROJECT_BY", Language.getLangLabel("Project by"));
                attributes.put("LANG_CREATED_TIME", Language.getLangLabel("Created time"));
                
                attributes.put("LANG_ROLL_DICE", Language.getLangLabel("Roll dice"));
                attributes.put("LANG_BIGGER", Language.getLangLabel("Bigger"));
                attributes.put("LANG_SMALLER", Language.getLangLabel("Smaller"));
                attributes.put("LANG_RECENT_BETS", Language.getLangLabel("Recent bets"));
                attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
                attributes.put("LANG_TIME", Language.getLangLabel("Time"));
                attributes.put("LANG_SOURCE_ADDRESS", Language.getLangLabel("Source address"));
                attributes.put("LANG_BET_SIZE", Language.getLangLabel("Bet size"));
                attributes.put("LANG_BIGGER_OR_SMALLER", Language.getLangLabel("Bigger or Smaller"));
                attributes.put("LANG_ROLL", Language.getLangLabel("Roll"));
                attributes.put("LANG_PROFIT", Language.getLangLabel("Profit"));
                attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
                attributes.put("LANG_UNRESOLVED", Language.getLangLabel("Unresolved"));

				return modelAndView(attributes, "index.html");
			}
		});
		get(new FreeMarkerRoute("/participate") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Participate");
				return modelAndView(attributes, "participate.html");
			}
		});
		get(new FreeMarkerRoute("/community") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);

				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Community");
                
                attributes.put("LANG_BITCOINTALK", Language.getLangLabel("Bitcointalk"));  
                attributes.put("LANG_CONTACT", Language.getLangLabel("Contact"));  
                attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
                attributes.put("LANG_WEBSITE", Language.getLangLabel("Website"));  
                attributes.put("LANG_RESOURCE", Language.getLangLabel("Resource"));  
                attributes.put("LANG_MEMBERS", Language.getLangLabel("Members"));  
                attributes.put("LANG_CHINA", Language.getLangLabel("China"));  
                attributes.put("LANG_CANADA", Language.getLangLabel("Canada"));  
                attributes.put("LANG_DONATIONS", Language.getLangLabel("Donations"));  
                attributes.put("LANG_DONATIONS_ARE_WELCOME", Language.getLangLabel("Donations are welcome."));  
                
				return modelAndView(attributes, "community.html");
			}
		});
		get(new FreeMarkerRoute("/technical") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				
				Blocks blocks = Blocks.getInstance();

                Integer btcBlockHeight=blocks.bitcoinBlock;
                
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Technical");
				attributes.put("house_edge", Config.houseEdge);
				attributes.put("house_address", Config.houseAddressFund);
				attributes.put("burn_address_fund", Config.burnAddressFund);
                attributes.put("burn_address_dark", Config.burnAddressDark);
				attributes.put("max_burn", Config.maxBurn);
				attributes.put("pob_trial_start_block", Config.pobTrialStartBlock);
				attributes.put("pob_trial_end_block", Config.pobTrialEndBlock);
                attributes.put("pob_trial_multiplier", Config.pobTrialMultiplier * (Config.btc_unit / Config.nbc_unit));
                attributes.put("pob_max_start_block", Config.pobMaxStartBlock);
				attributes.put("pob_max_end_block", Config.pobMaxEndBlock);
                attributes.put("pob_max_multiplier", Config.pobMaxMultiplier * (Config.btc_unit / Config.nbc_unit));
                attributes.put("pob_down_start_block", Config.pobDownStartBlock);
				attributes.put("pob_down_end_block", Config.pobDownEndBlock);
				attributes.put("pob_down_init_multiplier", Config.pobDownInitMultiplier * (Config.btc_unit / Config.nbc_unit) );
				attributes.put("pob_down_end_multiplier", Config.pobDownEndMultiplier * (Config.btc_unit / Config.nbc_unit) );
				attributes.put("burned_BTC", Util.btcBurned(null).doubleValue()/Config.btc_unit.doubleValue());
				attributes.put("burned_NBC", Util.nbcBurned(null).doubleValue()/Config.nbc_unit.doubleValue());
                attributes.put("burned_BTC_fund", Util.btcBurned(Config.burnAddressFund).doubleValue()/Config.btc_unit.doubleValue());
				attributes.put("burned_NBC_fund", Util.nbcBurned(Config.burnAddressFund).doubleValue()/Config.nbc_unit.doubleValue());
                attributes.put("burned_BTC_dark", Util.btcBurned(Config.burnAddressDark).doubleValue()/Config.btc_unit.doubleValue());
				attributes.put("burned_NBC_dark", Util.nbcBurned(Config.burnAddressDark).doubleValue()/Config.nbc_unit.doubleValue());
                
                if( btcBlockHeight>=Config.pobTrialStartBlock && btcBlockHeight<=Config.pobDownEndBlock) 
                    attributes.put("burn_status",Language.getLangLabel("ACTIVE") );
                else if( btcBlockHeight < Config.pobTrialStartBlock )
                    attributes.put("burn_status",Language.getLangLabel("WAITING") );
                else
                    attributes.put("burn_status",Language.getLangLabel("COMPLETED") );
                
                attributes.put("pos_first_block", Config.posFirstBlock);
				attributes.put("pos_end_block", Config.posEndBlock);
                attributes.put("pos_wait_blocks", Config.posWaitBlocks);
                attributes.put("pos_interest", Config.posInterest*100);
                
                if( btcBlockHeight>=Config.posFirstBlock && btcBlockHeight<=Config.posEndBlock) 
                    attributes.put("pos_status",Language.getLangLabel("ACTIVE") );
                else if( btcBlockHeight < Config.posFirstBlock )
                    attributes.put("pos_status",Language.getLangLabel("WAITING") );
                else
                    attributes.put("pos_status",Language.getLangLabel("COMPLETED") );
                
				return modelAndView(attributes, "technical"+Language.getCurrentLang()+".html");
			}
		});
		get(new FreeMarkerRoute("/balances") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Balances");
				Database db = Database.getInstance();
				ResultSet rs = db.executeQuery("select address,amount as balance,amount*100.0/(select sum(amount) from balances) as share from balances where asset='NBC' group by address order by amount desc;");
				ArrayList<HashMap<String, Object>> balances = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("address", rs.getString("address"));
						map.put("balance", BigInteger.valueOf(rs.getLong("balance")).doubleValue()/Config.btc_unit.doubleValue());
						map.put("share", rs.getDouble("share"));
						balances.add(map);
					}
				} catch (SQLException e) {
				}
				attributes.put("balances", balances);
				return modelAndView(attributes, "balances.html");
			}
		});		
		post(new FreeMarkerRoute("/exchange") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleExchangeRequest(request);			
				return modelAndView(attributes, "exchange.html");
			}
		});	
		get(new FreeMarkerRoute("/exchange") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleExchangeRequest(request);			
				return modelAndView(attributes, "exchange.html");
			}
		});	
		get(new FreeMarkerRoute("/unspents") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				
				Blocks blocks = Blocks.getInstance();
				blocks.deletePending();
				
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Unspents");
				
				String address=(String)attributes.get("address");	
				Double unspentTotal = 0.0;
				List<UnspentOutput> unspentOutputs = Util.getUnspents(address);
				ArrayList<HashMap<String, Object>> unspents = new ArrayList<HashMap<String, Object>>();
				for (UnspentOutput unspent : unspentOutputs) {
					HashMap<String,Object> map = new HashMap<String,Object>();	
					map.put("amount", unspent.amount);
					map.put("tx_hash", unspent.txid);
					map.put("vout", unspent.vout);
					map.put("type", unspent.type);
					map.put("confirmations", unspent.confirmations);
					unspentTotal += unspent.amount;
					unspents.add(map);
				}
				attributes.put("unspents", unspents);
				attributes.put("unspent_address", Util.unspentAddress(address));
				attributes.put("unspent_total", unspentTotal);

				return modelAndView(attributes, "unspents.html");
			}
		});			
		post(new FreeMarkerRoute("/wallet") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleWalletRequest(request);
				return modelAndView(attributes, "wallet.html");
			}
		});	
		get(new FreeMarkerRoute("/wallet") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleWalletRequest(request);
				return modelAndView(attributes, "wallet.html");
			}
		});	
		post(new FreeMarkerRoute("/casino") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCasinoRequest(request);
				return modelAndView(attributes, "casino.html");
			}
		});
		get(new FreeMarkerRoute("/casino") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCasinoRequest(request);
				return modelAndView(attributes, "casino.html");
			}
		});
		/*
		post(new FreeMarkerRoute("/worldcup") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleWorldCupRequest(request);
				return modelAndView(attributes, "worldcup.html");
			}
		});
		get(new FreeMarkerRoute("/worldcup") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleWorldCupRequest(request);
				return modelAndView(attributes, "worldcup.html");
			}
		});*/
		post(new FreeMarkerRoute("/crowdfunding") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCrowdfundingRequest(request);
				return modelAndView(attributes, "crowdfunding.html");
			}
		});
		get(new FreeMarkerRoute("/crowdfunding") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCrowdfundingRequest(request);
				return modelAndView(attributes, "crowdfunding.html");
			}
		});
		get(new FreeMarkerRoute("/crowdfunding-add") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateCommonStatus(request, attributes);
				attributes.put("title", "Create a new crowdfunding project");
				
				//check RSA keys
				try{
					String address=(String)attributes.get("address");
					JSONObject keyMap=Util.getRSAKeys(address,true);
					
					String publicKey = RSACoder.getPublicKey(keyMap);  

					attributes.put("rsa_public_key", publicKey);
                    
                    attributes.put("LANG_TITLE", Language.getLangLabel("Title"));
                    attributes.put("LANG_PROJECT_TITLE", Language.getLangLabel("project title"));
                    attributes.put("LANG_LOGO_IMAGE_URL", Language.getLangLabel("Logo image URL"));
                    attributes.put("LANG_THE_LOGO_IMAGE_SHOULD_BE", Language.getLangLabel("the logo image URL (The image should be jpg,png or gif . The suggested width*height is 100*100 pixels.)"));
                    attributes.put("LANG_TOPIC_IMAGE_URL", Language.getLangLabel("Topic image URL"));
                    attributes.put("LANG_THE_TOPIC_IMAGE_SHOULD_BE", Language.getLangLabel("The topic image URL (The image should be jpg,png or gif . The suggested width*height is 640*360 pixels.)"));
                    attributes.put("LANG_DETAIL_IMAGE_URL", Language.getLangLabel("Detail image URL"));
                    attributes.put("LANG_THE_DETAIL_IMAGE_SHOULD_BE", Language.getLangLabel("the introduction image URL (The image should be jpg,png or gif . The suggested width is 640 pixels. The height is less than 4000 pixels)"));
                    attributes.put("LANG_GOAL_CROWDFUNDING_AMOUNT", Language.getLangLabel("Goal crowdfunding amount"));
                    attributes.put("LANG_THE_GOAL_NEWBIECOIN_AMOUNT_FOR", Language.getLangLabel("the goal newbiecoin amount for successful crowdfunding"));
                    attributes.put("LANG_EXPIRE_DATE", Language.getLangLabel("Expire date"));
                    attributes.put("LANG_YYYY_MM_DD", Language.getLangLabel("YYYY-MM-DD"));
                    attributes.put("LANG_YOUR_NAME", Language.getLangLabel("Your name"));
                    attributes.put("LANG_THE_CREATOR_NAME_OF_YOU", Language.getLangLabel("the creator's name of you or your team"));
                    attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
                    attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("the public email for backers contacting you"));
                    attributes.put("LANG_WEBSITE", Language.getLangLabel("Website"));  
                    attributes.put("LANG_PROJECT_WEBSITE", Language.getLangLabel("project website"));
                    attributes.put("LANG_CROWDFUNDING_ITEM", Language.getLangLabel("Crowdfunding item"));
                    attributes.put("LANG_NEWB_AMOUNT", Language.getLangLabel("NEWB amount"));
                    attributes.put("LANG_MAX_BACKERS_NUMBER", Language.getLangLabel("max backers number"));
                    attributes.put("LANG_A_SHORT_DESCRIPTION_FOR", Language.getLangLabel("a short description for this item"));
                    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
                    attributes.put("LANG_CREATE_IT", Language.getLangLabel("Create it"));  
                    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
                    
					return modelAndView(attributes, "crowdfunding-add.html");
				}catch(Exception e){
					return null;
				}
			}
		});
		post(new FreeMarkerRoute("/crowdfunding-detail") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCrowdfundingDetailRequest(request);
				return modelAndView(attributes, "crowdfunding-detail.html");
			}
		});	
		get(new FreeMarkerRoute("/crowdfunding-detail") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = handleCrowdfundingDetailRequest(request);
				return modelAndView(attributes, "crowdfunding-detail.html");
			}
		});	
		get(new FreeMarkerRoute("/error") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				attributes.put("title", "Error");
				return modelAndView(attributes, "error.html");
			}
		});
	}
	
	public Map<String, Object> handleExchangeRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "Exchange");
		
		Blocks blocks = Blocks.getInstance();
		String address=(String)attributes.get("address");
		if (request.queryParams().contains("form") && request.queryParams("form").equals("cancel")) {
			String txHash = request.queryParams("tx_hash");
			try {
				Transaction tx = Cancel.create(txHash);
				blocks.sendTransaction(address,tx);
				attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
			} catch (Exception e) {
				attributes.put("error", Language.getLangLabel(e.getMessage()));
			}
		}
		else if (request.queryParams().contains("form") && request.queryParams("form").equals("btcpay")) {
			String orderMatchId = request.queryParams("order_match_id");
			try {
				Transaction tx = BTCPay.create(orderMatchId);
				blocks.sendTransaction(address,tx);
				attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
				
				Database db = Database.getInstance();
				db.executeUpdate("update order_matches set validity='btcpayed' where id='"+orderMatchId+"';");
			} catch (Exception e) {
				attributes.put("error", Language.getLangLabel(e.getMessage()));
			}
		}
		else if (request.queryParams().contains("form") && request.queryParams("form").equals("buy")) {
			String source = request.queryParams("source");
			Double price_btc = Double.parseDouble(request.queryParams("price_btc"));
			Double rawQuantity = Double.parseDouble(request.queryParams("quantity"));
			BigInteger quantity = new BigDecimal(rawQuantity*Config.nbc_unit).toBigInteger();
			BigInteger btcQuantity = new BigDecimal(rawQuantity.doubleValue() *Config.btc_unit * price_btc).toBigInteger();
			BigInteger expiration = BigInteger.valueOf(Long.parseLong(request.queryParams("expiration")));
			try {
				Transaction tx = Order.create(source, "BTC", btcQuantity, "NBC", quantity, expiration, BigInteger.ZERO, BigInteger.ZERO);
				
				blocks.sendTransaction(source,tx);				
				
				attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
			} catch (Exception e) {
				attributes.put("error", Language.getLangLabel(e.getMessage()));
			}					
		}
		else if (request.queryParams().contains("form") && request.queryParams("form").equals("sell")) {
			String source = request.queryParams("source");
			Double price_btc = Double.parseDouble(request.queryParams("price_btc"));
			Double rawQuantity = Double.parseDouble(request.queryParams("quantity"));
			BigInteger quantity = new BigDecimal(rawQuantity*Config.nbc_unit).toBigInteger();
			BigInteger btcQuantity = new BigDecimal(rawQuantity.doubleValue() *Config.btc_unit * price_btc).toBigInteger();
			BigInteger expiration = BigInteger.valueOf(Long.parseLong(request.queryParams("expiration")));
			try {
				Transaction tx = Order.create(source, "NBC", quantity, "BTC", btcQuantity, expiration, BigInteger.ZERO, BigInteger.ZERO);
				blocks.sendTransaction(source,tx);
				attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
			} catch (Exception e) {
				attributes.put("error", Language.getLangLabel(e.getMessage()));
			}
		}

		attributes.put("balanceNBC", Util.getBalance(address, "NBC").doubleValue() / Config.nbc_unit.doubleValue());
		attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.btc_unit.doubleValue());
		
		Database db = Database.getInstance();
		
		//get buy orders
		ResultSet rs = db.executeQuery("select (1.0*give_amount/get_amount)/("+Config.btc_unit+"/"+Config.nbc_unit+") as price_btc, get_remaining as quantity,tx_hash from orders where get_asset='NBC' and give_asset='BTC' and validity='valid' and give_remaining>0 and get_remaining>0 order by price_btc desc, quantity desc;");
		ArrayList<HashMap<String, Object>> ordersBuy = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("quantity", BigInteger.valueOf(rs.getLong("quantity")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("price_btc", rs.getDouble("price_btc"));
				map.put("tx_hash", rs.getString("tx_hash"));
				ordersBuy.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("orders_buy", ordersBuy);				
		
		//get sell orders
		rs = db.executeQuery("select (1.0*get_amount/give_amount)/("+Config.btc_unit+"/"+Config.nbc_unit+") as price_btc, give_remaining as quantity,tx_hash from orders where give_asset='NBC' and get_asset='BTC' and validity='valid' and give_remaining>0 and get_remaining>0 order by price_btc desc, quantity asc;");
		ArrayList<HashMap<String, Object>> ordersSell = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("quantity", BigInteger.valueOf(rs.getLong("quantity")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("price_btc", rs.getDouble("price_btc"));
				map.put("tx_hash", rs.getString("tx_hash"));
				ordersSell.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("orders_sell", ordersSell);	

		//get my order matches
		rs = db.executeQuery("select * from order_matches where ((tx0_address='"+address+"' and forward_asset='BTC') or (tx1_address='"+address+"' and backward_asset='BTC')) and (validity='pending' or validity='btcpayed')  order by tx0_block_index desc, tx0_index desc;");
		ArrayList<HashMap<String, Object>> myOrderMatches = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				if (rs.getString("forward_asset").equals("BTC")) {
					map.put("btc_owed", BigInteger.valueOf(rs.getLong("forward_amount")).doubleValue()/Config.btc_unit.doubleValue());
					map.put("nbc_return", BigInteger.valueOf(rs.getLong("backward_amount")).doubleValue()/Config.nbc_unit.doubleValue());
				} else {
					map.put("nbc_return", BigInteger.valueOf(rs.getLong("forward_amount")).doubleValue()/Config.nbc_unit.doubleValue());
					map.put("btc_owed", BigInteger.valueOf(rs.getLong("backward_amount")).doubleValue()/Config.btc_unit.doubleValue());
				}
				map.put("order_match_id", rs.getString("id"));
				map.put("validity", rs.getString("validity"));
				myOrderMatches.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_order_matches", myOrderMatches);	

		//get my orders
		//rs = db.executeQuery("select * from orders where source='"+address+"' order by block_index desc, tx_index desc;");
		rs = db.executeQuery("select distinct orders.*,pending_order.validity as has_pending_match from orders left join (select tx0_index,tx1_index,validity,forward_asset, forward_amount, backward_asset, backward_amount from order_matches where (tx0_address='"+address+"' or tx1_address='"+address+"') and (validity='pending'or validity='btcpayed') order by validity limit 1) as pending_order on  pending_order.tx0_index=orders.tx_index or pending_order.tx1_index=orders.tx_index   where source='"+address+"' order by block_index desc,tx_index desc;");
		ArrayList<HashMap<String, Object>> myOrders = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				if (rs.getString("get_asset").equals("NBC")) {
					map.put("buysell", "Buy");
					map.put("price_btc", (rs.getDouble("give_amount")/rs.getDouble("get_amount")) /(Config.btc_unit/Config.nbc_unit) );
					map.put("quantity_nbc", BigInteger.valueOf(rs.getLong("get_amount")).doubleValue()/Config.nbc_unit.doubleValue());
					map.put("quantity_btc", BigInteger.valueOf(rs.getLong("give_amount")).doubleValue()/Config.btc_unit.doubleValue());
					map.put("quantity_remaining_nbc", BigInteger.valueOf(rs.getLong("get_remaining")).doubleValue()/Config.nbc_unit.doubleValue());
					map.put("quantity_remaining_btc", BigInteger.valueOf(rs.getLong("give_remaining")).doubleValue()/Config.btc_unit.doubleValue());
				} else {
					map.put("buysell", "Sell");
					map.put("price_btc", (rs.getDouble("get_amount")/rs.getDouble("give_amount")) / (Config.btc_unit/Config.nbc_unit) );
					map.put("quantity_nbc", BigInteger.valueOf(rs.getLong("give_amount")).doubleValue()/Config.nbc_unit.doubleValue());
					map.put("quantity_btc", BigInteger.valueOf(rs.getLong("get_amount")).doubleValue()/Config.btc_unit.doubleValue());
					map.put("quantity_remaining_nbc", BigInteger.valueOf(rs.getLong("give_remaining")).doubleValue()/Config.nbc_unit.doubleValue());
					map.put("quantity_remaining_btc", BigInteger.valueOf(rs.getLong("get_remaining")).doubleValue()/Config.btc_unit.doubleValue());
				}
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("validity", rs.getString("validity"));
				map.put("has_pending_match", rs.getString("has_pending_match"));
				
				myOrders.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_orders", myOrders);				

		//get my pending confirm orders
		List<OrderInfo> ordersPending = Order.getPending(address);
		logger.info( "\n=============================\n ordersPending.size="+ordersPending.size()+"\n=====================\n");
		
		ArrayList<HashMap<String, Object>> myPendingOrders = new ArrayList<HashMap<String, Object>>();
		for (OrderInfo orderInfo : ordersPending) {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("source", orderInfo.source);
			map.put("tx_index",orderInfo.txIndex.toString());
			map.put("tx_hash", orderInfo.txHash);
			map.put("block_index", orderInfo.blockIndex.toString());
			map.put("block_time", Util.timeFormat(orderInfo.blockTime));
			map.put("validity",orderInfo.validity);
			
			if (orderInfo.getAsset.equals("NBC")) {
				map.put("buysell", "Buy");
				map.put("price_btc", (orderInfo.giveAmount.doubleValue()/orderInfo.getAmount.doubleValue()) /(Config.btc_unit/Config.nbc_unit) );
				map.put("quantity_nbc", orderInfo.getAmount.doubleValue()/Config.nbc_unit.doubleValue());
				map.put("quantity_btc", orderInfo.giveAmount.doubleValue()/Config.btc_unit.doubleValue());
					
			} else {
				map.put("buysell", "Sell");
				map.put("price_btc", (orderInfo.getAmount.doubleValue()/orderInfo.giveAmount.doubleValue()) / (Config.btc_unit/Config.nbc_unit) );
				map.put("quantity_nbc", orderInfo.giveAmount.doubleValue()/Config.nbc_unit.doubleValue());
				map.put("quantity_btc", orderInfo.getAmount.doubleValue()/Config.btc_unit.doubleValue());
			}
			
			map.put("quantity_remaining_nbc", map.get("quantity_nbc"));
			map.put("quantity_remaining_btc", map.get("quantity_btc"));
			
			myPendingOrders.add(map);
		}
		attributes.put("my_pending_orders", myPendingOrders);
        
        attributes.put("LANG_BUY", Language.getLangLabel("Buy"));  
        attributes.put("LANG_QUANTITY_THAT_YOU_WANT_TO_BUY", Language.getLangLabel("quantity that you want to buy"));  
        attributes.put("LANG_PRICE", Language.getLangLabel("Price"));  
        attributes.put("LANG_GOOD_FOR_ONE_HOUR", Language.getLangLabel("Good for one hour (6 blocks)"));  
        attributes.put("LANG_GOOD_FOR_ONE_DAY", Language.getLangLabel("Good for one day (144 blocks)"));  
        attributes.put("LANG_GOOD_FOR_ONE_WEEK", Language.getLangLabel("Good for one week (1008 blocks)"));  
        attributes.put("LANG_GOOD_FOR_ONE_MONTH", Language.getLangLabel("Good for one month (4320 blocks)"));  
        attributes.put("LANG_ORDER_BOOK", Language.getLangLabel("Order book"));  
        attributes.put("LANG_SELL", Language.getLangLabel("Sell"));  
        attributes.put("LANG_QUANTITY_THAT_YOU_WANT_TO_SELL", Language.getLangLabel("quantity that you want to sell"));  
        attributes.put("LANG_MY_ORDERS", Language.getLangLabel("My orders"));  
        attributes.put("LANG_BUY_OR_SELL", Language.getLangLabel("Buy/sell"));  
        attributes.put("LANG_STATUS", Language.getLangLabel("Status"));  
        attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));  
        attributes.put("LANG_ORDER_FILLED", Language.getLangLabel("order filled"));  
        attributes.put("LANG_INVALID_EXPIRED", Language.getLangLabel("invalid:expired"));  
        attributes.put("LANG_CANCEL", Language.getLangLabel("Cancel"));  
        attributes.put("LANG_BTC_PAYED_PENDING_CONFIRMED", Language.getLangLabel("BTC payed: pending confirmed"));  
        attributes.put("LANG_PENDING_PAY_BTC", Language.getLangLabel("Pending pay BTC"));  
        attributes.put("LANG_PENDING_BTC_PAYED", Language.getLangLabel("Pending BTC payed"));  
        attributes.put("LANG_MY_MATCHED_ORDERS", Language.getLangLabel("My matched orders"));  
        attributes.put("LANG_BTC_OWED", Language.getLangLabel("BTC owed"));  
        attributes.put("LANG_NEWB_IN_RETURN", Language.getLangLabel("NEWB in return"));  
        attributes.put("LANG_PAY_BTC", Language.getLangLabel("Pay BTC"));  
        attributes.put("LANG_SUBMIT", Language.getLangLabel("Submit"));  
        attributes.put("LANG_NO", Language.getLangLabel("No"));  
        attributes.put("LANG_YES", Language.getLangLabel("Yes"));  
        attributes.put("LANG_INVALID_ORDER", Language.getLangLabel("Invalid order"));  
        attributes.put("LANG_THE_ORDER_AMOUNT_SHOULD_BE", Language.getLangLabel("The order amount should be greater than 0.001 BTC!"));  
        attributes.put("LANG_NOW_THE_ORDER_AMOUNT_IS_ONLY", Language.getLangLabel("Now the order amount is only"));  
        attributes.put("LANG_ARE_YOU_SURE_TO", Language.getLangLabel("Are you sure to"));  
        attributes.put("LANG_SATOSHI", Language.getLangLabel("Satoshi"));  
        attributes.put("LANG_YOU_WOULD_PAY", Language.getLangLabel("You would pay"));  
        attributes.put("LANG_WHILE_YOUR_ORDER_BE_MATCHED_IN", Language.getLangLabel("while your order be matched in"));  
        attributes.put("LANG_BLOCKS", Language.getLangLabel("blocks"));  
        attributes.put("LANG_YOU_WOULD_GET", Language.getLangLabel("You would get"));  
        attributes.put("LANG_INVALID_PAYMENT", Language.getLangLabel("Invalid payment"));  
        attributes.put("LANG_THE_BTC_PAYMENT_SHOULD_BE", Language.getLangLabel("The btc payment should be greater than 0.001 BTC for each time!<br>Now your payment is only"));  
        attributes.put("LANG_ARE_YOU_SURE_TO_PAY", Language.getLangLabel("Are you sure to pay"));  
        attributes.put("LANG_INCLUDE", Language.getLangLabel("include"));  
        attributes.put("LANG_FEE_TO_BTC_NETWORK", Language.getLangLabel("fee to BTC network"));  
        attributes.put("LANG_IN_RETURN", Language.getLangLabel("in return."));  
        attributes.put("LANG_ARE_YOU_SURE_TO_CANCEL_THIS_ORDER", Language.getLangLabel("Are you sure to cancel this order ?"));  
        attributes.put("LANG_ORDER_TX_HASH", Language.getLangLabel("Order TX Hash"));  
        attributes.put("LANG_BUY_LOWER", Language.getLangLabel("buy"));  
        attributes.put("LANG_SELL_LOWER", Language.getLangLabel("sell"));  
        attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
		return attributes;		
	}
	
	public Map<String, Object> handleWalletRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "Wallet");
		
		Blocks blocks = Blocks.getInstance();
		String address=(String)attributes.get("address");
		if (request.queryParams().contains("form") && request.queryParams("form").equals("delete")) {
			ECKey deleteKey = null;
			String deleteAddress = request.queryParams("address");
			for (ECKey key : blocks.wallet.getKeys()) {
				if (key.toAddress(blocks.params).toString().equals(deleteAddress)) {
					deleteKey = key;
				}
			}
			if (deleteKey != null) {
				logger.info("Deleting private key");
				blocks.wallet.removeKey(deleteKey);
				attributes.put("success", Language.getLangLabel("Your private key has been deleted. You can no longer transact from this address."));							
				if (blocks.wallet.getKeys().size()<=0) {
					ECKey newKey = new ECKey();
					blocks.wallet.addKey(newKey);
				}
			}
		}
		if (request.queryParams().contains("form") && request.queryParams("form").equals("reimport")) {
			ECKey importKey = null;
			String deleteAddress = request.queryParams("address");
			for (ECKey key : blocks.wallet.getKeys()) {
				if (key.toAddress(blocks.params).toString().equals(deleteAddress)) {
					importKey = key;
				}
			}
			if (importKey != null) {
				logger.info("Reimporting private key transactions");
				try {
					blocks.importPrivateKey(importKey);
					attributes.put("success", Language.getLangLabel("Your transactions have been reimported."));
				} catch (Exception e) {
					attributes.put("error", Language.getLangLabel("Error when reimporting transactions: ")+e.getMessage());
				}
			}
		}
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("send")) {
			String source = request.queryParams("source");
			String destination = request.queryParams("destination");
			String quantityStr=request.queryParams("quantity");
			
			try{
				Address.getParametersFromAddress(destination);
			} catch(Exception e){
				destination="";
			}

			if(destination.length()==0){
				attributes.put("error", Language.getLangLabel("Please input a valid destination address that you want to send."));
			}else if(quantityStr.length()==0){
				attributes.put("error", Language.getLangLabel("Please input the NEWB amount that you want to send."));
			} else {
				try {
					Double rawQuantity = Double.parseDouble(quantityStr);
					BigInteger quantity = new BigDecimal(rawQuantity*Config.nbc_unit).toBigInteger();
				
					Transaction tx = Send.create(source, destination, "NBC", quantity);
					blocks.sendTransaction(source,tx);
					attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			}
		}

		if (request.queryParams().contains("form") && request.queryParams("form").equals("import")) {
			String privateKey = request.queryParams("privatekey");
			try {
				address = Blocks.getInstance().importPrivateKey(privateKey);
				request.session().attribute("address", address);
				attributes.put("address", address);				
				attributes.put("success", Language.getLangLabel("Your private key has been imported."));
			} catch (Exception e) {
				attributes.put("error", Language.getLangLabel("Error when importing private key: ")+e.getMessage());
			}
		}
		
		attributes.put("balanceNBC", Util.getBalance(address, "NBC").doubleValue() / Config.nbc_unit.doubleValue());
		attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.btc_unit.doubleValue());
		
		Database db = Database.getInstance();
		
		//get my sends
		ResultSet rs = db.executeQuery("select sends.*,transactions.block_time from sends,transactions where sends.tx_index=transactions.tx_index and  (sends.source='"+address+"') and sends.asset='NBC'  order by block_index desc, tx_index desc;");
		ArrayList<HashMap<String, Object>> mySends = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("validity", rs.getString("validity"));
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				map.put("destination", rs.getString("destination"));
				mySends.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_sends", mySends);								

		//get my receives
		rs = db.executeQuery("select sends.*,transactions.block_time from sends,transactions where sends.tx_index=transactions.tx_index and (sends.destination='"+address+"') and sends.asset='NBC'  order by block_index desc, tx_index desc;");
		ArrayList<HashMap<String, Object>> myReceives = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("validity", rs.getString("validity"));
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", Language.getLangLabel(rs.getString("source")));
				map.put("destination", rs.getString("destination"));
				myReceives.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_receives", myReceives);								
		
		//get my burns
		rs = db.executeQuery("select burns.*,transactions.block_time from burns,transactions where burns.tx_index=transactions.tx_index and burns.source='"+address+"' order by block_index desc, tx_index desc;");
		ArrayList<HashMap<String, Object>> myBurns = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("destination", rs.getString("destination"));
				map.put("validity", rs.getString("validity"));
				map.put("burned", BigInteger.valueOf(rs.getLong("burned")).doubleValue()/Config.btc_unit.doubleValue());
				map.put("earned", BigInteger.valueOf(rs.getLong("earned")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				myBurns.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_burns", myBurns);	
		
		/*
		//get sends
		rs = db.executeQuery("select * from sends where asset='NBC' and validity='valid' order by block_index desc, tx_index desc limit 20;");
		ArrayList<HashMap<String, Object>> sends = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				map.put("destination", rs.getString("destination"));
				sends.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("sends", sends);			
		
		//get burns
		rs = db.executeQuery("select * from burns where validity='valid' order by earned desc limit 20;");
		ArrayList<HashMap<String, Object>> burns = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("burned", BigInteger.valueOf(rs.getLong("burned")).doubleValue()/Config.btc_unit.doubleValue());
				map.put("earned", BigInteger.valueOf(rs.getLong("earned")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				burns.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("burns", burns);				
		*/

		//save wallet file
		try {
			blocks.wallet.saveToFile(new File(blocks.walletFile));
		} catch (IOException e) {
		}
        
        attributes.put("LANG_MY", Language.getLangLabel("My "));
        attributes.put("LANG_HIS", Language.getLangLabel("His "));
        attributes.put("LANG_BALANCE", Language.getLangLabel("balance"));
        attributes.put("LANG_NEWB", Language.getLangLabel("NEWB"));
        attributes.put("LANG_BTC", Language.getLangLabel("BTC"));
        attributes.put("LANG_IMPORT_PRIVATE_KEY", Language.getLangLabel("Import private key"));
        attributes.put("LANG_PRIVATE_KEY", Language.getLangLabel("private key"));
        attributes.put("LANG_YOUR_PRIVATE_KEY_SHOULD_BE", Language.getLangLabel("Your private key should be in WIF format. For more information about where to find this, see the Participate page."));
        attributes.put("LANG_SEND", Language.getLangLabel("Send"));
        attributes.put("LANG_DESTINATION_ADDRESS", Language.getLangLabel("destination address"));
        attributes.put("LANG_QUANTITY_NEWB", Language.getLangLabel("quantity (NEWB)"));
        attributes.put("LANG_BURNS", Language.getLangLabel("burns"));
        attributes.put("LANG_SENDING_TRANSACTIONS", Language.getLangLabel("sending transactions"));
        attributes.put("LANG_RECEIVING_TRANSACTIONS", Language.getLangLabel("receiving transactions"));
        attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
        attributes.put("LANG_TIME", Language.getLangLabel("Time"));
        attributes.put("LANG_SOURCE", Language.getLangLabel("Source"));
        attributes.put("LANG_DESTINATION", Language.getLangLabel("Destination"));
        attributes.put("LANG_BURNED", Language.getLangLabel("Burned"));
        attributes.put("LANG_EARNED", Language.getLangLabel("Earned"));
        attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
        attributes.put("LANG_QUANTITY", Language.getLangLabel("Quantity"));
        attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
		
		return attributes;
	}

	public Map<String, Object> handleCasinoRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "Casino");
		
		Blocks blocks = Blocks.getInstance();
		String address=(String)attributes.get("address");
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("bet")) {
			String source = request.queryParams("source");
			
			String betStr=request.queryParams("bet");
			if(betStr.length()>0){
				try {
					Double rawBet = Double.parseDouble(betStr);
					Short bigORsmall = Short.parseShort(request.queryParams("big_or_small"));
					BigInteger bet = new BigDecimal(rawBet*Config.nbc_unit).toBigInteger();
				
					Transaction tx = Bet.create(source,  bet, bigORsmall);
					blocks.sendTransaction(source,tx);
					attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			} else {
				attributes.put("error", "Please input the NEWB amount that you want to bet.");
			}
		}
		Integer lastBtcBlock=blocks.bitcoinBlock;
		Integer lastNbcBlock=blocks.newbiecoinBlock;
		Integer nextBtcBlock=lastBtcBlock+1;
		Integer currentBetStartBlock=nextBtcBlock-(nextBtcBlock-Config.betStartBlock) % Config.betPeriodBlocks;
		Integer currentBetEndBlock=currentBetStartBlock+Config.betPeriodBlocks-1;
		Integer prevBetStartBlock=currentBetStartBlock-Config.betPeriodBlocks;
		Integer prevBetEndBlock=currentBetStartBlock-1;
		
		attributes.put("betting_start_block", currentBetStartBlock.toString());
		attributes.put("betting_end_block", currentBetEndBlock.toString());
		attributes.put("prev_bet_start_block", prevBetStartBlock.toString());
		attributes.put("prev_bet_end_block", prevBetEndBlock.toString());
		
		Integer lastResolveBlockIndex=prevBetEndBlock+Config.betResolveWaitBlocks+1;
		
		String bettingStatus="Betting";

		if(lastBtcBlock<currentBetEndBlock){
			Integer  waitBlocks=currentBetEndBlock-lastBtcBlock;
			String   aboutWaitTimeDesc="";
			Integer  aboutHours=new Double(java.lang.Math.floor(new Double(10*waitBlocks).doubleValue()/new Double(60).doubleValue())).intValue();
			if(aboutHours>0)
				aboutWaitTimeDesc=aboutHours.toString() + " hours";
			Integer  aboutWaitMins=(10*waitBlocks)%60;
			if(aboutWaitMins>0)
				aboutWaitTimeDesc=aboutWaitTimeDesc+" "+aboutWaitMins.toString()+" minutes";
			
			bettingStatus="Betting , left "+waitBlocks.toString()+" blocks about "+aboutWaitTimeDesc;
		} 
		attributes.put("betting_status", bettingStatus);
		
		
		String prevBetStatus="Syncing your wallet.";
		
		if(lastBtcBlock<lastResolveBlockIndex){
			Integer  waitBlocks=lastResolveBlockIndex-lastBtcBlock;
			Integer  aboutWaitMins=10*waitBlocks;
			
			prevBetStatus="Pending resolved. Please wait about "+aboutWaitMins.toString()+" minutes";
		} else{
			Database db = Database.getInstance();
			ResultSet rs = db.executeQuery("select * from blocks where block_index="+lastResolveBlockIndex+";");
			try {
				if(rs.next()) {
					prevBetStatus="Resolved at Block("+lastResolveBlockIndex.toString()+") "+Util.timeFormat(rs.getInt("block_time"))+" .";
					if(lastNbcBlock<lastResolveBlockIndex) {
						prevBetStatus = prevBetStatus + " Syncing your wallet.";
					}
				} 
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		attributes.put("prev_bet_status", prevBetStatus);

		Database db = Database.getInstance();
		
		//get house info
		ResultSet rs = db.executeQuery("select count(amount) as fee_count, sum(amount) as sum_fee, avg(amount) as avg_fee from credits where address='"+Config.houseAddressFund+"' and asset='NBC' and calling_function='"+Config.houseFunctionName+"';");
		
		try {
			if(rs.next()){
				BigInteger totalHouseFee=BigInteger.valueOf(rs.getLong("sum_fee"));
				attributes.put("house_times", rs.getInt("fee_count"));
				attributes.put("total_house_fee", totalHouseFee.floatValue() / Config.nbc_unit.floatValue());
			}else{
				attributes.put("house_times", 0);
				attributes.put("total_house_fee", 0);
			}
		} catch (SQLException e) {
			attributes.put("house_times", "?");
			attributes.put("total_house_fee", "?");
		}
		attributes.put("house_address", Config.houseAddressFund);	
		attributes.put("house_edge", Config.houseEdge);
			
		//get top winners
		rs = db.executeQuery("select source, count(bet) as bet_count, avg(bet) as avg_bet, sum(profit) as sum_profit from bets where validity='valid' group by source order by sum(profit) desc limit 10;");
		ArrayList<HashMap<String, Object>> winners = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet_count", rs.getDouble("bet_count"));
				map.put("avg_bet", BigInteger.valueOf(rs.getLong("avg_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.nbc_unit.doubleValue());
				winners.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("winners", winners);				
		
		//get top 10 highest rollers
		rs = db.executeQuery("select source, count(bet) as bet_count, sum(bet) as sum_bet,  sum(profit) as sum_profit from bets where validity='valid' group by source order by sum(bet) desc limit 10;");
		ArrayList<HashMap<String, Object>> highRollers = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet_count", rs.getDouble("bet_count"));
				map.put("sum_bet", BigInteger.valueOf(rs.getLong("sum_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.nbc_unit.doubleValue());
				highRollers.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("high_rollers", highRollers);	
		
		//get top 10 largest bets
		rs = db.executeQuery("select bets.source as source,bet,bet_bs,profit,bets.tx_hash as tx_hash,rolla,rollb,roll,resolved,bets.tx_index as tx_index,bets.block_index,transactions.block_time from bets,transactions where bets.validity='valid' and bets.tx_index=transactions.tx_index order by bets.bet desc limit 10;");
		ArrayList<HashMap<String, Object>> bets = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("largest_bets", bets);

		
		//get last 200 bets
		rs = db.executeQuery("select bets.source as source,bet,bet_bs,profit,bets.tx_hash as tx_hash,rolla,rollb,roll,resolved,bets.tx_index as tx_index,bets.block_index,transactions.block_time from bets,transactions where bets.validity='valid' and bets.tx_index=transactions.tx_index order by bets.block_index desc, bets.tx_index desc limit 200;");
		bets = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("bets", bets);

		rs = db.executeQuery("select bets.source as source,bet,bet_bs,profit,bets.tx_hash as tx_hash,rolla,rollb,roll,resolved,bets.tx_index as tx_index,bets.block_index,transactions.block_time from bets,transactions where bets.validity='valid' and bets.source='"+address+"' and bets.tx_index=transactions.tx_index order by bets.block_index desc, bets.tx_index desc limit 200;");
		bets = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));	
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_bets", bets);
						
		List<BetInfo> betsPending = Bet.getPending(address);
		bets = new ArrayList<HashMap<String, Object>>();
		for (BetInfo betInfo : betsPending) {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("source", betInfo.source);
			map.put("bet", betInfo.bet.doubleValue()/Config.nbc_unit.doubleValue());
			map.put("bet_bs", betInfo.bet_bs);
			map.put("tx_hash", betInfo.txHash);
			map.put("block_index", betInfo.blockIndex.toString());
			map.put("block_time", Util.timeFormat(betInfo.blockTime));
			bets.add(map);
		}
		attributes.put("my_bets_pending", bets);
        
        attributes.put("LANG_ROLL_DICE", Language.getLangLabel("Roll dice"));
        attributes.put("LANG_BIGGER", Language.getLangLabel("Bigger"));
        attributes.put("LANG_SMALLER", Language.getLangLabel("Smaller"));
        attributes.put("LANG_RECENT_BETS", Language.getLangLabel("Recent bets"));
        attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
        attributes.put("LANG_TIME", Language.getLangLabel("Time"));
        attributes.put("LANG_SOURCE_ADDRESS", Language.getLangLabel("Source address"));
        attributes.put("LANG_BET_SIZE", Language.getLangLabel("Bet size"));
        attributes.put("LANG_BIGGER_OR_SMALLER", Language.getLangLabel("Bigger or Smaller"));
        attributes.put("LANG_ROLL", Language.getLangLabel("Roll"));
        attributes.put("LANG_PROFIT", Language.getLangLabel("Profit"));
        attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
        attributes.put("LANG_UNRESOLVED", Language.getLangLabel("Unresolved"));

		return attributes;
	}
	/*
	public Map<String, Object> handleWorldCupRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "WorldCup");
		
		Blocks blocks = Blocks.getInstance();
		String address=(String)attributes.get("address");
		if (request.queryParams().contains("form") && request.queryParams("form").equals("bet")) {
			String source = request.queryParams("source");
			
			String betStr=request.queryParams("bet");
			if(betStr.length()>0){
				try {
					Double rawBet = Double.parseDouble(betStr);
					Short championTeamId = Short.parseShort(request.queryParams("champion"));
					Short secondTeamId = Short.parseShort(request.queryParams("second"));
					BigInteger bet = new BigDecimal(rawBet*Config.nbc_unit).toBigInteger();
				
					Transaction tx = BetWorldCup.create(source,  bet, championTeamId,secondTeamId);
					blocks.sendTransaction(source,tx);
					attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			} else {
				attributes.put("error", "Please input the NEWB amount and the valid champion&second teams that you want to bet.");
			}
		}
		if (request.queryParams().contains("form") && request.queryParams("form").equals("broadcast")) {
			try {
				Short championTeamId = Short.parseShort(request.queryParams("champion"));
				Short secondTeamId = Short.parseShort(request.queryParams("second"));
			
				Transaction tx = BetWorldCup.createResolvedBroadcast(championTeamId,secondTeamId);
				blocks.sendTransaction(address,tx);
				attributes.put("success", "The broadcast had been submited.Please wait confirms for at least 1 block.");
			} catch (Exception e) {
				attributes.put("error", e.getMessage());
			}
		}
		
		attributes.put("betting_start_time", Util.timeFormat(Config.WORLDCUP2014_BETTING_START_UTC));
		attributes.put("betting_end_time", Util.timeFormat(Config.WORLDCUP2014_BETTING_END_UTC));
		attributes.put("resolve_scheme_time", Util.timeFormat(Config.WORLDCUP2014_RESOLVE_SCHEME_UTC));
		
		Integer lastBlockTime=Util.getLastBlockTimestamp();
		
		String teamSelectHtml="";
		for(int tt=1;tt<=32;tt++){
			Short teamId=new Short(new Integer(tt).toString());
			
			teamSelectHtml=teamSelectHtml+"<option value='"+teamId.toString()+"'>"+BetWorldCup.getTeamLabel(teamId)+"</item>";
		}
		attributes.put("team_select_html",teamSelectHtml);
		
		String bettingStatus="";
		if(lastBlockTime<Config.WORLDCUP2014_BETTING_START_UTC){
			bettingStatus="Waiting";
		} else if(lastBlockTime<Config.WORLDCUP2014_BETTING_END_UTC) { 
			Integer  waitSeconds=Config.WORLDCUP2014_BETTING_END_UTC-lastBlockTime;
			String   aboutWaitTimeDesc="";
			Integer  aboutDays=new Double(java.lang.Math.floor(waitSeconds.doubleValue()/new Double(60*60*24).doubleValue())).intValue();
			if(aboutDays>0)
				aboutWaitTimeDesc=aboutDays.toString() + " " + Language.getLangLabel("days");
			
			waitSeconds=waitSeconds%(60*60*24);
			Integer  aboutHours=new Double( java.lang.Math.floor(waitSeconds.doubleValue() /new Double(60*60).doubleValue())).intValue();
			if(aboutHours>0)
				aboutWaitTimeDesc=aboutWaitTimeDesc+" "+aboutHours.toString() + " hours";
			
			if(aboutDays==0 && aboutHours==0){
				waitSeconds=waitSeconds%(60*60);
				Integer  aboutWaitMins=new Double( java.lang.Math.floor(waitSeconds.doubleValue() /new Double(60).doubleValue())).intValue();
				if(aboutWaitMins>0){
					bettingStatus="Betting will be closed soon  , left only about "+aboutWaitMins.toString()+" minutes.";
					attributes.put("betting_enabled", "true");
				}else
					bettingStatus="Betting closed.";
				
			}else{
				bettingStatus="Betting , left about "+aboutWaitTimeDesc;
				attributes.put("betting_enabled", "true");
			}
		} else if(lastBlockTime<Config.WORLDCUP2014_RESOLVE_SCHEME_UTC) { 
			bettingStatus="Pending resolved";
		} else {
			Short rolled_result=BetWorldCup.getResolvedResult();
			if(rolled_result==null){
				bettingStatus="Waiting for the resolved broadcast";
			} else {
				bettingStatus="Resolved! The Champion&Second are "+BetWorldCup.getBetSetLabel(rolled_result);
			}
			if(Config.houseAddressFund.equals(address)){
				attributes.put("house_broadcast_enabled", "true");
			}
		}
		attributes.put("betting_status", bettingStatus);
		
		Database db = Database.getInstance();
		
		//get house info
		ResultSet rs = db.executeQuery("select count(amount) as fee_count, sum(amount) as sum_fee, avg(amount) as avg_fee from credits where address='"+Config.houseAddressFund+"' and asset='NBC' and calling_function='"+Config.houseWorldCupFunctionName+"';");
		
		try {
			if(rs.next()){
				BigInteger totalHouseFee=BigInteger.valueOf(rs.getLong("sum_fee"));
				attributes.put("house_times", rs.getInt("fee_count"));
				attributes.put("total_house_fee", totalHouseFee.floatValue() / Config.nbc_unit.floatValue());
			}else{
				attributes.put("house_times", 0);
				attributes.put("total_house_fee", 0);
			}
		} catch (SQLException e) {
			attributes.put("house_times", "?");
			attributes.put("total_house_fee", "?");
		}
		attributes.put("house_address", Config.houseAddressFund);	
		attributes.put("house_edge", Config.houseEdge);
		
		//get team bet statistics
		rs = db.executeQuery("select bet_set, count(bet) as bet_count, sum(bet) as sum_bet, avg(bet) as avg_bet, sum(profit) as sum_profit from bets_worldcup where validity='valid' and bet>0 group by bet_set order by sum_bet desc;");
		ArrayList<HashMap<String, Object>> teams = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("bet_set", BetWorldCup.getBetSetLabel(rs.getShort("bet_set")));
				map.put("bet_count", rs.getDouble("bet_count"));
				map.put("sum_bet", BigInteger.valueOf(rs.getLong("sum_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("avg_bet", BigInteger.valueOf(rs.getLong("avg_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				BigInteger sum_profit= BigInteger.valueOf(rs.getLong("sum_profit"));
				if(sum_profit.compareTo(BigInteger.ZERO)==0)
					map.put("sum_profit", "(Pending)");
				else
					map.put("sum_profit", sum_profit.doubleValue()/Config.nbc_unit.doubleValue());
					
				teams.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("teams", teams);	
		
		//get top winners
		rs = db.executeQuery("select source, count(bet) as bet_count, avg(bet) as avg_bet, sum(profit) as sum_profit from bets_worldcup where validity='valid' and bet>0  group by source order by sum_profit desc limit 10;");
		ArrayList<HashMap<String, Object>> winners = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet_count", rs.getDouble("bet_count"));
				map.put("avg_bet", BigInteger.valueOf(rs.getLong("avg_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				BigInteger sum_profit=BigInteger.valueOf(rs.getLong("sum_profit"));
				if(sum_profit.compareTo(BigInteger.ZERO)>0)
					map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.nbc_unit.doubleValue());
				else
					break;
					
				winners.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("winners", winners);				
		
		//get top 10 highest rollers
		rs = db.executeQuery("select source, count(bet) as bet_count, sum(bet) as sum_bet,  sum(profit) as sum_profit from bets_worldcup where validity='valid' and bet>0  group by source order by sum(bet) desc limit 10;");
		ArrayList<HashMap<String, Object>> highRollers = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet_count", rs.getDouble("bet_count"));
				map.put("sum_bet", BigInteger.valueOf(rs.getLong("sum_bet")).doubleValue()/Config.nbc_unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.nbc_unit.doubleValue());
				highRollers.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("high_rollers", highRollers);	
		
		//get top 10 largest bets_worldcup
		rs = db.executeQuery("select bets_worldcup.source as source,bet,bet_set,profit,bets_worldcup.tx_hash as tx_hash,roll,resolved,bets_worldcup.tx_index as tx_index,bets_worldcup.block_index,transactions.block_time from bets_worldcup,transactions where bets_worldcup.validity='valid'  and bets_worldcup.bet>0  and bets_worldcup.tx_index=transactions.tx_index order by bets_worldcup.bet desc limit 10;");
		ArrayList<HashMap<String, Object>> bets_worldcup = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_set", BetWorldCup.getBetSetLabel(rs.getShort("bet_set")));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets_worldcup.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("largest_bets", bets_worldcup);

		
		//get last 200 bets_worldcup
		rs = db.executeQuery("select bets_worldcup.source as source,bet,bet_set,profit,bets_worldcup.tx_hash as tx_hash,roll,resolved,bets_worldcup.tx_index as tx_index,bets_worldcup.block_index,transactions.block_time from bets_worldcup,transactions where bets_worldcup.validity='valid' and bets_worldcup.bet>0 and bets_worldcup.tx_index=transactions.tx_index order by bets_worldcup.block_index desc, bets_worldcup.tx_index desc limit 200;");
		bets_worldcup = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_set", BetWorldCup.getBetSetLabel(rs.getShort("bet_set")));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets_worldcup.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("bets", bets_worldcup);

		rs = db.executeQuery("select bets_worldcup.source as source,bet,bet_set,profit,bets_worldcup.tx_hash as tx_hash,roll,resolved,bets_worldcup.tx_index as tx_index,bets_worldcup.block_index,transactions.block_time from bets_worldcup,transactions where bets_worldcup.validity='valid' and bets_worldcup.source='"+address+"' and bets_worldcup.tx_index=transactions.tx_index order by bets_worldcup.block_index desc, bets_worldcup.tx_index desc limit 200;");
		bets_worldcup = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("source", rs.getString("source"));
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("bet_set", BetWorldCup.getBetSetLabel(rs.getShort("bet_set")));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("resolved", rs.getString("resolved"));	
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.nbc_unit.doubleValue());
				bets_worldcup.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_bets", bets_worldcup);
						
		List<BetWorldCupInfo> betsPending = BetWorldCup.getPending(address);
		bets_worldcup = new ArrayList<HashMap<String, Object>>();
		for (BetWorldCupInfo betInfo : betsPending) {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("source", betInfo.source);
			map.put("bet", betInfo.bet.doubleValue()/Config.nbc_unit.doubleValue());
			map.put("bet_set",BetWorldCup.getBetSetLabel( betInfo.bet_set));
			map.put("tx_hash", betInfo.txHash);
			map.put("block_index", betInfo.blockIndex.toString());
			map.put("block_time", Util.timeFormat(betInfo.blockTime));
			bets_worldcup.add(map);
		}
		attributes.put("my_bets_pending", bets_worldcup);

		return attributes;
	}
	*/
	public Map<String, Object> handleCrowdfundingRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "Crowdfunding");
		
		Blocks blocks = Blocks.getInstance();
		String address=(String)attributes.get("address");
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("add-project")) {
			logger.info("************* do add-project **************");
		
			String owner = request.queryParams("owner");
			
			String titleStr=request.queryParams("title");
			String expireDateStr=request.queryParams("expire_date");
			String logoImgUrlStr=request.queryParams("logo_img_url");
			String topicImgUrlStr=request.queryParams("topic_img_url");
			String detailImgUrlStr=request.queryParams("detail_img_url");
			String emailStr=request.queryParams("email");
			String nameStr=request.queryParams("name");
			String detailUrlStr=request.queryParams("web");
			String minFundStr=request.queryParams("min_fund");
			String rsaPublicKeyStr=request.queryParams("rsa_public_key");

			if(titleStr.length()>0 && expireDateStr.length()>0 && logoImgUrlStr.length()>0 && topicImgUrlStr.length()>0 && detailImgUrlStr.length()>0 && nameStr.length()>0 && emailStr.length()>0 && detailUrlStr.length()>0 && minFundStr.length()>0 && rsaPublicKeyStr.length()>0){
				try {
					Double rawMinNewbAmount = Double.parseDouble(minFundStr);
					BigInteger minNewbAmount = new BigDecimal(rawMinNewbAmount*Config.nbc_unit).toBigInteger();
					
					JSONArray itemSets = new JSONArray(); 
					for(int tt=1;tt<=5;tt++){
						String tmpPriceStr = request.queryParams("item"+tt+"_price");
						String tmpMaxStr   = request.queryParams("item"+tt+"_max_backers");
						String tmpLabelStr = request.queryParams("item"+tt+"_label");
						
						if(tmpPriceStr.length()>0 ){
							Double rawItemNewbAmount = Double.parseDouble(tmpPriceStr);
							BigInteger itemNewbAmount = new BigDecimal(rawItemNewbAmount*Config.nbc_unit).toBigInteger();
							
							Integer itemBackerLimit = Integer.parseInt(tmpMaxStr);
							
							JSONObject itemData = new JSONObject(); 
							itemData.put("price",itemNewbAmount); 
							itemData.put("max",itemBackerLimit); 
							itemData.put("label",tmpLabelStr); 
							
							itemSets.put(itemData);
						}
					}
					
					Integer expireUTC=Util.getDateTimestamp(expireDateStr,null);
					Calendar nowtime=Calendar.getInstance();
					Long leftSeconds=expireUTC-nowtime.getTimeInMillis()/1000L;

					if(leftSeconds<60*60*24L || leftSeconds>60*60*24*365L  ){
						attributes.put("error", Language.getLangLabel("Please input a valid expire date ( At least 1 day and at most 1 year from today)."));
					} else if( itemSets.length()==0 ){
						attributes.put("error", Language.getLangLabel("Please set at least one valid item for backing."));
					} else {
						Map mapProjectSet = new HashMap(); 
						mapProjectSet.put("title", titleStr); 
						mapProjectSet.put("logo_img_url", logoImgUrlStr); 
						mapProjectSet.put("topic_img_url", topicImgUrlStr); 
						mapProjectSet.put("detail_img_url", detailImgUrlStr); 
						mapProjectSet.put("name", nameStr); 
						mapProjectSet.put("email", emailStr); 
						mapProjectSet.put("web", detailUrlStr); 
						mapProjectSet.put("min_fund", minNewbAmount); 
						mapProjectSet.put("expire_utc", expireUTC); 
						mapProjectSet.put("item_sets", itemSets); 
						mapProjectSet.put("rsa_pub", rsaPublicKeyStr); 
						 
						JSONObject project_set = new JSONObject(mapProjectSet); 
						
						Transaction tx = CrowdfundingProject.createProject(owner,  project_set);
						blocks.sendTransaction(owner,tx);
						attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
					}
				} catch (Exception e) {
					logger.error("************* do add-project error: "+e.getMessage());
					attributes.put("error", e.getMessage());
				}
			} else {
				attributes.put("error", Language.getLangLabel("Please input valid title, expire date,min fund amount, logo/topic/detail img url,website url, your name and email."));
			}
		}
		
		Database db = Database.getInstance();
		
		ArrayList<HashMap<String, Object>> crowdfunding_projects = new ArrayList<HashMap<String, Object>>();
		
		List<CrowdfundingProjectInfo> projectsPending = CrowdfundingProject.getPending(address);
		logger.info( "\n=============================\n projectsPending.size="+projectsPending.size()+"\n=====================\n");
		
		crowdfunding_projects = new ArrayList<HashMap<String, Object>>();
		for (CrowdfundingProjectInfo projectInfo : projectsPending) {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("owner", projectInfo.owner);
			map.put("tx_index",projectInfo.txIndex.toString());
			map.put("tx_hash", projectInfo.txHash);
			map.put("block_index", projectInfo.blockIndex.toString());
			map.put("block_time", Util.timeFormat(projectInfo.blockTime));
			map.put("backers",projectInfo.backers);
			map.put("nbc_funded",projectInfo.nbcFunded);
			map.put("validity",projectInfo.validity);
			map.put("percent", 0); 
			
			try{
				map=CrowdfundingProject.parseProjectSet(map,projectInfo.projectSet);
				crowdfunding_projects.add(map);
			}catch (Exception e) {
				logger.error(e.toString());
			}
		}
		attributes.put("my_pending_projects", crowdfunding_projects);
		
		//get last 10 projects
		ResultSet rs = db.executeQuery("select cp.owner ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.project_set, cp.backers, cp.nbc_funded,cp.validity from crowdfunding_projects cp,transactions where cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc limit 10;");
		crowdfunding_projects = new ArrayList<HashMap<String, Object>>();
		try {
			while ( rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("owner", rs.getString("owner"));
				map.put("tx_index", rs.getString("tx_index"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("validity", rs.getString("validity"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("backers", rs.getInt("backers"));
				map.put("nbc_funded", BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/Config.nbc_unit.doubleValue());
				
				try{
					JSONObject project_set = new JSONObject(rs.getString("project_set")); 
					map=CrowdfundingProject.parseProjectSet(map,project_set);
					
					map.put("percent", new BigDecimal(100*BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()).toBigInteger( )); 
					
					crowdfunding_projects.add(map);
				}catch (Exception e) {
					logger.error(e.toString());
				}
			}
		} catch (SQLException e) {
		}
		
		if( !crowdfunding_projects.isEmpty() ){
			attributes.put("recent_projects", crowdfunding_projects);
			attributes.put("recommand_project", crowdfunding_projects.get(0));
		}
		
		//get my projects
		rs = db.executeQuery("select cp.owner ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.project_set, cp.backers, cp.nbc_funded,cp.validity from crowdfunding_projects cp,transactions where cp.owner='"+address+"' and cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc limit 100;");
		crowdfunding_projects = new ArrayList<HashMap<String, Object>>();
		try {
			while ( rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("owner", rs.getString("owner"));
				map.put("tx_index", rs.getString("tx_index"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("validity", rs.getString("validity"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("backers", rs.getInt("backers"));
				map.put("nbc_funded", BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/Config.nbc_unit.doubleValue());
				
				try{
					JSONObject project_set = new JSONObject(rs.getString("project_set")); 
					map=CrowdfundingProject.parseProjectSet(map,project_set);
					
					map.put("percent", new BigDecimal(100*BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()).toBigInteger( )); 
					
					crowdfunding_projects.add(map);
				}catch (Exception e) {
					logger.error(e.toString());
				}
			}
		} catch (SQLException e) {
		}
		
		attributes.put("my_projects", crowdfunding_projects);
		
		//get my backs
		rs = db.executeQuery("select bk.backer,bk.email,bk.tx_hash ,bk.tx_index ,bk.back_price_nbc,bk.validity ,bk.block_index,bk.project_tx_index,transactions.block_time,cp.owner ,cp.project_set, cp.backers, cp.nbc_funded,cp.validity as project_validity from crowdfunding_backers bk,crowdfunding_projects cp,transactions where bk.backer='"+address+"' and bk.project_tx_index=cp.tx_index and bk.tx_index=transactions.tx_index order by bk.block_index desc, bk.tx_index desc limit 100;");
		crowdfunding_projects = new ArrayList<HashMap<String, Object>>();
		try {
			while ( rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				
				Long back_item_price_nbc=rs.getLong("back_price_nbc");
				map.put("backer", rs.getString("backer"));
				map.put("email", rs.getString("email"));
				map.put("tx_index", rs.getString("tx_index"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("item_price", BigInteger.valueOf(back_item_price_nbc).doubleValue()/Config.nbc_unit.doubleValue());
				map.put("validity", rs.getString("validity"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("project_tx_index", rs.getString("project_tx_index"));
				map.put("project_validity", rs.getString("project_validity"));
				//map.put("owner", rs.getString("owner"));
				//map.put("backers", rs.getInt("backers"));
				//map.put("nbc_funded", BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/Config.nbc_unit.doubleValue());
				
				String originalEmailStr=Util.getOriginalBackContact(rs.getString("tx_hash"));
				if(originalEmailStr!=null)
					map.put("email", originalEmailStr);
					
				try{
					JSONObject project_set = new JSONObject(rs.getString("project_set")); 
					map.put("logo_img_url", HtmlRegexpUtil.filterHtml(project_set.getString("logo_img_url")));
					//map=CrowdfundingProject.parseProjectSet(map,project_set);
					
					//map.put("percent", new BigDecimal(100*BigInteger.valueOf(rs.getLong("nbc_funded")).doubleValue()/BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()).toBigInteger( )); 
					
					JSONArray  item_sets = project_set.getJSONArray("item_sets");
					ArrayList<HashMap<String, Object>> item_set_array = new ArrayList<HashMap<String, Object>>();
					for(int tt=0;tt<item_sets.length();tt++){
						JSONObject item_obj=(JSONObject)item_sets.get(tt);
						
						if(item_obj.getLong("price")==back_item_price_nbc){
							map.put("item_label", HtmlRegexpUtil.filterHtml(item_obj.getString("label")));
							break;
						}
					}
					
					crowdfunding_projects.add(map);
				}catch (Exception e) {
					logger.error(e.toString());
				}
			}
		} catch (SQLException e) {
		}
		
		attributes.put("my_backs", crowdfunding_projects);        
        
        
        attributes.put("LANG_CREATE_NEW_PROJECT", Language.getLangLabel("Create a new project"));
        attributes.put("LANG_LEFT", Language.getLangLabel("Left"));
        attributes.put("LANG_VIEW", Language.getLangLabel("View"));
        attributes.put("LANG_PROJECT_EXPIRED", Language.getLangLabel("project expired and pending resolved."));
        attributes.put("LANG_SUCCESSFULLY_FUNDED", Language.getLangLabel("Successfully funded!"));
        attributes.put("LANG_FAILED", Language.getLangLabel("Failed!"));
        attributes.put("LANG_CANCELED", Language.getLangLabel("Canceled"));
        attributes.put("LANG_TOTAL", Language.getLangLabel("Total"));
        attributes.put("LANG_OF", Language.getLangLabel("of"));                
        attributes.put("LANG_BACKERS", Language.getLangLabel("Backers"));
        attributes.put("LANG_PROJECT_BY", Language.getLangLabel("Project by"));
        attributes.put("LANG_CREATED_TIME", Language.getLangLabel("Created time"));
        
        attributes.put("LANG_THE_NEWEST", Language.getLangLabel("The newest!"));
        attributes.put("LANG_RECENT_PROJECTS", Language.getLangLabel("Recent projects"));
        attributes.put("LANG_MY_PROJECTS", Language.getLangLabel("My projects"));
        attributes.put("LANG_MY_BACKS", Language.getLangLabel("My backs"));

        attributes.put("LANG_LOGO", Language.getLangLabel("Logo"));
        attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
        attributes.put("LANG_TIME", Language.getLangLabel("Time"));
        attributes.put("LANG_TITLE", Language.getLangLabel("Title"));
        attributes.put("LANG_OWNER", Language.getLangLabel("Owner"));
        attributes.put("LANG_FUNDED", Language.getLangLabel("Funded"));
        attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
        attributes.put("LANG_PLEDGE", Language.getLangLabel("Pledge"));
        
        attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
        attributes.put("LANG_PROJECT", Language.getLangLabel("Project"));        
        attributes.put("LANG_BACKED_ITEM", Language.getLangLabel("Backed Item"));        
        attributes.put("LANG_BACKER", Language.getLangLabel("Backer"));        
        attributes.put("LANG_VALID", Language.getLangLabel("valid"));        
        attributes.put("LANG_SUCCESS", Language.getLangLabel("success"));
        attributes.put("LANG_REFUNDED", Language.getLangLabel("refunded"));
        attributes.put("LANG_PROJECT_FAILED", Language.getLangLabel("project failed"));
        attributes.put("LANG_PROJECT_CANCELED", Language.getLangLabel("project canceled"));
		
		return attributes;
	}

	public Map<String, Object> handleCrowdfundingDetailRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		
		attributes = updateCommonStatus(request, attributes);
		attributes.put("title", "View crowdfunding details");
		
		String project_tx_index_param=request.queryParams("project_tx_index");
		if(project_tx_index_param==null){
			if (request.session().attributes().contains("project_tx_index")) {
				project_tx_index_param=request.session().attribute("project_tx_index"); 
			} else {
				attributes.put("error", "Invalid project.");
				return attributes;
			}
		} 
		
		Integer project_tx_index=Integer.parseInt(project_tx_index_param); 
		logger.info("handleCrowdfundingDetailRequest() project_tx_index="+project_tx_index);
		request.session().attribute("project_tx_index", project_tx_index_param);
		
		CrowdfundingProject.updateProjectStat(project_tx_index.toString());
		CrowdfundingProjectInfo projectInfo=CrowdfundingProject.getProjectInfo(project_tx_index.toString());
		
		if(projectInfo==null){
			attributes.put("error", "Invalid project.");
		} else {	
			Blocks blocks = Blocks.getInstance();
			String address=(String)attributes.get("address");
			
			if (request.queryParams().contains("form") && request.queryParams("form").equals("back")) {
				String backer = request.queryParams("backer");
				String backPriceStr=request.queryParams("back_price");
				String originalEmailStr=request.queryParams("backer_email");
		
				logger.info("backPriceStr="+backPriceStr+" emailStr="+originalEmailStr);
				
				if ( backPriceStr.length()>0 && originalEmailStr.length()>5 ){
					try {
						Double rawBackPrice = Double.parseDouble(backPriceStr);
						BigInteger backPrice = new BigDecimal(rawBackPrice*Config.nbc_unit).toBigInteger();
						
						JSONObject project_set = projectInfo.projectSet; 
						String encodedEmailStr=originalEmailStr;
						if(project_set.has("rsa_pub")) {
							String rsa_pub_key=project_set.getString("rsa_pub");
							encodedEmailStr=Coder.encryptBASE64(
								RSACoder.encryptByPublicKey(originalEmailStr.getBytes(), rsa_pub_key)
								);
						} 
						
						Transaction tx = CrowdfundingBack.backProject(backer, projectInfo.txHash, backPrice,encodedEmailStr);
						blocks.sendTransaction(backer,tx);
						Util.exportOriginalBackContact(tx.getHashAsString(),originalEmailStr);
						attributes.put("success", Language.getLangLabel("Thank you for backing the project!")+Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
					} catch (Exception e) {
						attributes.put("error", e.getMessage());
					}
				} else {
					attributes.put("error", Language.getLangLabel("Please input your valid email address."));
				}
			}

			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("owner", projectInfo.owner);
			map.put("tx_index", projectInfo.txIndex.toString());
			map.put("tx_hash", projectInfo.txHash);
			map.put("validity", projectInfo.validity);
			map.put("block_index",projectInfo.blockIndex);
			map.put("block_time", Util.timeFormat(projectInfo.blockTime));
			map.put("backers", projectInfo.backers);
			map.put("nbc_funded", BigInteger.valueOf(projectInfo.nbcFunded).doubleValue()/Config.nbc_unit.doubleValue());
			
			try{
				JSONObject project_set = projectInfo.projectSet; 
				map=CrowdfundingProject.parseProjectSet(map,project_set);
				 
				map.put("percent", new BigDecimal(100*BigInteger.valueOf( projectInfo.nbcFunded ).doubleValue()/BigInteger.valueOf(project_set.getLong("min_fund")).doubleValue()).toBigInteger( )); 
				
				attributes.put("project", map);		
				
				JSONObject back_stat = projectInfo.backStat; 
				JSONObject myBackedItems = 
				      CrowdfundingProject.getProjectMyBackedItems(project_tx_index.toString(),address); 
				JSONArray  item_sets = project_set.getJSONArray("item_sets");
				
				List<CrowdfundingBackerInfo> myPendingBacks=CrowdfundingBack.getPending(address);
				
				ArrayList<HashMap<String, Object>> item_set_array = new ArrayList<HashMap<String, Object>>();
				for(int tt=0;tt<item_sets.length();tt++){
					JSONObject item_obj=(JSONObject)item_sets.get(tt);
					HashMap<String,Object> item = new HashMap<String,Object>();
					item.put("price", BigInteger.valueOf(item_obj.getLong("price")).doubleValue()/Config.nbc_unit.doubleValue());	
					item.put("max", item_obj.getInt("max"));	
					item.put("label", HtmlRegexpUtil.filterHtml(item_obj.getString("label")).replaceAll("[\\n]", "<br>"));	
					
					for(int bb=0;bb<myPendingBacks.size();bb++){
						CrowdfundingBackerInfo  tmpBackerInfo=myPendingBacks.get(bb);
						if(tmpBackerInfo.projectTxHash.equals(projectInfo.txHash) && tmpBackerInfo.price==item_obj.getLong("price")){
							item.put("back_is_pending",true);
						}
					}
					
					Integer item_backers=0;
					if(back_stat!=null && back_stat.has(""+item_obj.getLong("price")))
						item_backers=back_stat.getJSONObject(""+item_obj.getLong("price")).getInt("backers");
					
					if(item.get("back_is_pending")!=null)
						item_backers++;
						
					item.put("backers",item_backers);
					
					Integer leftChance=item_obj.getInt("max")-item_backers;
					if(leftChance<=0)
						item.put("left", 0);
					else
						item.put("left", leftChance);
					
					if(item_backers>0 && myBackedItems!=null && myBackedItems.has(""+item_obj.getLong("price"))){
						item.put("had_backed",true);
					}
					
					item_set_array.add(item);
				}
				
				attributes.put("items", item_set_array);		
			}catch (Exception e) {
				logger.error(e.toString());
			}
			
			if(attributes.containsKey("own") && address.equals(projectInfo.owner)){
				String rsa_prv_key=null;
				try{
					JSONObject rsaKeyMap=Util.getRSAKeys(projectInfo.owner,false);
					if(rsaKeyMap!=null)
						rsa_prv_key=RSACoder.getPrivateKey(rsaKeyMap);
				}catch(Exception e){
					logger.error(e.toString());
				}
				
				//View recent backers for the project owner
				Database db = Database.getInstance();
				ResultSet rs = db.executeQuery("select bk.backer,bk.email,bk.tx_hash ,bk.tx_index ,bk.back_price_nbc,bk.validity ,bk.block_index,bk.project_tx_index,transactions.block_time from crowdfunding_backers bk,transactions where bk.project_tx_index="+projectInfo.txIndex+" and bk.tx_index=transactions.tx_index order by bk.block_index desc, bk.tx_index limit 100;");
				ArrayList<HashMap<String, Object>> project_backs = new ArrayList<HashMap<String, Object>>();
				try {
					while ( rs.next()) {
						map = new HashMap<String,Object>();
						
						Long back_item_price_nbc=rs.getLong("back_price_nbc");
						map.put("backer", rs.getString("backer"));
						map.put("tx_index", rs.getString("tx_index"));
						map.put("tx_hash", rs.getString("tx_hash"));
						map.put("item_price", BigInteger.valueOf(back_item_price_nbc).doubleValue()/Config.nbc_unit.doubleValue()); 
						map.put("validity", rs.getString("validity"));
						map.put("block_index", rs.getString("block_index"));
						map.put("block_time", Util.timeFormat(rs.getInt("block_time")));

						try{
							JSONObject project_set = projectInfo.projectSet; 
							
							String emailStr=rs.getString("email");
							if(project_set.has("rsa_pub") && rsa_prv_key!=null) {
								try{
									emailStr=new String(
										RSACoder.decryptByPrivateKey(Coder.decryptBASE64(emailStr), rsa_prv_key)
										);
								}catch(Exception e){
									logger.error("Failed to decrypt back contact:"+e.toString());
									emailStr="(encypted contact)";
								}
							} 
							map.put("email", emailStr);
							
							JSONArray  item_sets = project_set.getJSONArray("item_sets");
							ArrayList<HashMap<String, Object>> item_set_array = new ArrayList<HashMap<String, Object>>();
							for(int tt=0;tt<item_sets.length();tt++){
								JSONObject item_obj=(JSONObject)item_sets.get(tt);
								
								if(item_obj.getLong("price")==back_item_price_nbc){
									map.put("item_label", HtmlRegexpUtil.filterHtml(item_obj.getString("label")));
									break;
								}
							}
							
							project_backs.add(map);
						}catch (Exception e) {
							logger.error(e.toString());
						}
					}
				} catch (SQLException e) {
				}
				
				attributes.put("project_backs", project_backs);
			}
			
		}					
		
        attributes.put("LANG_LEFT", Language.getLangLabel("Left"));
        attributes.put("LANG_VIEW", Language.getLangLabel("View"));
        attributes.put("LANG_PROJECT_EXPIRED", Language.getLangLabel("project expired and pending resolved."));
        attributes.put("LANG_SUCCESSFULLY_FUNDED", Language.getLangLabel("Successfully funded!"));
        attributes.put("LANG_FAILED", Language.getLangLabel("Failed!"));
        attributes.put("LANG_CANCELED", Language.getLangLabel("Canceled"));
        attributes.put("LANG_TOTAL", Language.getLangLabel("Total"));
        attributes.put("LANG_OF", Language.getLangLabel("of"));                
        attributes.put("LANG_BACKERS", Language.getLangLabel("Backers"));
        attributes.put("LANG_PROJECT_BY", Language.getLangLabel("Project by"));
        attributes.put("LANG_CREATED_TIME", Language.getLangLabel("Created time"));
        
        attributes.put("LANG_LOGO", Language.getLangLabel("Logo"));
        attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
        attributes.put("LANG_TIME", Language.getLangLabel("Time"));
        attributes.put("LANG_TITLE", Language.getLangLabel("Title"));
        attributes.put("LANG_OWNER", Language.getLangLabel("Owner"));
        attributes.put("LANG_FUNDED", Language.getLangLabel("Funded"));
        attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
        
        attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
        attributes.put("LANG_BACKED_ITEM", Language.getLangLabel("Backed Item"));        
        attributes.put("LANG_BACKER", Language.getLangLabel("Backer"));        
        attributes.put("LANG_VALID", Language.getLangLabel("valid"));        
        attributes.put("LANG_SUCCESS", Language.getLangLabel("success"));
        attributes.put("LANG_REFUNDED", Language.getLangLabel("refunded"));
        attributes.put("LANG_PROJECT_FAILED", Language.getLangLabel("project failed"));
        attributes.put("LANG_PROJECT_CANCELED", Language.getLangLabel("project canceled"));
        
        attributes.put("LANG_INVALID", Language.getLangLabel("Invalid"));  
        attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
        attributes.put("LANG_WEBSITE", Language.getLangLabel("Website"));  
        attributes.put("LANG_INTRODUCTION", Language.getLangLabel("Introduction"));  
        attributes.put("LANG_RECENT_BACKERS", Language.getLangLabel("Recent backers"));  
        attributes.put("LANG_PLEDGE", Language.getLangLabel("Pledge"));  
        attributes.put("LANG_LIMITED", Language.getLangLabel("Limited"));  
        attributes.put("LANG_LEFT_OF", Language.getLangLabel("left of"));  
        attributes.put("LANG_YOUR_BACK_IS_PENDING", Language.getLangLabel("Your back is pending"));  
        attributes.put("LANG_YOU_BACKED_IT", Language.getLangLabel("You backed it"));  
        attributes.put("LANG_FILLED", Language.getLangLabel("filled"));  
        attributes.put("LANG_BACK_IT", Language.getLangLabel("Back it!"));  
        attributes.put("LANG_INPUT_YOUR_CONTACT", Language.getLangLabel("Please input your contact here"));  
        attributes.put("LANG_YOUR_EMAIL", Language.getLangLabel("Your email"));  
	attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        attributes.put("LANG_THE_CREATER_WOULD_CONTACT_YOU", Language.getLangLabel("While the project reach goal,the creater would contact you by email for more informations such as delivery address if need."));  
        attributes.put("LANG_CANCEL", Language.getLangLabel("Cancel"));  
        attributes.put("LANG_SUBMIT", Language.getLangLabel("Submit"));  
        
		return attributes;
	}
}