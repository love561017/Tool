package tool.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tool.helper.StringHelper;

public class BeanToSqlColumns {

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "(?:/\\*\\*\\s*(.*?)\\s*\\*/\\s*)?private\\s+(\\w+)\\s+(\\w+)\\s*;",
            Pattern.DOTALL);

    // 從 sb.append("... AS aliasName ") 或 .append("  AS aliasName ") 提取別名
    private static final Pattern ALIAS_PATTERN = Pattern.compile(
            "\\bAS\\s+(\\w+)\\s+\"");

    public String convert(String alias, String beanFields) {
        return convert(alias, beanFields, null);
    }

    public String convert(String alias, String beanFields, String sqlSelect) {
        Set<String> existingAliases = extractAliases(sqlSelect);
        String trimAlias = alias == null ? "" : alias.trim();
        Matcher m = FIELD_PATTERN.matcher(beanFields);

        List<String> sqlLines    = new ArrayList<>();
        List<String> scalarLines = new ArrayList<>();

        while (m.find()) {
            String fieldName0 = m.group(3);
            if (existingAliases.contains(fieldName0)) continue;

            String rawComment = m.group(1);
            String comment   = rawComment != null ? rawComment.replaceAll("\\s+", " ").trim() : null;
            String javaType  = m.group(2);
            String fieldName = m.group(3);

            String snakeCol  = toSnakeCase(fieldName);
            String colLine;
            if (fieldName.endsWith("Snam") || fieldName.endsWith("Nam")) {
                colLine = "sb.append(\"      , " + trimAlias + ".\").appendLang(\""
                        + snakeCol + "\").append(\"  AS " + fieldName + " \");"
                        + (comment != null ? " /** " + comment + " */" : "");
            } else {
                colLine = "sb.append(\"      , " + trimAlias + "." + snakeCol
                        + " AS " + fieldName + " \");"
                        + (comment != null ? " /** " + comment + " */" : "");
            }
            String scalarLine = "        sb." + StringHelper.scalarMethod(javaType)
                             + "(\"" + fieldName + "\");";

            sqlLines.add(colLine);
            scalarLines.add(scalarLine);
        }

        StringBuilder sb = new StringBuilder();
        for (String line : sqlLines)    sb.append(line).append("\n");
        sb.append("\n");
        for (String line : scalarLines) sb.append(line).append("\n");
        return sb.toString();
    }

    private String toSnakeCase(String camel) {
        return camel.replaceAll("([A-Z])", "_$1").toUpperCase();
    }

    private Set<String> extractAliases(String sqlSelect) {
        Set<String> set = new HashSet<>();
        if (sqlSelect == null || sqlSelect.trim().isEmpty()) return set;
        Matcher m = ALIAS_PATTERN.matcher(sqlSelect);
        while (m.find()) set.add(m.group(1));
        return set;
    }
}
