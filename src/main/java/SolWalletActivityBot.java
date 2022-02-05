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

public class SolWalletActivityBot extends TelegramLongPollingBot {
    private static String BOT_NAME;
    private static String BOT_TOKEN;

    private String chatID = "136412831";
    private Main tracker;

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
    }

    public void print(String text) {
        sendMessage(chatID, text);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            if (text.equals("/run") || text.equals("/start")) {
                sendMessage(chatID, "Starting");
                tracker.startThreads();
                sendMessage(chatID, "Tracker started");
            } else if (text.equals("/stop")) {
                tracker.pause();
                sendMessage(chatID, "Tracker stopped");
            } else if (text.contains("/time ")) {
                int time = Integer.parseInt(text.replaceAll("\\D+",""));
                if (time > 0) {
                    tracker.setTime(time);
                    sendMessage(chatID, "Time set to " + time);
                }
            } else if (text.contains("/getTime")) {
                sendMessage(chatID, String.valueOf(tracker.getTime()));
            } else if (text.contains("/wallets")) {
                sendMessage(chatID, tracker.getWallets());
            } else if (text.contains("/get")) {
                sendDocument(chatID, "wallets.txt");
                sendDocument(chatID, "lastTransactions.txt");
                sendMessage(chatID, "Documents were sent");
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

    @Override
    public void onRegister() {
        super.onRegister();
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
}
