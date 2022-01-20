import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

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
        tracker.loadAccounts(".\\data\\wallets.txt");
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
                if (time > 5) {
                    tracker.setTime(time);
                    sendMessage(chatID, "Time set to " + time);
                }
            } else if (text.contains("/getTime")) {
                sendMessage(chatID, String.valueOf(tracker.getTime()));
            } else {
                sendMessage(chatID, "pff");
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
        }
    }
}
