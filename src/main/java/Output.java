import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.Locale;

public class Output {
    static final DecimalFormat df = new DecimalFormat("#.###", new DecimalFormatSymbols(Locale.US));

    public static void println(String text) {
        System.out.println(
                ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM))
                        + " " + text
        );
    }

    public static void writeJSONToFile(String transaction, JSONObject jsonObj) {
        String JSONBody = jsonObj.toString(2);
        Path file = Paths.get(transaction + ".txt");
        try {
            Files.write(file, Collections.singleton(JSONBody), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(String filePath, String text) {
        try {
            Files.writeString(Paths.get(filePath), text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}