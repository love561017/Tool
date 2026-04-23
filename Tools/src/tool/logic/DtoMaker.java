package tool.logic;

import java.util.*;
import java.util.regex.*;

/**
 * 從舊版 JSP 解析表單欄位，產生對應的 DTO Java 程式碼。
 *
 * 支援格式：
 *   - Struts 2: <s:textfield name="...">, <s:hidden name="...">, <s:select name="...">
 *   - Struts 1: <html:text property="...">, <html:hidden property="...">
 *   - 標準 HTML: <input name="...">, <select name="...">, <textarea name="...">
 *   - Struts 2 動態 name: name="%{'amFeeCfgMst.cycleList['+#stat.index+'].field'}"
 *
 * 分組規則：
 *   - criteria.contrNo        → DTO: criteria,       field: contrNo
 *   - amFeeCfgMst.contrNo     → DTO: amFeeCfgMst,    field: contrNo
 *   - amFeeCfgMst.cycleList[0].dtlFeeItem → 子 DTO: amFeeCfgMst.cycleList (List), field: dtlFeeItem
 *   - amFeeCfgMst.sub.field   → 平鋪到 amFeeCfgMst,  field: field
 *   - hrdlCde                 → 無前綴，忽略
 */
public class DtoMaker {

	// ---- Regex Patterns ----

