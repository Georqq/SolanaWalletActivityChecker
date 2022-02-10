package operations;

public class TransferSolanaOperation extends Operation {
    public final String sender;
    public final String receiver;
    public final double amount;

    public TransferSolanaOperation(String sender, String receiver, double amount) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
    }
}
