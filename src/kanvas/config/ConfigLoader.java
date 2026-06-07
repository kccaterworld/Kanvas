package kanvas.config;

import kanvas.KanvasException;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unchecked")

public class ConfigLoader {
    public static Config loadConfig(String projectPath) throws KanvasException {
        return loadConfig(projectPath, Collections.emptyMap());
    }

    public static Config loadConfig(String projectPath, Map<String, String> overrides) throws KanvasException {
        String content;
        Path base = Paths.get(projectPath);
        Path configPath = base.resolve("kanvas.toml");

        if (!Files.exists(configPath)) throw new ConfigException("kanvas.toml not found at " + configPath.toAbsolutePath());
        try {
            content = Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("Failed to read kanvas.toml: " + e.getMessage(), e);
        }
        Map<String, Object> json = TomlSimple.parse(content);

        String name = override(overrides, "name", getString(json, "name", null));
        String version = override(overrides, "version", getString(json, "version", null));
        String author = override(overrides, "author", getString(json, "author", ""));
        String description = override(overrides, "description", getString(json, "description", ""));
        String target = override(overrides, "target", getNestedString(json, "compiler", "target", null));
        String encoding = override(overrides, "encoding", getNestedString(json, "compiler", "encoding", null));
        String jarName = override(overrides, "jarName", getNestedString(json, "packaging", "jarName", null));
        String packageVersion = override(overrides, "packageVersion", getNestedString(json, "packaging", "version", version));
        String iconPath = override(overrides, "icon", getNestedString(json, "packaging", "icon", null));
        String mainClass = override(overrides, "mainClass", getString(json, "mainClass", null));
        validateRequired("name", name);
        validateRequired("version", version);
        validateRequired("compiler.target", target);
        validateRequired("compiler.encoding", encoding);
        validateRequired("packaging.jarName", jarName);
        Path mainFile = null;
        if (mainClass != null && !mainClass.isBlank()) mainFile = base.resolve("src").resolve(mainClass.replace('.', '/') + ".java");
        String outputDir = override(overrides, "outputDir", getNestedString(json, "modules", "outputDir", null));
        validateRequired("modules.outputDir", outputDir);
        List<File> srcDirs = new ArrayList<>();
        List<String> srcOverrides = overrideList(overrides, "srcDirs");
        if (srcOverrides != null) {
            for (String src : srcOverrides)
                if (src != null && !src.isBlank()) srcDirs.add(new File(base.resolve(src).toString()));
        }
        else {
            Object srcsObj = getNested(json, "modules", "srcDirs");
            if (srcsObj instanceof List) {
                for (Object srcObj : (List<Object>) srcsObj)
                    if (srcObj != null) srcDirs.add(new File(base.resolve(srcObj.toString()).toString()));
            }
        }
        if (srcDirs.isEmpty()) throw new ConfigException("modules.srcDirs must contain at least one directory");
        List<File> classpath = new ArrayList<>();
        List<String> classpathOverrides = overrideList(overrides, "classpath");
        Object classpathObj = json.get("classpath");
        if (classpathOverrides != null) {
            for (String classpathEntry : classpathOverrides)
                if (classpathEntry != null && !classpathEntry.isBlank()) classpath.add(new File(base.resolve(classpathEntry).toString()));
        }
        else if (classpathObj instanceof List) {
            for (Object classpathObject : (List<Object>) classpathObj)
                if (classpathObject != null) classpath.add(new File(base.resolve(classpathObject.toString()).toString()));
        }
        List<File> deps = new ArrayList<>();
        List<String> depOverrides = overrideList(overrides, "dependencies");
        if (depOverrides != null) {
            for (String dep : depOverrides)
                if (dep != null && !dep.isBlank()) deps.add(new File(base.resolve("lib").resolve(dep).toString()));
        }
        else {
            Object depsObj = json.get("dependencies");
            if (depsObj instanceof List) {
                for (Object depObject : (List<Object>) depsObj)
                    if (depObject != null) deps.add(new File(base.resolve("lib").resolve(depObject.toString()).toString()));
            }
        }
        List<String> nativeTargets = new ArrayList<>();
        List<String> nativeTargetOverrides = overrideList(overrides, "nativeTargets");
        if (nativeTargetOverrides != null) nativeTargets.addAll(nativeTargetOverrides);
        else {
            Object natTargsObj = getNested(json, "packaging", "nativeTargets");
            if (natTargsObj instanceof List) {
                for (Object nativeTarget : (List<Object>) natTargsObj)
                    if (nativeTarget != null) nativeTargets.add(nativeTarget.toString());
            }
        }

        return new Config(name, jarName, version, author, description,
                blankToNull(mainClass), target, encoding, packageVersion,
                iconPath == null || iconPath.isBlank() ? null : new File(base.resolve(iconPath).toString()),
                mainFile == null ? null : mainFile.toFile(),
                new File(base.resolve(outputDir).toString()),
                srcDirs, classpath, deps, nativeTargets);
    }

