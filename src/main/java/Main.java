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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {
    int count = 0;
    HttpPost httpPost;
    CloseableHttpClient client;
    SolWalletActivityBot listener;
    int time = 60;
    boolean isStopped = true;
    final DecimalFormat df = new DecimalFormat("#.###");
    private BlockingQueue<String> transactionsQueue;
    String[] walletAddresses;
    private String lastTransactionsFilePath;
    private String API_KEY_ID;
    private String API_SECRET_KEY;


    private Map<String, String> wallets = new HashMap<>();
    private Map<String, String> lastTransactions = new HashMap<>();
    String URLstr = "https://api.mainnet-beta.solana.com"; // "https://api.devnet.solana.com"
    String solscanLink = """
                <a href="https://solscan.io/tx/key">Solscan</a>""";
    String getTransactionsJSONLimit = """
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
                }
            """;
    String getTransactionsJSONLastTransaction = """
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
    String JSONbody = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getTransaction",
                    "params": [
                        "key"
                    ]
                }
            """;

    public Main() {
        httpPost = new HttpPost(URLstr);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        client = HttpClients.createDefault();
        transactionsQueue = new LinkedBlockingQueue<>();
    }

    public Main(SolWalletActivityBot bot) {
        this();
        listener = bot;
        loadAccounts(".\\data\\136412831_wallets.txt");
        loadLastTransactions(".\\data\\136412831_lastTransactions.txt");
        loadKeys(".\\data\\keys.txt");
    }

    public void trans(String... transactions) {
        for (String s : transactions) {
            postJson(s);
        }
    }

    public void checkAllAccounts() {
        //importLastTransactions();
        int i = 0;
        while (i < 100) {
            println(String.valueOf(i++));
            checkAccounts(walletAddresses);
            try {
                println("Waiting for " + time * 1000L + " seconds");
                Thread.sleep(time * 1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        println("End");
        println("Number of unknown transactions: " + count);
    }

    public void checkAccounts(String... walletAddresses) {
        try {
            println("Checking " + walletAddresses.length + " addresses");
            Map<String, Integer> transactionsMap = new TreeMap<>();
            for (String address : walletAddresses) {
                String lastTransactionStr = lastTransactions.get(address);
                String JSON;
                if (lastTransactionStr == null) {
                    JSON = getTransactionsJSONLimit.replace("key", address);
                } else {
                    JSON = getTransactionsJSONLastTransaction
                            .replace("key", address)
                            .replace("lastTransaction", lastTransactionStr);
                }
                StringEntity stringEntity = new StringEntity(JSON);
                httpPost.setEntity(stringEntity);
                CloseableHttpResponse response = client.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity);
                JSONObject jsonObj = new JSONObject(result);
                try {
                    JSONArray transactions = (JSONArray) jsonObj.get("result");
                    if (transactions.length() == 0) {
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
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    JSONObject error = (JSONObject) jsonObj.get("error");
                    int code = (Integer) error.get("code");
                    String message = (String) error.get("message");
                    println("Error: " + code + ", Message: " + message);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) { }
                }
            }
            int size = transactionsMap.size();
            if (size > 10) {
                println("List of unique transactions was formed: " + transactionsMap.size());
                transactionsMap
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(x -> postJson(x.getKey()));
                exportLastTransactions();
            } else if (size > 0) {
                transactionsMap.forEach((k, value) -> postJson(k));
                exportLastTransactions();
            } else {
                println("No new transactions found");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            System.out.println("NFT or token transfer");
            operation  = parseNFTtransfer(transaction);
        } else if (firstLine.equals("Program CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz invoke [1]")) {
            operation = parseSale(transaction);
        } else if (firstLine.equals("Program 675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8 invoke [1]")) {
            System.out.println("Raydium");
            operation = parseSale(transaction);
        }
        String link = solscanLink.replace("key", transactionStr);
        if (!(operation == null)) {
            println(formattedDate + " Message: " + addNameToWallet(getOp(operation)));
            listener.print(formattedDate + "\n" + formatMessage(operation) + "\n" + link);
        }
    }

    public void setTime(int time) {
        this.time = time;
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
            return formatAccount(lo.user) + " listed " + formatToken(lo.token) + " on " + replaceMarketplace(lo.marketplace);
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
            return lo.user + " listed " + lo.token + " on " + replaceMarketplace(lo.marketplace);
        } else if (op instanceof DelistingOperation dl) {
            return dl.user + " delisted " + dl.token + " from " + replaceMarketplace(dl.marketplace);
        } else if (op instanceof BidOperation bo) {
            return bo.user + " placed bid for " + formatDouble(bo.amount) + " SOL on " + replaceMarketplace(bo.marketplace);
        } else {
            return "Unknown operation";
        }
    }

    private String formatDouble(double d) {
        return df.format(d);
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

    // https://www.baeldung.com/httpclient-post-http-request
    public void postJson(String key) {
        try {
            String JSON = JSONbody.replace("key", key);
            StringEntity stringEntity = new StringEntity(JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject jsonObj = new JSONObject(result);
            //writeJSONToFile(".\\data\\transactions\\" + key, jsonObj);
            println("Parsing " + key);
            try {
                parseTransaction(jsonObj);
            } catch (org.json.JSONException e) {
                e.printStackTrace();
                JSONObject error = (JSONObject) jsonObj.get("error");
                int code = (Integer) error.get("code");
                String message = (String) error.get("message");
                System.out.println("Error: " + code + ", Message: " + message);
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) { }
                postJson(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                return new ListingOperation(newOwner, mint, marketplace);
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
        String sender = ((String) accountKeys.get(0));//(String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String receiver = ((String) accountKeys.get(1));
        JSONObject meta = (JSONObject) resultBody.get("meta");
        //String amount = String.format("%,.3f", getAmount(meta));
        return new TransferSolanaOperation(sender, receiver, getAmount(meta));
        //return sender + " transferred " + amount + " SOL" + " to " + receiver;
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

    public void writeJSONToFile(String transaction, JSONObject jsonObj) {
        String JSONbody = jsonObj.toString(2);
        Path file = Paths.get(transaction + ".txt");
        try {
            Files.write(file, Collections.singleton(JSONbody), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                //System.out.println(line);
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
            API_KEY_ID = pair[0];
            API_SECRET_KEY = pair[1];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportLastTransactions() {
        try {
            BufferedWriter bf = new BufferedWriter(new FileWriter(lastTransactionsFilePath));
            for (Map.Entry<String, String> entry : lastTransactions.entrySet()) {
                // put key and value separated by a colon
                bf.write(entry.getKey() + ":" + entry.getValue());
                // new line
                bf.newLine();
            }
            bf.flush();
            bf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getNFTName(String token) {
        String URI = "https://api.blockchainapi.com/v1/solana/nft/mainnet-beta/";
        HttpGet httpGet = new HttpGet(URI + token);
        httpGet.setHeader("APIKeyID", "3jz1EVE5cyuzmJ3");
        httpGet.setHeader("APISecretKey", "XyTtsHR8ZkmEpG3");
        CloseableHttpClient client = HttpClients.createDefault();
        String name = "";
        try {
            CloseableHttpResponse response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject res = new JSONObject(result);
            JSONObject data = (JSONObject) res.get("data");
            name = (String) data.get("name");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    public void startThreads() {
        //super.start();
        println("First run");
        checkAccounts(walletAddresses);
        println("First run ended");
        String[][] wallets = chuck(walletAddresses, 9);
        System.out.println(Arrays.deepToString(wallets));
        Thread prodThread1 = new Thread(new Producer(transactionsQueue, wallets[0], "1"));
        Thread prodThread2 = new Thread(new Producer(transactionsQueue, wallets[1], "2"));
        Thread prodThread3 = new Thread(new Producer(transactionsQueue, wallets[2],"3"));
        Thread prodThread4 = new Thread(new Producer(transactionsQueue, wallets[3],"4"));
        println("Prod threads started");
        Thread consThread = new Thread(new Consumer(transactionsQueue));
        println("Con thread started");
        //Starting producer and Consumer thread
        prodThread1.start();
        prodThread2.start();
        prodThread3.start();
        prodThread4.start();
        consThread.start();
        isStopped = false;
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

    public synchronized void pause() {
        exportLastTransactions();
        isStopped = true;
        println("Threads are interrupted");
    }

    private void println(String text) {
        System.out.println(
                ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM))
                        + " " + text
        );
    }

    class Producer extends Thread {
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
            try {
                while (!isStopped) {
                    println(name + ": checking addresses");
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
                            println("Transaction " + transactionKey + " was added for " + walletAddress);
                            transactionsQueue.put(transactionKey);
                        }
                        JSONObject lastTransaction = (JSONObject) transactions.get(0);
                        String lastTransactionKey = (String) lastTransaction.get("signature");
                        lastTransactions.put(walletAddress, lastTransactionKey);
                    }
                    println(name + ": All " + walletAddresses.length + " addresses were checked. Sleeping for " + time + " seconds");
                    try {
                        Thread.sleep(time * 1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private JSONArray getTransactions(String walletAddress) {
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
            JSONObject jsonObj = new JSONObject(result);
            return (JSONArray) jsonObj.get("result");
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    class Consumer extends Thread {
        private final BlockingQueue<String> transactionsQueue;

        public Consumer(BlockingQueue<String> transactionsQueue) {
            this.transactionsQueue = transactionsQueue;
        }

        @Override
        public void run() {
            try {
                while (!isStopped) {
                    String transactionSign = transactionsQueue.take();
                    postJson(transactionSign);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}