	/** Struts 2 表單元素 */
	private static final Pattern S2_TAG = Pattern.compile(
			"<s:(?:textfield|hidden|select|textarea|checkbox|radio|file)\\b([^>]*?)(?:/>|>)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/** Struts 1 表單元素 */
	private static final Pattern S1_TAG = Pattern.compile(
			"<html:(?:text|hidden|select|textarea|checkbox|radio|password)\\b([^>]*?)(?:/>|>)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/** 標準 HTML 表單元素 */
	private static final Pattern HTML_TAG = Pattern.compile(
			"<(?:input|select|textarea)\\b([^>]*?)(?:/>|>)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/** name 屬性（雙引號） */
	private static final Pattern NAME_DQ = Pattern.compile(
			"\\bname\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	/** name 屬性（單引號） */
	private static final Pattern NAME_SQ = Pattern.compile(
			"\\bname\\s*=\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);

	/** property 屬性（Struts 1） */
	private static final Pattern PROPERTY_ATTR = Pattern.compile(
			"\\bproperty\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

	/** type 屬性 */
	private static final Pattern TYPE_ATTR = Pattern.compile(
			"\\btype\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

	/** disabled 屬性（排除 disabled="false"） */
	private static final Pattern DISABLED_ATTR = Pattern.compile(
			"\\bdisabled\\s*=\\s*[\"'](?!false)(?!0)[^\"']*[\"']",
			Pattern.CASE_INSENSITIVE);

	/** JSP 行內註解 <%-- ... --%> */
	private static final Pattern JSP_CMT = Pattern.compile("<%--([\\s\\S]*?)--%>");

	/** Struts 2 動態表達式中的字串字面量 '%{...}' */
	private static final Pattern S2_EXPR_STR = Pattern.compile("'([^']*)'");

	private static final Set<String> SKIP_TYPES = new HashSet<>(
			Arrays.asList("submit", "button", "reset", "image"));

	// ----------------------------------------------------------------

	static class FieldInfo {
		final String dtoKey;   // DTO 分組 key，例如 "amFeeCfgMst" 或 "amFeeCfgMst.cycleList"
		final String field;    // Java 欄位名稱 (camelCase)
		final boolean isList;  // 此 dtoKey 是否為 List (有 [index] 標記)
		final String comment;  // JSP 行內說明

		FieldInfo(String dtoKey, String field, boolean isList, String comment) {
			this.dtoKey = dtoKey;
			this.field = field;
			this.isList = isList;
			this.comment = comment;
		}
	}

	/**
	 * @param jsp           JSP 原始內容
	 * @param nameMapping   前綴對應類別名稱，格式："criteria:AmAct05005Dto,amFeeCfgMst:AmFeeCfgMstDto"
	 *                      可留空，留空時自動以首字大寫+Dto命名
	 * @param tableColumnsMap  DB 欄位說明對照（可傳 null）
	 */
	public String process(String jsp, String nameMapping, Map<String, String> tableColumnsMap) {
		// 解析前綴→類別名稱對應
		Map<String, String> prefixMap = parsePrefixMapping(nameMapping);

		// 移除 <script>...</script>（避免 JS 內容干擾）
		String cleaned = jsp.replaceAll("(?is)<script\\b[^>]*>[\\s\\S]*?</script>", "");

		// 逐行解析欄位
		List<FieldInfo> allFields = extractFields(cleaned);

		if (allFields.isEmpty()) {
			return "// 未偵測到任何表單欄位。\n// 請確認 JSP 包含 <s:textfield>, <s:hidden>, <s:select> 或 <input> 等表單元素。";
		}

		// 以 dtoKey 分組
		// 使用 LinkedHashMap 保持順序（非 List 先，List 後）
		Map<String, LinkedHashSet<String>> dtoFields = new LinkedHashMap<>();
		Map<String, Boolean> dtoIsListMap = new LinkedHashMap<>();
		Map<String, String> fieldComments = new LinkedHashMap<>(); // "dtoKey.field" -> comment

		for (FieldInfo fi : allFields) {
			if (fi.dtoKey.isEmpty()) continue;
			dtoFields.computeIfAbsent(fi.dtoKey, k -> new LinkedHashSet<>()).add(fi.field);
			dtoIsListMap.put(fi.dtoKey, fi.isList);
			if (fi.comment != null && !fi.comment.isEmpty()) {
				fieldComments.put(fi.dtoKey + "." + fi.field, fi.comment);
			}
		}

		if (dtoFields.isEmpty()) {
			return "// 所有欄位均無前綴（如 hrdlCde），無法分組成 DTO。\n// 請確認欄位 name 包含前綴，例如 criteria.contrNo。";
		}

		// 建立父 DTO → 子 List DTO 的對應關係
		// key "amFeeCfgMst.cycleList" -> parent "amFeeCfgMst", listVar "cycleList"
		Map<String, List<String>> parentToSubLists = new LinkedHashMap<>();
		for (String key : dtoFields.keySet()) {
			if (dtoIsListMap.getOrDefault(key, false)) {
				int dot = key.lastIndexOf('.');
				if (dot > 0) {
					String parent = key.substring(0, dot);
					parentToSubLists.computeIfAbsent(parent, k -> new ArrayList<>()).add(key);
				}
			}
		}

		// 產生輸出
		StringBuilder sb = new StringBuilder();
		boolean first = true;

		for (Map.Entry<String, LinkedHashSet<String>> entry : dtoFields.entrySet()) {
			String key = entry.getKey();
			Set<String> fields = entry.getValue();
			boolean isList = dtoIsListMap.getOrDefault(key, false);
			String className = resolveClassName(key, prefixMap);

			// 取得此 DTO 的子 List
			List<String> subLists = parentToSubLists.getOrDefault(key, Collections.emptyList());

			if (!first) sb.append("\n\n");
			first = false;

			sb.append("// ").append(isList ? "[明細] " : "[主檔] ");
			sb.append(className).append("  (對應 JSP 前綴: ").append(key).append(")\n");
			sb.append(buildDtoClass(className, fields, subLists, dtoIsListMap, prefixMap, fieldComments, key, tableColumnsMap));
		}

		return sb.toString();
	}

	// ----------------------------------------------------------------
	// 欄位擷取
	// ----------------------------------------------------------------

	private List<FieldInfo> extractFields(String jsp) {
		List<FieldInfo> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		String[] lines = jsp.split("\\r?\\n");
		String prevComment = null; // 上一行的 JSP 註解

		for (String line : lines) {
			// 擷取此行的 JSP 行內註解
			String inlineComment = extractInlineComment(line);
			if (inlineComment != null) {
				prevComment = inlineComment;
			}

			// 嘗試所有 tag 類型
			processTagsInLine(line, S2_TAG, false, prevComment, result, seen);
			processTagsInLine(line, S1_TAG, true, prevComment, result, seen);
			processTagsInLine(line, HTML_TAG, false, prevComment, result, seen);

			// 如果此行有 tag，下一行前清掉 prevComment
			// (只對 inline comment 有效；每行更新)
			if (line.matches(".*<(?:s:|html:)?(?:textfield|hidden|select|textarea|input)\\b.*")) {
				prevComment = null;
			}
		}

		return result;
	}

	private void processTagsInLine(String line, Pattern tagPat, boolean useProperty,
			String comment, List<FieldInfo> result, Set<String> seen) {

		Matcher m = tagPat.matcher(line);
		while (m.find()) {
			String attrs = m.group(1);

			// 跳過 disabled
			if (DISABLED_ATTR.matcher(attrs).find()) continue;

			// 跳過 type=submit/button/reset/image
			Matcher typeMatcher = TYPE_ATTR.matcher(attrs);
			if (typeMatcher.find() && SKIP_TYPES.contains(typeMatcher.group(1).toLowerCase())) continue;

			// 取得欄位 name（Struts 2/HTML 用 name，Struts 1 也可能用 property）
			String rawName = extractName(attrs, useProperty);
			if (rawName == null || rawName.isEmpty()) continue;

			// 正規化動態 name（處理 %{'...'} 表達式）
			String name = normalizeName(rawName);
			if (name.isEmpty() || name.contains("${")) continue;

			// 取此行的 inline comment（優先）
			String fieldComment = extractInlineComment(line);
			if (fieldComment == null) fieldComment = comment;

			// 解析 DTO key / field 名稱
			FieldInfo fi = parsePath(name, fieldComment);
			if (fi == null) continue;

			// 去重（同一欄位只記一次）
			String dedup = fi.dtoKey + "." + fi.field;
			if (!seen.add(dedup)) continue;

			result.add(fi);
		}
	}

	// ----------------------------------------------------------------
	// 路徑解析
	// ----------------------------------------------------------------

	/**
	 * 將 JSP field name 解析成 FieldInfo。
	 *
	 * "criteria.contrNo"                            -> dtoKey=criteria,              field=contrNo,    isList=false
	 * "amFeeCfgMst.contrNo"                         -> dtoKey=amFeeCfgMst,           field=contrNo,    isList=false
	 * "amFeeCfgMst.cycleList[0].dtlFeeItem"         -> dtoKey=amFeeCfgMst.cycleList, field=dtlFeeItem, isList=true
	 * "amFeeCfgMst.amFeeMaxMinAmtCfg.maxMinCurr"    -> dtoKey=amFeeCfgMst,           field=maxMinCurr, isList=false (平鋪)
	 * "hrdlCde"                                     -> dtoKey="",                    忽略
	 */
	private FieldInfo parsePath(String name, String comment) {
		String[] parts = name.split("\\.");

		if (parts.length == 1) {
			// 無前綴，忽略（如 hrdlCde）
			return new FieldInfo("", toCamelCase(parts[0]), false, comment);
		}

		// 尋找第一個有 [] 的 segment
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].contains("[")) {
				String listName = parts[i].replaceAll("\\[.*?\\]", "");
				String dtoKey = i == 0 ? listName : parts[0] + "." + listName;

				// 剩餘的 segment 作為 field（取最後一段）
				String[] remaining = Arrays.copyOfRange(parts, i + 1, parts.length);
				if (remaining.length == 0) return null;
				String field = toCamelCase(remaining[remaining.length - 1]);

				return new FieldInfo(dtoKey, field, true, comment);
			}
		}

		// 無 [] → 第一段為 dtoKey，最後一段為 field（中間層平鋪）
		return new FieldInfo(parts[0], toCamelCase(parts[parts.length - 1]), false, comment);
	}

	// ----------------------------------------------------------------
	// 名稱處理
	// ----------------------------------------------------------------

	/**
	 * 正規化 Struts 2 動態 name 表達式。
	 * "%{'amFeeCfgMst.cycleList['+#stat.index+'].dtlFeeItem'}" → "amFeeCfgMst.cycleList[0].dtlFeeItem"
	 */
	private String normalizeName(String raw) {
		if (!raw.startsWith("%{")) return raw.trim();

		// 擷取所有單引號字串
		Matcher m = S2_EXPR_STR.matcher(raw);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			sb.append(m.group(1));
		}
		String result = sb.toString();

		// 修正空括號 [] → [0]
		result = result.replaceAll("\\[\\]", "[0]");

		return result.isEmpty() ? raw : result;
	}

	private String extractName(String attrs, boolean useProperty) {
		// 優先用 name（雙引號）
		Matcher m = NAME_DQ.matcher(attrs);
		if (m.find()) return m.group(1);

		// 次用 name（單引號）
		m = NAME_SQ.matcher(attrs);
		if (m.find()) return m.group(1);

		// Struts 1：用 property
		if (useProperty) {
			m = PROPERTY_ATTR.matcher(attrs);
			if (m.find()) return m.group(1);
		}
		return null;
	}

	private String extractInlineComment(String line) {
		Matcher m = JSP_CMT.matcher(line);
		if (m.find()) {
			String c = m.group(1).trim();
			return c.isEmpty() ? null : c;
		}
		return null;
	}

	/** snake_CASE / UPPER_CASE → camelCase */
	private String toCamelCase(String s) {
		s = s.replaceAll("\\[.*?\\]", ""); // 移除 [index]
		if (s.contains("_")) {
			String[] parts = s.toLowerCase().split("_");
			StringBuilder sb = new StringBuilder(parts[0]);
			for (int i = 1; i < parts.length; i++) {
				if (!parts[i].isEmpty()) {
					sb.append(Character.toUpperCase(parts[i].charAt(0)));
					sb.append(parts[i].substring(1));
				}
			}
			return sb.toString();
		}
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	/** camelCase 首字大寫 → 用於類別名稱 */
	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		String c = toCamelCase(s);
		return Character.toUpperCase(c.charAt(0)) + c.substring(1);
	}

	// ----------------------------------------------------------------
	// DTO 類別名稱解析
	// ----------------------------------------------------------------

	/**
	 * 根據 dtoKey 和用戶設定的對應，決定 DTO 類別名稱。
	 * key "amFeeCfgMst.cycleList" → "CycleListDto"（取最後一段）
	 */
	private String resolveClassName(String dtoKey, Map<String, String> prefixMap) {
		if (prefixMap.containsKey(dtoKey)) return prefixMap.get(dtoKey);

		// 取最後一段作為類別名稱基底
		String base = dtoKey.contains(".") ? dtoKey.substring(dtoKey.lastIndexOf('.') + 1) : dtoKey;
		return capitalize(base) + "Dto";
	}

	private Map<String, String> parsePrefixMapping(String mapping) {
		Map<String, String> map = new LinkedHashMap<>();
		if (mapping == null || mapping.trim().isEmpty()) return map;
		for (String entry : mapping.split("[,\n]")) {
			String[] kv = entry.trim().split(":");
			if (kv.length == 2 && !kv[0].trim().isEmpty()) {
				map.put(kv[0].trim(), kv[1].trim());
			}
		}
		return map;
	}

	// ----------------------------------------------------------------
	// DTO 程式碼產生
	// ----------------------------------------------------------------

	private String buildDtoClass(String className, Set<String> fields,
			List<String> subListKeys, Map<String, Boolean> dtoIsListMap,
			Map<String, String> prefixMap, Map<String, String> fieldComments,
			String dtoKey, Map<String, String> tableColumnsMap) {

		StringBuilder sb = new StringBuilder();
		sb.append("public class ").append(className).append(" {\n\n");

		// 一般欄位
		for (String f : fields) {
			String comment = resolveComment(dtoKey + "." + f, fieldComments, f, tableColumnsMap);
			if (comment != null) {
				sb.append("    /** ").append(comment).append(" */\n");
			}
			sb.append("    private String ").append(f).append(";\n");
		}

		// 子 List 欄位（例如 List<CycleListDto> cycleList）
		for (String subKey : subListKeys) {
			String subClassName = resolveClassName(subKey, prefixMap);
			String listVarName = subKey.substring(subKey.lastIndexOf('.') + 1);
			listVarName = toCamelCase(listVarName);
			sb.append("    private java.util.List<").append(subClassName).append("> ")
					.append(listVarName).append(";\n");
		}

		sb.append("\n");

		// Getter / Setter
		for (String f : fields) {
			appendGetterSetter(sb, f, "String");
		}
		for (String subKey : subListKeys) {
			String subClassName = resolveClassName(subKey, prefixMap);
			String listVarName = toCamelCase(subKey.substring(subKey.lastIndexOf('.') + 1));
			appendListGetterSetter(sb, listVarName, subClassName);
		}

		sb.append("}");
		return sb.toString();
	}

	private String resolveComment(String key, Map<String, String> fieldComments,
			String fieldName, Map<String, String> tableColumnsMap) {
		// 1. 從 JSP 行內 comment 取
		String c = fieldComments.get(key);
		if (c != null && !c.isEmpty()) return c;

		// 2. 從 DB 欄位對照取
		if (tableColumnsMap != null && !tableColumnsMap.isEmpty()) {
			String upper = toUpperUnderscore(fieldName);
			c = tableColumnsMap.get(upper);
			if (c == null) c = tableColumnsMap.get(fieldName);
			if (c != null) return c;
		}
		return null;
	}

	private void appendGetterSetter(StringBuilder sb, String f, String type) {
		String upper = Character.toUpperCase(f.charAt(0)) + f.substring(1);
		sb.append("    public ").append(type).append(" get").append(upper).append("() {\n");
		sb.append("        return ").append(f).append(";\n");
		sb.append("    }\n\n");
		sb.append("    public void set").append(upper).append("(").append(type).append(" ")
				.append(f).append(") {\n");
		sb.append("        this.").append(f).append(" = ").append(f).append(";\n");
		sb.append("    }\n\n");
	}

	private void appendListGetterSetter(StringBuilder sb, String varName, String itemType) {
		String upper = Character.toUpperCase(varName.charAt(0)) + varName.substring(1);
		sb.append("    public java.util.List<").append(itemType).append("> get")
				.append(upper).append("() {\n");
		sb.append("        return ").append(varName).append(";\n");
		sb.append("    }\n\n");
		sb.append("    public void set").append(upper).append("(java.util.List<")
				.append(itemType).append("> ").append(varName).append(") {\n");
		sb.append("        this.").append(varName).append(" = ").append(varName).append(";\n");
		sb.append("    }\n\n");
	}

	/** camelCase → UPPER_CASE（用於查 DB comment） */
	private String toUpperUnderscore(String camel) {
		StringBuilder sb = new StringBuilder();
		for (char c : camel.toCharArray()) {
			if (Character.isUpperCase(c)) sb.append('_');
			sb.append(Character.toUpperCase(c));
		}
		return sb.toString();
	}
}
