import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.setPort;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
	
	public Map<String, Object> updateChatStatus(Request request, Map<String, Object> attributes) {
		if (request.session().attributes().contains("chat_open")) {
			attributes.put("chat_open", request.session().attribute("chat_open"));
		} else {
			attributes.put("chat_open", 1);
		}
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
				return Util.nbcSupply().toString();
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
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateChatStatus(request, attributes);
				attributes.put("title", "A coin for betting in a decentralized casino");

				Blocks blocks = Blocks.getInstance();
				
				if (request.queryParams().contains("reparse")) {
					blocks.reparse();
				}
				
				attributes.put("blocksBTC", blocks.getHeight());
				attributes.put("blocksNBC", Util.getLastBlock());
				attributes.put("version", Config.version);
				attributes.put("news_url", Config.newsUrl);
				attributes.put("min_version", Util.getMinVersion());
				attributes.put("min_version_major", Util.getMinMajorVersion());
				attributes.put("min_version_minor", Util.getMinMinorVersion());
				attributes.put("version_major", Config.majorVersion);
				attributes.put("version_minor", Config.minorVersion);
				blocks.versionCheck();
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
					map.put("balance_NBC", Util.getBalance(addr, "NBC").floatValue() / Config.unit.floatValue());
					addresses.add(map);
				}
				attributes.put("address", address);				
				attributes.put("addresses", addresses);
				for (ECKey key : blocks.wallet.getKeys()) {
					if (key.toAddress(blocks.params).toString().equals(address)) {
						attributes.put("own", true);
					}
				}
				
				Database db = Database.getInstance();
				ResultSet rs = db.executeQuery("select address,amount as balance,amount*100.0/(select sum(amount) from balances) as share from balances where asset='NBC' group by address order by amount desc limit 10;");
				ArrayList<HashMap<String, Object>> balances = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("address", rs.getString("address"));
						map.put("balance", BigInteger.valueOf(rs.getLong("balance")).doubleValue()/Config.unit.doubleValue());
						map.put("share", rs.getDouble("share"));
						balances.add(map);
					}
				} catch (SQLException e) {
				}
				attributes.put("balances", balances);
				
				rs = db.executeQuery("select bets.source as source,bet,bet_bs,profit,bets.tx_hash as tx_hash,rolla,rollb,roll,resolved,bets.tx_index as tx_index,bets.block_index,transactions.block_time from bets,transactions where bets.validity='valid' and bets.tx_index=transactions.tx_index order by bets.block_index desc, bets.tx_index desc limit 10;");
				ArrayList<HashMap<String, Object>> bets = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("source", rs.getString("source"));
						map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.unit.doubleValue());
						map.put("bet_bs", rs.getShort("bet_bs"));
						map.put("tx_hash", rs.getString("tx_hash"));
						map.put("roll", rs.getDouble("roll"));
						map.put("resolved", rs.getString("resolved"));
                        map.put("block_index", rs.getString("block_index"));
						map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
						map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.unit.doubleValue());
						bets.add(map);
					}
				} catch (SQLException e) {
				}
				attributes.put("bets", bets);

				return modelAndView(attributes, "index.html");
			}
		});
		get(new FreeMarkerRoute("/participate") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateChatStatus(request, attributes);
				attributes.put("title", "Participate");
				attributes.put("version", Config.version);
				attributes.put("min_version", Util.getMinVersion());
				attributes.put("min_version_major", Util.getMinMajorVersion());
				attributes.put("min_version_minor", Util.getMinMinorVersion());
				attributes.put("version_major", Config.majorVersion);
				attributes.put("version_minor", Config.minorVersion);
				Blocks.getInstance().versionCheck();
				if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
				return modelAndView(attributes, "participate.html");
			}
		});
		get(new FreeMarkerRoute("/community") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateChatStatus(request, attributes);
				attributes.put("title", "Community");
				attributes.put("version", Config.version);
				attributes.put("min_version", Util.getMinVersion());
				attributes.put("min_version_major", Util.getMinMajorVersion());
				attributes.put("min_version_minor", Util.getMinMinorVersion());
				attributes.put("version_major", Config.majorVersion);
				attributes.put("version_minor", Config.minorVersion);
				Blocks.getInstance().versionCheck();
				if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
				return modelAndView(attributes, "community.html");
			}
		});
		get(new FreeMarkerRoute("/technical") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
                
                Integer btcBlockHeight=Blocks.getInstance().getHeight();
                Integer lastBlock = Util.getLastBlock();
                
				attributes = updateChatStatus(request, attributes);
				attributes.put("title", "Technical");
				attributes.put("version", Config.version);
				attributes.put("min_version", Util.getMinVersion());	
				attributes.put("min_version_major", Util.getMinMajorVersion());
				attributes.put("min_version_minor", Util.getMinMinorVersion());
				attributes.put("version_major", Config.majorVersion);
				attributes.put("version_minor", Config.minorVersion);
				Blocks.getInstance().versionCheck();
				if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
				attributes.put("house_edge", Config.houseEdge);
				attributes.put("house_address", Config.houseAddressFund);
				attributes.put("burn_address_fund", Config.burnAddressFund);
                attributes.put("burn_address_dark", Config.burnAddressDark);
				attributes.put("max_burn", Config.maxBurn);
				attributes.put("pob_trial_start_block", Config.pobTrialStartBlock);
				attributes.put("pob_trial_end_block", Config.pobTrialEndBlock);
                attributes.put("pob_trial_multiplier", Config.pobTrialMultiplier);
                attributes.put("pob_max_start_block", Config.pobMaxStartBlock);
				attributes.put("pob_max_end_block", Config.pobMaxEndBlock);
                attributes.put("pob_max_multiplier", Config.pobMaxMultiplier);
                attributes.put("pob_down_start_block", Config.pobDownStartBlock);
				attributes.put("pob_down_end_block", Config.pobDownEndBlock);
				attributes.put("pob_down_init_multiplier", Config.pobDownInitMultiplier);
				attributes.put("pob_down_end_multiplier", Config.pobDownEndMultiplier);
				attributes.put("burned_BTC", Util.btcBurned(null).doubleValue()/Config.unit.doubleValue());
				attributes.put("burned_NBC", Util.nbcBurned(null).doubleValue()/Config.unit.doubleValue());
                attributes.put("burned_BTC_fund", Util.btcBurned(Config.burnAddressFund).doubleValue()/Config.unit.doubleValue());
				attributes.put("burned_NBC_fund", Util.nbcBurned(Config.burnAddressFund).doubleValue()/Config.unit.doubleValue());
                attributes.put("burned_BTC_dark", Util.btcBurned(Config.burnAddressDark).doubleValue()/Config.unit.doubleValue());
				attributes.put("burned_NBC_dark", Util.nbcBurned(Config.burnAddressDark).doubleValue()/Config.unit.doubleValue());
                
                if( btcBlockHeight>=Config.pobTrialStartBlock && btcBlockHeight<=Config.pobDownEndBlock) 
                    attributes.put("burn_status","ACTIVE" );
                else if( btcBlockHeight < Config.pobTrialStartBlock )
                    attributes.put("burn_status","WAITING" );
                else
                    attributes.put("burn_status","COMPLETED" );
                
                attributes.put("pos_first_block", Config.posFirstBlock);
				attributes.put("pos_end_block", Config.posEndBlock);
                attributes.put("pos_wait_blocks", Config.posWaitBlocks);
                attributes.put("pos_interest", Config.posInterest*100);
                
                if( btcBlockHeight>=Config.posFirstBlock && btcBlockHeight<=Config.posEndBlock) 
                    attributes.put("pos_status","ACTIVE" );
                else if( btcBlockHeight < Config.posFirstBlock )
                    attributes.put("pos_status","WAITING" );
                else
                    attributes.put("pos_status","COMPLETED" );
                
				return modelAndView(attributes, "technical.html");
			}
		});
		get(new FreeMarkerRoute("/balances") {
			@Override
			public ModelAndView handle(Request request, Response response) {
				setConfiguration(configuration);
				Map<String, Object> attributes = new HashMap<String, Object>();
				request.session(true);
				attributes = updateChatStatus(request, attributes);
				attributes.put("title", "Balances");
				attributes.put("version", Config.version);
				attributes.put("min_version", Util.getMinVersion());
				attributes.put("min_version_major", Util.getMinMajorVersion());
				attributes.put("min_version_minor", Util.getMinMinorVersion());
				attributes.put("version_major", Config.majorVersion);
				attributes.put("version_minor", Config.minorVersion);
				Blocks.getInstance().versionCheck();
				if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
				Database db = Database.getInstance();
				ResultSet rs = db.executeQuery("select address,amount as balance,amount*100.0/(select sum(amount) from balances) as share from balances where asset='NBC' group by address order by amount desc;");
				ArrayList<HashMap<String, Object>> balances = new ArrayList<HashMap<String, Object>>();
				try {
					while (rs.next()) {
						HashMap<String,Object> map = new HashMap<String,Object>();
						map.put("address", rs.getString("address"));
						map.put("balance", BigInteger.valueOf(rs.getLong("balance")).doubleValue()/Config.unit.doubleValue());
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
		attributes = updateChatStatus(request, attributes);
		attributes.put("title", "Exchange");
		
		Blocks blocks = Blocks.getInstance();
		attributes.put("blocksBTC", blocks.getHeight());
		attributes.put("blocksNBC", Util.getLastBlock());
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
			map.put("balance_NBC", Util.getBalance(addr, "NBC").floatValue() / Config.unit.floatValue());
			addresses.add(map);
		}
		attributes.put("address", address);				
		attributes.put("addresses", addresses);		
		for (ECKey key : blocks.wallet.getKeys()) {
			if (key.toAddress(blocks.params).toString().equals(address)) {
				attributes.put("own", true);
			}
		}		
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("cancel")) {
			String txHash = request.queryParams("tx_hash");
			try {
				Transaction tx = Cancel.create(txHash);
				blocks.sendTransaction(tx);
				attributes.put("success", "Your request for canceling order has been submited.Please wait confirms for about 6 blocks.");
			} catch (Exception e) {
				attributes.put("error", e.getMessage());
			}
		}
		if (request.queryParams().contains("form") && request.queryParams("form").equals("btcpay")) {
			String orderMatchId = request.queryParams("order_match_id");
			try {
				Transaction tx = BTCPay.create(orderMatchId);
				blocks.sendTransaction(tx);
				attributes.put("success", "Your payment had been submited.Please wait confirms for about 6 blocks.");
			} catch (Exception e) {
				attributes.put("error", e.getMessage());
			}
		}
		if (request.queryParams().contains("form") && request.queryParams("form").equals("buy")) {
			String source = request.queryParams("source");
			Double price = Double.parseDouble(request.queryParams("price"));
			Double rawQuantity = Double.parseDouble(request.queryParams("quantity"));
			BigInteger quantity = new BigDecimal(rawQuantity*Config.unit).toBigInteger();
			BigInteger btcQuantity = new BigDecimal(quantity.doubleValue() * price).toBigInteger();
			BigInteger expiration = BigInteger.valueOf(Long.parseLong(request.queryParams("expiration")));
			try {
				Transaction tx = Order.create(source, "BTC", btcQuantity, "NBC", quantity, expiration, BigInteger.ZERO, BigInteger.ZERO);
				blocks.sendTransaction(tx);
				attributes.put("success", "Your order of buying NEWB had been submited.Please wait confirms for about 6 blocks.");
			} catch (Exception e) {
				attributes.put("error", e.getMessage());
			}					
		}
		if (request.queryParams().contains("form") && request.queryParams("form").equals("sell")) {
			String source = request.queryParams("source");
			Double price = Double.parseDouble(request.queryParams("price"));
			Double rawQuantity = Double.parseDouble(request.queryParams("quantity"));
			BigInteger quantity = new BigDecimal(rawQuantity*Config.unit).toBigInteger();
			BigInteger btcQuantity = new BigDecimal(quantity.doubleValue() * price).toBigInteger();
			BigInteger expiration = BigInteger.valueOf(Long.parseLong(request.queryParams("expiration")));
			try {
				Transaction tx = Order.create(source, "NBC", quantity, "BTC", btcQuantity, expiration, BigInteger.ZERO, BigInteger.ZERO);
				blocks.sendTransaction(tx);
				attributes.put("success", "Your order of selling NEWB had been submited.Please wait confirms for about 6 blocks.");
			} catch (Exception e) {
				attributes.put("error", e.getMessage());
			}
		}

		attributes.put("balanceNBC", Util.getBalance(address, "NBC").doubleValue() / Config.unit.doubleValue());
		attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.unit.doubleValue());
		
		Database db = Database.getInstance();
		
		//get buy orders
		ResultSet rs = db.executeQuery("select 1.0*give_amount/get_amount as price, get_remaining as quantity,tx_hash from orders where get_asset='NBC' and give_asset='BTC' and validity='valid' and give_remaining>0 and get_remaining>0 order by price desc, quantity desc;");
		ArrayList<HashMap<String, Object>> ordersBuy = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("quantity", BigInteger.valueOf(rs.getLong("quantity")).doubleValue()/Config.unit.doubleValue());
				map.put("price", rs.getDouble("price"));
				map.put("tx_hash", rs.getString("tx_hash"));
				ordersBuy.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("orders_buy", ordersBuy);				
		
		//get sell orders
		rs = db.executeQuery("select 1.0*get_amount/give_amount as price, give_remaining as quantity,tx_hash from orders where give_asset='NBC' and get_asset='BTC' and validity='valid' and give_remaining>0 and get_remaining>0 order by price desc, quantity asc;");
		ArrayList<HashMap<String, Object>> ordersSell = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("quantity", BigInteger.valueOf(rs.getLong("quantity")).doubleValue()/Config.unit.doubleValue());
				map.put("price", rs.getDouble("price"));
				map.put("tx_hash", rs.getString("tx_hash"));
				ordersSell.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("orders_sell", ordersSell);				

		//get my orders
		rs = db.executeQuery("select * from orders where source='"+address+"' order by block_index desc, tx_index desc;");
		ArrayList<HashMap<String, Object>> myOrders = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				if (rs.getString("get_asset").equals("NBC")) {
					map.put("buysell", "Buy");
					map.put("price", rs.getDouble("give_amount")/rs.getDouble("get_amount"));
					map.put("quantity_nbc", BigInteger.valueOf(rs.getLong("get_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_btc", BigInteger.valueOf(rs.getLong("give_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_remaining_nbc", BigInteger.valueOf(rs.getLong("get_remaining")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_remaining_btc", BigInteger.valueOf(rs.getLong("give_remaining")).doubleValue()/Config.unit.doubleValue());
				} else {
					map.put("buysell", "Sell");
					map.put("price", rs.getDouble("get_amount")/rs.getDouble("give_amount"));
					map.put("quantity_nbc", BigInteger.valueOf(rs.getLong("give_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_btc", BigInteger.valueOf(rs.getLong("get_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_remaining_nbc", BigInteger.valueOf(rs.getLong("give_remaining")).doubleValue()/Config.unit.doubleValue());
					map.put("quantity_remaining_btc", BigInteger.valueOf(rs.getLong("get_remaining")).doubleValue()/Config.unit.doubleValue());
				}
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("validity", rs.getString("validity"));
				myOrders.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_orders", myOrders);				

		//get my order matches
		rs = db.executeQuery("select * from order_matches where ((tx0_address='"+address+"' and forward_asset='BTC') or (tx1_address='"+address+"' and backward_asset='BTC')) and validity='pending' order by tx0_block_index desc, tx0_index desc;");
		ArrayList<HashMap<String, Object>> myOrderMatches = new ArrayList<HashMap<String, Object>>();
		try {
			while (rs.next()) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				if (rs.getString("forward_asset").equals("BTC")) {
					map.put("btc_owed", BigInteger.valueOf(rs.getLong("forward_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("nbc_return", BigInteger.valueOf(rs.getLong("backward_amount")).doubleValue()/Config.unit.doubleValue());
				} else {
					map.put("nbc_return", BigInteger.valueOf(rs.getLong("forward_amount")).doubleValue()/Config.unit.doubleValue());
					map.put("btc_owed", BigInteger.valueOf(rs.getLong("backward_amount")).doubleValue()/Config.unit.doubleValue());
				}
				map.put("order_match_id", rs.getString("id"));
				myOrderMatches.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("my_order_matches", myOrderMatches);	

		return attributes;		
	}
	
	public Map<String, Object> handleWalletRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateChatStatus(request, attributes);
		attributes.put("title", "Wallet");
		
		Blocks blocks = Blocks.getInstance();
		attributes.put("blocksBTC", blocks.getHeight());
		attributes.put("blocksNBC", Util.getLastBlock());
		attributes.put("version", Config.version);
		attributes.put("min_version", Util.getMinVersion());
		attributes.put("min_version_major", Util.getMinMajorVersion());
		attributes.put("min_version_minor", Util.getMinMinorVersion());
		attributes.put("version_major", Config.majorVersion);
		attributes.put("version_minor", Config.minorVersion);
		Blocks.getInstance().versionCheck();
		if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
		
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
				attributes.put("success", "Your private key has been deleted. You can no longer transact from this address.");							
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
				blocks.importPrivateKey(importKey.toString());
				attributes.put("success", "Your transactions have been reimported.");
			}
		}
		
		String address = Util.getAddresses().get(0);
		Boolean isMyAddress = false;
		
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
			map.put("balance_NBC", Util.getBalance(addr, "NBC").floatValue() / Config.unit.floatValue());
			addresses.add(map);
			
			if(address.equals(addr))
				isMyAddress=true;
		}
		attributes.put("address", address);				
		attributes.put("addresses", addresses);				
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("import")) {
			String privateKey = request.queryParams("privatekey");
			
			if(privateKey.length()==0){
				attributes.put("error", "Please input the private key string that you want to import.");
			} else {
				address = Blocks.getInstance().importPrivateKey(privateKey);
				request.session().attribute("address", address);
				attributes.put("success", "Your private key has been imported.");
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
				attributes.put("error", "Please input a valid destination address  that you want to send.");
			}else if(quantityStr.length()==0){
				attributes.put("error", "Please input the NEWB amount that you want to send.");
			} else {
				try {
					Double rawQuantity = Double.parseDouble(quantityStr);
					BigInteger quantity = new BigDecimal(rawQuantity*Config.unit).toBigInteger();
				
					Transaction tx = Send.create(source, destination, "NBC", quantity);
					blocks.sendTransaction(tx);
					attributes.put("success", "Your request of sending NEWB had been submited.Please wait confirms for about 6 blocks.");
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			}
		}
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("burn")) {
			String source = request.queryParams("source");
			String destination = request.queryParams("destination");
			String quantityStr=request.queryParams("quantity");
			if(quantityStr.length()>0){
				try {
					Double rawQuantity = Double.parseDouble(request.queryParams("quantity"));
					BigInteger quantity = new BigDecimal(rawQuantity*Config.unit).toBigInteger();
				
					Transaction tx = Burn.create(source, destination, quantity);
					blocks.sendTransaction(tx);
					attributes.put("success", "Your request of burning BTC had been submited.Please wait confirms for about 6 blocks.");
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			} else {
				attributes.put("error", "Please input the BTC amount that you want to burn.");
			}
		}

		attributes.put("balanceNBC", Util.getBalance(address, "NBC").doubleValue() / Config.unit.doubleValue());
		attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.unit.doubleValue());

		if(isMyAddress){
			attributes.put("is_my_wallet", "My");	
		} else {
			attributes.put("is_my_wallet", "His");	
		}
		
		Integer btcBlockHeight=Blocks.getInstance().getHeight();
		if( btcBlockHeight>=Config.pobTrialStartBlock && btcBlockHeight<Config.pobDownEndBlock) {
			attributes.put("is_burning","ACTIVE" );
			attributes.put("min_burn_btc", new Double(Config.dustSize.doubleValue() / Config.unit));
			attributes.put("burn_address_fund", Config.burnAddressFund);
			attributes.put("burn_address_dark", Config.burnAddressDark);
		}
		
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
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.unit.doubleValue());
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
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
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
				map.put("burned", BigInteger.valueOf(rs.getLong("burned")).doubleValue()/Config.unit.doubleValue());
				map.put("earned", BigInteger.valueOf(rs.getLong("earned")).doubleValue()/Config.unit.doubleValue());
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
				map.put("amount", BigInteger.valueOf(rs.getLong("amount")).doubleValue()/Config.unit.doubleValue());
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
				map.put("burned", BigInteger.valueOf(rs.getLong("burned")).doubleValue()/Config.unit.doubleValue());
				map.put("earned", BigInteger.valueOf(rs.getLong("earned")).doubleValue()/Config.unit.doubleValue());
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("source", rs.getString("source"));
				burns.add(map);
			}
		} catch (SQLException e) {
		}
		attributes.put("burns", burns);				
		*/
		
		return attributes;
	}
	
	
	public Map<String, Object> handleCasinoRequest(Request request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		request.session(true);
		attributes = updateChatStatus(request, attributes);
		attributes.put("title", "Casino");
		
		Blocks blocks = Blocks.getInstance();
		Integer lastBtcBlock=blocks.getHeight();
		Integer lastNbcBlock=Util.getLastBlock();
		attributes.put("blocksBTC", lastBtcBlock);
		attributes.put("blocksNBC", lastNbcBlock);
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
			map.put("balance_NBC", Util.getBalance(addr, "NBC").floatValue() / Config.unit.floatValue());
			addresses.add(map);
		}
		attributes.put("address", address);				
		attributes.put("addresses", addresses);
		for (ECKey key : blocks.wallet.getKeys()) {
			if (key.toAddress(blocks.params).toString().equals(address)) {
				attributes.put("own", true);
			}
		}
		
		if (request.queryParams().contains("form") && request.queryParams("form").equals("bet")) {
			String source = request.queryParams("source");
			
			String betStr=request.queryParams("bet");
			if(betStr.length()>0){
				try {
					Double rawBet = Double.parseDouble(betStr);
					Short bigORsmall = Short.parseShort(request.queryParams("big_or_small"));
					BigInteger bet = new BigDecimal(rawBet*Config.unit).toBigInteger();
				
					Transaction tx = Bet.create(source,  bet, bigORsmall);
					blocks.sendTransaction(tx);
					attributes.put("success", "Thank you for betting! Your request had been submited.Please wait confirms for about 6 blocks.");
				} catch (Exception e) {
					attributes.put("error", e.getMessage());
				}
			} else {
				attributes.put("error", "Please input the NEWB amount that you want to bet.");
			}
		}
		
		Integer currentBetStartBlock=lastBtcBlock-(lastBtcBlock-Config.betStartBlock) % Config.betPeriodBlocks;
		Integer currentBetEndBlock=currentBetStartBlock+Config.betPeriodBlocks-1;
		Integer prevBetStartBlock=currentBetStartBlock-Config.betPeriodBlocks;
		Integer prevBetEndBlock=currentBetStartBlock-1;
		
		attributes.put("betting_start_block", currentBetStartBlock);
		attributes.put("betting_end_block", currentBetEndBlock);
		attributes.put("prev_bet_start_block", prevBetStartBlock);
		attributes.put("prev_bet_end_block", prevBetEndBlock);
		
		if(lastNbcBlock>prevBetEndBlock+Config.betResolveWaitBlocks)
			attributes.put("prev_bet_status", "Resolved");
		else
			attributes.put("prev_bet_status", "Pending resolved");
		
		Database db = Database.getInstance();
		
		//get house info
		ResultSet rs = db.executeQuery("select count(amount) as fee_count, sum(amount) as sum_fee, avg(amount) as avg_fee from credits where address='"+Config.houseAddressFund+"' and asset='NBC' and calling_function='"+Config.houseFunctionName+"';");
		
		try {
			if(rs.next()){
				BigInteger totalHouseFee=BigInteger.valueOf(rs.getLong("sum_fee"));
				attributes.put("house_times", rs.getInt("fee_count"));
				attributes.put("total_house_fee", totalHouseFee.floatValue() / Config.unit.floatValue());
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
				map.put("avg_bet", BigInteger.valueOf(rs.getLong("avg_bet")).doubleValue()/Config.unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.unit.doubleValue());
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
				map.put("sum_bet", BigInteger.valueOf(rs.getLong("sum_bet")).doubleValue()/Config.unit.doubleValue());
				//map.put("avg_newbie", rs.getDouble("avg_newbie"));
				map.put("sum_profit", BigInteger.valueOf(rs.getLong("sum_profit")).doubleValue()/Config.unit.doubleValue());
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
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.unit.doubleValue());
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
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.unit.doubleValue());
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
				map.put("bet", BigInteger.valueOf(rs.getLong("bet")).doubleValue()/Config.unit.doubleValue());
				map.put("bet_bs", rs.getShort("bet_bs"));
				map.put("tx_hash", rs.getString("tx_hash"));
				map.put("roll", rs.getDouble("roll"));
				map.put("resolved", rs.getString("resolved"));	
				map.put("block_index", rs.getString("block_index"));
				map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
				map.put("profit", BigInteger.valueOf(rs.getLong("profit")).doubleValue()/Config.unit.doubleValue());
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
			map.put("bet", betInfo.bet.doubleValue()/Config.unit.doubleValue());
			map.put("bet_bs", betInfo.bet_bs);
			map.put("tx_hash", betInfo.txHash);
			map.put("block_index", betInfo.blockIndex.toString());
			map.put("block_time", Util.timeFormat(betInfo.blockTime));
			bets.add(map);
		}
		attributes.put("my_bets_pending", bets);

		return attributes;
	}
}