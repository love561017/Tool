package tool.logic;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

public class BeanMaker {

    static class ColumnInfo {
        String fieldName;  // camelCase Java field name
        String columnKey;  // UPPER_SNAKE for DB lookup
        String javaType;   // "String", "Integer", "BigDecimal"
        String comment;    // null if none

        ColumnInfo(String fieldName, String columnKey, String javaType, String comment) {
            this.fieldName = fieldName;
            this.columnKey = columnKey;
            this.javaType = javaType;
            this.comment = comment;
        }
    }

    /**
     * @param sqlInput        one or more SELECT statements separated by blank lines
     * @param beanName        Java class name (e.g. "AmAct05005Sql101Bean")
     * @param tableColumnsMap COLUMN_NAME -> Chinese description from DB (may be null/empty)
     */
    public String process(String sqlInput, String beanName, Map<String, String> tableColumnsMap) {
        if (StringUtils.isBlank(beanName)) {
            return "// 請輸入 Bean 名稱";
        }
        if (StringUtils.isBlank(sqlInput)) {
            return "// 請輸入 SQL";
        }

        // Split multiple SQLs by blank lines
        String normalized = sqlInput.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalized.split("(?m)\\n[ \\t]*\\n");

        List<ColumnInfo> allColumns = new ArrayList<>();
        Set<String> seenFieldNames = new LinkedHashSet<>();

        for (String block : blocks) {
            if (StringUtils.isBlank(block)) continue;
            List<ColumnInfo> cols = parseColumns(block);
            for (ColumnInfo col : cols) {
                if (seenFieldNames.add(col.fieldName)) {
                    // Fill comment from DB if not found in SQL
                    if (col.comment == null && tableColumnsMap != null && !tableColumnsMap.isEmpty()) {
                        col.comment = tableColumnsMap.get(col.columnKey);
                    }
                    allColumns.add(col);
                }
            }
        }

        if (allColumns.isEmpty()) {
            return "// 未找到任何欄位，請確認 SQL 包含 SELECT...FROM 結構";
        }

        return generateBean(beanName, allColumns);
    }

    private List<ColumnInfo> parseColumns(String sql) {
        List<ColumnInfo> result = new ArrayList<>();

        // Find SELECT keyword
        int selectIdx = sql.toUpperCase().indexOf("SELECT");
        if (selectIdx < 0) return result;
        int afterSelect = selectIdx + 6;

        // Find first top-level FROM (not inside parentheses)
        int fromIdx = findTopLevelKeyword(sql, afterSelect, "FROM");
        if (fromIdx < 0) return result;

        String columnBlock = sql.substring(afterSelect, fromIdx);
        String[] lines = columnBlock.split("\n");

        String pendingComment = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Strip leading comma
            if (trimmed.startsWith(",")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.isEmpty()) continue;

            // Comment-only line → becomes preceding comment for next column
            if (trimmed.startsWith("--")) {
                String text = trimmed.substring(2).trim()
                        .replaceAll("^[\"']+|[\"']+$", "").trim();
                if (!text.isEmpty()) {
                    pendingComment = text;
                }
                continue;
            }

            // Inline comment on same line
            String inlineComment = null;
            int inlineIdx = findInlineCommentIdx(trimmed);
            if (inlineIdx >= 0) {
                inlineComment = trimmed.substring(inlineIdx + 2).trim();
                trimmed = trimmed.substring(0, inlineIdx).trim();
            }

            // Preceding comment takes priority over inline comment
            String comment = (pendingComment != null) ? pendingComment : inlineComment;
            pendingComment = null;

            ColumnInfo col = parseExpression(trimmed, comment);
            if (col != null) {
                result.add(col);
            }
        }

