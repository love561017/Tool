


import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class LineBoot {
    public static void main(String[] args) throws Exception {
        // 你的 LINE Bot Channel access token
        String channelToken = "x1D7qvyKMZ/yyG7wFVA7AQlfLHXX6ACnMF/zytvINl2CtBmw5mcDqyki+aEgsAM0gbDK3P2Xbzo+OPpPwVD1cMnHdMctFQB10iR2O+F8nKv1nZ2tXzc2f0u2rMmhUx7XUKAX2E40TY0MXYQNf0oX7gdB04t89/1O/w1cDnyilFU=";
        String userId = "2007791527";

        String apiUrl = "https://api.line.me/v2/bot/message/push";
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // 設定 HTTP Header
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + channelToken);

        // 訊息內容 JSON
        JSONObject json = new JSONObject();
        json.put("to", userId);
        JSONArray messages = new JSONArray();
        JSONObject subMsg = new JSONObject();
        subMsg.put("type", "text");
        subMsg.put("text", "早安！這是 Java 發出的 LINE 訊息。");
        messages.add(subMsg);
        json.put("messages", messages);
        System.out.println(json.toString());
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.toString().getBytes("utf-8"));
        }

        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);
    }
}
