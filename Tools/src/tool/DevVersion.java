package tool;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import tool.logic.LineBoot;

public class DevVersion {
	// DEV群組id
	private static String DEV_GROUP = null;
	// rs網址
	private static String RS_URL = null;
	// 前次執行星期幾
	private static int LAST_DATE_OF_WEEK;
	// 輪值人員id
	private static String USER_LIST = null;
	// 值日生
	private static String USER_ID = null;
	// 值日生INDEX
	private static int USER_INDEX;

	public static void getPorp() throws Exception {
		String localPath = Paths.get("").toAbsolutePath().toString();
		System.out.println(localPath);
		Properties prop = new Properties();
		try (FileInputStream fs = new FileInputStream(localPath + "/devVersion.properties")) {
			prop.load(fs);
			DEV_GROUP = prop.getProperty("devgroupId");
			RS_URL = "http://" + prop.getProperty("rsUrl");
			LAST_DATE_OF_WEEK = StringUtils.isBlank(prop.getProperty("lastDateOfWeek")) ? 1
					: Integer.parseInt(prop.getProperty("lastDateOfWeek"));
			USER_LIST = prop.getProperty("userList");
			USER_INDEX = StringUtils.isBlank(prop.getProperty("userIndex")) ? 0
					: Integer.parseInt(prop.getProperty("userIndex"));
		}
		DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
		if (dayOfWeek.getValue() < LAST_DATE_OF_WEEK) {
			USER_INDEX++;
			USER_INDEX = USER_INDEX % USER_LIST.split(",").length;
		}
		LAST_DATE_OF_WEEK = dayOfWeek.getValue();
		USER_ID = USER_LIST.split(",")[USER_INDEX];
		try (FileOutputStream out = new FileOutputStream(localPath + "/devVersion.properties")) {
			prop.setProperty("userIndex", String.valueOf(USER_INDEX));
			prop.setProperty("lastDateOfWeek", String.valueOf(LAST_DATE_OF_WEEK));
			prop.store(out, "");
		}
	}

	public static void main(String[] args) throws Exception {
		// Step 0: 取參數
		getPorp();
		// Step 1: 登入
		String login = RS_URL + "?ukey=jason320&upwd=123456";
		String process = RS_URL + "/envList/process";
		URL loginUrl = new URL(login);
		HttpURLConnection conn = (HttpURLConnection) loginUrl.openConnection();
		conn.setRequestMethod("GET");

		// 取得登入回傳的 Cookie（例如 JSESSIONID）
		List<String> cookiesHeader = conn.getHeaderFields().get("Set-Cookie");
		String cookieString = "";
		if (cookiesHeader != null) {
			StringBuilder cookieBuilder = new StringBuilder();
			for (String cookie : cookiesHeader) {
				String sessionCookie = cookie.split(";", 2)[0]; // 只取前半段
																// (cookie=value)
				cookieBuilder.append(sessionCookie).append("; ");
			}
			cookieString = cookieBuilder.toString();
		}

		// Optional: 檢查登入結果
		System.out.println("登入回應碼: " + conn.getResponseCode());
		conn.disconnect();

		// Step 2: 訪問登入後才能看的頁面
		URL protectedUrl = new URL(process); // 改成你想看的頁面
		HttpURLConnection conn2 = (HttpURLConnection) protectedUrl.openConnection();
		conn2.setRequestMethod("GET");
		conn2.setRequestProperty("Cookie", cookieString); // 帶入登入時取得的 Cookie
		conn2.setDoOutput(true);
		conn2.setRequestProperty("Content-Type", "application/json");

		BufferedReader in = new BufferedReader(new InputStreamReader(conn2.getInputStream(), "UTF-8"));
		String inputLine;
		StringBuilder response = new StringBuilder();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		System.out.println("保護頁面內容: ");
		System.out.println(response.toString());
		conn2.disconnect();

		String checkEnvUrl = RS_URL + "/envList/checkEnv";
		URL url = new URL(checkEnvUrl);
		HttpURLConnection conn3 = (HttpURLConnection) url.openConnection();

		// 設定 HTTP Header
		conn3.setRequestMethod("GET");

		int responseCode = conn3.getResponseCode();
		System.out.println("Response Code: " + responseCode);

		if (responseCode == HttpURLConnection.HTTP_OK) {
			// 讀取 HTML 內容
			BufferedReader in3 = new BufferedReader(new InputStreamReader(conn3.getInputStream(), "UTF-8"));
			String inputLine3;
			StringBuilder response3 = new StringBuilder();
			boolean start = false;
			int updateTimeNextLine = 4;
			int i = 0;
			String lastUpdTime = null;
			while ((inputLine3 = in3.readLine()) != null) {
				if (inputLine3.contains("最後更新時間：")) {
					i = 1;
				}
				if (i != 0) {
					i++;
				}
				if (i == updateTimeNextLine) {
					lastUpdTime = inputLine3.trim();
				}

				if (inputLine3.contains("<textarea id=\"autoDeployRslt\" class=\"ctrlHide\" disabled>")) {
					response3.append(inputLine3.split("disabled>")[1]).append("\n");
					start = true;
				} else if (start) {
					if (inputLine3.contains("</textarea>")) {
						inputLine3 = inputLine3.replace("</textarea>", "");
						start = false;
					}
					response3.append(inputLine3).append("\n");
				}
			}
			in3.close();
			if (StringUtils.isNotBlank(response3.toString())) {
				// LineBoot.sendMsg("Ufcc43832ac6b0156fb5d5210a78a5677",
				// "最後更新時間：" + lastUpdTime);
				LineBoot.sendMsg(DEV_GROUP, "  請協助確認本日各環境狀態：", new String[] { USER_ID });
				if (!lastUpdTime.contains(DateFormatUtils.format(new Date(System.currentTimeMillis()), "yyyy/MM/dd"))
						|| response3.toString().contains("錯誤") || response3.toString().contains("失敗")) {
					LineBoot.sendMsg(DEV_GROUP, "自動布板發生錯誤請輪值人員處理");
				}
				LineBoot.sendMsg(DEV_GROUP, response3.toString());
			}
		} else {
			System.out.println("❌ 無法連線，HTTP 錯誤碼：" + responseCode);
		}

	}
}
