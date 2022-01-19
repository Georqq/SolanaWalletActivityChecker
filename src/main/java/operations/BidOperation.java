package operations;

public class BidOperation extends Operation {
    public String user;
    public double amount;
    public String marketplace;

    public BidOperation(String user, double amount, String marketplace) {
        this.user = user;
        this.amount = amount;
        this.marketplace = marketplace;
    }
}
