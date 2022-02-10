package operations;

public class SaleOperation extends Operation {
    public final String buyer;
    public final String seller;
    public final String token;
    public final double amount;
    public final String marketplace;

    public SaleOperation(String buyer, String seller, String token, double amount, String marketplace) {
        this.buyer = buyer;
        this.seller = seller;
        this.token = token;
        this.amount = amount;
        this.marketplace = marketplace;
    }
}