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
        Path configPath = base.resolve("kanvas.json");

        if (!Files.exists(configPath)) throw new ConfigException("kanvas.json not found at " + configPath.toAbsolutePath());
        try {
            content = Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("Failed to read kanvas.json: " + e.getMessage(), e);
        }
        Object parsed = JsonSimple.parse(content);
        if (!(parsed instanceof Map)) throw new ConfigException("kanvas.json root must be a JSON object");

        Map<String, Object> json = (Map<String, Object>) parsed;

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
        if (value == null || value.isBlank()) throw new ConfigException(field + " is required in kanvas.json");
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

    static class JsonSimple {
        public static Object parse(String s) throws KanvasException {
            return new Parser(stripBom(s).trim()).parseValue();
        }

        private static String stripBom(String s) {
            return (s.startsWith("\uFEFF")) ? s.substring(1) : s;
        }

        private static class Parser {
            private final String s;
            private int i = 0;

            Parser(String s) { this.s = s; }

            void skipWhitespace() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            }

            char peek() { return i < s.length() ? s.charAt(i) : '\0'; }
            char next() { return i < s.length() ? s.charAt(i++) : '\0'; }

            Object parseValue() throws KanvasException {
                skipWhitespace();
                char c = peek();
                if (c == '{') return parseObject();
                if (c == '[') return parseArray();
                if (c == '"') return parseString();
                if (c == 't' || c == 'f') return parseBoolean();
                if (c == 'n') { parseNull(); return null; }
                return parseNumber();
            }

            Map<String, Object> parseObject() throws KanvasException {
                Map<String, Object> map = new LinkedHashMap<>();
                expect('{');
                skipWhitespace();
                if (peek() == '}') { next(); return map; }
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    Object val = parseValue();
                    map.put(key, val);
                    skipWhitespace();
                    char c = next();
                    if (c == '}') break;
                    if (c != ',') throw new ConfigException("Expected ',' or '}' in object at pos " + i);
                }
                return map;
            }

            List<Object> parseArray() throws KanvasException {
                List<Object> arr = new ArrayList<>();
                expect('[');
                skipWhitespace();
                if (peek() == ']') { next(); return arr; }
                while (true) {
                    skipWhitespace();
                    Object v = parseValue();
                    arr.add(v);
                    skipWhitespace();
                    char c = next();
                    if (c == ']') break;
                    if (c != ',') throw new ConfigException("Expected ',' or ']' in array at pos " + i);
                }
                return arr;
            }

            String parseString() throws KanvasException {
                expect('"');
                StringBuilder sb = new StringBuilder();
                while (true) {
                    if (i >= s.length()) throw new ConfigException("Unterminated string");
                    char c = next();
                    if (c == '"') break;
                    if (c == '\\') {
                        char e = next();
                        switch (e) {
                            case '"': sb.append('"'); break;
                            case '\\': sb.append('\\'); break;
                            case '/': sb.append('/'); break;
                            case 'b': sb.append('\b'); break;
                            case 'f': sb.append('\f'); break;
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            case 'u':
                                String hex = s.substring(i, i+4); i += 4;
                                sb.append((char) Integer.parseInt(hex, 16));
                                break;
                            default:
                                sb.append(e);
                        }
                    } else sb.append(c);
                }
                return sb.toString();
            }

            Boolean parseBoolean() throws KanvasException {
                if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                throw new ConfigException("Invalid token for boolean at pos " + i);
            }

            void parseNull() throws KanvasException {
                if (s.startsWith("null", i)) { i += 4; return; }
                throw new ConfigException("Invalid token at pos " + i);
            }

            Number parseNumber() throws KanvasException {
                int start = i;
                if (peek() == '-') i++;
                while (Character.isDigit(peek())) i++;
                if (peek() == '.') {
                    i++;
                    while (Character.isDigit(peek())) i++;
                }
                String num = s.substring(start, i);
                try {
                    if (num.contains(".")) return Double.parseDouble(num);
                    return Long.parseLong(num);
                } catch (NumberFormatException ex) {
                    throw new ConfigException("Invalid number: " + num);
                }
            }

            void expect(char c) throws KanvasException {
                skipWhitespace();
                char n = next();
                if (n != c) throw new ConfigException("Expected '" + c + "' but found '" + n + "' at pos " + i);
            }
        }
    }
}
