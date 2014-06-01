import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.text.NumberFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.PeerGroup.FilterRecalculateMode;
import com.google.bitcoin.core.Transaction.SigHash;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.net.discovery.DnsDiscovery;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.script.ScriptChunk;
import com.google.bitcoin.script.ScriptOpCodes;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.H2FullPrunedBlockStore;
import com.google.bitcoin.wallet.WalletTransaction;
import com.google.common.util.concurrent.ListenableFuture;
import com.sun.org.apache.xpath.internal.compiler.OpCodes;

public class Blocks implements Runnable {
	public NetworkParameters params;
	public Logger logger = LoggerFactory.getLogger(Blocks.class);
	private static Blocks instance = null;
	public Wallet wallet;
	public String walletFile = "resources/db/wallet";
	public PeerGroup peerGroup;
	public BlockChain blockChain;
	public BlockStore blockStore;
	public Boolean working = false;
	public Boolean parsing = false;
	public Integer parsingBlock = 0;
	public Integer versionCheck = 0;
	public Integer bitcoinBlock = 0;
	public Integer newbiecoinBlock = 0;
	public String statusMessage = "";

	public static Blocks getInstanceSkipVersionCheck() {
		if(instance == null) {
			instance = new Blocks();
		} 
		return instance;
	}

	public static Blocks getInstanceFresh() {
		if(instance == null) {
			instance = new Blocks();
			instance.versionCheck();
		} 
		return instance;
	}

	public static Blocks getInstanceAndWait() {
		if(instance == null) {
			instance = new Blocks();
			instance.versionCheck();
			instance.init();
		} 
		instance.follow();
		return instance;
	}

	public static Blocks getInstance() {
		if(instance == null) {
			instance = new Blocks();
			instance.versionCheck();
			instance.init();
		} 
		new Thread() { public void run() {instance.follow();}}.start();
		return instance;
	}

