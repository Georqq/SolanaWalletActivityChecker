import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NFTCollection {
    private String name;
    private double floorPrice;
    private double avgPrice24hr;
    private List<NFT> NFTs;

    public NFTCollection(String name, double floorPrice, double avgPrice24hr) {
        this.name = name;
        this.floorPrice = floorPrice;
        this.avgPrice24hr = avgPrice24hr;
        NFTs = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public double getFloorPrice() {
        return floorPrice;
    }

    public double getAvgPrice24hr() {
        return avgPrice24hr;
    }

    public List<NFT> getNFTs() {
        return NFTs;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFloorPrice(double floorPrice) {
        this.floorPrice = floorPrice;
    }

    public void setAvgPrice24hr(double avgPrice24hr) {
        this.avgPrice24hr = avgPrice24hr;
    }

    public void setNFTs(List<NFT> NFTs) {
        this.NFTs = NFTs;
    }

    public List<NFT> findNFTs(double expectedPrice, int limit) {
        String URI = "https://api-mainnet.magiceden.io/rpc/getListedNFTsByQuery?nowait=true&q=";
        String q = """
            {"$match":{"collectionSymbol":"colName"},"$sort":{"takerAmount":1,"createdAt":-1},"$skip":0,"$limit":limit}""";
        q = URLEncoder.encode(q, StandardCharsets.UTF_8)
                .replace("colName", name)
                .replace("limit", String.valueOf(limit));
        HttpGet httpGet = new HttpGet(URI + q);
        httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        List<NFT> NFTs = new ArrayList<>();
        JSONObject res = NFTCollectionFloorMonitor.executeResponse(httpGet);
        JSONArray results = (JSONArray) res.get("results");
        for (Object o : results) {
            JSONObject jo = (JSONObject) o;
            double price = NFTCollection.toDouble(jo.get("price"));
            if (price <= expectedPrice) {
                String token = jo.getString("mintAddress");
                String NFTName = getNFTName(token);
                NFT NFT = new NFT(NFTName, token, price, this);
                this.add(NFT);
                NFTs.add(NFT);
            }
        }
        return NFTs;
    }

    public void add(NFT nft) {
        NFTs.add(nft);
    }

    public NFT findNFT(String mintAddress) {
        JSONObject results = getResult(mintAddress);
        String name = (String) results.get("title");
        double price = toDouble(results.get("price"));
        return new NFT(name, mintAddress, price);
    }

    public String getNFTName(String token) {
        return (String) getResult(token).get("title");
    }

    private JSONObject getResult(String mintAddress) {
        String URI = "https://api-mainnet.magiceden.io/rpc/getNFTByMintAddress/";
        HttpGet httpGet = new HttpGet(URI + mintAddress);
        httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        JSONObject res = NFTCollectionFloorMonitor.executeResponse(httpGet);
        return (JSONObject) res.get("results");
    }

    public static double toDouble(Object price) {
        if (price instanceof BigDecimal) {
            return ((BigDecimal) price).doubleValue();
        } else if (price instanceof Long) {
            return ((long) price);
        } else if (price instanceof Integer) {
            return ((int) price);
        } else if (price instanceof Double) {
            return (double) price;
        }
        System.out.println(price.getClass());
        return 0.;
    }
}
