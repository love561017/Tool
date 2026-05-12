package tool.logic;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.Main;
import tool.helper.StringHelper;
import tool.swing.TableBackupMain;

public class TableBackup {

	private Connection conn;
	private boolean isDb2;
	private StringBuffer test;
	private StringBuffer delete;

	private static Logger logger = LoggerFactory.getLogger(TableBackup.class);

	public static StringBuffer INSERT_SQL = new StringBuffer().append(" SELECT 'INSERT INTO dbo.' + TABLE_NAME +  ")
			.append("   '( %s ) \r\n    VALUES( %s ) \r\n GO' AS INSERT_SQL ")
			.append("   , STRING_AGG(CAST(COLUMN_NAME AS NVARCHAR(MAX)), ', ') AS COLUMNS_NAM ")
			.append("   , STRING_AGG(CAST(DATA_TYPE AS NVARCHAR(MAX)), ', ') AS DATA_TYPE ")
			.append("   , COUNT(TABLE_NAME) AS COLUMN_COUNT ").append(" FROM INFORMATION_SCHEMA.COLUMNS ")
			.append(" WHERE TABLE_NAME = ? ").append(" GROUP BY TABLE_NAME ");

	private static final String INSERT_SQL_DB2 =
			" SELECT LISTAGG(COLNAME, ', ') WITHIN GROUP (ORDER BY COLNO) AS COLUMNS_NAM"
			+ " , LISTAGG(TYPENAME, ', ') WITHIN GROUP (ORDER BY COLNO) AS DATA_TYPE"
			+ " , COUNT(*) AS COLUMN_COUNT"
			+ " FROM SYSCAT.COLUMNS"
			+ " WHERE TABNAME = ? AND TABSCHEMA = CURRENT SCHEMA";

	public void prcs(TableBackupMain.TableBackupParm tp) {
		test = new StringBuffer();
		delete = new StringBuffer();
		String[] arrayOfString = tp.getTxtSql().getText().replace("\r\n", "\n").replace("\r", "\n").split("\n");
		for (String line : arrayOfString) {
			System.out.println(line);
			genSql(line, tp);
		}
		if (tp.getRb1().isSelected()) {
			tp.getTxtInsertSql().append("***媒體檔已產生***");
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

	public TableBackup(String jdbc) {
		isDb2 = jdbc.startsWith("DB2:");
		try {
			conn = Main.getConnection(jdbc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//可以被數值化的全部都數值化，如果欄位是chart會自行隱含轉換
	public boolean ynDecimal(String tmpVal) {
		try {
			new BigDecimal(tmpVal);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public void genSql(String sql, TableBackupMain.TableBackupParm tp) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		BufferedWriter writer = null;
		String table = getStringBetween(sql, " FROM ", " WHERE ");
		try {
			pstmt = conn.prepareStatement(isDb2 ? INSERT_SQL_DB2 : INSERT_SQL.toString());
			pstmt.setString(1, table);
			rs = pstmt.executeQuery();
			String columnsNam = null;
			String insertSql = null;
			String oid = null;
			String[] dataType = null;
			int columnCount = 0;
			while (rs.next()) {
				if (!isDb2) insertSql = rs.getString("INSERT_SQL");
				columnsNam = rs.getString("COLUMNS_NAM");
				columnCount = rs.getInt("COLUMN_COUNT");
				dataType = rs.getString("DATA_TYPE").split(", ");
				oid = columnsNam.split(",")[0].trim();
			}
			if (isDb2) {
				insertSql = "INSERT INTO " + table.trim() + "( %s )\nVALUES( %s )";
			}
			pstmt = conn.prepareStatement(sql.replace("*", columnsNam));
			rs = pstmt.executeQuery();

			String testSql = null;
			String testSqlVal = "";
			if (tp.getRb1().isSelected()) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				File file = new File(tp.getPath().getText() + "\\" + sdf.format(Calendar.getInstance().getTime()));
				if (!file.exists()) {
					file.mkdirs();
				}
				writer = new BufferedWriter(new FileWriter(tp.getPath().getText() + "\\"
						+ sdf.format(Calendar.getInstance().getTime()) + "\\" + table.trim() + ".txt"));
			}
			String insertSqlLine = null;
			while (rs.next()) {
				String val = null;
				for (int i = 1; i <= columnCount; i++) {
					String tmpVal = rs.getString(i);
					if (null == val) {
						val = tp.getRb3().isSelected() ? "REPLACE(NEWID(), '-', '')" : "'" + rs.getString(i) + "'";
					} else {
						if (null == tmpVal || StringHelper.ynMatch(dataType[i-1], "decimal", "int")) {
							val += ", " + rs.getString(i);
						} else {
							val += ", '" + rs.getString(i) + "'";
						}
					}

					if (null == testSql) {
						if (i == 1) {
							testSqlVal += "'T'";
						} else {
							if (i <= 2) {
								testSqlVal += ", 'T'";
							} else {
								if (null == tmpVal || StringHelper.ynMatch(dataType[i-1], "decimal", "int")) {
									testSqlVal += ", " + rs.getString(i);
								} else {
									testSqlVal += ", '" + rs.getString(i) + "'";
								}
							}
						}
					}
				}
				if (null == testSql) {
					testSql = String.format(insertSql, columnsNam, testSqlVal);
				}
				insertSqlLine = String.format(insertSql, columnsNam, val);
				if (tp.getRb1().isSelected()) {
					writer.write(insertSqlLine);
					writer.newLine();
				} else if (tp.getRb2().isSelected()) {
					tp.getTxtInsertSql().append(insertSqlLine);
					tp.getTxtInsertSql().append("\r\n");
				}
//				System.out.println(String.format(insertSql, columnsNam, val.replaceFirst(",", "")));
			}
			delete.append("DELETE " + sql.substring(sql.indexOf("*") + 1));
			delete.append("\r\n");
			if (!isDb2) delete.append("GO\r\n");

			test.append("--新增測試資料\r\n");
			test.append(testSql);
			test.append("\r\n");
			test.append("--刪除測試資料\r\n");
			test.append("DELETE FROM " + table + " WHERE " + oid + " = 'T'\r\n");
			if (!isDb2) test.append("GO\r\n");
			rs.close();
			pstmt.close();
			if (null != writer) {
				writer.close();
				writer = null;
			}
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
			if (null != writer) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
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

}
