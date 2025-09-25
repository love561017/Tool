package redmine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang3.StringUtils;

public class UpdateRedmine {

	public static void main(String[] args) {
		try {
			// Step 1: 登入
			String login = "http://172.17.15.101/redmine/login";
			String process = "http://172.17.15.101/redmine/projects/am004/repository/dev004";
			URL loginUrl = new URL(login);
			HttpURLConnection conn = (HttpURLConnection) loginUrl.openConnection();
			conn.setRequestMethod("GET");
			conn.setDoOutput(true); // 允許寫入 request body
            conn.setDoInput(true);  // 允許讀取 response
//			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
//            conn.setRequestProperty("accept-encoding", "gzip, deflate");
            conn.setRequestProperty("accept-language", "zh-TW,zh;q=0.9");
            conn.setRequestProperty("cache-control", "max-age=0");
            conn.setRequestProperty("connection", "keep-alive");
            conn.setRequestProperty("content-length", "233");
            conn.setRequestProperty("host", "172.17.15.101");
            conn.setRequestProperty("origin", "http://172.17.15.101");
            conn.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36");
            try (OutputStream os = conn.getOutputStream()) {
                os.write("username=jason320".getBytes());
            }
			
//			String jsonInputString = "{\"username\":\"jason320\",\"password\":\"123456\"}";
//            try (OutputStream os = conn.getOutputStream()) {
//                byte[] input = jsonInputString.getBytes("utf-8");
//                os.write(input, 0, input.length);
//            }

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
			BufferedReader in1 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			String inputLine1;
			StringBuilder response1 = new StringBuilder();
			while ((inputLine1 = in1.readLine()) != null) {
				response1.append(inputLine1);
			}
			in1.close();
			System.out.println("保護頁面內容: ");
			System.out.println(response1.toString());
			conn.disconnect();

			// Step 2: 訪問登入後才能看的頁面
			URL protectedUrl = new URL(process);
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

//			String checkEnvUrl = "http://172.17.15.102/RSv2/envList/checkEnv";
//			URL url = new URL(checkEnvUrl);
//			HttpURLConnection conn3 = (HttpURLConnection) url.openConnection();
//
//			// 設定 HTTP Header
//			conn3.setRequestMethod("GET");
//
//			int responseCode = conn3.getResponseCode();
//			System.out.println("Response Code: " + responseCode);
//
//			if (responseCode == HttpURLConnection.HTTP_OK) {
//				// 讀取 HTML 內容
//				BufferedReader in3 = new BufferedReader(new InputStreamReader(conn3.getInputStream(), "UTF-8"));
//				String inputLine3;
//				StringBuilder response3 = new StringBuilder();
//				boolean start = false;
//				int updateTimeNextLine = 4;
//				int i = 0;
//				String lastUpdTime = null;
//				while ((inputLine3 = in3.readLine()) != null) {
//					if (inputLine3.contains("最後更新時間：")) {
//						i = 1;
//					}
//					if (i != 0) {
//						i++;
//					}
//					if (i == updateTimeNextLine) {
//						lastUpdTime = inputLine3.trim();
//					}
//
//					if (inputLine3.contains("<textarea id=\"autoDeployRslt\" class=\"ctrlHide\" disabled>")) {
//						response3.append(inputLine3.split("disabled>")[1]).append("\n");
//						start = true;
//					} else if (start) {
//						if (inputLine3.contains("</textarea>")) {
//							inputLine3 = inputLine3.replace("</textarea>", "");
//							start = false;
//						}
//						response3.append(inputLine3).append("\n");
//					}
//				}
//				in3.close();
//				if (StringUtils.isNotBlank(response3.toString())) {
//					// LineBoot.sendMsg("Ufcc43832ac6b0156fb5d5210a78a5677",
//					// "最後更新時間：" + lastUpdTime);
//					System.out.println(lastUpdTime);
//					// 顯示 HTML 原始碼
//					System.out.println("✅ 抓到的 HTML：\n");
//					System.out.println(response3.toString());
//
//					if (!lastUpdTime
//							.contains(DateFormatUtils.format(new Date(System.currentTimeMillis()), "yyyy/MM/dd"))
//							|| response3.toString().contains("錯誤") || response3.toString().contains("失敗")) {
////									LineBoot.sendMsg("Ca5feb992d44b876a4c75fb177f9fd06c", "自動布板發生錯誤請輪值人員處理");
//					}
////								LineBoot.sendMsg("Ca5feb992d44b876a4c75fb177f9fd06c", response3.toString());
//					// Ca5feb992d44b876a4c75fb177f9fd06c//群組
//					// LineBoot.sendMsg("Ufcc43832ac6b0156fb5d5210a78a5677",
//					// response.toString());//我自己
//					// LineBoot.sendMsg("Cb85261cf8754839f7b93627145058153",
//					// response.toString());//群組測試
//				}
//			} else {
//				System.out.println("❌ 無法連線，HTTP 錯誤碼：" + responseCode);
//			}
		} catch (Exception e) {

		}

	}

}
