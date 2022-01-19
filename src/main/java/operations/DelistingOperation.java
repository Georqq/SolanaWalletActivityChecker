package operations;

public class DelistingOperation extends Operation {
    public String user;
    public String token;
    public String marketplace;

    public DelistingOperation(String user, String token, String marketplace) {
        this.user = user;
        this.token = token;
        this.marketplace = marketplace;
    }
}
