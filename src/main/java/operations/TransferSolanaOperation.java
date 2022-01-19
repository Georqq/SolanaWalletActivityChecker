package operations;

public class TransferSolanaOperation extends Operation {
    public String sender;
    public String receiver;
    public double amount;

    public TransferSolanaOperation(String sender, String receiver, double amount) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
    }
}
