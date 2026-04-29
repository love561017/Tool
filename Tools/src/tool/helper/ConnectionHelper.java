/**
 * 
 */
package tool.helper;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.jasypt.util.text.BasicTextEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ConnectionHelper {
	private static final Logger logger = LoggerFactory.getLogger(ConnectionHelper.class);
	private static ApplicationContext context = null;
	private static Map<String, DataSource> dataSourceMap = new HashMap<>();
	private static ConnectionHelper connHelper = null;
	
	public static ConnectionHelper getInstence() {
		if (connHelper == null) {
			connHelper = new ConnectionHelper();
		}
		return connHelper;
	}

	private ConnectionHelper() {
	}

	public Connection getConnection(String jdbc) throws Exception {
		Connection conn = this.getDataSource(jdbc).getConnection();
		conn.setAutoCommit(false);
		return conn;
	}

	public DataSource getDataSource(String dbName) {
		if (null == dataSourceMap.get(dbName)) {
			context = new ClassPathXmlApplicationContext("spring.xml");
			BasicDataSource basicDataSource = (BasicDataSource) context.getBean("dataSource");
			String localPath = Paths.get("", "jdbc.properties").toAbsolutePath().toString();
			try (FileInputStream fs = new FileInputStream(localPath)) {
				Properties prop = new Properties();
				prop.load(fs);
				BasicTextEncryptor bte = new BasicTextEncryptor();
				bte.setPassword(prop.getProperty("username"));
				basicDataSource.setPassword(bte.decrypt(prop.getProperty("pwd")));
				basicDataSource.setUrl(basicDataSource.getUrl().replace("@", dbName));
			} catch (IOException e) {
				logger.error("getDataSource error", e);
			}
			dataSourceMap.put(dbName, basicDataSource);
		}
		return dataSourceMap.get(dbName);
	}


}
