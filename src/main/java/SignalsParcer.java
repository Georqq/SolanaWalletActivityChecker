import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignalsParcer {
    public static void main(String[] args) {
        Path fileName = Path.of("E:\\Projects\\SolanaWalletActivityChecker\\signals.txt");
        String JSON = "";
        try {
            JSON = Files.readString(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject jsonObj = new JSONObject(JSON);
        JSONArray messages = jsonObj.getJSONArray("messages");
        int numberOfWins = 0;
        int[] numberOfWinsAvg = new int[]{0, 0, 0, 0, 0, 0};
        int numOfLoses = 0;
        int[] numberOfLosesAvg = new int[]{0, 0, 0, 0, 0, 0};
        for (Object jo : messages) {
            String text = jo.toString();
            if (text.contains("#FUTURE")) {
                if (text.contains("#WIN")) {
                    numberOfWins++;
                    int numOfAvgs = getNum(text);
                    numberOfWinsAvg[numOfAvgs]++;
                } else if (text.contains("#LOSE")) {
                    numOfLoses++;
                    int numOfAvgs = getNum(text);
                    numberOfLosesAvg[numOfAvgs]++;
                }
            }
        }
        System.out.println("Interval: " + ((JSONObject) messages.get(0)).getString("date") + " - " + ((JSONObject) messages.get(messages.length() - 1)).getString("date"));
        System.out.println("numberOfWins " + numberOfWins);
        System.out.println("numberOfWinsAvg " + Arrays.toString(numberOfWinsAvg));

        System.out.println("numOfLoses " + numOfLoses);
        System.out.println("numberOfLosesAvg " + Arrays.toString(numberOfLosesAvg));
    }

    private static int getNum(String text) {
        Pattern pattern = Pattern.compile("Averaging: (\\d)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            return -1;
        }
    }
}
