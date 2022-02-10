import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class NFTCollectionFloorMonitor extends Thread {
    private static CloseableHttpClient client;
    private List<FloorPriceChangeListener> listeners;
    private long sleepTime = 45_000;
    private boolean isActive = false;
    private String floorCollectionsFilePath;
    private List<NFTCollection> collections;

    public NFTCollectionFloorMonitor(FloorPriceChangeListener listener, String filePath) {
        init();
        listeners.add(listener);
        setCollections(filePath);
        floorCollectionsFilePath = filePath;
    }

    public NFTCollectionFloorMonitor(FloorPriceChangeListener listener, Map<String, Double> collectionsFloorPrices) {
        init();
        listeners.add(listener);
        fillC(collectionsFloorPrices);
    }

    private void init() {
        collections = new ArrayList<>();
        listeners = new ArrayList<>();
        client = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();
    }

    public void setCollections(String filePath) {
        Map<String, Double> collectionsFloorPrices = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                int index = line.indexOf(" ");
                String collection = line.substring(0, index);
                double price = Double.parseDouble(line.substring(index + 1));
                collectionsFloorPrices.put(collection, price);
            }
            fillC(collectionsFloorPrices);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fillC(Map<String, Double> collectionsFloorPrices) {
        for (Map.Entry<String, Double> entry : collectionsFloorPrices.entrySet()) {
            String name = entry.getKey();
            double floorPrice = entry.getValue();
            double avgSalePrice24hr = -1.;
            NFTCollection collection = new NFTCollection(name, floorPrice, avgSalePrice24hr);
            collections.add(collection);
        }
    }

    public void addListener(FloorPriceChangeListener listener) {
        listeners.add(listener);
    }

    public void setSleepTime(int time) {
        this.sleepTime = time * 1000L;
    }

    public int getSleepTime() {
        return (int) (sleepTime / 1000);
    }

    public void startMonitor() {
        this.start();
        isActive = true;
    }

    public void stopMonitor() {
        isActive = false;
        Output.println("Monitor is interrupted");
        exportFloorCollections();
    }

    private void exportFloorCollections() {
        try (BufferedWriter bf = new BufferedWriter(new FileWriter(floorCollectionsFilePath))) {
            for (NFTCollection col : collections) {
                bf.write(col.getName() + " " + col.getFloorPrice());
                bf.newLine();
            }
            bf.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (isActive) {
            updatePrices();
            Output.println("Sleeping for " + (sleepTime / 1000) + " s");
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void updatePrices() {
        Output.println("Updating floor and avg prices for " + collections.size() + " collections");
        for (NFTCollection col : collections) {
            String collectionName = col.getName();
            try {
                JSONObject results = getResults(collectionName);
                double floorPrice = getNFTCollectionFloorPrice(results);
                double avgPrice = getNFTCollectionAvgPrice(results);
                double prevFloor = col.getFloorPrice();
                if (floorPrice < prevFloor && floorPrice > 0.001) {
                    Output.println(collectionName + ": current floor value is " + floorPrice + ", previous floor is " + prevFloor);
                    List<NFT> NFTs = col.findNFTs(prevFloor, 20);
                    Output.println(collectionName + ": " + NFTs.size() + " NFTs were found");
                    for (FloorPriceChangeListener listener : listeners) {
                        for (NFT nftData : NFTs) {
                            listener.send(nftData);
                            Output.println(nftData.toString());
                        }
                    }
                    col.setFloorPrice(floorPrice);
                }
                col.setAvgPrice24hr(avgPrice);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private JSONObject getResults(String collectionName) throws JSONException {
        String URI = "https://api-mainnet.magiceden.io/rpc/getCollectionEscrowStats/" + collectionName;
        HttpGet httpGet = new HttpGet(URI);
        httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        JSONObject res = executeResponse(httpGet);
        return (JSONObject) res.get("results");
    }

    public static double getNFTCollectionFloorPrice(JSONObject results) {
        return NFTCollection.toDouble(results.get("floorPrice"));
    }

    public static double getNFTCollectionAvgPrice(JSONObject results) {
        return NFTCollection.toDouble(results.get("avgPrice24hr"));
    }

    public static JSONObject executeResponse(HttpRequestBase request) {
        try (CloseableHttpResponse response = client.execute(request)) {
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            return new JSONObject(result);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }
}