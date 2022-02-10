import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class Output {
    static final DecimalFormat df = new DecimalFormat("#.###", new DecimalFormatSymbols(Locale.US));

    public static void println(String text) {
        System.out.println(
                ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM))
                        + " " + text
        );
    }
}