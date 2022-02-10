package operations;

public class ListingOperation extends Operation {
    public final String user;
    public final String token;
    public final String marketplace;
    public final double price;

    public ListingOperation(String user, String token, String marketplace, double price) {
        this.user = user;
        this.token = token;
        this.marketplace = marketplace;
        this.price = price;
    }
}
