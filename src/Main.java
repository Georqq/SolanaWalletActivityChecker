import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    private Map<String, String> wallets = new HashMap<>();
    String URLstr = "https://api.mainnet-beta.solana.com";
    String getTransactionsJSON = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getSignaturesForAddress",
                    "params": [
                        "key",
                        {
                            "limit": 3
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

    public void checkAccount(String key) {
        try {
            HttpPost httpPost = new HttpPost(URLstr);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            String JSON = getTransactionsJSON.replace("key", key);
            StringEntity stringEntity = new StringEntity(JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject jsonObj = new JSONObject(result);
            //System.out.println(JSON);
            //System.out.println(jsonObj.toString(2));
            JSONArray transactions = (JSONArray) jsonObj.get("result");
            for (int i = transactions.length() - 1; i >= 0; i--) {
                JSONObject transaction = (JSONObject) transactions.get(i);
                String transactionKey = (String) transaction.get("signature");
                postJson(JSONbody.replace("key", transactionKey));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkAllAccounts() {
        String[] walletAddresses = wallets.keySet().toArray(new String[wallets.size()]);
        checkAccounts(walletAddresses);
    }

    public void checkAccounts(String... keys) {
        try {
            Map<Integer, String> transactionsMap = new TreeMap<>();
            HttpPost httpPost = new HttpPost(URLstr);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            CloseableHttpClient client = HttpClients.createDefault();
            for (String key : keys) {
                String JSON = getTransactionsJSON.replace("key", key);
                StringEntity stringEntity = new StringEntity(JSON);
                httpPost.setEntity(stringEntity);
                CloseableHttpResponse response = client.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity);
                JSONObject jsonObj = new JSONObject(result);
                JSONArray transactions = (JSONArray) jsonObj.get("result");
                for (int i = transactions.length() - 1; i >= 0; i--) {
                    JSONObject transaction = (JSONObject) transactions.get(i);
                    int blockTime = (int) transaction.get("blockTime");
                    String transactionKey = (String) transaction.get("signature");
                    transactionsMap.put(blockTime, transactionKey);
                }
            }
            // transactionsMap.forEach((k, value) -> System.out.println(k + ":" + value));
            transactionsMap.forEach((k, value) -> postJson(JSONbody.replace("key", value)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //https://www.baeldung.com/httpurlconnection-post
    public void response() {
        try {
            URL url = new URL(URLstr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = JSONbody;
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void trans(String... transactions) {
        for (String s : transactions) {
            postJson(JSONbody.replace("key", s));
        }
    }

    public void parseTransaction(JSONObject transaction) {
        JSONObject resultBody = (JSONObject) transaction.get("result");
        // date & time
        int timeInSeconds = (Integer) resultBody.get("blockTime");
        Date date = new Date(timeInSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        //sdf.setTimeZone(TimeZone.getTimeZone("UTC+3"));
        String formattedDate = sdf.format(date);
        // text
        JSONObject meta = (JSONObject) resultBody.get("meta");
        JSONArray logMessages = (JSONArray) meta.get("logMessages");
        String firstLine = (String) logMessages.get(0);
        String text = "";
        if (firstLine.equals("Program MEisE1HzehtrDpAAT8PnLHjpSSkRYakotTuJRPjTpo8 invoke [1]")) {
            //System.out.println("ME operation");
            text = parseSale(transaction);
        } else if (firstLine.equals("Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL invoke [1]")) {
            //System.out.println("Alpha.art operation");
            text = parseSale(transaction);
        } else if (firstLine.equals("Program 11111111111111111111111111111111 invoke [1]")) {
            if (logMessages.length() > 15) {
                //System.out.println("Mint");
                text = parseNMint(transaction);
            } else {
                //System.out.println("SOL transfer");
                text = parseTokenTransfer(transaction);
            }
        } else if (firstLine.equals("Program DeJBGdMFa1uynnnKiwrVioatTuHmNLpyFKnmB5kaFdzQ invoke [1]")) {
            //System.out.println("NFT transfer");
            text  = parseNFTtransfer(transaction);
        } else  if (firstLine.equals("Program CJsLwbP1iu5DuUikHEJnLfANgKy6stB2uFgvBBHoyxwz invoke [1]")) {
            //System.out.println("Solanart operation");
        } else {
            JSONObject trans = (JSONObject) resultBody.get("transaction");
            JSONArray signatures = (JSONArray) trans.get("signatures");
            text = (String) signatures.get(0);
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
            /*
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(URLstr);
            String json = JSONbody;
            StringEntity entity = new StringEntity(json);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            CloseableHttpResponse response = client.execute(httpPost);
            client.close();
            System.out.println(response.toString());
             */
            /*
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(URLstr);
            String JSON_STRING = JSONbody;
            HttpEntity stringEntity = new StringEntity(JSON_STRING, ContentType.APPLICATION_JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            HttpEntity entity = response2.getEntity();
            System.out.println(entity.toString());
             */
            /*
            // https://techndeck.com/post-request-with-json-body-using-apache-httpclient/
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(URLstr);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            StringEntity stringEntity = new StringEntity(JSONbody);
            httpPost.setEntity(stringEntity);
            System.out.println("Executing request " + httpPost.getRequestLine());
            HttpResponse response = httpclient.execute(httpPost);
            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatusLine().getStatusCode());
            }
            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                System.out.println("Response:\n" + result.append(line));
            }
            // https://stackoverflow.com/questions/20374156/sending-and-parsing-response-using-http-client-for-a-json-list/20376055
            HttpEntity entity = response.getEntity();
            Header encodingHeader = entity.getContentEncoding();
            // you need to know the encoding to parse correctly
            Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 :
                    Charsets.toCharset(encodingHeader.getValue());
            // use org.apache.http.util.EntityUtils to read json as string
            String json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(json);

             */
            HttpPost httpPost = new HttpPost(URLstr);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            StringEntity stringEntity = new StringEntity(key);
            httpPost.setEntity(stringEntity);
            CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject jsonObj = new JSONObject(result);
            //writeJSONToFile(jsonObj);
            parseTransaction(jsonObj);
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
        String owner = (String) ((JSONObject) postTokenBalances.get(0)).get("owner");
        String mint = (String) ((JSONObject) postTokenBalances.get(0)).get("mint");
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
        for (int i = 0; i < dif.length; i++) {
            if (Math.abs(dif[i]) > max) {
                max = Math.abs(dif[i]);
            }
        }
        return max * 1E-9;
    }

    public void writeJSONToFile(JSONObject jsonObj) {
        JSONObject resultBody = (JSONObject) jsonObj.get("result");
        JSONObject transaction = (JSONObject) resultBody.get("transaction");
        JSONArray signatures = (JSONArray) transaction.get("signatures");
        String transactionKey = (String) signatures.get(0);
        String JSONbody = jsonObj.toString(2);
        Path file = Paths.get(transactionKey + ".txt");
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
                System.out.println(line);
                int index = line.indexOf("\t");
                String wallet = line.substring(0, index);
                String person = line.substring(index + 1);
                wallets.put(wallet, person);
            }
            wallets.forEach((k, value) -> System.out.println(k + ":" + value));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        //System.out.println("# 1");
        //main.response();
        /*
        System.out.println("Test");
        main.trans(
                "2CMwyZ1LDK9wb5PKRLLiyzbtH7qTRZY6rpJn8tkhULVCTbjPopAaawTwg7o5Yz27BSnWzZa6psvQo2Dy8t3sR8fG", // 16:41:10 CAq...RTv  bought (BASC) (EK5...KVH) from eeK...vPA for 8.69 SOL on ME
                "32n2Wj2iso7wFLagpsHUxibKi5f3LbDLXTVswuJ8QN4hoC8iX5tBndEMqDANWPcWq595gtpipmg3uQFqGFq7qML9",
                "BcP3zoLuCcDhuyaFGsoWchHEANtnXhEFK7M7zBAG2bYsv3ZYFAo72ybGg1vKdWuUFQEXXTE9fBufewokbU1vn3J", // 19:04:11 FYouR ... M33 sold Ahm5k ... u93 to FiT6B ... Ax2 for 3.2 SOL on Magic Eden
                "5HHXdpzAaDpRvPBTBXgzoyxcg6xm313MvRuAQvGsS9xi8eiX1V7FVNUcng2SkDq6poiodh7yrEKM2hxWhdGEx4bU", // 19:31:14 FYouR...M33 transferred 7 SOL to DhtpVv...VTL7pL
                "2cdHnGkCyrCm3FoSdGUtxp2KruZFepG3uy8C1mBzynF2nnWg3GMFKrfyXiZQVBjT3bksx69FcJWT7k6WSugTk6YH", // 06:37:41 FYouR ... M33 bought 8Nvra...2wK from 22EJX ... QnM for 0.24 SOL on Magic Eden
                "5maxmFniVqDdFZsqmsop6Z5P3esSmogYabNNtdh5wAa9ehtDr5ToBCvdyvxWFZ8vrK48K8Z1ZEV4kmeSrfPb7K3u", // 19:00:26 BW..E minted 3Vj...4yv for 1.28293972 SOL on ME
                "4yd1rzeFb4jt4cZPUDM86qnagBmnvvtT2tqvZBUn6TkXHd8F2jnuezhCeE7jfDyw2p3DxWZhqGJjFvrCnXhXA8kB", // 23:05:55 CDne6C...VS1nCi transferred Mortuary Inc Plot x2 #1174 (MORTUARY) (34q...mW3) to BW..E
                "4hc7KDVnZud9rM6rcdCXzWKgjWwiB1zwDmokFCLsty4XQHQWrzmHunzVtgfzkSojaXX9wCwdQKKiKnuHVNpqy1ow", // 23:22:26 BWNv...KYvb bought SMB #2770 from 5Mcc...ttJB for 8.5 SOL on Alpha.Art
                "2w5WeUvCDJ2CCbvwVkoLPQ8wvEB3muzTmFf2vacCerACE6MqXeA9P3Xx6As3UzXUp1n9P5h11v88uBBMr5K4Mr1R" // 19:08:02 FYouR ... M33 listed 3i18o ... sjR for 3.14269 SOL on Magic Eden
        );
         */
        System.out.println("Check accounts");
        //main.checkAccount("FYouRQbrUbG3bzRGv3iaTAPuxWgHdH4raxxBHo7w1M33");
        main.loadAccounts("E:\\Docs\\wallets.txt");
        /*
        main.checkAccounts(
                "FYouRQbrUbG3bzRGv3iaTAPuxWgHdH4raxxBHo7w1M33",
                "DhtpVvsq1tvUKAqudFkXocbiyMZeqti7ay5KGaVTL7pL",
                "2Hco2qei82kADRcZdm71gLfSZULojnBY2gfPamSphXsX",
                "BNedxPqVzNAYDfJsdCeMqegTGTTG4qbjci9WkwsXBGsx"
        );
         */
        main.checkAllAccounts();
    }
}