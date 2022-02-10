package operations;

public class MintOperation extends Operation {
    public final String minter;
    public final String token;
    public final double amount;

    public MintOperation(String minter, String token, double amount) {
        this.minter = minter;
        this.token = token;
        this.amount = amount;
    }
}