	public void versionCheck() {
		Integer minMajorVersion = Util.getMinMajorVersion();
		Integer minMinorVersion = Util.getMinMinorVersion();
		if (Config.majorVersion<minMajorVersion || (Config.majorVersion.equals(minMajorVersion) && Config.minorVersion<minMinorVersion)) {
			statusMessage = "Version is out of date, updating now"; 
			logger.info(statusMessage);
			try {
				Runtime.getRuntime().exec("java -jar update/update.jar");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			System.exit(0);
		}
	}

	@Override
	public void run() {
		while (true) {
			Blocks.getInstance();
			try {
				logger.info("Looping blocks");
				Thread.sleep(1000*60); //once a minute, we run blocks.follow()
			} catch (InterruptedException e) {
				logger.info(e.toString());
			}
		}
	}

	public void init() {
		params = MainNetParams.get();
		try {
			if ((new File(walletFile)).exists()) {
				statusMessage = "Found wallet file"; 
				logger.info(statusMessage);
				wallet = Wallet.loadFromFile(new File(walletFile));
			} else {
				statusMessage = "Creating new wallet file"; 
				logger.info(statusMessage);
				wallet = new Wallet(params);
				ECKey newKey = new ECKey();
				newKey.setCreationTimeSeconds(Config.burnCreationTime);
				wallet.addKey(newKey);
			}
			String fileBTCdb = Config.dbPath+Config.appName.toLowerCase()+".h2.db";
			if (!new File(fileBTCdb).exists()) {
				statusMessage = "Downloading BTC database"; 
				logger.info(statusMessage);
				Util.downloadToFile(Config.downloadUrl+Config.appName.toLowerCase()+".h2.db", fileBTCdb);
			}
			String fileNBCdb = Database.dbFile;
			if (!new File(fileNBCdb).exists()) {
				statusMessage = "Downloading "+Config.appName+" database"; 
				logger.info(statusMessage);
				Util.downloadToFile(Config.downloadUrl+Config.appName.toLowerCase()+"-"+Config.majorVersionDB.toString()+".db", fileNBCdb);
			}
			statusMessage = "Downloading Bitcoin blocks";
			blockStore = new H2FullPrunedBlockStore(params, Config.dbPath+Config.appName.toLowerCase(), 2000);
			blockChain = new BlockChain(params, wallet, blockStore);
			peerGroup = new PeerGroup(params, blockChain);
			peerGroup.addWallet(wallet);
			peerGroup.setFastCatchupTimeSecs(Config.burnCreationTime);
			wallet.autosaveToFile(new File(walletFile), 1, TimeUnit.MINUTES, null);
			peerGroup.addPeerDiscovery(new DnsDiscovery(params));
			peerGroup.startAndWait();
			peerGroup.addEventListener(new NewbiecoinPeerEventListener());
			peerGroup.downloadBlockChain();
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}

	public void follow() {
		follow(false);
	}
	public void follow(Boolean force) {
		logger.info("Working status: "+working);
		if (!working || force) {
			statusMessage = "Checking block height";
			logger.info(statusMessage);
			if (!force) {
				working = true;
			}
            
            Integer blockHeight = 0 ;
            Integer lastBlock   = 0 ;
            Integer nextBlock   = 0 ;
            
			try {
				blockHeight = blockStore.getChainHead().getHeight();
				lastBlock = Util.getLastBlock();
                
                statusMessage = 
                "\n++++++++++++++++++++++++++++++++++\n lastBlock = "+lastBlock.toString()+"\n++++++++++++++++++++++++++++++++++";
                logger.info(statusMessage);
                
				if (lastBlock == 0) {
					lastBlock = Config.firstBlock - 1;
				}
				nextBlock = lastBlock + 1;
                
                statusMessage = 
                "lastBlock = "+lastBlock.toString() + "    BtcBlockHeight = "+blockHeight.toString();
                logger.info(statusMessage);

				if (lastBlock < blockHeight) {
					//traverse new blocks
					Database db = Database.getInstance();
					logger.info("Bitcoin block height: "+blockHeight);	
					logger.info("Newbiecoin block height: "+lastBlock);
					bitcoinBlock = blockHeight;
					newbiecoinBlock = lastBlock;
					Integer blocksToScan = blockHeight - lastBlock;
					List<Sha256Hash> blockHashes = new ArrayList<Sha256Hash>();

					Block block = peerGroup.getDownloadPeer().getBlock(blockStore.getChainHead().getHeader().getHash()).get(30, TimeUnit.SECONDS);
					while (blockStore.get(block.getHash()).getHeight()>lastBlock) {
						blockHashes.add(block.getHash());
						block = blockStore.get(block.getPrevBlockHash()).getHeader();
					}

					for (int i = blockHashes.size()-1; i>=0; i--) { //traverse blocks in reverse order
						block = peerGroup.getDownloadPeer().getBlock(blockHashes.get(i)).get(30, TimeUnit.SECONDS);
						blockHeight = blockStore.get(block.getHash()).getHeight();
						newbiecoinBlock = blockHeight;
						statusMessage = "Catching Newbiecoin up to Bitcoin "+Util.format((blockHashes.size() - i)/((double) blockHashes.size())*100.0)+"%";	
						logger.info("Catching Newbiecoin up to Bitcoin (block "+blockHeight.toString()+"): "+Util.format((blockHashes.size() - i)/((double) blockHashes.size())*100.0)+"%");	
						importBlock(block, blockHeight);
					}
				}
			} catch (Exception e) {
                logger.error("---------------------\n------ Follow Loop Failed ------\n-------------------------\n");
				logger.error(e.toString());
			}	
            
            try{
                statusMessage = 
                    "\n++++++++++++++++++++++++++++++++++\n nextBlock = "+nextBlock.toString()+"\n++++++++++++++++++++++++++++++++++" ;
                logger.info(statusMessage);
                if (lastBlock < blockHeight) {
                    if (getDBMinorVersion()<Config.minorVersionDB){
                        statusMessage = 
                            "reparse " + getDBMinorVersion() +" < "+Config.minorVersionDB.toString() ;
                        logger.info(statusMessage);
                        
						reparse(true);
						updateMinorVersion();		    	
					}else{
						parseFrom(nextBlock, true);
					}
					Bet.resolve(blockHeight);
					Order.expire();
                }
            }catch (Exception e) {
				logger.error(e.toString());
			}	
            
			if (!force) {
				working = false;
			}
		}
	}

	public void reDownloadBlockTransactions(Integer blockHeight) {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks where block_index='"+blockHeight.toString()+"';");
		try {
			if (rs.next()) {
				Block block = peerGroup.getDownloadPeer().getBlock(new Sha256Hash(rs.getString("block_hash"))).get();
				db.executeUpdate("delete from transactions where block_index='"+blockHeight.toString()+"';");
				for (Transaction tx : block.getTransactions()) {
					importTransaction(tx, block, blockHeight);
				}
			}
		} catch (Exception e) {

		}
	}

	public void importBlock(Block block, Integer blockHeight) {
		statusMessage = "Importing block "+blockHeight;
		logger.info(statusMessage);
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("select * from blocks where block_hash='"+block.getHashAsString()+"';");
		try {
			if (!rs.next()) {
				db.executeUpdate("INSERT INTO blocks(block_index,block_hash,block_time,block_nonce) VALUES('"+blockHeight.toString()+"','"+block.getHashAsString()+"','"+block.getTimeSeconds()+"','"+block.getNonce()+"')");
			}
			for (Transaction tx : block.getTransactions()) {
				importTransaction(tx, block, blockHeight);
			}
			Bet.resolve(blockHeight); 
			Order.expire();
		} catch (SQLException e) {
		}
	}

	public void importTransaction(Transaction tx, Block block, Integer blockHeight) {
		BigInteger fee = BigInteger.ZERO;
		String destination = "";
		BigInteger btcAmount = BigInteger.ZERO;
		List<Byte> dataArrayList = new ArrayList<Byte>();
		byte[] data = null;
		String source = "";

		Database db = Database.getInstance();

		//check to see if this is a burn ot bet
		for (TransactionOutput out : tx.getOutputs()) {
			try {
				Script script = out.getScriptPubKey();
				Address address = script.getToAddress(params);
				if (address.toString().equals(Config.burnAddressFund) || address.toString().equals(Config.burnAddressDark)) {
					destination = address.toString();
					btcAmount = out.getValue();
				}
			} catch(ScriptException e) {				
			}
		}

		for (TransactionOutput out : tx.getOutputs()) {
			//fee = fee.subtract(out.getValue()); //TODO, turn this on
			try {
				Script script = out.getScriptPubKey();
				List<ScriptChunk> asm = script.getChunks();
				if (asm.size()==2 && asm.get(0).equalsOpCode(106)) { //OP_RETURN
					for (byte b : asm.get(1).data) dataArrayList.add(b);
				} else if (asm.size()>=5 && asm.get(0).equalsOpCode(81) && asm.get(3).equalsOpCode(82) && asm.get(4).equalsOpCode(174)) { //MULTISIG
					for (int i=1; i<asm.get(2).data[0]+1; i++) dataArrayList.add(asm.get(2).data[i]);
				}

				if (destination.equals("") && btcAmount==BigInteger.ZERO && dataArrayList.size()==0) {
					Address address = script.getToAddress(params);
					destination = address.toString();
					btcAmount = out.getValue();					
				}
			} catch(ScriptException e) {				
			}
		}
		if (destination.equals(Config.burnAddressFund) || destination.equals(Config.burnAddressDark)) {
		} else if (dataArrayList.size()>Config.prefix.length()) {
			byte[] prefixBytes = Config.prefix.getBytes();
			byte[] dataPrefixBytes = Util.toByteArray(dataArrayList.subList(0, Config.prefix.length()));
			dataArrayList = dataArrayList.subList(Config.prefix.length(), dataArrayList.size());
			data = Util.toByteArray(dataArrayList);
			if (!Arrays.equals(prefixBytes,dataPrefixBytes)) {
				return;
			}
		} else {
			return;
		}
		for (TransactionInput in : tx.getInputs()) {
			if (in.isCoinBase()) return;
			try {
				Script script = in.getScriptSig();
				//fee = fee.add(in.getValue()); //TODO, turn this on
				Address address = script.getFromAddress(params);
				if (source.equals("")) {
					source = address.toString();
				}else if (!source.equals(address.toString()) 
                       && !destination.equals(Config.burnAddressFund)
                       && !destination.equals(Config.burnAddressDark)
                ){ //require all sources to be the same unless this is a burn
					return;
				}
			} catch(ScriptException e) {
			}
		}

		logger.info("Incoming transaction from "+source+" to "+destination+" ("+tx.getHashAsString()+")");

		if (!source.equals("") && (destination.equals(Config.burnAddressFund) || destination.equals(Config.burnAddressDark) || dataArrayList.size()>0)) {
			String dataString = "";
			if (destination.equals(Config.burnAddressFund) || destination.equals(Config.burnAddressDark)) {
			}else{
				try {
					dataString = new String(data,"ISO-8859-1");
				} catch (UnsupportedEncodingException e) {
				}
			}
			db.executeUpdate("delete from transactions where tx_hash='"+tx.getHashAsString()+"' and block_index<0");
			ResultSet rs = db.executeQuery("select * from transactions where tx_hash='"+tx.getHashAsString()+"';");
			try {
				if (!rs.next()) {
					if (block!=null) {
						PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data) VALUES('"+(Util.getLastTxIndex()+1)+"','"+tx.getHashAsString()+"','"+blockHeight+"','"+block.getTimeSeconds()+"','"+source+"','"+destination+"','"+btcAmount.toString()+"','"+fee.toString()+"',?)");
						ps.setString(1, dataString);
						ps.execute();
					}else{
						PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data) VALUES('"+(Util.getLastTxIndex()+1)+"','"+tx.getHashAsString()+"','-1','','"+source+"','"+destination+"','"+btcAmount.toString()+"','"+fee.toString()+"',?)");
						ps.setString(1, dataString);
						ps.execute();
					}
				}
			} catch (SQLException e) {
				logger.error(e.toString());
			}
		}
	}

