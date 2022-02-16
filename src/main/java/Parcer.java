import io.swagger.models.auth.In;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Parcer {
    private final Map<String, Double> steps;
    private String filePath;

    public Parcer(String filePath) {
        this.filePath = filePath;
        steps = new HashMap<>();
        fillMaps(filePath);
    }

    private void fillMaps(String filePath) {
        Path fileName = Path.of(filePath);
        String JSON = "";
        try {
            JSON = Files.readString(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject jsonObj = new JSONObject(JSON);
        JSONArray symbols = jsonObj.getJSONArray("symbols");
        for (Object jo : symbols) {
            String currency = ((JSONObject) jo).getString("symbol");
            JSONArray filters = ((JSONObject) jo).getJSONArray("filters");
            JSONObject lotSizeFilterType = filters.getJSONObject(2);
            double stepSize = Double.parseDouble(lotSizeFilterType.getString("stepSize"));
            steps.put(currency, stepSize);
        }
    }

    public Map<String, Double> getStepsMap() {
        return steps;
    }
}
