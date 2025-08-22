package tool.logic;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.Main;
import tool.swing.RptLogToolMain;

public class RptLogTool {

	private Connection conn;
	private StringBuffer test;
	private StringBuffer delete;

	private static Logger logger = LoggerFactory.getLogger(RptLogTool.class);

	public void prcs(RptLogToolMain.RptLogToolParm tp) {
		tp.getLogList().removeAllElements();
		getLog(tp);
	}

	public void close() {
		if (null != conn) {
			try {
				conn.close();
				conn = null;
			} catch (SQLException e) {
				logger.error(e.toString());
			}
		}
	}

	public static String getStringBetween(String val, String str, String end) {
		val = val.toUpperCase();
		int strIndex = val.indexOf(str);
		int endIndex = val.indexOf(end);
		return StringUtils.substring(val, strIndex + 5, endIndex).trim();
	}

	public RptLogTool(String jdbc) {
		try {
			conn = Main.getConnection(jdbc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class RptLog {
		private String oid;
		private String progCode;
		private String userId;
		private String rptId;
		private String ver;
		private String rptExpPath;

		private Map<String, Object> logMap;

		private Map<String, Object> paramMap;

		public String getOid() {
			return oid;
		}

		public void setOid(String oid) {
			this.oid = oid;
		}

		public String getProgCode() {
			return progCode;
		}

		public void setProgCode(String progCode) {
			this.progCode = progCode;
		}

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}

		public String getRptId() {
			return rptId;
		}

		public void setRptId(String rptId) {
			this.rptId = rptId;
		}

		public String getVer() {
			return ver;
		}

		public void setVer(String ver) {
			this.ver = ver;
		}

		public String getRptExpPath() {
			return rptExpPath;
		}

		public void setRptExpPath(String rptExpPath) {
			this.rptExpPath = rptExpPath;
		}

		public Map<String, Object> getLogMap() {
			return logMap;
		}

		public void setLogMap(Map<String, Object> logMap) {
			this.logMap = logMap;
		}

		public Map<String, Object> getParamMap() {
			return paramMap;
		}

		public void setParamMap(Map<String, Object> paramMap) {
			this.paramMap = paramMap;
		}

		@Override
		public String toString() {
			// JList 會呼叫 toString() 當顯示文字
			StringBuffer sb = new StringBuffer();
			sb.append(StringUtils.rightPad(progCode, 12, " "));
			sb.append("｜");
			sb.append(StringUtils.rightPad(rptId, 15, " "));
			sb.append("｜");
			sb.append(StringUtils.rightPad(StringUtils.left(ver, 23), 23, " "));
			sb.append("｜");
			return sb.toString();
		}

	}

	public void getLog(RptLogToolMain.RptLogToolParm tp) {

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			StringBuffer selectSql = new StringBuffer();
			List<String> values = new ArrayList<>();
			selectSql.append(" SELECT TOP ");
			selectSql.append(StringUtils.isNotBlank(tp.getMaxData().getText()) ? tp.getMaxData().getText() : 100);
			selectSql.append(" * FROM RPT_REPORT_LOG ");
			String Where = " WHERE ";
			if (null != tp.getDatePicker().getDate()) {
				selectSql.append(Where);
				selectSql.append(" RPT_EXEC_TIME LIKE ? ");
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
				values.add(sdf.format(tp.getDatePicker().getDate()) + "%");
				Where = " AND ";
			}
			if (StringUtils.isNotBlank(tp.getActnCde().getText())) {
				selectSql.append(Where);
				selectSql.append(" PROG_CODE = ? ");
				values.add(tp.getActnCde().getText());
				Where = " AND ";
			}
			if (StringUtils.isNotBlank(tp.getUserId().getText())) {
				selectSql.append(Where);
				selectSql.append(" USER_ID = ? ");
				values.add(tp.getUserId().getText());
				Where = " AND ";
			}
			selectSql.append(" ORDER BY VER DESC  ");
			pstmt = conn.prepareStatement(selectSql.toString());
			int i = 1;
			for (String val : values) {
				pstmt.setString(i++, val);
			}
			rs = pstmt.executeQuery();
			//TITLE
			RptLog title = new RptLog();
			title.oid = null;
			title.progCode = "PROG_CODE";
			title.rptExpPath = "RPT_EXP_PATH";
			title.rptId = "RPT_ID";
			title.userId = "USER_ID";
			title.ver = "VER";
			tp.getLogList().addElement(title);
			
			while (rs.next()) {
				RptLog log = new RptLog();
				log.oid = rs.getString("RPT_REPORT_LOG_OID");
				log.progCode = rs.getString("PROG_CODE");
				log.rptExpPath = rs.getString("RPT_EXP_PATH");
				log.rptId = rs.getString("RPT_ID");
				log.userId = rs.getString("USER_ID");
				log.ver = rs.getString("VER");
				tp.getLogList().addElement(log);

				Map<String, Object> map = stringToMapObject(rs.getString("RPT_REPORT_PARMS"));
				Map<String, Object> paramMap = stringToMapObject(MapUtils.getString(map, "paramMap"));
				map.remove("paramMap");
				String sql = MapUtils.getString(map, "sql");
				for (String tmpKey : paramMap.keySet()) {
					String key = "$P{" + tmpKey + "}";
					if (sql.contains(key)) {
						sql = sql.replaceAll("\\$P\\{" + tmpKey + "\\}", "'" + MapUtils.getString(paramMap, tmpKey) + "'");
					}
				}
				map.put("sqlFormatted", sql);
				log.logMap = map;
				log.paramMap = paramMap;
			}
			rs.close();
			pstmt.close();
			rs = null;
			pstmt = null;
		} catch (Exception e) {
			logger.error(e.toString());
			System.out.println(e.toString());
		} finally {
			if (null != rs) {
				try {
					rs.close();
					rs = null;
				} catch (SQLException e) {
					logger.error(e.toString());
				}
			}
			if (null != pstmt) {
				try {
					pstmt.close();
					pstmt = null;
				} catch (SQLException e) {
					logger.error(e.toString());
				}
			}
		}
	}

	public static Map<String, Object> stringToMapObject(String input) {
		Map<String, Object> map = new HashMap<String, Object>();
		String[] nameValuePairs = input.split("&");
		for (String nameValuePair : nameValuePairs) {
			String[] nameValue = nameValuePair.split("=");
			try {
				if (nameValue.length > 0) {
					map.put(URLDecoder.decode(nameValue[0], "UTF-8"),
							nameValue.length > 1 ? URLDecoder.decode(nameValue[1], "UTF-8") : "");
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("This method requires UTF-8 encoding support", e);
			}
		}

		return map;
	}

	public StringBuffer getTest() {
		return test;
	}

	public void setTest(StringBuffer test) {
		this.test = test;
	}

	public StringBuffer getDelete() {
		return delete;
	}

	public void setDelete(StringBuffer delete) {
		this.delete = delete;
	}

}