	public void reparse() {
		reparse(false);
	}
	public void reparse(final Boolean force) {
		Database db = Database.getInstance();
		db.executeUpdate("delete from debits;");
		db.executeUpdate("delete from credits;");
		db.executeUpdate("delete from balances;");
		db.executeUpdate("delete from sends;");
		db.executeUpdate("delete from orders;");
		db.executeUpdate("delete from order_matches;");
		db.executeUpdate("delete from btcpays;");
		db.executeUpdate("delete from bets;");
		db.executeUpdate("delete from burns;");
		db.executeUpdate("delete from cancels;");
		db.executeUpdate("delete from order_expirations;");
		db.executeUpdate("delete from order_match_expirations;");
		db.executeUpdate("delete from messages;");
		new Thread() { public void run() {parseFrom(0, force);}}.start();
	}

	public void parseFrom(Integer blockNumber) {
		parseFrom(blockNumber, false);
	}
	public void parseFrom(Integer blockNumber, Boolean force) {
		if (!working || force) {
			parsing = true;
			if (!force) {
				working = true;
			}
			Database db = Database.getInstance();
			ResultSet rs = db.executeQuery("select * from blocks where block_index>="+blockNumber.toString()+" order by block_index asc;");
			try {
				while (rs.next()) {
					Integer blockIndex = rs.getInt("block_index");
                    Integer blockTime = rs.getInt("block_time");  //Added for POS
					parseBlock(blockIndex,blockTime);
					Bet.resolve(blockIndex);
					Order.expire(blockIndex);
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!force) {
				working = false;
			}
			parsing = false;
		}
	}

	public List<Byte> getMessageFromTransaction(String txDataString) {
		byte[] data;
		List<Byte> message = null;
		try {
			data = txDataString.getBytes("ISO-8859-1");
			List<Byte> dataArrayList = Util.toByteArrayList(data);

			message = dataArrayList.subList(4, dataArrayList.size());		
			return message;
		} catch (UnsupportedEncodingException e) {
		}
		return message;
	}

	public List<Byte> getMessageTypeFromTransaction(String txDataString) {
		byte[] data;
		List<Byte> messageType = null;
		try {
			data = txDataString.getBytes("ISO-8859-1");
			List<Byte> dataArrayList = Util.toByteArrayList(data);

			messageType = dataArrayList.subList(0, 4);
			return messageType;
		} catch (UnsupportedEncodingException e) {
		}
		return messageType;
	}	

	public void parseBlock(Integer blockIndex,Integer blockTime) { //Add  blockTime for POS
		Database db = Database.getInstance();
		ResultSet rsTx = db.executeQuery("select * from transactions where block_index="+blockIndex.toString()+" order by tx_index asc;");
		parsingBlock = blockIndex;
		statusMessage = "\n++++++++++++++++++++++++++++++++++\n Parsing block "+blockIndex.toString()+"\n++++++++++++++++++++++++++++++++++\n";
		logger.info(statusMessage);
		try {
			while (rsTx.next()) {
				Integer txIndex = rsTx.getInt("tx_index");
				String source = rsTx.getString("source");
				String destination = rsTx.getString("destination");
				BigInteger btcAmount = BigInteger.valueOf(rsTx.getInt("btc_amount"));
				String dataString = rsTx.getString("data");

				if (destination.equals(Config.burnAddressFund) || destination.equals(Config.burnAddressDark)) {
					//parse Burn
					Burn.parse(txIndex);
				} else {
					List<Byte> messageType = getMessageTypeFromTransaction(dataString);
					List<Byte> message = getMessageFromTransaction(dataString);

					if (messageType!=null && messageType.size()>=4 && message!=null) {
						if (messageType.get(3)==Bet.id.byteValue()) {
							Bet.parse(txIndex, message);
						} else if (messageType.get(3)==Send.id.byteValue()) {
							Send.parse(txIndex, message);
						} else if (messageType.get(3)==Order.id.byteValue()) {
							Order.parse(txIndex, message);
						} else if (messageType.get(3)==Cancel.id.byteValue()) {
							Cancel.parse(txIndex, message);
						} else if (messageType.get(3)==BTCPay.id.byteValue()) {
							BTCPay.parse(txIndex, message);
						}						
					}
				}
			}
            
            //POS
            Pos.doPosBlock(blockIndex,blockTime);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public void createTables() {
		Database db = Database.getInstance();
		try {
			// Blocks
			db.executeUpdate("CREATE TABLE IF NOT EXISTS blocks(block_index INTEGER PRIMARY KEY, block_hash TEXT UNIQUE, block_time INTEGER,block_nonce BIGINT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS blocks_block_index_idx ON blocks (block_index)");

			// Transactions
			db.executeUpdate("CREATE TABLE IF NOT EXISTS transactions(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, block_time INTEGER, source TEXT, destination TEXT, btc_amount INTEGER, fee INTEGER, data BLOB, supported BOOL DEFAULT 1)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS transactions_block_index_idx ON transactions (block_index)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS transactions_tx_index_idx ON transactions (tx_index)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS transactions_tx_hash_idx ON transactions (tx_hash)");

			// (Valid) debits
			db.executeUpdate("CREATE TABLE IF NOT EXISTS debits(block_index INTEGER, address TEXT, asset TEXT, amount INTEGER, calling_function TEXT, event TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS debits_address_idx ON debits (address)");

			// (Valid) credits
			db.executeUpdate("CREATE TABLE IF NOT EXISTS credits(block_index INTEGER, address TEXT, asset TEXT, amount INTEGER, calling_function TEXT, event TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS credits_address_idx ON credits (address)");

			// Balances
			db.executeUpdate("CREATE TABLE IF NOT EXISTS balances(address TEXT, asset TEXT, amount INTEGER)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS address_idx ON balances (address)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS asset_idx ON balances (asset)");

			// Sends
			db.executeUpdate("CREATE TABLE IF NOT EXISTS sends(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, destination TEXT, asset TEXT, amount INTEGER, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS sends_block_index_idx ON sends (block_index)");

			// Orders
			db.executeUpdate("CREATE TABLE IF NOT EXISTS orders(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, give_asset TEXT, give_amount INTEGER, give_remaining INTEGER, get_asset TEXT, get_amount INTEGER, get_remaining INTEGER, expiration INTEGER, expire_index INTEGER, fee_required INTEGER, fee_provided INTEGER, fee_remaining INTEGER, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON orders (block_index)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS expire_index_idx ON orders (expire_index)");

			// Order Matches
			db.executeUpdate("CREATE TABLE IF NOT EXISTS order_matches(id TEXT PRIMARY KEY, tx0_index INTEGER, tx0_hash TEXT, tx0_address TEXT, tx1_index INTEGER, tx1_hash TEXT, tx1_address TEXT, forward_asset TEXT, forward_amount INTEGER, backward_asset TEXT, backward_amount INTEGER, tx0_block_index INTEGER, tx1_block_index INTEGER, tx0_expiration INTEGER, tx1_expiration INTEGER, match_expire_index INTEGER, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS match_expire_index_idx ON order_matches (match_expire_index)");

			// BTCpays
			db.executeUpdate("CREATE TABLE IF NOT EXISTS btcpays(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, destination TEXT, btc_amount INTEGER, order_match_id TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON btcpays (block_index)");

			// Bets
			db.executeUpdate("CREATE TABLE IF NOT EXISTS bets(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, bet INTEGER, bet_bs INTEGER,  profit INTEGER, nbc_supply INTEGER, rolla REAL, rollb REAL, roll REAL, resolved TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON bets (block_index)");

			// Burns
			db.executeUpdate("CREATE TABLE IF NOT EXISTS burns(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, burned INTEGER, earned INTEGER, validity TEXT,destination TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS validity_idx ON burns (validity)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS source_idx ON burns (source)");
            db.executeUpdate("CREATE INDEX IF NOT EXISTS dest_idx ON burns (destination)");

			// Cancels
			db.executeUpdate("CREATE TABLE IF NOT EXISTS cancels(tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE, block_index INTEGER, source TEXT, offer_hash TEXT, validity TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS cancels_block_index_idx ON cancels (block_index)");

			// Order Expirations
			db.executeUpdate("CREATE TABLE IF NOT EXISTS order_expirations(order_index INTEGER PRIMARY KEY, order_hash TEXT UNIQUE, source TEXT, block_index INTEGER)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON order_expirations (block_index)");

			// Order Match Expirations
			db.executeUpdate("CREATE TABLE IF NOT EXISTS order_match_expirations(order_match_id TEXT PRIMARY KEY, tx0_address TEXT, tx1_address TEXT, block_index INTEGER)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON order_match_expirations (block_index)");

			// Messages
			db.executeUpdate("CREATE TABLE IF NOT EXISTS messages(message_index INTEGER PRIMARY KEY, block_index INTEGER, command TEXT, category TEXT, bindings TEXT)");
			db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON messages (block_index)");

			updateMinorVersion();
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}

	public Integer getDBMinorVersion() {
		Database db = Database.getInstance();
		ResultSet rs = db.executeQuery("PRAGMA user_version;");
		try {
			while(rs.next()) {
				return rs.getInt("user_version");
			}
		} catch (SQLException e) {
		}	
		return 0;
	}

	public void updateMinorVersion() {
		// Update minor version
		Database db = Database.getInstance();
		db.executeUpdate("PRAGMA user_version = "+Config.minorVersionDB.toString());
	}

	public Integer getHeight() {
		try {
			Integer height = blockStore.getChainHead().getHeight();
			return height;
		} catch (BlockStoreException e) {
		}
		return 0;
	}

	public String importPrivateKey(String privateKey) {
		DumpedPrivateKey dumpedPrivateKey;
		String address = "";
		ECKey key = null;
		logger.info("Importing private key");
		try {
			dumpedPrivateKey = new DumpedPrivateKey(params, privateKey);
			key = dumpedPrivateKey.getKey();
			address = key.toAddress(params).toString();
		} catch (AddressFormatException e) {
			//If it's not a private key, maybe it's an address
			address = privateKey;
			/*
			wallet.addWatchedAddress(new Address(params, address));
			} catch (AddressFormatException e1) {
			}
			 */
		}
		logger.info("Importing address "+address);
		if (key!=null) {
			wallet.removeKey(key);
			wallet.addKey(key);
		}
		List<Map.Entry<String,String>> txsInfo = Util.infoGetTransactions(address);
		BigInteger balance = BigInteger.ZERO;
		BigInteger balanceSent = BigInteger.ZERO;
		BigInteger balanceReceived = BigInteger.ZERO;
		Integer transactionCount = 0;
		for (Map.Entry<String,String> txHashBlockHash : txsInfo) {
			String txHash = txHashBlockHash.getKey();
			String blockHash = txHashBlockHash.getValue();
			try {
				Block block = peerGroup.getDownloadPeer().getBlock(new Sha256Hash(blockHash)).get();
				List<Transaction> txs = block.getTransactions();
				for (Transaction tx : txs) {
					if (tx.getHashAsString().equals(txHash)){// && wallet.isPendingTransactionRelevant(tx)) {
						transactionCount ++;
						wallet.receivePending(tx, peerGroup.getDownloadPeer().downloadDependencies(tx).get());
						balanceReceived = balanceReceived.add(tx.getValueSentToMe(wallet));
						balanceSent = balanceSent.add(tx.getValueSentFromMe(wallet));
						balance = balance.add(tx.getValueSentToMe(wallet));
						balance = balance.subtract(tx.getValueSentFromMe(wallet));
					}
				}
			} catch (InterruptedException e) {
			} catch (ExecutionException e) {				
			}
		}
		logger.info("Address balance: "+balance);
		return address;
	}

	public Transaction transaction(String source, String destination, BigInteger btcAmount, BigInteger fee, String dataString) throws Exception {
        logger.info("\n=================================\n Do new transaction\n source="+source+"\n destination="+destination+"\n btcAmount="+btcAmount.toString()+"\n fee="+fee.toString());
        
		Transaction tx = new Transaction(params);
		LinkedList<TransactionOutput> unspentOutputs = wallet.calculateAllSpendCandidates(true);
		if (destination.equals("") || btcAmount.compareTo(BigInteger.valueOf(Config.dustSize))>=0) {

			byte[] data = null;
			List<Byte> dataArrayList = new ArrayList<Byte>();
			try {
				data = dataString.getBytes("ISO-8859-1");
				dataArrayList = Util.toByteArrayList(data);
			} catch (UnsupportedEncodingException e) {
			}

			BigInteger totalOutput = fee;
			BigInteger totalInput = BigInteger.ZERO;

			try {
				if (!destination.equals("") && btcAmount.compareTo(BigInteger.ZERO)>0) {
					totalOutput = totalOutput.add(btcAmount);
					tx.addOutput(btcAmount, new Address(params, destination));
				}
			} catch (AddressFormatException e) {
			}
            
            logger.info("\n***********************************\n dataArrayList.size() =  "+dataArrayList.size()+"  \n***********************************");

			for (int i = 0; i < dataArrayList.size(); i+=32) {
				List<Byte> chunk = new ArrayList<Byte>(dataArrayList.subList(i, Math.min(i+32, dataArrayList.size())));
				chunk.add(0, (byte) chunk.size());
				while (chunk.size()<32+1) {
					chunk.add((byte) 0);
				}
				List<ECKey> keys = new ArrayList<ECKey>();
				for (ECKey key : wallet.getKeys()) {
					try {
						if (key.toAddress(params).equals(new Address(params, source))) {
							keys.add(key);
							break;
						}
					} catch (AddressFormatException e) {
					}
				}
				keys.add(new ECKey(null, Util.toByteArray(chunk)));
				Script script = ScriptBuilder.createMultiSigOutputScript(1, keys);
				tx.addOutput(BigInteger.valueOf(Config.dustSize), script);
				totalOutput = totalOutput.add(BigInteger.valueOf(Config.dustSize));
			}

			for (TransactionOutput out : unspentOutputs) {
				Script script = out.getScriptPubKey();
				Address address = script.getToAddress(params);
				if (address.toString().equals(source)) {
					if (totalOutput.compareTo(totalInput)>0) {
						totalInput = totalInput.add(out.getValue());
						tx.addInput(out);
					}
				}
			}
			if (totalInput.compareTo(totalOutput)<0) {
				logger.info("Not enough inputs. Output: "+totalOutput.toString()+", input: "+totalInput.toString());
                NumberFormat nf = NumberFormat.getInstance();
				throw new Exception("Not enough BTC to cover transaction fee of "+String.format("%.8f", (totalOutput.doubleValue()/Config.unit))+" BTC.");
                 
			}
			BigInteger totalChange = totalInput.subtract(totalOutput);

			try {
				if (totalChange.compareTo(BigInteger.ZERO)>0) {
					tx.addOutput(totalChange, new Address(params, source));
				}
			} catch (AddressFormatException e) {
			}
		}
		return tx;
	}

	public Boolean sendTransaction(Transaction tx) throws Exception {
		try {
			tx.signInputs(SigHash.ALL, wallet);
			//System.out.println(tx);
			Blocks blocks = Blocks.getInstance();
			//blocks.wallet.commitTx(txBet);
			ListenableFuture<Transaction> future = null;
			try {
				future = peerGroup.broadcastTransaction(tx);
				future.get(60, TimeUnit.SECONDS);
				//} catch (TimeoutException e) {
				//	logger.error(e.toString());
				//	future.cancel(true);
			} catch (Exception e) {
				throw new Exception("Transaction timed out. Please try again.");
			}
			blocks.importTransaction(tx, null, null);
			return true;
			/*
			byte[] rawTxBytes = tx.bitcoinSerialize();
			String rawTx = new BigInteger(1, rawTxBytes).toString(16);
			rawTx = "0" + rawTx;
			System.out.println(rawTx);
			 */
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}		
	}
}