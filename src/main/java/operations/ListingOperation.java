package operations;

public class ListingOperation extends Operation {
    public String user;
    public String token;
    public String marketplace;

    public ListingOperation(String user, String token, String marketplace) {
        this.user = user;
        this.token = token;
        this.marketplace = marketplace;
    }
}
