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
import java.text.SimpleDateFormat;
import java.util.*;

public class Scheduler {
    String[] walletAddresses;
    final String SOLANA_MAINNET_URL = "https://api.mainnet-beta.solana.com";
    final String SOLANA_DEVNET_URL = "https://api.devnet.solana.com";
    private final HttpPost httpPost;
    private final CloseableHttpClient client;
    final String JSON =  """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "limit": 1000
                        }
                    ]
                }""";
    final String JSONbody = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getTransaction",
                    "params": [
                        "key"
                    ]
                }""";

    public Scheduler(String[] walletAddresses) {
        this.walletAddresses = walletAddresses;
        httpPost = new HttpPost(SOLANA_MAINNET_URL);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        client = HttpClients.createDefault();
    }

    public Scheduler(String filePath) {
        importWallets(filePath);
        httpPost = new HttpPost(SOLANA_MAINNET_URL);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        client = HttpClients.createDefault();
        checkAccounts(walletAddresses);
    }

    private void importWallets(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            List<String> wallets = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                int index = line.indexOf("\t");
                String wallet = line.substring(0, index);
                wallets.add(wallet);
            }
            walletAddresses = wallets.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkAccounts(String... walletAddresses) {
        try {
            Output.println("Checking " + walletAddresses.length + " addresses");
            for (String address : walletAddresses) {
                Output.println(address);
                Map<String, Integer> transactionsMap = new TreeMap<>();
                String JSON1 = JSON.replace("key", address);
                StringEntity stringEntity = new StringEntity(JSON1);
                httpPost.setEntity(stringEntity);
                CloseableHttpResponse response = client.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity);
                JSONObject jsonObj = new JSONObject(result);
                Output.writeJSONToFile("E:\\Projects\\SolanaWalletActivityChecker\\heroku\\data\\schedule\\transactionsForAddress\\" + address, jsonObj);
                try {
                    JSONArray transactions = (JSONArray) jsonObj.get("result");
                    if (transactions.length() == 0) {
                        continue;
                    }
                    int addedTransactionsCount = 0;
                    Output.println("Transactions count: " + transactions.length());
                    for (int i = transactions.length() - 1; i >= 0; i--) {
                        try {
                            JSONObject transaction = (JSONObject) transactions.get(i);
                            if (!transaction.get("err").toString().equals("null")) {
                                continue;
                            }
                            String transactionKey = (String) transaction.get("signature");
                            JSONObject transactionJSON = getTransactionJSON(transactionKey);
                            List<String> signers = getSignersList(transactionJSON);
                            Output.println(transactionKey + " " + signers);
                            if (!signers.contains(address)) {
                                continue;
                            }
                            int blockTime = (int) transaction.get("blockTime");
                            transactionsMap.put(transactionKey, blockTime);
                            addedTransactionsCount++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Thread.sleep(1000);
                            i++;
                        }
                    }
                    Output.println(addedTransactionsCount + " transactions were successfully added for " + address);
                    toFile(address, transactionsMap);
                    Thread.sleep(5000);
                } catch (org.json.JSONException e) {
                    Output.writeJSONToFile("E:\\Projects\\SolanaWalletActivityChecker\\heroku\\data\\schedule\\errors\\" + address, jsonObj);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getTransactionJSON(String signature) throws JSONException {
        try {
            String JSON = JSONbody.replace("key", signature);
            StringEntity stringEntity = new StringEntity(JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject jsonObj = new JSONObject(result);
            response.close();
            return jsonObj;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }

    public List<String> getSignersList(JSONObject jo) throws JSONException {
        List<String> signers = new ArrayList<>();
        JSONObject resultBody = (JSONObject) jo.get("result");
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONObject message = (JSONObject) transaction.get("message");
        JSONObject header = (JSONObject) message.get("header");
        int numRequiredSignatures = header.getInt("numRequiredSignatures");
        JSONArray accountKeys = (JSONArray) message.get("accountKeys");
        if (numRequiredSignatures == 1) {
            signers.add(accountKeys.getString(0));
        } else {
            for (int i = 0; i < numRequiredSignatures; i++) {
                signers.add(accountKeys.getString(i));
            }
        }
        return signers;
    }

    public String toDate(int timeInSeconds) {
        Date date = new Date(timeInSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return sdf.format(date);
    }

    public void toFile(String fileName, Map<String, Integer> transactionsMap) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("E:\\Projects\\SolanaWalletActivityChecker\\heroku\\data\\schedule\\" + fileName + ".txt"))){
            for (Map.Entry<String, Integer> entry : transactionsMap.entrySet()) {
                int time = entry.getValue();
                bw.write(entry.getKey() + "\t" + time + "\t" + toDate(time));
                bw.newLine();
            }
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
