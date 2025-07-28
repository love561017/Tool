import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TableBackup {

	private Connection conn;
	private StringBuffer test;
	private StringBuffer delete;
	private StringBuffer sb;

	private static Logger logger = LoggerFactory.getLogger(TableBackup.class);

	public static StringBuffer INSERT_SQL = new StringBuffer().append(" SELECT 'INSERT INTO dbo.' + TABLE_NAME +  ")
			.append("   '( %s ) \r\n    VALUES( %s ) \r\n GO' AS INSERT_SQL ")
			.append("   , STRING_AGG(COLUMN_NAME, ', ') AS COLUMNS_NAM ")
			.append("   , COUNT(TABLE_NAME) AS COLUMN_COUNT ").append(" FROM INFORMATION_SCHEMA.COLUMNS ")
			.append(" WHERE TABLE_NAME = ? ").append(" GROUP BY TABLE_NAME ");

	public void prcs(String sql) {
		test = new StringBuffer();
		delete = new StringBuffer();
		sb = new StringBuffer();
		sql = sql.replace("\r\n", "\n").replace("\r", "\n");
		String[] arrayOfString = sql.split("\n");
		for (String line : arrayOfString) {
			System.out.println(line);
			genSql(line);
		}
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

	TableBackup(String jdbc) {
		try {
			conn = Main.getConnection(jdbc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void genSql(String sql) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		String table = getStringBetween(sql, " FROM ", " WHERE ");
		try {
			pstmt = conn.prepareStatement(INSERT_SQL.toString());
			pstmt.setString(1, table);
			rs = pstmt.executeQuery();
			String columnsNam = null;
			String insertSql = null;
			String oid = null;
			int columnCount = 0;
			while (rs.next()) {
				insertSql = rs.getString("INSERT_SQL");
				columnsNam = rs.getString("COLUMNS_NAM");
				columnCount = rs.getInt("COLUMN_COUNT");
				oid = columnsNam.split(",")[0].trim();
			}
			pstmt = conn.prepareStatement(sql.replace("*", columnsNam));
			rs = pstmt.executeQuery();

			String testSql = null;
			String testSqlVal = "";
			while (rs.next()) {
				String val = "";
				for (int i = 1; i <= columnCount; i++) {
					String tmpVal = rs.getString(i);
					if (null == tmpVal) {
						val += ", " + rs.getString(i);
					} else {
						val += ", '" + rs.getString(i) + "'";
					}
					if (null == testSql) {
						if (i <= 2) {
							testSqlVal += ", 'T'";
						} else {
							if (null == tmpVal) {
								testSqlVal += ", " + rs.getString(i);
							} else {
								testSqlVal += ", '" + rs.getString(i) + "'";
							}
						}
					}
				}
				if (null == testSql) {
					testSql = String.format(insertSql, columnsNam, testSqlVal.replaceFirst(",", ""));
				}
				sb.append(String.format(insertSql, columnsNam, val.replaceFirst(",", "")));
				sb.append("\r\n");
//				System.out.println(String.format(insertSql, columnsNam, val.replaceFirst(",", "")));
			}
			delete.append("DELETE " + sql.substring(sql.indexOf("*") + 1));
			delete.append("\r\n");
			delete.append("GO");
			delete.append("\r\n");
			
			test.append("--新增測試資料\r\n");
			test.append(testSql);
			test.append("\r\n");
			test.append("--刪除測試資料\r\n");
			test.append("DELETE FROM " + table + " WHERE " + oid + " = 'T' \r\n GO ");
			test.append("\r\n");
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

	public StringBuffer getSb() {
		return sb;
	}

	public void setSb(StringBuffer sb) {
		this.sb = sb;
	}

}
