import com.binance.client.model.trade.Order;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.io.*;
import java.util.List;
import java.util.Map;

public class SolWalletActivityBot extends TelegramLongPollingBot implements WalletActivityListener, FloorPriceChangeListener {
    private static String BOT_NAME;
    private static String BOT_TOKEN;

    private final String chatID = "136412831";
    private final Main tracker;
    private final NFTCollectionFloorMonitor monitor;
    private final BinanceTradeBot binanceTradeBot;

    public static void main(String[] args)  {
        try {
            // Create the TelegramBotsApi object to register your bots
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Register your newly created AbilityBot
            botsApi.registerBot(new SolWalletActivityBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public SolWalletActivityBot() {
        loadBotDataFromFile();
        tracker = new Main(this);
        monitor = new NFTCollectionFloorMonitor(this, "./data/136412831_collections.txt");
        binanceTradeBot = new BinanceTradeBot(this);
    }

    @Override
    public void print(String text) {
        sendMessage(chatID, text);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            if (text.equalsIgnoreCase("/run") || text.equalsIgnoreCase("/start")) {
                sendMessage(chatID, "Starting");
                monitor.startMonitor();
                sendMessage(chatID, "Collections monitor started");
                tracker.startThreads();
                sendMessage(chatID, "Tracker started");
            } else if (text.equalsIgnoreCase("/stop") || text.equalsIgnoreCase("/interrupt")) {
                tracker.pause();
                monitor.stopMonitor();
                sendMessage(chatID, "Tracker & monitor stopped");
            }  else if (text.equalsIgnoreCase("/restart")) {
                tracker.restart();
                monitor.restart();
                sendMessage(chatID, "Tracker restarted");
            } else if (text.matches("/time \\d+")) {
                int time = Integer.parseInt(text.replaceAll("\\D+",""));
                if (time > 0) {
                    tracker.setTime(time);
                    sendMessage(chatID, "Tracker time set to " + time);
                }
            } else if (text.matches("/mon[tT]ime \\d+")) {
                int time = Integer.parseInt(text.replaceAll("\\D+",""));
                if (time > 0) {
                    monitor.setSleepTime(time);
                    sendMessage(chatID, "Monitor time set to " + time);
                }
            } else if (text.equalsIgnoreCase("/getTime")) {
                sendMessage(chatID,
                        "Tracker sleep time: " + tracker.getTime() + "\nMonitor sleep time: " + monitor.getSleepTime()
                );
            } else if (text.matches("/get[qQ]ueue[sS]ize") || text.matches("/get[qQ][sS]")) {
                sendMessage(chatID, "Queue size: " + tracker.getQueueSize());
            } else if (text.matches("get[tT]]hreads[cC]onditions") || text.matches("/get[tT][cC]")) {
                sendMessage(chatID, tracker.getThreadsConditions());
                sendMessage(chatID, monitor.getThreadCondition());
            } else if (text.equalsIgnoreCase("/wallets")) {
                sendMessage(chatID, tracker.getWallets());
            } else if (text.equalsIgnoreCase("/get")) {
                sendDocument(chatID, "wallets.txt");
                sendDocument(chatID, "lastTransactions.txt");
                sendDocument(chatID, "collections.txt");
                sendMessage(chatID, "Documents were sent");
            } else if (text.contains("The signal only for futures trading") && text.contains("Price:") && text.contains("SL: ≈") && text.contains("TP №1: ≈")) {
                binanceTradeBot.sendMessage(text);
            } else if (text.contains("/getmop") || text.contains("/get[mM]ax[oO]rder[pP]rice")) {
                sendMessage(chatID, "Max order price: " + binanceTradeBot.getMaxOrderPrice());
            } else if (text.contains("/setmop \\d+") || text.contains("/set[mM]ax[oO]rder[pP]rice \\d+")) {
                double price = Double.parseDouble(text.split(" ")[1]);
                binanceTradeBot.setMaxOrderPrice(price);
                sendMessage(chatID, "Max order price: " + binanceTradeBot.getMaxOrderPrice());
            } else {
                sendMessage(chatID, text);
            }
        } else if (update.getMessage().hasDocument()){
            int doc_size = update.getMessage().getDocument().getFileSize();
            if (doc_size > 10_000) {
                sendMessage(chatID, "File is too big");
                return;
            }
            String doc_id = update.getMessage().getDocument().getFileId();
            String doc_name = update.getMessage().getDocument().getFileName();
            String doc_mine = update.getMessage().getDocument().getMimeType();
            String getID = String.valueOf(update.getMessage().getFrom().getId());

            Document document = new Document();
            document.setMimeType(doc_mine);
            document.setFileName(doc_name);
            document.setFileSize(doc_size);
            document.setFileId(doc_id);

            GetFile getFile = new GetFile();
            getFile.setFileId(document.getFileId());
            String filePath;
            try {
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
                filePath = "./data/" + getID + "_" + doc_name;
                downloadFile(file, new File(filePath));
                if (filePath.contains("wallets")) {
                    tracker.loadAccounts(filePath);
                    sendMessage(chatID, "Wallets were saved");
                } else if (filePath.contains("lastTran")) {
                    tracker.loadLastTransactions(filePath);
                    sendMessage(chatID, "Last transactions were saved");
                } else if (filePath.contains("collections")) {
                    monitor.setCollections(filePath);
                    sendMessage(chatID, "Collections were saved");
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.enableHtml(true);
        message.disableWebPagePreview();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendDocument(String chatId, String type) {
        SendDocument sd = SendDocument
                .builder()
                .chatId(chatId)
                .document(new InputFile(new File("./data/" + chatId + "_" + type)))
                .build();
        try {
            execute(sd);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    public void loadBotDataFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(".\\data\\bot.txt"))) {
            BOT_NAME = br.readLine();
            BOT_TOKEN = br.readLine();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            Map<String, String> getenv = System.getenv();
            BOT_NAME = getenv.get("BOT_NAME");
            BOT_TOKEN = getenv.get("BOT_TOKEN");
        }
    }

    @Override
    public void send(NFT data) {
        String text = """
                <a href="https://nft.raydium.io/item-details/key">name</a>
                price prp
                floor flp
                24hr avg price avpr""";
        text = text.replace("key", data.getMintAddress())
                .replace("name", data.getName())
                .replace("prp", Output.df.format(data.getPrice()))
                .replace("flp", Output.df.format(data.getCollectionFloorPrice()))
                .replace("avpr", Output.df.format(data.getCollectionAvgPrice24hr()));
        sendMessage(chatID, text);
    }

    public void sendMsg(Order order) {
        sendMessage(chatID, order.toString());
    }
}
