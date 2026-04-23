package tool.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 將 Java 類別中欄位的 JavaDoc 或 // 註解，
 * 自動補寫到對應的 getter / setter 方法上。
 *
 * 例如：
 *   /** 契約編號 *\/
 *   private String contrNo;
 *
 *   public String getContrNo() { ... }  →  加上 /** 契約編號 *\/
 *   public void setContrNo(...) { ... }  →  加上 /** 契約編號 *\/
 */
public class GetterSetterComment {

	/** JavaDoc 緊接著欄位宣告（中間允許 @Annotation） */
	private static final Pattern FIELD_JAVADOC = Pattern.compile(
			"(/\\*\\*[\\s\\S]*?\\*/)(?:[ \t]*\\r?\\n[ \t]*@[^\r\n]*)*[ \t]*\\r?\\n[ \t]*"
					+ "(?:private|protected|public)(?:\\s+(?:static|final|transient|volatile))*"
					+ "\\s+[\\w.<>\\[\\],\\s]+?\\s+(\\w+)\\s*[;=]");

	/** // 單行註解緊接著欄位宣告 */
	private static final Pattern FIELD_LINE_COMMENT = Pattern.compile(
			"(//[^\r\n]+)[ \t]*\\r?\\n[ \t]*"
					+ "(?:private|protected|public)(?:\\s+(?:static|final|transient|volatile))*"
					+ "\\s+[\\w.<>\\[\\],\\s]+?\\s+(\\w+)\\s*[;=]");

	/** getter 方法（含 is 前綴） */
	private static final Pattern GETTER = Pattern.compile(
			"^([ \t]*)public[ \t]+[\\w.<>\\[\\],?\\s]+?[ \t]+(?:get|is)(\\w+)[ \t]*\\([ \t]*\\)[ \t]*(?:throws[^{]+)?\\{",
			Pattern.MULTILINE);

	/** setter 方法 */
	private static final Pattern SETTER = Pattern.compile(
			"^([ \t]*)public[ \t]+(?:void|[\\w.<>\\[\\],?\\s]+?)[ \t]+set(\\w+)[ \t]*\\([^)]*\\)[ \t]*(?:throws[^{]+)?\\{",
			Pattern.MULTILINE);

	public String process(String code) {
		// ── Step 1：建立 欄位名(小寫) → 註解 的對照表 ──
		Map<String, String> commentMap = new LinkedHashMap<>();

		Matcher m1 = FIELD_JAVADOC.matcher(code);
		while (m1.find()) {
			commentMap.putIfAbsent(m1.group(2).toLowerCase(), m1.group(1));
		}

		Matcher m2 = FIELD_LINE_COMMENT.matcher(code);
		while (m2.find()) {
			commentMap.putIfAbsent(m2.group(2).toLowerCase(), m2.group(1));
		}

		if (commentMap.isEmpty()) {
			return code + "\n// [get/setter 註解] 未找到帶有註解的欄位宣告，無需修改。";
		}

		// ── Step 2：在 getter 前插入對應的欄位註解 ──
		StringBuffer sb1 = new StringBuffer();
		Matcher getter = GETTER.matcher(code);
		while (getter.find()) {
			String indent = getter.group(1);
			String suffix = getter.group(2);
			String fieldKey = lcFirst(suffix);
			String comment = commentMap.get(fieldKey.toLowerCase());

			if (comment != null && !hasPrecedingComment(code, getter.start())) {
				getter.appendReplacement(sb1,
						Matcher.quoteReplacement(reindent(comment, indent) + "\n" + getter.group()));
			} else {
				getter.appendReplacement(sb1, Matcher.quoteReplacement(getter.group()));
			}
		}
		getter.appendTail(sb1);

		// ── Step 3：在 setter 前插入對應的欄位註解 ──
		String intermediate = sb1.toString();
		StringBuffer sb2 = new StringBuffer();
		Matcher setter = SETTER.matcher(intermediate);
		while (setter.find()) {
			String indent = setter.group(1);
			String suffix = setter.group(2);
			String fieldKey = lcFirst(suffix);
			String comment = commentMap.get(fieldKey.toLowerCase());

			if (comment != null && !hasPrecedingComment(intermediate, setter.start())) {
				setter.appendReplacement(sb2,
						Matcher.quoteReplacement(reindent(comment, indent) + "\n" + setter.group()));
			} else {
				setter.appendReplacement(sb2, Matcher.quoteReplacement(setter.group()));
			}
		}
		setter.appendTail(sb2);

		return sb2.toString();
	}

	// ----------------------------------------------------------------

	/**
	 * 判斷 position 前是否已有註解行，避免重複插入。
	 * 向前掃描最多 10 行，遇到第一個非空非 @Annotation 行即停止。
	 */
	private boolean hasPrecedingComment(String code, int position) {
		String before = code.substring(0, position);
		String[] lines = before.split("\\r?\\n");
		for (int i = lines.length - 1; i >= Math.max(0, lines.length - 10); i--) {
			String line = lines[i].trim();
			if (line.isEmpty()) continue;
			if (line.startsWith("*") || line.startsWith("/**") ||
					line.startsWith("/*") || line.startsWith("//") ||
					line.endsWith("*/")) {
				return true;
			}
			// 遇到第一個實質行（非空、非 annotation）→ 停止
			if (!line.startsWith("@")) break;
		}
		return false;
	}

	/**
	 * 將 comment 的每一行重新縮排為 indent，並保持 JavaDoc 格式。
	 * 單行 /** ... *\/ 維持單行；多行 JavaDoc 每行補對齊空白。
	 */
	private String reindent(String comment, String indent) {
		comment = comment.trim();
		String[] lines = comment.split("\\r?\\n");
		if (lines.length == 1) {
			return indent + comment;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) sb.append("\n");
			String line = lines[i].trim();
			if (line.isEmpty()) continue;
			// JavaDoc 中間行以 " *" 縮排
			if (i > 0 && !line.startsWith("/")) {
				sb.append(indent).append(" ").append(line);
			} else {
				sb.append(indent).append(line);
			}
		}
		return sb.toString();
	}

	/** ContrNo → contrNo */
	private String lcFirst(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}
}
