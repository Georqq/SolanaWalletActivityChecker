import operations.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    HttpPost httpPost;
    CloseableHttpClient client;
    final WalletActivityListener listener;
    int time = 45;
    private BlockingQueue<String> transactionsQueue;
    String[] walletAddresses;
    private String lastTransactionsFilePath;
    private Map<String, String> wallets;
    private Map<String, String> lastTransactions;
    private List<String> unknownTransactionsList;
    private final Scheduler scheduler;
    private int intervals = 24;

    private ScheduledThreadPoolExecutor prodsScheduleExecutor;
    private Future<?> allThreadsManager;
    private Future<?> conManager;
    private ScheduledThreadPoolExecutor scheduleExecutor;
    private List<ScheduledFuture<?>> scheduleManagers;
    private List<Runnable> procs;

    final String SOLANA_MAINNET_URL = "https://api.mainnet-beta.solana.com";
    final String SOLANA_DEVNET_URL = "https://api.devnet.solana.com";
    final String solscanLink = """
                <a href="https://solscan.io/tx/key">Solscan</a>""";
    final String getTransactionsJSONLimit = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "limit": 1
                        }
                    ]
                }""";
    final String getTransactionsJSONLastTransaction = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "until": "lastTransaction"
                        }
                    ]
                }
            """;
    final String JSONbody = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getTransaction",
                    "params": [
                        "key"
                    ]
                }
            """;

    public Main(WalletActivityListener listener) {
        init();
        this.listener = listener;
        loadAccounts(".\\data\\136412831_wallets.txt");
        loadLastTransactions(".\\data\\136412831_lastTransactions.txt");
        loadKeys(".\\data\\keys.txt");
        intervals = 96;
        println("Number of time intervals: " + intervals);
        scheduler = new Scheduler(walletAddresses, intervals);
        scheduler.fillSchedules();
        println("Schedules drawn up");
        println("Waiting for start command");
    }

    private void init() {
        httpPost = new HttpPost(SOLANA_MAINNET_URL);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        client = HttpClients.createDefault();
        transactionsQueue = new LinkedBlockingQueue<>();
        wallets = new HashMap<>();
        lastTransactions = new ConcurrentHashMap<>();
        unknownTransactionsList = new ArrayList<>();
    }

    public void checkAccounts(String... walletAddresses) {
        println("Checking " + walletAddresses.length + " addresses");
        Map<String, Integer> transactionsMap = new TreeMap<>();
        for (String address : walletAddresses) {
            try {
                JSONArray transactions = getTransactions(address);
                if (transactions.isEmpty()) {
                    continue;
                }
                int addedTransactionsCount = 0;
                for (int i = transactions.length() - 1; i >= 0; i--) {
                    JSONObject transaction = (JSONObject) transactions.get(i);
                    if (!transaction.get("err").toString().equals("null")) {
                        continue;
                    }
                    int blockTime = (int) transaction.get("blockTime");
                    String transactionKey = (String) transaction.get("signature");
                    transactionsMap.put(transactionKey, blockTime);
                    addedTransactionsCount++;
                }
                if (addedTransactionsCount == 1) {
                    println("1 transaction was successfully added for " + address);
                } else {
                    println(addedTransactionsCount + " transactions were successfully added for " + address);
                }
                JSONObject lastTransaction = (JSONObject) transactions.get(0);
                String lastTransactionKey = (String) lastTransaction.get("signature");
                lastTransactions.put(address, lastTransactionKey);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        int size = transactionsMap.size();
        if (size > 0) {
            println("List of unique transactions was formed: " + transactionsMap.size());
            if (size > 5) {
                transactionsMap
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(x -> postJson(x.getKey()));
                exportLastTransactions();
            } else {
                transactionsMap.forEach((k, value) -> postJson(k));
                exportLastTransactions();
            }
        } else {
            println("No new transactions found");
        }
    }

    public int getQueueSize() {
        return transactionsQueue.size();
    }

    public String getThreadsConditions() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < scheduleManagers.size(); i++) {
            ScheduledFuture<?> sf = scheduleManagers.get(i);
            if (sf == null) {
                text.append("Thread ").append(i).append(" is null\n");
            } else if (sf.isCancelled()) {
                text.append("Thread ").append(i).append(" is cancelled\n");
            } else if (!sf.isCancelled()) {
                text.append("Thread ").append(i).append(" is working\n");
            }
        }
        if (conManager == null) {
            text.append("Consumer thread is null\n");
        } else if (conManager.isCancelled()) {
            text.append("Consumer thread is cancelled\n");
        } else if (!conManager.isCancelled()) {
            text.append("Consumer thread is working\n");
        }
        println(text.toString());
        return text.toString();
    }

    public void parseTransaction(JSONObject transaction) throws org.json.JSONException {
        JSONObject resultBody = (JSONObject) transaction.get("result");
        // date & time
        int timeInSeconds = (Integer) resultBody.get("blockTime");
        Date date = new Date(timeInSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String formattedDate = sdf.format(date);
        JSONObject trans = (JSONObject) resultBody.get("transaction");
        JSONArray signatures = (JSONArray) trans.get("signatures");
        String transactionStr = (String) signatures.get(0);
        // text
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String firstLine = (String) logMessages.get(0);
        Operation operation = null;
        if (firstLine.contains("MEisE1HzehtrDpAAT8PnLHjpSSkRYakotTuJRPjTpo8") || firstLine.contains("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")) {
            if (meta.toString().contains("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")) {
                operation = parseSale(transaction);
            } else {
                operation = parseBid(transaction);
            }
        } else if (firstLine.equals("Program 11111111111111111111111111111111 invoke [1]")) {
            if (logMessages.length() == 2) {
                operation = parseTokenTransfer(transaction);
            } else {
                if (logMessages.toString().contains("InitializeMint")) {
                    operation = parseMint(transaction);
                }
            }
        } else if (firstLine.equals("Program DeJBGdMFa1uynnnKiwrVioatTuHmNLpyFKnmB5kaFdzQ invoke [1]")) {
            //System.out.println("NFT or token transfer");
            operation  = parseNFTtransfer(transaction);
        } else if (firstLine.equals("Program CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz invoke [1]")) {
            operation = parseSale(transaction);
        } else if (firstLine.equals("Program 675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8 invoke [1]")) {
            System.out.println("Raydium");
            operation = parseSale(transaction);
        }
        String link = solscanLink.replace("key", transactionStr);
        if (!(operation == null)) {
            println("Message: " + formattedDate+ addNameToWallet(getOp(operation)));
            listener.print(formattedDate + "\n" + formatMessage(operation) + "\n" + link);
        } else {
            unknownTransactionsList.add(transactionStr);
            println(formattedDate + " Unknown operation: " + transactionStr);
        }
    }

    public void setTime(int time) {
        this.time = time;
        for (int i = 0; i < scheduleManagers.size(); i++) {
            ScheduledFuture<?> sf = scheduleManagers.get(i);
            if (sf != null) {
                sf.cancel(false);
            }
            sf = scheduleExecutor.scheduleAtFixedRate(procs.get(i), 0, time, TimeUnit.SECONDS);
            scheduleManagers.set(i, sf);
        }
        println("Time was set to " + time);
    }

    public int getTime() {
        return time;
    }

    public String getWallets() {
        StringBuilder sb = new StringBuilder();
        wallets.forEach((k, value) -> sb.append(k).append(":").append(value).append("\n"));
        return sb.toString();
    }

    private String formatMessage(Operation op) {
        if (op instanceof SaleOperation so) {
            return formatAccount(so.buyer) + " bought " + formatToken(so.token) + " from " +
                    formatAccount(so.seller) + " for " + formatDouble(so.amount) + " SOL on " + replaceMarketplace(so.marketplace);
        } else if (op instanceof MintOperation mo) {
            return formatAccount(mo.minter) + " minted " + formatToken(mo.token) + " for " + formatDouble(mo.amount) + " SOL";
        } else if (op instanceof TransferSolanaOperation tso) {
            return formatAccount(tso.sender) + " sent " + formatDouble(tso.amount) + " SOL to " + formatAccount(tso.receiver);
        } else if (op instanceof TransferNFTOperation tno) {
            return formatAccount(tno.sender) + " sent " + formatToken(tno.token) + " to " + formatAccount(tno.receiver);
        } else if (op instanceof ListingOperation lo) {
            return formatAccount(lo.user) + " listed " + formatToken(lo.token) + (lo.price > 0. ? " for " + lo.price + " SOL" : "") + " on " + replaceMarketplace(lo.marketplace);
        } else if (op instanceof DelistingOperation dl) {
            return formatAccount(dl.user) + " delisted " + formatToken(dl.token) + " from " + replaceMarketplace(dl.marketplace);
        } else if (op instanceof BidOperation bo) {
            return formatAccount(bo.user) + " placed bid for " + formatDouble(bo.amount) + " SOL on " + replaceMarketplace(bo.marketplace);
        } else {
            return "Unknown operation";
        }
    }

    private String getOp(Operation op) {
        if (op instanceof SaleOperation so) {
            return so.buyer + " bought " + so.token + " from " +
                    so.seller + " for " + formatDouble(so.amount) + " SOL on " + replaceMarketplace(so.marketplace);
        } else if (op instanceof MintOperation mo) {
            return mo.minter + " minted " + mo.token + " for " + formatDouble(mo.amount) + " SOL";
        } else if (op instanceof TransferSolanaOperation tso) {
            return tso.sender + " sent " + formatDouble(tso.amount) + " SOL to " + tso.receiver;
        } else if (op instanceof TransferNFTOperation tno) {
            return tno.sender + " sent " + tno.token + " to " + tno.receiver;
        } else if (op instanceof ListingOperation lo) {
            return lo.user + " listed " + lo.token + (lo.price > 0. ? " for " + lo.price + " SOL": "") + " on " + replaceMarketplace(lo.marketplace);
        } else if (op instanceof DelistingOperation dl) {
            return dl.user + " delisted " + dl.token + " from " + replaceMarketplace(dl.marketplace);
        } else if (op instanceof BidOperation bo) {
            return bo.user + " placed bid for " + formatDouble(bo.amount) + " SOL on " + replaceMarketplace(bo.marketplace);
        } else {
            return "Unknown operation";
        }
    }

    private String formatDouble(double d) {
        return Output.df.format(d);
    }

    private String replaceMarketplace(String address) {
        return address
                .replace("MEisE1HzehtrDpAAT8PnLHjpSSkRYakotTuJRPjTpo8", "MagicEden")
                .replace("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", "Alpha.art")
                .replace("CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz", "Solanart")
                .replace("617jbWo616ggkDxvW1Le8pV38XLbVSyWY8ae6QUmGBAU", "Solsea");
    }

    private String formatToken(String token) {
        String accountBase = """
                <a href="https://nft.raydium.io/item-details/key">name</a>""";
        String formattedAccount = accountBase.replace("key", token);
        String tokenName = getNFTName(token);
        String shortToken = token.substring(0, 3) + "..." + token.substring(token.length() - 3);
        tokenName = tokenName.length() > 0 ? tokenName : shortToken;
        formattedAccount = formattedAccount.replace("name", tokenName);
        return formattedAccount;
    }

    private String formatAccount(String wallet) {
        String accountBase = """
                <a href="https://nft.raydium.io/u/key?tab=activites">name</a>""";
        String formattedAccount = accountBase.replace("key", wallet);
        String replacement;
        if (wallets.containsKey(wallet)) {
            replacement = wallets.get(wallet);
        } else {
            replacement = wallet.substring(0, 3) + "..." + wallet.substring(wallet.length() - 3);
        }
        formattedAccount = formattedAccount.replace("name", replacement);
        return formattedAccount;
    }

    private String addNameToWallet(String text) {
        for (Map.Entry<String, String> entry : wallets.entrySet()) {
            String wallet = entry.getKey();
            String name = entry.getValue();
            text = text.replace(wallet, wallet + " (" + name + ")");
        }
        return text;
    }

    public boolean postJson(String key) {
        println("Parsing " + key);
        String JSON = JSONbody.replace("key", key);
        JSONObject jsonObj = null;
        try {
            StringEntity stringEntity = new StringEntity(JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            jsonObj = new JSONObject(result);
            parseTransaction(jsonObj);
            response.close();
            return true;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            if (jsonObj != null) {
                processError(jsonObj, key);
            }
            return false;
        }
    }

    public Operation parseSale(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray postTokenBalances = (JSONArray) meta.get("postTokenBalances");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String marketplace = ((String) logMessages.get(0)).split(" ")[1];
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String seller = ((String) accountKeys.get(2));
        String newOwner = ((String) accountKeys.get(0));
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
        double amount = getAmount(meta);
        if (amount < .003) {
            if (logMessages.toString().contains("11111111111111111111111111111111")) {
                return new ListingOperation(newOwner, mint, marketplace, getNFTPrice(mint, marketplace));
            } else {
                return new DelistingOperation(newOwner, mint, marketplace);
            }

        } else {
            return new SaleOperation(newOwner, seller, mint, amount, marketplace);
        }
        //return text;
    }

    public BidOperation parseBid(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String marketplace = ((String) logMessages.get(0)).split(" ")[1];
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String bidPlacer = ((String) accountKeys.get(0));
        double amount = getAmount(meta);
        return new BidOperation(bidPlacer, amount, marketplace);
    }

    public TransferNFTOperation parseNFTtransfer(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray postTokenBalances = (JSONArray) meta.get("postTokenBalances");
        String oldOwner = (String) ((JSONObject) postTokenBalances.get(1)).get("owner");
        String newOwner = (String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
        return new TransferNFTOperation(oldOwner, newOwner, mint);
        //return oldOwner + " transferred " + mint + " to " + newOwner;
    }

    public MintOperation parseMint(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray postTokenBalances = (JSONArray) meta.get("postTokenBalances");
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
        String owner;
        try {
            owner = (String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        } catch (JSONException e) {
            e.printStackTrace();
            owner = "null";
        }
        return new MintOperation(owner, mint, getAmount(meta));
    }

    public TransferSolanaOperation parseTokenTransfer(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String sender = ((String) accountKeys.get(0));
        String receiver = ((String) accountKeys.get(1));
        JSONObject meta = (JSONObject) resultBody.get("meta");
        return new TransferSolanaOperation(sender, receiver, getAmount(meta));
    }

    public double getAmount(JSONObject meta) {
        JSONArray postBalances = meta.getJSONArray("postBalances");
        JSONArray preBalances = meta.getJSONArray("preBalances");
        long[] dif = new long[postBalances.length()];
        long pre, post;
        for (int i = 0; i < postBalances.length(); i++) {
            if (postBalances.get(i) instanceof Long) {
                post = (long) postBalances.get(i);
            } else {
                post = (int) postBalances.get(i);
            }
            if (preBalances.get(i) instanceof Long) {
                pre = (long) preBalances.get(i);
            } else {
                pre = (int) preBalances.get(i);
            }
            dif[i] = post - pre;
        }
        long max = Integer.MIN_VALUE;
        for (long l : dif) {
            if (Math.abs(l) > max) {
                max = Math.abs(l);
            }
        }
        return max * 1E-9;
    }

    public void loadAccounts(String file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                int index = line.indexOf("\t");
                String wallet = line.substring(0, index);
                String person = line.substring(index + 1);
                wallets.put(wallet, person);
            }
            walletAddresses = wallets.keySet().toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadLastTransactions(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] pair = line.split(":");
                String wallet = pair[0];
                String person = pair[1];
                lastTransactionsFilePath = filePath;
                lastTransactions.put(wallet, person);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadKeys(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            String[] pair = line.split(":");
            String API_KEY_ID = pair[0];
            String API_SECRET_KEY = pair[1];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportLastTransactions() {
        try (BufferedWriter bf = new BufferedWriter(new FileWriter(lastTransactionsFilePath))) {
            for (Map.Entry<String, String> entry : lastTransactions.entrySet()) {
                bf.write(entry.getKey() + ":" + entry.getValue());
                bf.newLine();
            }
            bf.flush();
            println("Last transactions were exported");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportUnknownTransactions() {
        String unknownTransactionsFilePath = ".\\data\\unknownTransactions.txt";
        try (BufferedWriter bf = new BufferedWriter(new FileWriter(unknownTransactionsFilePath, true))) {
            for (String s : unknownTransactionsList) {
                bf.write(s);
                bf.newLine();
            }
            bf.flush();
            println("Unknown transactions were exported");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNFTName(String token) {
        String URI = "https://api-mainnet.magiceden.io/rpc/getNFTByMintAddress/";
        HttpGet httpGet = new HttpGet(URI + token);
        httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        String name = "";
        try (CloseableHttpResponse response = client.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject res = new JSONObject(result);
            JSONObject results = (JSONObject) res.get("results");
            name = (String) results.get("title");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return name;
    }

    private double getNFTPrice(String token, String marketplace) {
        String URI = "https://api-mainnet.magiceden.io/rpc/getNFTByMintAddress/";
        HttpGet httpGet = new HttpGet(URI + token);
        httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        double price = 0.;
        try (CloseableHttpResponse response = client.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject res = new JSONObject(result);
            JSONObject results = (JSONObject) res.get("results");
            Object priceObj = results.get("price");
            if (priceObj instanceof BigDecimal bigDecimalPrice) {
                price = bigDecimalPrice.doubleValue();
            } else if (priceObj instanceof Integer) {
                price = (double) (int) priceObj;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return price;
    }

    public void restart() {
        pause();
        startThreads();
    }

    public void startThreads() {
        println("First run");
        checkAccounts(walletAddresses);
        println("First run ended");
        startProdThreadsExecutorThread();
        startConsumerThread();
    }

    private void startProdThreadsExecutorThread() {
        runAllThreads();
        long currentTime = System.currentTimeMillis();
        int timeMins = 24 * 60 / intervals;
        println("Time in minutes between checks: " + timeMins);
        int round = timeMins * 60 * 1000;
        long startTime = (currentTime / round) * round + round;
        //SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        //String formattedTime = sdf.format(startTime);
       //println("Threads will start at " + formattedTime);
        Runnable run = () -> {
            cancelProducerThreads();
            runAllThreads();
        };
        prodsScheduleExecutor = new ScheduledThreadPoolExecutor(1);
        long delayMS = startTime - System.currentTimeMillis();
        int delayS = (int) (delayMS / 1000);
        int periodS = timeMins * 60;
        allThreadsManager = prodsScheduleExecutor.scheduleAtFixedRate(run, delayS, periodS, TimeUnit.SECONDS);
        //println("Time before start: " + delayS + " s");
    }

    private void runAllThreads() {
        println("Starting threads");
        setActualWallets();
        startProducersThreads();
    }

    private void setActualWallets() {
        long timeMillis = System.currentTimeMillis() + 1000L;
        println("Selecting wallets for next run. Wallets count: " + wallets.size());
        println(String.format(
                "Time: %02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(timeMillis),
                TimeUnit.MILLISECONDS.toMinutes(timeMillis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeMillis)),
                TimeUnit.MILLISECONDS.toSeconds(timeMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeMillis)))
        );
        List<String> actualWallets = new ArrayList<>();
        for (Map.Entry<String, String> mapEntry : wallets.entrySet()) {
            String walletAddress = mapEntry.getKey();
            println("Checking if wallet is active: " + walletAddress);
            if (isWalletActive(walletAddress, timeMillis)) {
                actualWallets.add(walletAddress);
                println("Wallet added: " + walletAddress);
            } else {
                println("Wallet skipped: " + walletAddress);
            }
        }
        walletAddresses = actualWallets.toArray(new String[0]);
        println(walletAddresses.length + " wallets were selected");
    }

    private boolean isWalletActive(String walletAddress, long time) {
        return scheduler.isWalletActive(walletAddress, time);
    }

    private void startProducersThreads() {
        println("Starting prod threads");
        int chunkSize = findChunkSize();
        String[][] wallets = chuck(walletAddresses, chunkSize);
        // Producers
        scheduleExecutor = new ScheduledThreadPoolExecutor(wallets.length);
        scheduleManagers = new ArrayList<>();
        procs = new ArrayList<>();
        for (int i = 0; i < wallets.length; i++) {
            println("[Thread " + i + "] wallets: " + Arrays.toString(wallets[i]));
            Runnable proc = new Producer(transactionsQueue, wallets[i], "Thread " + i);
            procs.add(proc);
            scheduleManagers.add(scheduleExecutor.scheduleAtFixedRate(proc, 0, time, TimeUnit.SECONDS));
        }
        println(scheduleManagers.size() + " prod threads started");
    }

    private int findChunkSize() {
        if (walletAddresses.length >= 10 && walletAddresses.length <= 20) {
            return 5;
        } else if (walletAddresses.length >= 30) {
            return 8;
        } else {
            return 10;
        }
    }

    public String[][] chuck(String[] array, int chunkSize) {
        int numOfChunks = (int) Math.ceil((double) array.length / chunkSize);
        String[][] output = new String[numOfChunks][];
        for (int i = 0; i < numOfChunks; ++i) {
            int start = i * chunkSize;
            int length = Math.min(array.length - start, chunkSize);
            String[] temp = new String[length];
            System.arraycopy(array, start, temp, 0, length);
            output[i] = temp;
        }
        return output;
    }

    private void startConsumerThread() {
        println("Starting con thread");
        Runnable consThread = new Consumer(transactionsQueue);
        ExecutorService consExec = Executors.newSingleThreadExecutor();
        conManager = consExec.submit(consThread);
        println("Con thread started");
    }

    public void pause() {
        exportLastTransactions();
        exportUnknownTransactions();
        cancelProducerThreads();
        cancelConsumerThread();
        cancelExecutorThread();
    }

    private void cancelProducerThreads() {
        println("Stopping prod threads");
        if (scheduleManagers != null) {
            for (int i = 0; i < scheduleManagers.size(); i++) {
                ScheduledFuture<?> sf = scheduleManagers.get(i);
                if (sf.isCancelled()) {
                    println("Thread " + i + " is already interrupted");
                    continue;
                }
                sf.cancel(false);
                if (sf.isCancelled()) {
                    println("Thread " + i + " is interrupted");
                }
            }
        } else {
            println("scheduleManagers is null");
        }
        if (scheduleExecutor != null) {
            scheduleExecutor.shutdown();
        } else {
            println("scheduleExecutor is null");
        }
        println("Prod threads are interrupted");
    }

    private void cancelConsumerThread() {
        println("Stopping con thread");
        if (conManager != null) {
            if (conManager.isCancelled()) {
                println("Con thread is already interrupted");
            }
            conManager.cancel(false);
            if (conManager.isCancelled()) {
                println("Con thread is interrupted");
            }
        } else {
            println("Con manager is null");
        }
    }

    private void cancelExecutorThread() {
        println("Stopping allThreadsManager");
        if (allThreadsManager != null) {
            if (allThreadsManager.isCancelled()) {
                println("Prod scheduler thread is already interrupted");
            }
            allThreadsManager.cancel(false);
            if (allThreadsManager.isCancelled()) {
                println("Prod scheduler thread is interrupted");
            }
        } else {
            println("allThreadsManager is null");
        }
    }

    class Producer implements Runnable {
        private final BlockingQueue<String> transactionsQueue;
        private final String[] walletAddresses;
        private final String name;

        public Producer(BlockingQueue<String> transactionsQueue, String[] walletAddresses, String name) {
            this.transactionsQueue = transactionsQueue;
            this.walletAddresses = walletAddresses;
            this.name = name;
        }

        @Override
        public void run() {
            println('[' + name + "] checking addresses");
            for (String walletAddress : walletAddresses) {
                JSONArray transactions = getTransactions(walletAddress);
                if (transactions.length() == 0) {
                    continue;
                }
                for (int i = transactions.length() - 1; i >= 0; i--) {
                    JSONObject transaction = (JSONObject) transactions.get(i);
                    if (!transaction.get("err").toString().equals("null")) {
                        continue;
                    }
                    String transactionKey = (String) transaction.get("signature");
                    try {
                        transactionsQueue.put(transactionKey);
                        println('[' + name + "] transaction " + transactionKey + " was added for " + walletAddress);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                JSONObject lastTransaction = (JSONObject) transactions.get(0);
                String lastTransactionKey = (String) lastTransaction.get("signature");
                String prevTransaction = lastTransactions.get(walletAddress);
                lastTransactions.put(walletAddress, lastTransactionKey);
                println('[' + name + "] wallet " + walletAddress + ": last transaction " + prevTransaction + " was replaced by " + lastTransactionKey);
            }
            println('[' + name + "] " + walletAddresses.length + " addresses were checked. Sleeping for " + time + " seconds");
        }
    }

    private JSONArray getTransactions(String walletAddress) {
        JSONObject jsonObj = null;
        try {
            String lastTransactionStr = lastTransactions.get(walletAddress);
            String JSON;
            if (lastTransactionStr == null) {
                JSON = getTransactionsJSONLimit.replace("key", walletAddress);
            } else {
                JSON = getTransactionsJSONLastTransaction
                        .replace("key", walletAddress)
                        .replace("lastTransaction", lastTransactionStr);
            }
            StringEntity stringEntity = new StringEntity(JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            jsonObj = new JSONObject(result);
            response.close();
            return (JSONArray) jsonObj.get("result");
        } catch (JSONException | IOException | ClassCastException e) {
            e.printStackTrace();
            processError(jsonObj, walletAddress);
        }
        return new JSONArray();
    }

    private void processError(JSONObject jo, String key) {
        if (jo != null) {
            Error error = new Error(jo);
            println(error.toString());
            if (error.getCode() == 429) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            } else {
                Output.writeJSONToFile(".\\data\\failed\\" + key, jo);
                println("JSON was written to file " + key + ".txt");
            }
        } else {
            println("JSONObject is null");
        }
    }

    class Consumer implements Runnable {
        private final BlockingQueue<String> transactionsQueue;

        public Consumer(BlockingQueue<String> transactionsQueue) {
            this.transactionsQueue = transactionsQueue;
        }

        @Override
        public void run() {
            while (!conManager.isCancelled()) {
                try {
                    String transactionSign = transactionsQueue.take();
                    while (!postJson(transactionSign)) {
                        Thread.sleep(1500);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    private void println(String text) {
        Output.println("[TRACKER] " + text);
    }
}