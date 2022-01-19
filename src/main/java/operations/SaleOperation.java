package operations;

public class SaleOperation extends Operation {
    public String buyer;
    public String seller;
    public String token;
    public double amount;
    public String marketplace;

    public SaleOperation(String buyer, String seller, String token, double amount, String marketplace) {
        this.buyer = buyer;
        this.seller = seller;
        this.token = token;
        this.amount = amount;
        this.marketplace = marketplace;
    }
}