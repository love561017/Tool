package tool.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public class SqlFormat {
	private final static Map<String, String> FIXED_VAL_MAP = new HashMap<>();

	public SqlFormat() {
		if (FIXED_VAL_MAP.size() == 0) {
			FIXED_VAL_MAP.put("CONTR_AUT_OWN_USR_CDE", "getDataOwner().getUserId()");
			FIXED_VAL_MAP.put("ROLE_CDE", "getGrpCtg()");
			FIXED_VAL_MAP.put("SIGN_ON_USR_CDE", "getUserMst().getUserId()");
		}
	}

	public String genSql(String sql, String prev, String infoName) {
		String rtn = "";
		String parm = "";
		sql = sql.replace("\r\n", "\n").replace("\r", "\n");
		String[] arrayOfString = sql.split("\n");
		Set<String> parmDeclaration = new HashSet<>();
		int j = arrayOfString.length;
		A: for (int b = 0; b < j; b++) {
			String line = arrayOfString[b];
			if (StringUtils.equals(line, "re")) {
				continue;
			}
			if (StringUtils.equals(line, "bk"))
				break;
			String desc = "";
			if (StringUtils.trim(line).length() == 0)
				continue;
			if (StringUtils.trim(line).length() > 2) {
				if (StringUtils.equals(StringUtils.trim(line).substring(0, 2), "--")
						|| StringUtils.equals(StringUtils.trim(line).substring(0, 2), "//")) {
					desc = StringUtils.trim(line);
					rtn = String.valueOf(rtn) + desc.replace("--", "//") + "\n";
				} else {
					String[] tmp1 = line.split("--");
					String[] tmp2 = line.split("//");
					if (tmp1.length == 2) {
						line = tmp1[0];
						desc = "//".concat(tmp1[1]);
					} else if (tmp2.length == 2) {
						line = tmp2[0];
						desc = "//".concat(tmp2[1]);
					}
					for (String column : FIXED_VAL_MAP.keySet()) {
						if (line.contains(column)) {
							int i = line.indexOf(column);
							line = StringUtils.left(line, i) + column + " = ? ";
							rtn = String.valueOf(rtn) + prev + ".append(\" " + line + " \");" + desc + "\n";
							rtn = String.valueOf(rtn) + "values.add(" + infoName + "." + FIXED_VAL_MAP.get(column)
									+ ");\n";
							continue A;
						}
					}

					if (line.contains("'Parm")) {
						for (int x = 1; x <= 20; x++) {
							parm = "'Parm" + x + "'";
							if (line.contains(parm)) {
								line = line.replace(parm, "?");
								parm = parm.replace("'", "");
								parmDeclaration.add("String " + parm + " = null;");
								break;
							}
						}
					}
					rtn = String.valueOf(rtn) + prev + ".append(\" " + line + " \");" + desc + "\n";
					if (StringUtils.isNotBlank(parm)) {
						rtn = String.valueOf(rtn) + "//values.add(" + parm + ");\n";
						parm = "";
					}

				}
				continue;
			}
			rtn = String.valueOf(rtn) + prev + ".append(\" " + line + " \");" + desc + "\n";
		}
		if (parmDeclaration.size() > 0) {
			List<String> parmList = new ArrayList<>(parmDeclaration);
			Collections.sort(parmList);
			return StringUtils.join(parmList, "\n") + "\n" + rtn;
		} else {
			return rtn;
		}

	}

	public String genSqlForSPEC(String sql, final Map<String, String> TABLE_NAMES_MAP,
			final Map<String, String> TABLE_COLUMNS_MAP) {
		String rtn = "";

		String[] arrayOfString = sql.split("\n");
		int j = arrayOfString.length;
		for (int b = 0; b < j; b++) {
			String line = arrayOfString[b];
			if (line.indexOf("@") > 0) {
				String newDesc = null;
				String asName = "";
				String tempLineToUpper = line.toUpperCase();

				if (tempLineToUpper.indexOf("FROM") >= 0) {
					String table = StringUtils.trim(tempLineToUpper.replace("FROM", "").replace("@", ""));
					newDesc = TABLE_NAMES_MAP.get(table.split(" ")[0]);
					asName = table.split(" ").length == 1 ? "" : " (" + table.split(" ")[1].toLowerCase() + ")";
				} else if (tempLineToUpper.indexOf("JOIN") >= 0) {
					String[] temp = tempLineToUpper.split("JOIN");
					String table = StringUtils
							.trim((temp.length == 0 ? tempLineToUpper.replace("JOIN", "") : temp[1]).replace("@", ""));
					newDesc = TABLE_NAMES_MAP.get(table.split(" ")[0]);
					asName = " (" + table.split(" ")[1].toLowerCase() + ")";
				} else if (tempLineToUpper.indexOf("AS") > 0) {
					String[] temp = tempLineToUpper.split("AS");
					String column = StringUtils
							.trim((temp.length == 0 ? tempLineToUpper.replace("AS", "") : temp[1]).replace("@", ""));
					newDesc = TABLE_COLUMNS_MAP.get(column);
				} else if (line.indexOf(".") > 0) {
					String column = StringUtils.trim(line.split("\\.")[1].replace("@", "").replace(",", ""));
					newDesc = TABLE_COLUMNS_MAP.get(column);
				} else {
					String column = StringUtils.trim(line.replace("@", "").replace(",", ""));
					newDesc = null == TABLE_COLUMNS_MAP.get(column) ? TABLE_NAMES_MAP.get(column)
							: TABLE_COLUMNS_MAP.get(column);
				}
				if (null != newDesc) {
					line = line.replace("@", "--" + newDesc + asName);
				}
			}

			rtn = String.valueOf(rtn) + line + "\n";
		}
		return rtn;
	}

}
