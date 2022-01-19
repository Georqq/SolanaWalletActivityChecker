package operations;

public class TransferNFTOperation extends Operation {
    public String sender;
    public String receiver;
    public String token;

    public TransferNFTOperation(String sender, String receiver, String token) {
        this.sender = sender;
        this.receiver = receiver;
        this.token = token;
    }
}