        return result;
    }

    /** Find first occurrence of keyword at top nesting level starting from startIdx. */
    private int findTopLevelKeyword(String sql, int startIdx, String keyword) {
        int depth = 0;
        int len = sql.length();
        int kLen = keyword.length();

        for (int i = startIdx; i < len; i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i++;
                while (i < len && sql.charAt(i) != '\'') i++;
                continue;
            }
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && i + kLen <= len) {
                String chunk = sql.substring(i, i + kLen).toUpperCase();
                if (chunk.equals(keyword)) {
                    boolean leftOk  = (i == 0 || !isIdentChar(sql.charAt(i - 1)));
                    boolean rightOk = (i + kLen >= len || !isIdentChar(sql.charAt(i + kLen)));
                    if (leftOk && rightOk) return i;
                }
            }
        }
        return -1;
    }

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Find index of "--" that is not inside parentheses or single-quoted string. */
    private int findInlineCommentIdx(String line) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inQuote)  { inQuote = true;  continue; }
            if (c == '\'' && inQuote)   { inQuote = false; continue; }
            if (inQuote) continue;
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (c == '-' && line.charAt(i + 1) == '-' && depth == 0) return i;
        }
        return -1;
    }

    /** Parse a column expression into a ColumnInfo, or null if unrecognisable. */
    private ColumnInfo parseExpression(String expr, String comment) {
        if (expr.isEmpty()) return null;

        String exprUpper = expr.toUpperCase();

        // Detect Java type from aggregate function
        String javaType = "String";
        if (exprUpper.matches("COUNT\\s*\\(.*")) {
            javaType = "Integer";
        } else if (exprUpper.matches("(SUM|AVG)\\s*\\(.*")) {
            javaType = "BigDecimal";
        }

        // Find top-level AS to get alias
        int asIdx = findTopLevelKeyword(exprUpper, 0, "AS");
        String columnKey;

        if (asIdx >= 0) {
            String alias = exprUpper.substring(asIdx + 2).trim();
            columnKey = alias.split("[\\s,)]+")[0];
        } else {
            // No alias: skip expressions that are bare function calls
            if (expr.contains("(")) return null;

            // Strip optional table prefix (e.g. a1.CONTR_NO → CONTR_NO)
            String stripped = exprUpper.trim();
            int dotIdx = stripped.lastIndexOf('.');
            if (dotIdx >= 0) {
                stripped = stripped.substring(dotIdx + 1).trim();
            }
            columnKey = stripped.replaceAll("[\\s,]+$", "");
        }

        if (columnKey.isEmpty()) return null;

        return new ColumnInfo(toCamelCase(columnKey), columnKey, javaType, comment);
    }

    private String toCamelCase(String upperSnake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        boolean first = true;

        for (char c : upperSnake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (first) {
                sb.append(Character.toLowerCase(c));
                first = false;
                nextUpper = false;
            } else if (nextUpper) {
                sb.append(Character.isLetter(c) ? Character.toUpperCase(c) : c);
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String generateBean(String beanName, List<ColumnInfo> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(beanName).append(" {\n");

        // Field declarations
        for (ColumnInfo col : columns) {
            if (col.comment != null) {
                sb.append("    /** ").append(col.comment).append(" */\n");
            }
            sb.append("    private ").append(col.javaType).append(" ").append(col.fieldName).append(";\n");
        }

        sb.append("\n");

        // Getters and setters
        for (ColumnInfo col : columns) {
            String upper = Character.toUpperCase(col.fieldName.charAt(0)) + col.fieldName.substring(1);

            if (col.comment != null) sb.append("    /** ").append(col.comment).append(" */\n");
            sb.append("    public ").append(col.javaType).append(" get").append(upper).append("() {\n");
            sb.append("        return ").append(col.fieldName).append(";\n");
            sb.append("    }\n\n");

            if (col.comment != null) sb.append("    /** ").append(col.comment).append(" */\n");
            sb.append("    public void set").append(upper).append("(")
              .append(col.javaType).append(" ").append(col.fieldName).append(") {\n");
            sb.append("        this.").append(col.fieldName).append(" = ").append(col.fieldName).append(";\n");
            sb.append("    }\n\n");
        }

        sb.append("}");
        return sb.toString();
    }
}
