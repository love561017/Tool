import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class Test {
	private static final String CHANNEL_ACCESS_TOKEN = "x1D7qvyKMZ/yyG7wFVA7AQlfLHXX6ACnMF/zytvINl2CtBmw5mcDqyki+aEgsAM0gbDK3P2Xbzo+OPpPwVD1cMnHdMctFQB10iR2O+F8nKv1nZ2tXzc2f0u2rMmhUx7XUKAX2E40TY0MXYQNf0oX7gdB04t89/1O/w1cDnyilFU=";

	public static void main(String[] args) {
		try {
			String userId = "Ufcc43832ac6b0156fb5d5210a78a5677"; // 要查的 userId
			String name = getDisplayName(userId);
			System.out.println("使用者名稱: " + name);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String getDisplayName(String userId) throws Exception {
		// 1. 建立 URL
		String urlStr = "https://api.line.me/v2/bot/profile/" + userId;
		URL url = new URL(urlStr);

		// 2. 開連線
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", "Bearer " + CHANNEL_ACCESS_TOKEN);
		conn.setDoInput(true);

		// 3. 讀取回應
		int responseCode = conn.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {

				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}

				String responseBody = sb.toString();
				// 簡單處理：從 JSON 抓 displayName（這裡沒用外部 JSON lib）
				// 假設回傳格式一定包含 "displayName":"xxx"
				int idx = responseBody.indexOf("\"displayName\":\"");
				if (idx != -1) {
					int start = idx + 15; // "displayName":" 長度
					int end = responseBody.indexOf("\"", start);
					return responseBody.substring(start, end);
				} else {
					return "(無法找到 displayName)";
				}
			}
		} else {
			throw new RuntimeException("HTTP error code: " + responseCode);
		}
	}

}
