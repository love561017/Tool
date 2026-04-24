package tool.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DaoSqlConverter {

    private static final Pattern LANG_VAR_PATTERN = Pattern.compile(
            "String\\s+(\\w+)\\s*=\\s*LocaleDataHelper\\.getPropert[^(]*\\(\"([^\"]+)\"");

    private static final Pattern VALUES_ADD_PATTERN = Pattern.compile(
            "^\\s*values\\.add\\((.+)\\)\\s*;\\s*$");

    private static final Pattern SCALAR_PATTERN = Pattern.compile(
            "scalarList\\.add\\(new HibernateScalarHelper\\(\"([^\"]+)\"");

    private static final Pattern CONDITIONS_GET_PATTERN = Pattern.compile(
            "\\(String\\)\\s*conditions\\.get\\(\"(\\w+)\"\\)|conditions\\.get\\(\"(\\w+)\"\\)");

    private static final Pattern PARM_PATTERN = Pattern.compile("'(Parm[^']*)'", Pattern.CASE_INSENSITIVE);

    // SQL keywords that should NOT be used as alias names
    private static final java.util.Set<String> SQL_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "AS", "JOIN", "LEFT", "RIGHT",
            "INNER", "OUTER", "GROUP", "ORDER", "BY", "HAVING", "UNION", "ALL", "DISTINCT",
            "COUNT", "SUM", "MAX", "MIN", "AVG", "CASE", "WHEN", "THEN", "ELSE", "END",
            "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "NULL", "IS"
    ));

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public String convert(String input) {
        String trimmedInput = input.trim();
        if (!trimmedInput.contains("sb.append(") && !trimmedInput.contains("StringBuffer")
                && !trimmedInput.contains("SQLQueryBuilder")) {
            return convertSql(input);
        }
        return convertDao(input);
    }

    // ─── Plain SQL Conversion ─────────────────────────────────────────────────

    private String convertSql(String input) {
        input = input.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = input.split("\n");

        StringBuilder result = new StringBuilder();
        result.append("SQLQueryBuilder sb = new SQLQueryBuilder(info);\n");

        boolean inSelect = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("--")) {
                result.append("// ").append(trimmed.substring(2).trim()).append("\n");
                continue;
            }
            if (trimmed.startsWith("//")) {
                result.append(trimmed.charAt(2) == ' ' ? trimmed : "// " + trimmed.substring(2)).append("\n");
                continue;
            }

            // Split inline SQL comment
            String sqlPart = trimmed;
            String commentPart = null;
            int dashIdx = trimmed.indexOf("--");
            if (dashIdx >= 0) {
                sqlPart = trimmed.substring(0, dashIdx).trim();
                commentPart = trimmed.substring(dashIdx + 2).trim();
            }

            if (!sqlPart.isEmpty()) {
                // Update SELECT state BEFORE processing
                String sqlUpper = sqlPart.trim().toUpperCase();
                if (sqlUpper.startsWith("SELECT")) {
                    inSelect = true;
                } else if (sqlUpper.startsWith("FROM") || sqlUpper.startsWith("WHERE")
                        || sqlUpper.startsWith("JOIN") || sqlUpper.startsWith("LEFT ")
                        || sqlUpper.startsWith("INNER") || sqlUpper.startsWith("RIGHT")
                        || sqlUpper.startsWith("ORDER") || sqlUpper.startsWith("GROUP")
                        || sqlUpper.startsWith("HAVING")) {
                    inSelect = false;
                }
                // Lines starting with "," stay in current SELECT state

                result.append(convertSqlLine(sqlPart, inSelect));
            }
            if (commentPart != null && !commentPart.isEmpty()) {
                result.append("// ").append(commentPart).append("\n");
            }
        }

        return result.toString();
    }

    private String convertSqlLine(String sql, boolean inSelect) {
        // Apply camelCase alias for SELECT columns
        if (inSelect) {
            sql = transformSelectContent(sql);
        }

        Matcher m = PARM_PATTERN.matcher(sql);
        if (!m.find()) {
            return "sb.append(\" " + sql + " \");\n";
        }

        // Build chained append with .param() for each 'ParmXXX'
        StringBuilder line = new StringBuilder();
        m.reset();
        int prevEnd = 0;
        boolean first = true;

        while (m.find()) {
            String sqlBefore = sql.substring(prevEnd, m.start()).stripTrailing();
            String rawName = m.group(1);
            String varName = Character.toLowerCase(rawName.charAt(0)) + rawName.substring(1);

            if (first) {
                line.append("sb.append(\" ").append(sqlBefore).append(" \").param(").append(varName).append(")");
                first = false;
            } else {
                line.append(".append(\" ").append(sqlBefore).append(" \").param(").append(varName).append(")");
            }
            prevEnd = m.end();
        }

        String sqlAfter = sql.substring(prevEnd).trim();
        if (!sqlAfter.isEmpty()) {
            line.append(".append(\" ").append(sqlAfter).append(" \")");
        }
        line.append(";\n");
        return line.toString();
    }

    // ─── Java DAO Conversion ──────────────────────────────────────────────────

    private String convertDao(String input) {
        input = input.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = input.split("\n");

        // First pass: collect lang vars and all values.add() expressions in order
        Map<String, String> langVarMap = new LinkedHashMap<>();
        LinkedList<String> valuesQueue = new LinkedList<>();

        for (String line : lines) {
            Matcher langM = LANG_VAR_PATTERN.matcher(line.trim());
            if (langM.find()) {
                langVarMap.put(langM.group(1), langM.group(2));
            }
            Matcher valM = VALUES_ADD_PATTERN.matcher(line);
            if (valM.find()) {
                valuesQueue.add(convertConditionsGet(valM.group(1).trim()));
            }
        }

        // Second pass: convert each line
        StringBuilder result = new StringBuilder();
        boolean inSelect = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            String indent = getIndent(line);

            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            // Remove boilerplate
            if (isBoilerplateLine(trimmed)) continue;
            if (LANG_VAR_PATTERN.matcher(trimmed).find()) continue;
            if (VALUES_ADD_PATTERN.matcher(line).find()) continue;
            if (trimmed.startsWith("conditionStr = AmConstants.CONDITION_STR_AND")) continue;
            if (trimmed.startsWith("List<HibernateScalarHelper>")) continue;

            // StringBuffer → SQLQueryBuilder
            if (trimmed.matches("StringBuffer\\s+\\w+\\s*=\\s*new StringBuffer\\(\\)\\s*;")) {
                Matcher m = Pattern.compile("StringBuffer\\s+(\\w+)").matcher(trimmed);
                String varName = m.find() ? m.group(1) : "sb";
                result.append(indent).append("SQLQueryBuilder ").append(varName)
                      .append(" = new SQLQueryBuilder(info);\n");
                continue;
            }

            // scalarList.add() → sb.scalarString()
            Matcher scalarM = SCALAR_PATTERN.matcher(trimmed);
            if (scalarM.find()) {
                result.append(indent).append("sb.scalarString(\"").append(scalarM.group(1)).append("\");\n");
                continue;
            }

            // if condition block (always in WHERE territory, skip alias)
            if ((trimmed.startsWith("if (StringUtils.isNotEmpty") || trimmed.startsWith("if (StringUtils.isNotBlank"))
                    && trimmed.contains("conditions.get")) {
                List<String> block = new ArrayList<>();
                block.add(line);
                int depth = countChar(trimmed, '{') - countChar(trimmed, '}');
                while (depth > 0 && ++i < lines.length) {
                    block.add(lines[i]);
                    depth += countChar(lines[i], '{') - countChar(lines[i], '}');
                }
                result.append(convertIfBlock(block, indent, valuesQueue));
                continue;
            }

            // sb.append(conditionStr) outside if block → sb.append("     AND ...")
            if (trimmed.startsWith("sb.append(conditionStr).append(\"")) {
                Matcher sqlM = Pattern.compile("sb\\.append\\(conditionStr\\)\\.append\\(\"(.*)\"\\)\\s*;").matcher(trimmed);
                if (sqlM.find()) {
                    String sql = sqlM.group(1).trim();
                    trimmed = "sb.append(\"     AND " + sql + "\");";
                }
                result.append(indent).append(convertSbAppend(trimmed, langVarMap, valuesQueue)).append("\n");
                continue;
            }

            // sb.append() — update SELECT state and apply alias
            if (trimmed.startsWith("sb.append(")) {
                String firstContent = extractFirstStrContent(trimmed);
                if (firstContent != null) {
                    String sqlTrim = firstContent.trim().toUpperCase();
                    if (sqlTrim.startsWith("SELECT")) {
                        inSelect = true;
                    } else if (sqlTrim.startsWith("FROM") || sqlTrim.startsWith("LEFT ")
                            || sqlTrim.startsWith("JOIN") || sqlTrim.startsWith("WHERE")
                            || sqlTrim.startsWith("ORDER") || sqlTrim.startsWith("GROUP")
                            || sqlTrim.startsWith("INNER") || sqlTrim.startsWith("RIGHT")
                            || sqlTrim.startsWith("HAVING")) {
                        inSelect = false;
                    }
                    // lines starting with "," stay in current inSelect state
                }

                String converted = convertSbAppend(trimmed, langVarMap, valuesQueue);
                if (inSelect) {
                    converted = applyAliasToSelectLine(converted, indent);
                }
                result.append(indent).append(converted).append("\n");
                continue;
            }

            // Comment: add space after // if missing
            if (trimmed.startsWith("//") && trimmed.length() > 2 && trimmed.charAt(2) != ' ') {
                result.append(indent).append("// ").append(trimmed.substring(2)).append("\n");
                continue;
            }

            // Pass through with minor conversions
            line = convertConditionsGet(line);
            line = line.replace("StringUtils.isNotEmpty", "StringUtils.isNotBlank");
            result.append(line).append("\n");
        }

        return result.toString();
    }

    // ─── Alias Helpers ────────────────────────────────────────────────────────

    /**
     * Convert SNAKE_CASE (or any _-delimited name) to lowerCamelCase.
     * e.g. "CONTR_NO" → "contrNo", "FEE_ITEM_OPT_CTG" → "feeItemOptCtg"
     */
    private String toAlias(String name) {
        name = name.trim();
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String lower = parts[i].toLowerCase();
            if (i == 0) {
                sb.append(lower);
            } else {
                sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Core SELECT-column transformer: given raw SQL content (may include leading
     * SELECT / comma), converts or adds a camelCase AS alias.
     *
     * Examples:
     *   "a.CONTR_NO"                          → "a.CONTR_NO AS contrNo"
     *   ", a.FEE_ITEM"                         → ", a.FEE_ITEM AS feeItem"
     *   "SELECT a.CONTR_NO"                    → "SELECT a.CONTR_NO AS contrNo"
     *   "COALESCE(C.COUNT_SN,0) AS COUNT_SN"  → "COALESCE(C.COUNT_SN,0) AS countSn"
     */
    private String transformSelectContent(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return content;

        // Parse leading prefix (SELECT keyword or leading comma)
        String prefix = "";
        String colExpr = trimmed;
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("SELECT ")) {
            prefix = "SELECT ";
            colExpr = trimmed.substring(7).trim();
        } else if (trimmed.startsWith(",")) {
            int end = 1;
            while (end < trimmed.length() && trimmed.charAt(end) == ' ') end++;
            prefix = trimmed.substring(0, end);
            colExpr = trimmed.substring(end);
        }

        // Existing AS alias → convert to camelCase
        Matcher asM = Pattern.compile("(?i)\\bAS\\s+(\\w+)\\s*$").matcher(colExpr);
        if (asM.find()) {
            String newAlias = toAlias(asM.group(1));
            String base = colExpr.substring(0, asM.start()).trim();
            return prefix + base + " AS " + newAlias;
        }

        // No alias: add one for simple column references (TABLE.COL or just COL)
        if (colExpr.matches("[A-Za-z_][\\w.]*")) {
            String colName = colExpr.contains(".")
                    ? colExpr.substring(colExpr.lastIndexOf('.') + 1)
                    : colExpr;
            if (!SQL_KEYWORDS.contains(colName.toUpperCase())) {
                return prefix + colExpr + " AS " + toAlias(colName);
            }
        }

        return trimmed; // complex expression without AS — leave unchanged
    }

    /**
     * Apply alias to an already-converted sb.append(...) line in SELECT mode.
     *
     * Single-string append:  sb.append(" , a.FEE_ITEM ");
     *   → modifies string content via transformSelectContent
     *
     * Chained append (appendLang, etc.):  sb.append("   , g1.").appendLang("CURR_NAM").append("  AS CMPT_CURR_NAM ");
     *   → only converts existing AS alias to camelCase
     */
    private String applyAliasToSelectLine(String line, String indent) {
        String trimmed = line.trim();

        // Case 1: single sb.append("CONTENT");
        Matcher single = Pattern.compile("^sb\\.append\\(\"([^\"]*)\"\\);$").matcher(trimmed);
        if (single.matches()) {
            String content = single.group(1);
            String processed = transformSelectContent(content.trim());
            // Preserve a leading space inside the string literal
            return "sb.append(\" " + processed + " \");";
        }

        // Case 2: complex chain — only convert existing AS alias
        Matcher asM = Pattern.compile("(?i)\\bAS\\s+(\\w+)(\\s*)\"").matcher(line);
        if (asM.find()) {
            StringBuffer sb = new StringBuffer();
            asM.reset();
            while (asM.find()) {
                String newAlias = toAlias(asM.group(1));
                asM.appendReplacement(sb, Matcher.quoteReplacement("AS " + newAlias + asM.group(2) + "\""));
            }
            asM.appendTail(sb);
            return sb.toString();
        }

        return line;
    }

    /** Extract the content of the first string literal in an sb.append("...") call. */
    private String extractFirstStrContent(String line) {
        Matcher m = Pattern.compile("sb\\.append\\(\"([^\"]*)\"").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    // ─── sb.append / ? Replacement ────────────────────────────────────────────

    private String convertSbAppend(String trimmed, Map<String, String> langVarMap, LinkedList<String> valuesQueue) {
        for (Map.Entry<String, String> e : langVarMap.entrySet()) {
            trimmed = trimmed.replace(".append(" + e.getKey() + ")", ".appendLang(\"" + e.getValue() + "\")");
        }
        while (trimmed.contains("?")) {
            String val = valuesQueue.isEmpty() ? "/* MISSING */" : valuesQueue.poll();
            trimmed = replaceFirstQuestion(trimmed, val);
        }
        return trimmed;
    }

    private String replaceFirstQuestion(String line, String value) {
        int qIdx = line.indexOf('?');
        if (qIdx == -1) return line;

        String before = line.substring(0, qIdx);
        String after = line.substring(qIdx + 1);
        String paramCall = ".param(" + value + ")";
        String afterTrimmed = after.stripLeading();

        if (afterTrimmed.startsWith("\");")) {
            return before + "\")" + paramCall + ";";
        } else {
            return before + "\")" + paramCall + ".append(\"" + after;
        }
    }

    // ─── if Condition Block ───────────────────────────────────────────────────

    private String convertIfBlock(List<String> block, String indent, LinkedList<String> valuesQueue) {
        StringBuilder result = new StringBuilder();

        String ifLine = block.get(0).trim();
        Matcher cgM = CONDITIONS_GET_PATTERN.matcher(ifLine);
        String newCondition;
        if (cgM.find()) {
            String key = cgM.group(1) != null ? cgM.group(1) : cgM.group(2);
            newCondition = "if (StringUtils.isNotBlank(" + keyToGetter("dto", key) + "))";
        } else {
            newCondition = ifLine;
        }

        // Find inner sb.append(conditionStr) line only.
        // values.add() inside the block was already added to valuesQueue during the
        // first pass, so consume it from there (keeps queue ordering correct).
        String appendLine = null;
        for (int i = 1; i < block.size(); i++) {
            String t = block.get(i).trim();
            if (t.startsWith("sb.append(conditionStr)")) {
                appendLine = t;
            }
        }

        result.append(indent).append(newCondition).append(" {\n");

        if (appendLine != null) {
            Matcher sqlM = Pattern.compile("\\.append\\(\"([^\"]+)\"\\)\\s*;").matcher(appendLine);
            if (sqlM.find()) {
                String sql = sqlM.group(1).trim();
                String sqlBeforeQ = sql.endsWith("?") ? sql.substring(0, sql.length() - 1).trim() : sql;
                String pv = valuesQueue.isEmpty() ? "/* MISSING */" : valuesQueue.poll();
                result.append(indent).append("    ")
                      .append("sb.whereAnd().append(\"AND ").append(sqlBeforeQ).append(" \").param(").append(pv).append(");\n");
            } else {
                result.append(indent).append("    ").append(appendLine).append("\n");
            }
        }

        result.append(indent).append("}\n");
        return result.toString();
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private boolean isBoilerplateLine(String trimmed) {
        return trimmed.matches("List<Object>\\s+values\\s*=\\s*new ArrayList.*")
                || trimmed.matches("String\\s+conditionStr\\s*=\\s*AmConstants.*");
    }

    private String getIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) if (ch == c) count++;
        return count;
    }

    private String convertConditionsGet(String expr) {
        Matcher m = CONDITIONS_GET_PATTERN.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(keyToGetter("dto", key)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String keyToGetter(String obj, String key) {
        return obj + ".get" + Character.toUpperCase(key.charAt(0)) + key.substring(1) + "()";
    }
}
