import org.json.JSONObject;

public class Error {
    private int code;
    private String message;

    public Error(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public Error(JSONObject jo) {
        JSONObject error = (JSONObject) jo.get("error");
        if (error == null) {
            throw new IllegalArgumentException();
        }
        code = (Integer) error.get("code");
        message = (String) error.get("message");
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "Error: code= " + code + ", message= " + message;
    }
}
