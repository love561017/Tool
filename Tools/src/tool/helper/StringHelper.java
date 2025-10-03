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
}
