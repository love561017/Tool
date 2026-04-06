package tool;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.helper.ConnectionHelper;
import tool.swing.DtoMakerMain;
import tool.swing.Encrypt;
import tool.swing.RptLogToolMain;
import tool.swing.SqlFmtMain;
import tool.swing.TableBackupMain;

public class Main extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1771327149780136836L;
	private static Logger logger = LoggerFactory.getLogger(Main.class);
	private static JFrame frame;

	private static Connection connection;
	public static String USER_ID; 

	private final static String GET_CLOUMNS_NAME = "SELECT c.COLUMN_NAME\r\n"
			+ "  , MAX(ep.value) AS 'Column Description'\r\n" + "FROM INFORMATION_SCHEMA.COLUMNS c\r\n"
			+ "JOIN sys.extended_properties ep\r\n" + "  ON ep.major_id = OBJECT_ID(c.TABLE_NAME)\r\n"
			+ "    AND ep.name = 'COMMENT' AND ep.minor_id = c.ORDINAL_POSITION\r\n" + "GROUP BY c.COLUMN_NAME";

	private final static String GET_TABLES_NAME = "SELECT c.TABLE_NAME\r\n" + "  , ep.value AS 'Table Description'\r\n"
			+ "FROM INFORMATION_SCHEMA.TABLES c\r\n" + "JOIN sys.extended_properties ep\r\n"
			+ "  ON ep.major_id = OBJECT_ID(c.TABLE_NAME)\r\n" + "  AND ep.name = 'COMMENT'  AND ep.minor_id = 0";

	public static Map<String, String> tableNamesMap = new HashMap<>();
	public static Map<String, String> tableColumnsMap = new HashMap<>();
	public static Map<String, String> dataSourceMap = new LinkedHashMap<>();
	
	
	public static void main(String[] args) {
		try {
			getPorp();
			dataSourceMap.put("永豐", "SINOPAC");
			dataSourceMap.put("玉山", "ESUN");
			dataSourceMap.put("台新", "TSIB");
			dataSourceMap.put("中信", "CTBC");
			
			dataSourceMap.put("LINEBANK", "LINEBANK");
			
			
			
			
			Font f = new Font("微軟正黑體", 0, 14);
			frame = new JFrame();
			frame.setBounds(100, 100, 450, 300);
			frame.setDefaultCloseOperation(3);
			frame.setSize(800, 830);
			
			JTabbedPane tp = new JTabbedPane();
			frame.add(tp, BorderLayout.CENTER);
			
			SqlFmtMain sqlWindow = new SqlFmtMain();
			tp.addTab("SQL 格式化", sqlWindow.initialize(f));

			Encrypt encrypt = new Encrypt();
			tp.addTab("加密/解密", encrypt.initialize(f));
			
			TableBackupMain tableBackupMain = new TableBackupMain();
			tp.addTab("資料備份", tableBackupMain.initialize(f));
			
			RptLogToolMain rptLogToolMain = new RptLogToolMain();
			tp.addTab("報表LOG", rptLogToolMain.initialize(f));
			
			DtoMakerMain dtoMakerMain = new DtoMakerMain();
			tp.addTab("DTO產生", dtoMakerMain.initialize(f));

			

			frame.setVisible(true);
			frame.setAlwaysOnTop(true);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public static void getPorp() throws Exception {
		String localPath = Paths.get("").toAbsolutePath().toString();
		System.out.println(localPath);
		Properties prop = new Properties();
		try (FileInputStream fs = new FileInputStream(localPath + "/jdbc.properties")) {
			prop.load(fs);
		}
		USER_ID = prop.getProperty("userId");
	}
	
	public static Connection getConnection(String jdbc) throws Exception {
		if (null != connection && connection.isClosed()) {
			connection = null;
		}
		if (null == connection) {
			try {
				connection = ConnectionHelper.getInstence().getConnection(jdbc);
				connection.setAutoCommit(false);
			} catch (Exception e) {
				throw e;
			}
		}
		return connection;
	}

	public static String getNames(String jdbc) {
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		String result = "";
		try {
			conn = getConnection(jdbc);
			pstmt = conn.prepareStatement(GET_TABLES_NAME);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				tableNamesMap.put(rs.getString("TABLE_NAME"), rs.getString("Table Description"));
			}
			pstmt = conn.prepareStatement(GET_CLOUMNS_NAME);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				tableColumnsMap.put(rs.getString("COLUMN_NAME"), rs.getString("Column Description"));
			}
			rs.close();
			pstmt.close();
			conn.close();
			rs = null;
			pstmt = null;
			conn = null;
			if (StringUtils.isNotBlank(result)) {
				return result;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (null != rs) {
				try {
					rs.close();
					rs = null;
				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				}
			}
			if (null != pstmt) {
				try {
					pstmt.close();
					pstmt = null;
				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				}
			}
			if (null != conn) {
				try {
					conn.close();
					conn = null;
				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
		return result;
	}

}
