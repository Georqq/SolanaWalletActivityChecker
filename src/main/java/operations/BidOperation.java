package operations;

public class BidOperation extends Operation {
    public final String user;
    public final double amount;
    public final String marketplace;

    public BidOperation(String user, double amount, String marketplace) {
        this.user = user;
        this.amount = amount;
        this.marketplace = marketplace;
    }
}
