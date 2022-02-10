package operations;

public class DelistingOperation extends Operation {
    public final String user;
    public final String token;
    public final String marketplace;

    public DelistingOperation(String user, String token, String marketplace) {
        this.user = user;
        this.token = token;
        this.marketplace = marketplace;
    }
}
