package tool.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tool.helper.StringHelper;

public class BeanFieldParser {

    // Excludes static fields; allows final/transient/volatile modifiers; allows field initializers
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*private(?!\\s+static)\\s+(?:(?:final|transient|volatile)\\s+)*(\\S+(?:<[^>]+>)?)\\s+(\\w+)[^;]*;");
    private static final Pattern EXTENDS_PATTERN = Pattern.compile(
            "\\bclass\\s+\\w+(?:<[^>]+>)?\\s+extends\\s+(\\w+)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?!static\\s)([\\w.]+)\\s*;");

    /**
     * Parse all fields from beanName.java found under beanPath, following extends chain.
     * Uses package/import declarations to locate parent classes across the source tree.
     * Stops at StandardEntity. Returns fieldName (camelCase) → Java type.
     */
    public static Map<String, String> parseFields(String beanPath, String beanName) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();

        Path initialFile = findFile(beanPath, beanName.trim() + ".java");
        if (initialFile == null) return result;

        String sourceRoot = computeSourceRoot(initialFile);
        parseRecursiveFromFile(initialFile, beanName.trim(), result, visited, sourceRoot);
        return result;
    }

    /**
     * Derive the source root by navigating up from the file's parent using
     * the package declaration. e.g. file in .../src/com/ddsc/am/, package com.ddsc.am
     * → source root is .../src/
     */
    private static String computeSourceRoot(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                Matcher m = PACKAGE_PATTERN.matcher(line);
                if (m.find()) {
                    String[] pkgParts = m.group(1).split("\\.");
                    Path dir = file.getParent();
                    for (int i = pkgParts.length - 1; i >= 0 && dir != null; i--) {
                        if (dir.getFileName() == null
                                || !dir.getFileName().toString().equals(pkgParts[i])) {
                            return null;
                        }
                        dir = dir.getParent();
                    }
                    return dir != null ? dir.toString() : null;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static void parseRecursiveFromFile(Path file, String className,
                                               Map<String, String> result, Set<String> visited,
                                               String sourceRoot) {
        if ("StandardEntity".equalsIgnoreCase(className) || !visited.add(className)) return;
        if (file == null) return;

        try {
            List<String> lines = Files.readAllLines(file);
            Map<String, String> imports = new LinkedHashMap<>(); // simpleName → fully qualified name
            String parentClass = null;

            for (String line : lines) {
                // Collect imports (skip static and wildcard)
                Matcher impM = IMPORT_PATTERN.matcher(line);
                if (impM.find()) {
                    String fqn = impM.group(1);
                    if (!fqn.endsWith("*")) {
                        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                        imports.putIfAbsent(simple, fqn);
                    }
                    continue;
                }

                // Collect extends (first occurrence)
                if (parentClass == null) {
                    Matcher extM = EXTENDS_PATTERN.matcher(line);
                    if (extM.find()) parentClass = extM.group(1);
                }

                // Collect fields
                Matcher fieldM = FIELD_PATTERN.matcher(line);
                if (fieldM.find()) {
                    result.putIfAbsent(fieldM.group(2), stripGenerics(fieldM.group(1)));
                }
            }

            if (parentClass != null && !"StandardEntity".equalsIgnoreCase(parentClass)) {
                Path parentFile = resolveClassFile(parentClass, imports, sourceRoot);
                parseRecursiveFromFile(parentFile, parentClass, result, visited, sourceRoot);
            }
        } catch (IOException e) {
            // silently skip unreadable files
        }
    }

    /**
     * Locate a class file: first try using the import statement to build an exact path,
     * then fall back to a recursive search from sourceRoot.
     */
    private static Path resolveClassFile(String simpleName, Map<String, String> imports, String sourceRoot) {
        String fqn = imports.get(simpleName);
        if (fqn != null && sourceRoot != null) {
            String[] parts = fqn.split("\\.");
            parts[parts.length - 1] = parts[parts.length - 1] + ".java";
            Path candidate = Paths.get(sourceRoot, parts);
            if (Files.exists(candidate)) return candidate;
        }
        if (sourceRoot != null) {
            return findFile(sourceRoot, simpleName + ".java");
        }
        return null;
    }

    private static Path findFile(String searchRoot, String fileName) {
        try (Stream<Path> stream = Files.walk(Paths.get(searchRoot))) {
            Optional<Path> found = stream
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();
            return found.orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt >= 0 ? type.substring(0, lt).trim() : type;
    }

    /** Map Java type name to SQLQueryBuilder scalar method. */
    public static String javaTypeToScalarMethod(String javaType) {
        return StringHelper.scalarMethod(javaType);
    }
}
