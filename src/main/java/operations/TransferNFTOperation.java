package operations;

public class TransferNFTOperation extends Operation {
    public final String sender;
    public final String receiver;
    public final String token;

    public TransferNFTOperation(String sender, String receiver, String token) {
        this.sender = sender;
        this.receiver = receiver;
        this.token = token;
    }
}
