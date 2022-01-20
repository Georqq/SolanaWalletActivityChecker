import operations.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
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

public class Main extends Thread {
    int count = 0;
    HttpPost httpPost;
    CloseableHttpClient client;
    SolWalletActivityBot listener;
    int time = 60;
    boolean stopped = true;
    final DecimalFormat df = new DecimalFormat("#.###");

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
    }

    public Main(SolWalletActivityBot bot) {
        this();
        listener = bot;
    }

    public void trans(String... transactions) {
        for (String s : transactions) {
            postJson(s);
        }
    }

    public void checkAllAccounts() {
        String[] walletAddresses = wallets.keySet().toArray(new String[0]);
        importLastTransactions();
        int i = 0;
        while (i < 100) {
            println(String.valueOf(i++));
            checkAccounts(walletAddresses);
            try {
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
            //lastTransactions.forEach((k, value) -> System.out.println(k + ":" + value));
            println("Checking " + walletAddresses.length + " addresses");
            Map<String, Integer> transactionsMap = new TreeMap<>();
            for (String address : walletAddresses) {
                //System.out.println(address);
                String lastTransactionStr = lastTransactions.get(address);
                String JSON;
                if (lastTransactionStr == null) {
                    JSON = getTransactionsJSONLimit.replace("key", address);
                } else {
                    JSON = getTransactionsJSONLastTransaction
                            .replace("key", address)
                            .replace("lastTransaction", lastTransactionStr);
                    //System.out.println(JSON);
                }
                StringEntity stringEntity = new StringEntity(JSON);
                httpPost.setEntity(stringEntity);
                CloseableHttpResponse response = client.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity);
                JSONObject jsonObj = new JSONObject(result);
                //System.out.println(key);
                //writeJSONToFile(".\\data\\wallets\\" + address, jsonObj);
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
                // transactionsMap.forEach((k, value) -> System.out.println(k + ":" + value));
                //transactionsMap.forEach((k, value) -> postJson(k));
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
                } else {
                    System.out.println("Not mint");
                    operation = parseTokenTransfer(transaction);
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
            return dl.user + " delisted " + dl.token + " on " + replaceMarketplace(dl.marketplace);
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
        String shortToken = token.substring(0, 3) + "..." + token.substring(token.length() - 3);
        formattedAccount = formattedAccount.replace("name", shortToken);
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
            System.out.println("Parsing " + key);
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
        //System.out.println(jsonObj.toString(2));
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray postTokenBalances = (JSONArray) meta.get("postTokenBalances");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String marketplace = ((String) logMessages.get(0)).split(" ")[1];
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String seller = ((String) accountKeys.get(2));
        String newOwner = ((String) accountKeys.get(0));//(String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
        double amount = getAmount(meta);
        //String text;
        if (amount < .003) {
            //text = newOwner + " listed " + mint + " on " + marketplace;
            if (logMessages.toString().contains("11111111111111111111111111111111")) {
                return new ListingOperation(newOwner, mint, marketplace);
            } else {
                return new DelistingOperation(newOwner, mint, marketplace);
            }

        } else {
            String priceStr = String.format("%,.3f", amount);
            //text = newOwner + " bought " + mint + " from " + seller + " for " + priceStr + " SOL" + " on " + marketplace;
            return new SaleOperation(newOwner, seller, mint, amount, marketplace);
        }
        //return text;
    }

    public BidOperation parseBid(JSONObject jsonObj) {
        //System.out.println(jsonObj.toString(2));
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String marketplace = ((String) logMessages.get(0)).split(" ")[1];
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String bidPlacer = ((String) accountKeys.get(0));
        double amount = getAmount(meta);
        //String priceStr = String.format("%,.3f", amount);
        return new BidOperation(bidPlacer, amount, marketplace);
        //return bidPlacer + " placed bid for " + priceStr + " SOL" + " on " + marketplace;
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
            JSONArray innerInstructions = (JSONArray) meta.get("innerInstructions");
            JSONArray instructions = (JSONArray) ((JSONObject) innerInstructions.get(0)).get("instructions");
            owner = "null";
        }
        //String amount = String.format("%,.3f", getAmount(meta));
        return new MintOperation(owner, mint, getAmount(meta));
        //return owner + " minted " + mint + " for " + amount;
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
                //System.out.println(line);
                int index = line.indexOf("\t");
                String wallet = line.substring(0, index);
                String person = line.substring(index + 1);
                wallets.put(wallet, person);
            }
            //wallets.forEach((k, value) -> System.out.println(k + ":" + value));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void importLastTransactions() {
        try (BufferedReader br = new BufferedReader(new FileReader(".\\data\\LastTransactions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                //System.out.println(line);
                String[] pair = line.split(":");
                String wallet = pair[0];
                String person = pair[1];
                lastTransactions.put(wallet, person);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportLastTransactions() {
        try {
            BufferedWriter bf = new BufferedWriter(new FileWriter(".\\data\\LastTransactions.txt"));
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

    @Override
    public synchronized void start() {
        super.start();
        stopped = false;
        println("Thread started");
    }

    public synchronized void pause() {
        stopped = true;
        println("Thread is interrupted");
    }

    private void println(String text) {
        System.out.println(
                ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM))
                        + " " + text
        );
    }

    @Override
    public void run() {
        String[] walletAddresses = wallets.keySet().toArray(new String[0]);
        importLastTransactions();
        int i = 1;
        while (!stopped) {
            println("Iteration # " + i++);
            checkAccounts(walletAddresses);
            try {
                Thread.sleep(time * 1000L);
            } catch (InterruptedException e) {
                stopped = true;
                break;
            }
        }
        println("End");
        println("Number of unknown transactions: " + count);
    }

    public static void main(String[] args) {
        Main main = new Main();
        /*
        main.trans(
                "2CMwyZ1LDK9wb5PKRLLiyzbtH7qTRZY6rpJn8tkhULVCTbjPopAaawTwg7o5Yz27BSnWzZa6psvQo2Dy8t3sR8fG", // 16:41:10 CAq...RTv  bought (BASC) (EK5...KVH) from eeK...vPA for 8.69 SOL on ME
                "32n2Wj2iso7wFLagpsHUxibKi5f3LbDLXTVswuJ8QN4hoC8iX5tBndEMqDANWPcWq595gtpipmg3uQFqGFq7qML9",
                "BcP3zoLuCcDhuyaFGsoWchHEANtnXhEFK7M7zBAG2bYsv3ZYFAo72ybGg1vKdWuUFQEXXTE9fBufewokbU1vn3J", // 19:04:11 FYouR ... M33 sold Ahm5k ... u93 to FiT6B ... Ax2 for 3.2 SOL on Magic Eden
                "5HHXdpzAaDpRvPBTBXgzoyxcg6xm313MvRuAQvGsS9xi8eiX1V7FVNUcng2SkDq6poiodh7yrEKM2hxWhdGEx4bU", // 19:31:14 FYouR...M33 transferred 7 SOL to DhtpVv...VTL7pL
                "2cdHnGkCyrCm3FoSdGUtxp2KruZFepG3uy8C1mBzynF2nnWg3GMFKrfyXiZQVBjT3bksx69FcJWT7k6WSugTk6YH", // 06:37:41 FYouR ... M33 bought 8Nvra...2wK from 22EJX ... QnM for 0.24 SOL on Magic Eden
                "5maxmFniVqDdFZsqmsop6Z5P3esSmogYabNNtdh5wAa9ehtDr5ToBCvdyvxWFZ8vrK48K8Z1ZEV4kmeSrfPb7K3u", // 19:00:26 BW..E minted 3Vj...4yv for 1.28293972 SOL on ME
                "4yd1rzeFb4jt4cZPUDM86qnagBmnvvtT2tqvZBUn6TkXHd8F2jnuezhCeE7jfDyw2p3DxWZhqGJjFvrCnXhXA8kB", // 23:05:55 CDne6C...VS1nCi transferred Mortuary Inc Plot x2 #1174 (MORTUARY) (34q...mW3) to BW..E
                "4hc7KDVnZud9rM6rcdCXzWKgjWwiB1zwDmokFCLsty4XQHQWrzmHunzVtgfzkSojaXX9wCwdQKKiKnuHVNpqy1ow", // 23:22:26 BWNv...KYvb bought SMB #2770 from 5Mcc...ttJB for 8.5 SOL on Alpha.Art
                "2w5WeUvCDJ2CCbvwVkoLPQ8wvEB3muzTmFf2vacCerACE6MqXeA9P3Xx6As3UzXUp1n9P5h11v88uBBMr5K4Mr1R", // 19:08:02 FYouR ... M33 listed 3i18o ... sjR for 3.14269 SOL on Magic Eden
                "3U1FtJyhMaxUq9oPjyxpb2UGrfisvx6qD7hzo3UXxe76QNScH7EsZNrXwUCDoqRum9FG3n18AgSwrMvsK2kWfZT", // EvAKnJ...iCkbcd PLACEd BID 30 SOL
                "3TohRzmvgFn95zcnP5ujgHPL3CuwkSJaffZmLVCNaGQvV8kjaRHFfMnF9YnHi7JM9howKhGknD8NTwzzp9zmt4pR" // 17:25:38 CxbZgp...7Wd5gz transferred 1 ATLAS to zbFjDh...Y9WxRG
        );

         */
        //println("Check accounts");
        main.loadAccounts(".\\data\\wallets.txt");
        main.checkAllAccounts();
    }
}