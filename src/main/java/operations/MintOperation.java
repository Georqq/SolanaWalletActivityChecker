package operations;

public class MintOperation extends Operation {
    public String minter;
    public String token;
    public double amount;

    public MintOperation(String minter, String token, double amount) {
        this.minter = minter;
        this.token = token;
        this.amount = amount;
    }
}