    private static String override(Map<String, String> overrides, String key, String fallback) {
        if (overrides == null || !overrides.containsKey(key)) return fallback;
        return overrides.get(key);
    }

    private static List<String> overrideList(Map<String, String> overrides, String key) {
        if (overrides == null || !overrides.containsKey(key)) return null;
        String value = overrides.get(key);
        if (value == null || value.isBlank()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (String part : value.split(","))
            if (!part.trim().isEmpty()) values.add(part.trim());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void validateRequired(String field, String value) throws KanvasException {
        if (value == null || value.isBlank()) throw new ConfigException(field + " is required in kanvas.toml");
    }

    private static String getString(Map<String, Object> json, String key, String def) {
        Object v = json.get(key);
        return v == null ? def : v.toString();
    }

    private static String getNestedString(Map<String, Object> json, String parent, String key, String def) {
        Object parentObj = json.get(parent);
        if (parentObj instanceof Map) {
            Object v = ((Map<String, Object>) parentObj).get(key);
            return v == null ? def : v.toString();
        }
        return def;
    }

    private static Object getNested(Map<String, Object> json, String parent, String key) {
        Object parentObj = json.get(parent);
        if (parentObj instanceof Map) return ((Map<String, Object>) parentObj).get(key);
        return null;
    }

    static class TomlSimple {
        public static Map<String, Object> parse(String content) throws KanvasException {
            String s = content.startsWith("\uFEFF") ? content.substring(1) : content;
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> current = root;
            for (String rawLine : s.split("\r?\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && !line.startsWith("[[")) {
                    int end = line.indexOf(']');
                    if (end < 0) throw new ConfigException("Invalid TOML table header: " + line);
                    String tableName = line.substring(1, end).trim();
                    Map<String, Object> table = new LinkedHashMap<>();
                    root.put(tableName, table);
                    current = table;
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) throw new ConfigException("Invalid TOML line: " + line);
                String key = line.substring(0, eq).trim();
                String valueStr = line.substring(eq + 1).trim();
                current.put(key, parseValue(valueStr));
            }
            return root;
        }

        private static Object parseValue(String s) throws KanvasException {
            if (s.isEmpty()) return null;
            if (s.startsWith("\"")) return parseBasicString(s, new int[]{0});
            if (s.startsWith("'")) return parseLiteralString(s, new int[]{0});
            if (s.startsWith("[")) return parseArray(s, new int[]{0});
            if (s.equals("true")) return Boolean.TRUE;
            if (s.equals("false")) return Boolean.FALSE;
            int hash = s.indexOf('#');
            String num = (hash >= 0 ? s.substring(0, hash) : s).trim();
            if (num.isEmpty()) return null;
            try {
                if (num.contains(".")) return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw new ConfigException("Cannot parse TOML value: " + s);
            }
        }

        private static String parseBasicString(String s, int[] pos) throws KanvasException {
            pos[0]++;
            StringBuilder sb = new StringBuilder();
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos[0] >= s.length()) throw new ConfigException("Unterminated escape in TOML string");
                    char e = s.charAt(pos[0]++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            throw new ConfigException("Unterminated TOML string");
        }

        private static String parseLiteralString(String s, int[] pos) throws KanvasException {
            pos[0]++;
            StringBuilder sb = new StringBuilder();
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]++);
                if (c == '\'') return sb.toString();
                sb.append(c);
            }
            throw new ConfigException("Unterminated TOML literal string");
        }

        private static List<Object> parseArray(String s, int[] pos) throws KanvasException {
            pos[0]++;
            List<Object> list = new ArrayList<>();
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]);
                if (Character.isWhitespace(c)) { pos[0]++; continue; }
                if (c == ']') { pos[0]++; break; }
                if (c == ',') { pos[0]++; continue; }
                if (c == '#') break;
                if (c == '"') list.add(parseBasicString(s, pos));
                else if (c == '\'') list.add(parseLiteralString(s, pos));
                else if (c == '[') list.add(parseArray(s, pos));
                else {
                    int start = pos[0];
                    while (pos[0] < s.length() && s.charAt(pos[0]) != ',' && s.charAt(pos[0]) != ']') pos[0]++;
                    String item = s.substring(start, pos[0]).trim();
                    if (item.equals("true")) list.add(Boolean.TRUE);
                    else if (item.equals("false")) list.add(Boolean.FALSE);
                    else if (!item.isEmpty()) {
                        try {
                            list.add(item.contains(".") ? Double.parseDouble(item) : Long.parseLong(item));
                        } catch (NumberFormatException e) {
                            throw new ConfigException("Cannot parse TOML array value: " + item);
                        }
                    }
                }
            }
            return list;
        }
    }
}
