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
import java.util.concurrent.*;

public class NFTCollectionFloorMonitor {
    private static CloseableHttpClient client;
    private List<FloorPriceChangeListener> listeners;
    private long sleepTime = 45_000;
    private final String floorCollectionsFilePath;
    private List<NFTCollection> collections;
    private ScheduledThreadPoolExecutor scheduleExecutor;
    private ScheduledFuture<?> scheduleManager;
    private Runnable monitor;

    public NFTCollectionFloorMonitor(FloorPriceChangeListener listener, String filePath) {
        init();
        listeners.add(listener);
        setCollections(filePath);
        floorCollectionsFilePath = filePath;
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
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(";")) {
                    continue;
                }
                String[] values = line.split(" ");
                String collectionName = values[0];
                double expectedFloorPrice = Double.parseDouble(values[1]);
                double realFloorPrice = 0.0;
                double avgSalePrice = 0.0;
                if (values.length > 2) {
                    realFloorPrice = Double.parseDouble(values[2]);
                    if (values.length > 3) {
                        avgSalePrice = Double.parseDouble(values[3]);
                    }
                }
                collections.add(new NFTCollection(collectionName, expectedFloorPrice, realFloorPrice, avgSalePrice));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    private void fillC(Map<String, Double> collectionsFloorPrices) {
        for (Map.Entry<String, Double> entry : collectionsFloorPrices.entrySet()) {
            String name = entry.getKey();
            double floorPrice = entry.getValue();
            double avgSalePrice24hr = -1.;
            NFTCollection collection = new NFTCollection(name, floorPrice, avgSalePrice24hr);
            collections.add(collection);
        }
    }

     */

    public void addListener(FloorPriceChangeListener listener) {
        listeners.add(listener);
    }

    public void setSleepTime(int time) {
        this.sleepTime = time * 1000L;
        changePeriodTime();
        Output.println("Waiting time was set to " + time + " s");
    }

    public int getSleepTime() {
        return (int) (sleepTime / 1000);
    }

    private void changePeriodTime() {
        if (scheduleManager != null) {
            if (!scheduleManager.isCancelled()) {
                scheduleManager.cancel(false);
                scheduleManager = scheduleExecutor.scheduleAtFixedRate(monitor, 0, sleepTime, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void restart() {
        stopMonitor();
        startMonitor();
    }

    public void startMonitor() {
        monitor = () -> {
            updatePrices();
            Output.println("Sleeping for ~" + (sleepTime / 1000) + " s");
        };
        scheduleExecutor = new ScheduledThreadPoolExecutor(1);
        scheduleManager = scheduleExecutor.scheduleAtFixedRate(monitor, 0, sleepTime, TimeUnit.MILLISECONDS);
    }

    public void stopMonitor() {
        if (!scheduleManager.isCancelled()) {
            exportFloorCollections();
            scheduleManager.cancel(false);
            scheduleExecutor.shutdown();
            if (scheduleManager.isCancelled()) {
                Output.println("Monitor is interrupted");
            }
        } else {
            Output.println("Monitor is already interrupted");
        }
    }

    private void exportFloorCollections() {
        try (BufferedWriter bf = new BufferedWriter(new FileWriter(floorCollectionsFilePath))) {
            bf.write("; collectionName expectedFloorPrice ActualFloorPrice AvgSalePrice24hr");
            for (NFTCollection col : collections) {
                bf.write(col.toString());
                bf.newLine();
            }
            bf.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updatePrices() {
        Output.println("Updating floor and avg prices for " + collections.size() + " collections");
        for (NFTCollection col : collections) {
            String collectionName = col.getName();
            try {
                JSONObject results = getResults(collectionName);
                double actualFloorPrice = getNFTCollectionFloorPrice(results);
                double avgSalePrice24hr = getNFTCollectionAvgPrice(results);
                double expectedFloorPrice = col.getExpectedFloorPrice();
                if (actualFloorPrice < expectedFloorPrice && actualFloorPrice > 0.001) {
                    Output.println(collectionName + ": current floor value is " + actualFloorPrice + ", previous floor is " + expectedFloorPrice);
                    List<NFT> NFTs = col.findNFTs(expectedFloorPrice, 20);
                    Output.println(collectionName + ": " + NFTs.size() + " NFTs were found");
                    for (FloorPriceChangeListener listener : listeners) {
                        for (NFT nftData : NFTs) {
                            listener.send(nftData);
                            Output.println(nftData.toString());
                        }
                    }
                    col.setExpectedFloorPrice(actualFloorPrice);
                    Output.println("Expected floor price was changed for collection " + collectionName + " from " + expectedFloorPrice + " to " + actualFloorPrice);
                }
                col.setActualFloorPrice(actualFloorPrice);
                col.setAvgPrice24hr(avgSalePrice24hr);
            } catch (JSONException e) {
                Output.println("caught");
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
        return NFTCollection.getCorrectedPrice(results.get("floorPrice"));
    }

    public static double getNFTCollectionAvgPrice(JSONObject results) {
        return NFTCollection.getCorrectedPrice(results.get("avgPrice24hr"));
    }

    public static JSONObject executeResponse(HttpRequestBase request) {
        String result = "";
        try (CloseableHttpResponse response = client.execute(request)) {
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity);
            return new JSONObject(result);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            Output.writeToFile("E:\\Projects\\SolanaWalletActivityChecker\\heroku\\data\\failed\\entity.txt", result);
            Output.println("Error, response was written to file");
        }
        return new JSONObject();
    }

    public String getThreadCondition() {
        StringBuilder text = new StringBuilder();
        if (scheduleManager == null) {
            text.append("Monitor thread is null\n");
        } else if (scheduleManager.isCancelled()) {
            text.append("Monitor thread is cancelled\n");
        } else if (!scheduleManager.isCancelled()) {
            text.append("Monitor thread is working\n");
        }
        Output.println(text.toString());
        return text.toString();
    }
}