package tool.logic;

import java.util.Map;

public class DtoMaker {

	public String process(String jsp, String dtoNam, final Map<String, String> TABLE_COLUMNS_MAP) {

		for (String str : jsp.split("\r\n")) {

			if (str.contains("name") && (str.contains("textfield") || str.contains("input") || str.contains("hidden"))) {
				if (str.contains(dtoNam)) {
					int i = str.indexOf(dtoNam);
					str = str.substring(i);
					str = str.substring(0, str.indexOf("\""));
					str = str.substring(str.lastIndexOf(".") + 1).replace("'", "");
					System.out.println("小DTO:"+str);
				} else {
					int i = str.indexOf("name");
					str = str.substring(i);
					str = str.substring(str.indexOf("\"") + 1);
					str = str.substring(0, str.indexOf("\""));
					System.out.println("大DTO:"+str);
				}

			}
		}
		return jsp;
	}
}
