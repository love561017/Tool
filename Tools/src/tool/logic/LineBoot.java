package tool.logic;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class LineBoot {
	private static final String CHANNEL_ACCESS_TOKEN = "x1D7qvyKMZ/yyG7wFVA7AQlfLHXX6ACnMF/zytvINl2CtBmw5mcDqyki+aEgsAM0gbDK3P2Xbzo+OPpPwVD1cMnHdMctFQB10iR2O+F8nKv1nZ2tXzc2f0u2rMmhUx7XUKAX2E40TY0MXYQNf0oX7gdB04t89/1O/w1cDnyilFU=";
	private static final String LINE_BOT_API_URL = "https://api.line.me/v2/bot/";
//	private static final String TEST_GROUP = "Cb85261cf8754839f7b93627145058153";
	private static String TEST_GROUP = null;
	private static final String ME = "Ufcc43832ac6b0156fb5d5210a78a5677";
	private static int lastDateOfWeek;
	private static String userList = null;
	private static String userId = null;
	private static int userIndex;

	// 測試用
	public static void main(String[] args) throws Exception {
		getPorp();
//		sendMsg(TEST_GROUP, "請協助確認本日各環境狀態：", new String[] { ME });
//		sendMsg(TEST_GROUP, "XXXX");
	}

	//測試用
	public static void getPorp() throws Exception {
		String localPath = Paths.get("").toAbsolutePath().toString();
		System.out.println(localPath);
		Properties prop = new Properties();
		try (FileInputStream fs = new FileInputStream(localPath + "/devVersion.properties")) {
			prop.load(fs);
			TEST_GROUP = prop.getProperty("devgroupId");
			lastDateOfWeek = StringUtils.isBlank(prop.getProperty("lastDateOfWeek")) ? 1
					: Integer.parseInt(prop.getProperty("lastDateOfWeek"));
			userList = prop.getProperty("userList");
			userIndex = StringUtils.isBlank(prop.getProperty("userIndex")) ? 0
					: Integer.parseInt(prop.getProperty("userIndex"));
		}
		DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
		if (dayOfWeek.getValue() < lastDateOfWeek) {
			userIndex++;
			userIndex = userIndex % userList.split(",").length;
		}
		lastDateOfWeek = dayOfWeek.getValue();
		userId = userList.split(",")[userIndex];
		System.out.println(userId);
		try (FileOutputStream out = new FileOutputStream(localPath + "/devVersion.properties")) {
			prop.setProperty("userIndex", String.valueOf(userIndex));
			prop.setProperty("lastDateOfWeek", String.valueOf(lastDateOfWeek));
			prop.store(out, "");
		}
	}

	public static void sendMsg(String userId, String msg) throws Exception {
		sendMsg(userId, msg, null);
	}

	public static void sendMsg(String userId, String msg, String[] tagIds) throws Exception {
		String apiUrl = LINE_BOT_API_URL + "message/push";
		URL url = new URL(apiUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		// 設定 HTTP Header
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Authorization", "Bearer " + CHANNEL_ACCESS_TOKEN);

		// 訊息內容 JSON
		JSONObject json = new JSONObject();
		json.put("to", userId);
		JSONArray messages = new JSONArray();
		JSONObject subMsg = new JSONObject();
		subMsg.put("type", "textV2");
		if (null != tagIds && tagIds.length > 0) {
			JSONObject substitution = new JSONObject();
			int index = 0;
			StringBuffer sb = new StringBuffer();
			for (String tagId : tagIds) {
				JSONObject user = new JSONObject();
				String userX = "user" + (index + 1);
				user.put("type", "mention");
				JSONObject mentionee = new JSONObject();
				mentionee.put("type", "user");
				mentionee.put("userId", tagId);
				user.put("mentionee", mentionee);
				substitution.put(userX, user);
				sb.append("{").append(userX).append("} ");
				index++;
			}
			sb.append(msg);
			msg = sb.toString();
			subMsg.put("substitution", substitution);
		}
		subMsg.put("text", msg);
		messages.add(subMsg);
		json.put("messages", messages);
		System.out.println(json.toString());
		try (OutputStream os = conn.getOutputStream()) {
			os.write(json.toString().getBytes("utf-8"));
		}
		int responseCode = conn.getResponseCode();
		System.out.println("Response Code: " + responseCode);
	}

	public static String getUserName(String userId) throws Exception {
		String urlStr = LINE_BOT_API_URL + "profile/" + userId;
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", "Bearer " + CHANNEL_ACCESS_TOKEN);
		conn.setDoInput(true);
		int responseCode = conn.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {

				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}
				String responseBody = sb.toString();
				int idx = responseBody.indexOf("\"displayName\":\"");
				if (idx != -1) {
					int start = idx + 15; // "displayName":" 長度
					int end = responseBody.indexOf("\"", start);
					return responseBody.substring(start, end);
				} else {
					return "(無法找到 userName)";
				}
			}
		} else {
			throw new RuntimeException("HTTP error code: " + responseCode);
		}
	}
}
