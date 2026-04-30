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
            "scalarList\\.add\\(new HibernateScalarHelper\\(\"([^\"]+)\"(?:,\\s*StandardBasicTypes\\.(\\w+))?");

    private static final Pattern CONDITIONS_GET_PATTERN = Pattern.compile(
            "\\(String\\)\\s*conditions\\.get\\(\"(\\w+)\"\\)|conditions\\.get\\(\"(\\w+)\"\\)");

    private static final Pattern PARM_PATTERN = Pattern.compile("'(Parm[^']*)'", Pattern.CASE_INSENSITIVE);

    // Matches SELECT columns with a _LANG\d+ suffix, e.g. ", c.CORP_SNAM_LANG1 AS HQ_SNAM"
    private static final Pattern LANG_COL_DETECT = Pattern.compile(
            "(?i)^(SELECT\\s+|,\\s*)?([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*?)(_LANG\\d+)(?:\\s+AS\\s+([A-Za-z_]\\w*))?\\s*$");

    // SQL keywords that should NOT be used as alias names
    private static final java.util.Set<String> SQL_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "AS", "JOIN", "LEFT", "RIGHT",
            "INNER", "OUTER", "GROUP", "ORDER", "BY", "HAVING", "UNION", "ALL", "DISTINCT",
            "COUNT", "SUM", "MAX", "MIN", "AVG", "CASE", "WHEN", "THEN", "ELSE", "END",
            "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "NULL", "IS"
    ));

    private static final Pattern ANY_APPEND_PATTERN = Pattern.compile("\\w+\\.append\\(");

    /** When false, aliases are kept as lowercase_snake instead of lowerCamelCase. */
    private boolean camelCaseAlias = true;

    private static class ChainSeg {
        final boolean isLang;
        final String content;
        ChainSeg(boolean isLang, String content) { this.isLang = isLang; this.content = content.trim(); }
    }

    public DaoSqlConverter setCamelCaseAlias(boolean camelCaseAlias) {
        this.camelCaseAlias = camelCaseAlias;
        return this;
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public String convert(String input) {
        return convert(input, this.camelCaseAlias);
    }

    public String convert(String input, boolean camelCaseAlias) {
        boolean prev = this.camelCaseAlias;
        this.camelCaseAlias = camelCaseAlias;
        try {
            String trimmedInput = input.trim();
            boolean looksLikeDao = trimmedInput.contains("StringBuffer")
                    || trimmedInput.contains("StringBuilder")
                    || trimmedInput.contains("SQLQueryBuilder")
                    || ANY_APPEND_PATTERN.matcher(trimmedInput).find();
            return looksLikeDao ? convertDao(input) : convertSql(input);
        } finally {
            this.camelCaseAlias = prev;
        }
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
        if (inSelect) {
            String langLine = tryBuildLangLine(sql);
            if (langLine != null) return langLine + "\n";
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

        // Normalize StringBuffer variable name (e.g. queryString → sb)
        Matcher sbDeclM = Pattern.compile("(?:StringBuffer|StringBuilder)\\s+(\\w+)\\s*=").matcher(input);
        if (sbDeclM.find()) {
            String sbVar = sbDeclM.group(1);
            if (!sbVar.equals("sb")) {
                input = input.replaceAll("(?:StringBuffer|StringBuilder)\\s+" + Pattern.quote(sbVar) + "\\b", "StringBuffer sb");
                input = input.replace(sbVar + ".append(", "sb.append(");
            }
        } else {
            // No declaration found — detect variable name from first ident.append( call
            Matcher appendM = Pattern.compile("^\\s*(\\w+)\\.append\\(", Pattern.MULTILINE).matcher(input);
            if (appendM.find()) {
                String sbVar = appendM.group(1);
                if (!sbVar.equals("sb")) {
                    input = input.replace(sbVar + ".append(", "sb.append(");
                }
            }
        }

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
        int subqueryDepth = 0;
        boolean selectColumnsStarted = false; // true once first SELECT column has been output

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
                String scalarMethod = toScalarMethod(scalarM.group(2));
                result.append(indent).append("sb.").append(scalarMethod).append("(\"").append(toAlias(scalarM.group(1))).append("\");\n");
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
                        selectColumnsStarted = false;
                    } else if (sqlTrim.startsWith("FROM") || sqlTrim.startsWith("LEFT ")
                            || sqlTrim.startsWith("JOIN") || sqlTrim.startsWith("WHERE")
                            || sqlTrim.startsWith("ORDER") || sqlTrim.startsWith("GROUP")
                            || sqlTrim.startsWith("INNER") || sqlTrim.startsWith("RIGHT")
                            || sqlTrim.startsWith("HAVING")) {
                        inSelect = false;
                        selectColumnsStarted = false;
                    }
                    // lines starting with "," stay in current inSelect state
                }

                // shouldAlias: true only when we are in SELECT scope AND not inside a subquery
                boolean shouldAlias = inSelect && subqueryDepth == 0;

                // Expand multi-column SELECT into individual formatted lines
                if (shouldAlias) {
                    Matcher singleM = Pattern.compile("^sb\\.append\\(\"([^\"]*)\"\\);$").matcher(trimmed);
                    if (singleM.matches()) {
                        String expanded = expandMultiColumnSelect(singleM.group(1), indent);
                        if (expanded != null) {
                            subqueryDepth += netParenChange(trimmed);
                            selectColumnsStarted = true;
                            result.append(expanded);
                            continue;
                        }
                    }
                }

                String converted = convertSbAppend(trimmed, langVarMap, valuesQueue);
                if (shouldAlias) {
                    String expandedChain = tryExpandChainedColumns(converted, indent, selectColumnsStarted);
                    if (expandedChain != null) {
                        subqueryDepth += netParenChange(trimmed);
                        selectColumnsStarted = true;
                        result.append(expandedChain);
                        continue;
                    }
                    converted = applyAliasToSelectLine(converted, indent);
                    selectColumnsStarted = true;
                }
                subqueryDepth += netParenChange(trimmed);
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
     * If {@code sql} is a SELECT column with a _LANG\d+ suffix (e.g. ", c.CORP_SNAM_LANG1 AS HQ_SNAM"),
     * returns the chained sb.append line using appendLang; otherwise returns null.
     */
    private String tryBuildLangLine(String sql) {
        Matcher m = LANG_COL_DETECT.matcher(sql.trim());
        if (!m.matches()) return null;

        String prefix      = m.group(1) != null ? m.group(1) : "";  // ", " or "SELECT " or ""
        String tableAlias  = m.group(2);   // e.g. "c"
        String baseColName = m.group(3);   // e.g. "CORP_SNAM"
        String aliasName   = m.group(5);   // e.g. "HQ_SNAM", may be null

        String camelAlias  = (aliasName != null) ? toAlias(aliasName) : toAlias(baseColName);

        return "sb.append(\" " + prefix + tableAlias + ".\").appendLang(\"" + baseColName + "\").append(\"  AS " + camelAlias + " \");";
    }

    /**
     * Convert SNAKE_CASE to lowerCamelCase when camelCaseAlias is true,
     * otherwise return lowercase_snake_case unchanged.
     * e.g. "CONTR_NO" → "contrNo" (camel) or "contr_no" (snake)
     */
    private String toScalarMethod(String basicType) {
        if (basicType == null) return "scalarString";
        switch (basicType.toUpperCase()) {
            case "STRING":      return "scalarString";
            case "BIG_DECIMAL": return "scalarDecimal";
            case "INTEGER":     return "scalarInteger";
            case "TIMESTAMP":   return "scalarTime";
            default:            return "scalarString";
        }
    }

    private String toAlias(String name) {
        name = name.trim();
        if (!camelCaseAlias) return name;
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

        // No alias: add one for simple column references (camelCase mode only)
        if (camelCaseAlias && colExpr.matches("[A-Za-z_][\\w.]*")) {
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
            String langLine = tryBuildLangLine(content.trim());
            if (langLine != null) return langLine;
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

    /**
     * Splits a string by top-level commas (ignores commas inside parentheses).
     * e.g. "a.COL1, COALESCE(a.X,0), a.COL2" → ["a.COL1", "COALESCE(a.X,0)", "a.COL2"]
     */
    private List<String> splitTopLevelCommas(String content) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(content.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(content.substring(start).trim());
        return parts;
    }

    /**
     * If rawContent (the string inside sb.append("...")) contains multiple SELECT columns,
     * expands them into individual sb.append lines with camelCase AS aliases.
     * Returns null when there is only one column (let existing logic handle it).
     */
    private String expandMultiColumnSelect(String rawContent, String indent) {
        String trimmed = rawContent.trim();
        boolean hasSelect = trimmed.toUpperCase().startsWith("SELECT ");
        String colsStr = hasSelect ? trimmed.substring(7).trim() : trimmed;

        // Strip leading comma (e.g. continuation line ", a.COL, b.COL")
        if (colsStr.startsWith(",")) {
            colsStr = colsStr.substring(1).trim();
        }
        // Strip trailing comma (old-style split-across-multiple-appends)
        if (colsStr.endsWith(",")) {
            colsStr = colsStr.substring(0, colsStr.length() - 1).trim();
        }

        List<String> cols = splitTopLevelCommas(colsStr);
        List<String> filtered = new ArrayList<>();
        for (String c : cols) {
            if (!c.trim().isEmpty()) filtered.add(c.trim());
        }
        if (filtered.size() <= 1) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < filtered.size(); i++) {
            String sqlInput = (i == 0 && hasSelect)
                    ? "SELECT " + filtered.get(i)
                    : "," + filtered.get(i);
            String transformed = transformSelectContent(sqlInput);
            result.append(indent).append("sb.append(\" ").append(transformed).append(" \");\n");
        }
        return result.toString();
    }

    /** Parse sb.append("X").appendLang("Y").append("Z")... into ordered ChainSeg list. */
    private List<ChainSeg> parseChainSegs(String line) {
        List<ChainSeg> segs = new ArrayList<>();
        Matcher firstM = Pattern.compile("^sb\\.append\\(\"([^\"]*)\"\\)").matcher(line.trim());
        if (!firstM.find()) return segs;
        segs.add(new ChainSeg(false, firstM.group(1)));
        Matcher restM = Pattern.compile("\\.appendLang\\(\"([^\"]*)\"\\)|\\.append\\(\"([^\"]*)\"\\)")
                .matcher(line.trim());
        restM.region(firstM.end(), line.trim().length());
        while (restM.find()) {
            if (restM.group(1) != null) segs.add(new ChainSeg(true,  restM.group(1)));
            else                        segs.add(new ChainSeg(false, restM.group(2)));
        }
        return segs;
    }

    /** Split segments into per-column groups by top-level commas within plain segments. */
    private List<List<ChainSeg>> splitSegsByComma(List<ChainSeg> segs) {
        List<List<ChainSeg>> cols = new ArrayList<>();
        List<ChainSeg> current = new ArrayList<>();
        for (ChainSeg seg : segs) {
            if (seg.isLang) { current.add(seg); continue; }
            String text = seg.content;
            int depth = 0, start = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if      (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 0) {
                    String before = text.substring(start, i).trim();
                    if (!before.isEmpty()) current.add(new ChainSeg(false, before));
                    if (!current.isEmpty()) cols.add(current);
                    current = new ArrayList<>();
                    start = i + 1;
                }
            }
            String rem = text.substring(start).trim();
            if (!rem.isEmpty()) current.add(new ChainSeg(false, rem));
        }
        boolean hasContent = false;
        for (ChainSeg s : current) if (!s.content.isEmpty()) { hasContent = true; break; }
        if (hasContent) cols.add(current);
        return cols;
    }

    /** Render one column group as a single sb.append (possibly chained) line. */
    private String renderColumnGroup(List<ChainSeg> segs, String commaPrefix, String indent) {
        if (segs.isEmpty()) return "";
        boolean hasLang = false;
        for (ChainSeg s : segs) if (s.isLang) { hasLang = true; break; }

        if (!hasLang) {
            StringBuilder joined = new StringBuilder();
            for (ChainSeg s : segs) joined.append(s.content);
            String sql = commaPrefix + joined.toString().trim();
            return indent + "sb.append(\" " + transformSelectContent(sql) + " \");\n";
        }

        StringBuilder out = new StringBuilder();
        out.append(indent).append("sb.append(\" ").append(commaPrefix);
        boolean inStr = true;
        for (ChainSeg seg : segs) {
            if (!seg.isLang) {
                if (!inStr) { out.append(".append(\" "); inStr = true; }
                String content = seg.content;
                Matcher asM = Pattern.compile("(?i)\\bAS\\s+(\\w+)\\s*$").matcher(content);
                if (asM.find()) {
                    content = content.substring(0, asM.start()).trim() + " AS " + toAlias(asM.group(1));
                }
                out.append(content);
            } else {
                out.append(inStr ? "\").appendLang(\"" : ".appendLang(\"").append(seg.content).append("\")");
                inStr = false;
            }
        }
        out.append(inStr ? " \");\n" : ";\n");
        return out.toString();
    }

    /** Net change in parenthesis depth from all string literals in a sb.append line. */
    private int netParenChange(String sbAppendLine) {
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(sbAppendLine);
        int net = 0;
        while (m.find()) {
            for (char c : m.group(1).toCharArray()) {
                if      (c == '(') net++;
                else if (c == ')') net--;
            }
        }
        return net;
    }

    /**
     * Expand a chained append line in SELECT mode into individual per-column lines.
     * Handles any number of appendLang calls and multi-column strings.
     * Returns null only when the line is a simple single-segment plain append
     * (already handled by expandMultiColumnSelect + applyAliasToSelectLine).
     */
    private String tryExpandChainedColumns(String line, String indent, boolean firstColNeedsComma) {
        List<ChainSeg> segs = parseChainSegs(line.trim());
        if (segs.isEmpty()) return null;
        // Single plain segment: let the simpler paths handle it
        if (segs.size() == 1 && !segs.get(0).isLang) return null;

        // Strip leading "SELECT " or "," prefix from the first plain segment
        String linePrefix = "";
        if (!segs.isEmpty() && !segs.get(0).isLang) {
            String first = segs.get(0).content;
            if (first.toUpperCase().startsWith("SELECT ")) {
                linePrefix = "SELECT ";
                String rest = first.substring(7).trim();
                if (rest.isEmpty()) segs.remove(0); else segs.set(0, new ChainSeg(false, rest));
            } else if (first.startsWith(",")) {
                linePrefix = ",";
                String rest = first.substring(1).trim();
                if (rest.isEmpty()) segs.remove(0); else segs.set(0, new ChainSeg(false, rest));
            } else if (firstColNeedsComma) {
                linePrefix = ",";
            }
        } else if (!segs.isEmpty() && segs.get(0).isLang && firstColNeedsComma) {
            linePrefix = ",";
        }
        if (segs.isEmpty()) return null;

        List<List<ChainSeg>> cols = splitSegsByComma(segs);
        cols.removeIf(col -> { for (ChainSeg s : col) if (!s.content.isEmpty()) return false; return true; });
        if (cols.isEmpty()) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            result.append(renderColumnGroup(cols.get(i), i == 0 ? linePrefix : ",", indent));
        }
        return result.toString();
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
                String sqlClean = sqlBeforeQ.replaceFirst("(?i)^(AND|WHERE)\\s+", "").trim();
                String pv = valuesQueue.isEmpty() ? "/* MISSING */" : valuesQueue.poll();
                result.append(indent).append("    ")
                      .append("sb.whereAnd().append(\" ").append(sqlClean).append(" \").param(").append(pv).append(");\n");
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
