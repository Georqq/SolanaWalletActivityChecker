public class NFT {
    private String name;
    private String mintAddress;
    private double price;
    private NFTCollection collection;

    public NFT(String name, String token, double price) {
        this.name = name;
        this.mintAddress = token;
        this.price = price;
    }

    public NFT(String name, String token, double price, NFTCollection collection) {
        this.name = name;
        this.mintAddress = token;
        this.price = price;
        this.collection = collection;
    }

    public String getName() {
        return name;
    }

    public String getMintAddress() {
        return mintAddress;
    }

    public double getPrice() {
        return price;
    }

    public NFTCollection getCollection() {
        return collection;
    }

    public double getCollectionFloorPrice() {
        return collection.getActualFloorPrice();
    }

    public double getCollectionAvgPrice24hr() {
        return collection.getAvgPrice24hr();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMintAddress(String mintAddress) {
        this.mintAddress = mintAddress;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setCollection(NFTCollection collection) {
        this.collection = collection;
    }

    @Override
    public String toString() {
        return "name: " + name +
                " address: " + mintAddress +
                " price: " + Output.df.format(price) +
                " floor: " + Output.df.format(getCollectionFloorPrice()) +
                " avg 24 hrs sale price: " + Output.df.format(collection.getAvgPrice24hr());
    }
}