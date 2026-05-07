package tool.helper;

import org.apache.commons.lang3.StringUtils;

public class StringHelper {
	public static boolean ynMatch(String val, String... arr) {
		for (String v : arr) {
			if (StringUtils.equals(val, v)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 將 Java 型別名稱或 StandardBasicTypes 名稱對應到 SQLQueryBuilder 的 scalar 方法名稱。
	 * 支援：String/STRING → scalarString、BigDecimal/BIG_DECIMAL → scalarDecimal、
	 * Integer/int/INTEGER → scalarInteger、Timestamp/Date/TIMESTAMP → scalarTime。
	 */
	public static String scalarMethod(String type) {
		if (type == null) return "scalarString";
		switch (type.toUpperCase()) {
			case "STRING":                          return "scalarString";
			case "BIGDECIMAL": case "BIG_DECIMAL":  return "scalarDecimal";
			case "INTEGER":    case "INT":           return "scalarInteger";
			case "TIMESTAMP":  case "DATE":          return "scalarTime";
			default:                                 return "scalarString";
		}
	}
}
