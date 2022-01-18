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
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    int count = 0;
    HttpPost httpPost;
    CloseableHttpClient client;

    private Map<String, String> wallets = new HashMap<>();
    private Map<String, String> lastTransactions = new HashMap<>();
    String URLstr = "https://api.mainnet-beta.solana.com"; // "https://api.devnet.solana.com"
    String getTransactionsJSONLimit = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "limit": 10
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
    String getTokenJSON = """
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
            System.out.println(i++);
            checkAccounts(walletAddresses);
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("End");
        System.out.println("Number of unknown transactions: " + count);
    }

    public void checkAccounts(String... walletAddresses) {
        try {
            //lastTransactions.forEach((k, value) -> System.out.println(k + ":" + value));
            Map<Integer, String> transactionsMap = new TreeMap<>();
            System.out.println("Checking " + walletAddresses.length + " addresses");
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
                writeJSONToFile(".\\data\\wallets\\" + address, jsonObj);
                try {
                    JSONArray transactions = (JSONArray) jsonObj.get("result");
                    if (transactions.length() == 0) {
                        continue;
                    }
                    int count = 0, duplicatesCount = 0;
                    for (int i = transactions.length() - 1; i >= 0; i--) {
                        JSONObject transaction = (JSONObject) transactions.get(i);
                        int blockTime = (int) transaction.get("blockTime");
                        String transactionKey = (String) transaction.get("signature");
                        if (transactionsMap.containsKey(blockTime)) {
                            duplicatesCount++;
                        }
                        transactionsMap.put(blockTime, transactionKey);
                        count++;
                    }
                    int addedTransactionsCount = count - duplicatesCount;
                    if (addedTransactionsCount == 1) {
                        System.out.println("1 transaction was successfully added for " + address);
                    } else {
                        System.out.println(addedTransactionsCount + " transactions were successfully added for " + address);
                    }
                    JSONObject lastTransaction = (JSONObject) transactions.get(0);
                    String lastTransactionKey = (String) lastTransaction.get("signature");
                    lastTransactions.put(address, lastTransactionKey);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    JSONObject error = (JSONObject) jsonObj.get("error");
                    int code = (Integer) error.get("code");
                    String message = (String) error.get("message");
                    System.out.println("Error: " + code + ", Message: " + message);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) { }
                }
            }
            int size = transactionsMap.size();
            if (size > 1) {
                System.out.println("List of unique transactions was formed: " + transactionsMap.size());
                // transactionsMap.forEach((k, value) -> System.out.println(k + ":" + value));
                transactionsMap.forEach((k, value) -> postJson(value));
                //lastTransactions.forEach((k, value) -> System.out.println(k + ":" + value));
                exportLastTransactions();
            } else {
                System.out.println("No new  transactions found");
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
        //sdf.setTimeZone(TimeZone.getTimeZone("UTC+3"));
        String formattedDate = sdf.format(date);
        JSONObject trans = (JSONObject) resultBody.get("transaction");
        JSONArray signatures = (JSONArray) trans.get("signatures");
        String transactionStr = (String) signatures.get(0);
        //System.out.println(transactionStr);
        // text
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String firstLine = (String) logMessages.get(0);
        String text = "";
        if (firstLine.contains("MEisE1HzehtrDpAAT8PnLHjpSSkRYakotTuJRPjTpo8") || firstLine.contains("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")) {
            //System.out.println("ME operation");
            if (meta.toString().contains("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")) {
                //System.out.println("sale operation");
                text = parseSale(transaction);
            } else {
                //System.out.println("bid");
                text = parseBid(transaction);
            }
        } else if (firstLine.equals("Program 11111111111111111111111111111111 invoke [1]")) {
            if (logMessages.length() == 2) {
                //System.out.println("Transfer");
                text = parseTokenTransfer(transaction);
            } else {
                String thirdLine = (String) logMessages.get(2);
                if (thirdLine.equals("Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [1]")) {
                    //System.out.println("Mint ");
                    text = parseNMint(transaction);
                } else {
                    System.out.println("Not mint");
                    text = parseTokenTransfer(transaction);
                }
            }
        } else if (firstLine.equals("Program DeJBGdMFa1uynnnKiwrVioatTuHmNLpyFKnmB5kaFdzQ invoke [1]")) {
            System.out.println("NFT or token transfer");
            text  = parseNFTtransfer(transaction);
        } else if (firstLine.equals("Program CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz invoke [1]")) {
            //System.out.println("Solanart operation");
            text = parseSale(transaction);
        } else if (firstLine.equals("Program 675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8 invoke [1]")) {
            System.out.println("Raydium");
            text = parseSale(transaction);
        }
        if (!text.equals("")) {
            text = replace(text);
            System.out.println(formattedDate + " " + text);
        } else {
            count++;
        }
    }

    private String replace(String text) {
        for (Map.Entry<String, String> entry : wallets.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        text = text
                .replace("MEisE1HzehtrDpAAT8PnLHjpSSkRYakotTuJRPjTpo8", "MagicEden")
                .replace("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", "Alpha.art")
                .replace("CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz", "Solanart")
                .replace("617jbWo616ggkDxvW1Le8pV38XLbVSyWY8ae6QUmGBAU", "Solsea")
                .replace("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", "Raydium Liquidity Pool V4");
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
            writeJSONToFile(".\\data\\transactions\\" + key, jsonObj);
            System.out.println(key);
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

    public String parseSale(JSONObject jsonObj) {
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
        String text;
        if (amount < .003) {
            text = newOwner + " listed " + mint + " on " + marketplace;
        } else {
            String priceStr = String.format("%,.3f", amount);
            text = newOwner + " bought " + mint + " from " + seller + " for " + priceStr + " SOL" + " on " + marketplace;
        }
        return text;
    }

    public String parseBid(JSONObject jsonObj) {
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
        String priceStr = String.format("%,.3f", amount);
        return bidPlacer + " placed bid for " + priceStr + " SOL" + " on " + marketplace;
    }

    public String parseNFTtransfer(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray postTokenBalances = (JSONArray) meta.get("postTokenBalances");
        String oldOwner = (String) ((JSONObject) postTokenBalances.get(1)).get("owner");
        String newOwner = (String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
        return oldOwner + " transferred " + mint + " to " + newOwner;
    }

    public String parseNMint(JSONObject jsonObj) {
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
        String amount = String.format("%,.3f", getAmount(meta));
        return owner + " minted " + mint + " for " + amount;
    }

    public String parseTokenTransfer(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        String sender = ((String) accountKeys.get(0));//(String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String receiver = ((String) accountKeys.get(1));
        JSONObject meta = (JSONObject) resultBody.get("meta");
        String amount = String.format("%,.3f", getAmount(meta));
        return sender + " transferred " + amount + " SOL" + " to " + receiver;
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

    public static void main(String[] args) {
        Main main = new Main();
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
        System.out.println("Check accounts");
        main.loadAccounts(".\\data\\wallets.txt");
        main.checkAllAccounts();
    }
}