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
    HttpPost httpPost;
    CloseableHttpClient client;

    private Map<String, String> wallets = new HashMap<>();
    private Map<String, String> lastTransactions = new HashMap<>();
    String URLstr = "https://api.devnet.solana.com"; //"https://api.mainnet-beta.solana.com";
    String getTransactionsJSONLimit = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "limit": 5
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

    public void checkAllAccounts() {
        String[] walletAddresses = wallets.keySet().toArray(new String[0]);
        //importLastTransactions();
        int i = 0;
        while (i < 1) {
            System.out.println(i++);
            checkAccounts(walletAddresses);
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("End");
        exportLastTransactions();
    }

    public void checkAccounts(String... walletAddresses) {
        try {
            //lastTransactions.forEach((k, value) -> System.out.println(k + ":" + value));
            Map<Integer, String> transactionsMap = new TreeMap<>();
            System.out.println("Checking addresses: " + walletAddresses.length);
            for (String address : walletAddresses) {
                System.out.println(address);
                String lastTransactionStr = lastTransactions.get(address);
                String JSON;
                if (lastTransactionStr == null) {
                    JSON = getTransactionsJSONLimit.replace("key", address);
                } else {
                    JSON = getTransactionsJSONLastTransaction.replace("lastTransaction", lastTransactionStr);
                }
                StringEntity stringEntity = new StringEntity(JSON);
                httpPost.setEntity(stringEntity);
                CloseableHttpResponse response = client.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity);
                JSONObject jsonObj = new JSONObject(result);
                //System.out.println(key);
                //writeJSONToFile(address, jsonObj);
                try {
                    JSONArray transactions = (JSONArray) jsonObj.get("result");
                    for (int i = transactions.length() - 1; i >= 0; i--) {
                        JSONObject transaction = (JSONObject) transactions.get(i);
                        int blockTime = (int) transaction.get("blockTime");
                        String transactionKey = (String) transaction.get("signature");
                        transactionsMap.put(blockTime, transactionKey);
                    }
                    JSONObject lastTransaction = (JSONObject) transactions.get(0);
                    String lastTransactionKey = (String) lastTransaction.get("signature");
                    lastTransactions.put(address, lastTransactionKey);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("List of transactions was formed: " + transactionsMap.size());
            // transactionsMap.forEach((k, value) -> System.out.println(k + ":" + value));
            transactionsMap.forEach((k, value) -> postJson(value));
            //lastTransactions.forEach((k, value) -> System.out.println(k + ":" + value));
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
            text = parseSale(transaction);
        } else if (firstLine.equals("Program 11111111111111111111111111111111 invoke [1]")) {
            if (logMessages.length() > 15) {
                System.out.println("Mint ");
                text = parseNMint(transaction);
            } else {
                //System.out.println("SOL transfer");
                text = parseTokenTransfer(transaction);
            }
        } else if (firstLine.equals("Program DeJBGdMFa1uynnnKiwrVioatTuHmNLpyFKnmB5kaFdzQ invoke [1]")) {
            //System.out.println("NFT transfer");
            text  = parseNFTtransfer(transaction);
        } else if (firstLine.equals("Program CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz invoke [1]")) {
            //System.out.println("Solanart operation");
            text = parseSale(transaction);
        }
        text = replace(text);
        System.out.println(formattedDate + " " + text);
    }

    private String replace(String text) {
        for (Map.Entry<String, String> entry : wallets.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
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
            //writeJSONToFile(key, jsonObj);
            System.out.println(key);
            try {
                parseTransaction(jsonObj);
            } catch (org.json.JSONException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String parseSale(JSONObject jsonObj) {
        //System.out.println(jsonObj.toString(2));
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        int timeInSeconds = (Integer) resultBody.get("blockTime");
        Date date = new Date(timeInSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        //sdf.setTimeZone(TimeZone.getTimeZone("UTC+3"));
        String formattedDate = sdf.format(date);
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

        return owner + " minted " + mint;
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
        System.out.println("Check accounts");
        main.loadAccounts(".\\data\\wallets.txt");
        main.checkAllAccounts();
    }
}