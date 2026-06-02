package kanvas.processor;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unchecked")

public class ConfigLoader {
    public static Config loadConfig(String projectPath) {
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

        String name = getString(json, "name", base.getFileName().toString());
        String version = getString(json, "version", "0.1.0");
        String author = getString(json, "author", "");
        String description = getString(json, "description", "");
        String target = getNestedString(json, "compiler", "target", "11");
        String encoding = getNestedString(json, "compiler", "encoding", "UTF-8");
        String jarName = getNestedString(json, "packaging", "jarName", name + ".jar");
        String mainClass = getString(json, "mainClass", null);
        Path mainFile = null;
        if (mainClass != null) mainFile = base.resolve("src").resolve(mainClass.replace('.', '/') + ".java");
        String outputDir = getNestedString(json, "modules", "outputDir", "build/classes");
        List<File> srcDirs = new ArrayList<>();
        Object srcsObj = getNested(json, "modules", "srcDirs");
        if (srcsObj instanceof List)
            for (Object obj : (List<Object>) srcsObj)
                if (obj != null) srcDirs.add(new File(base.resolve(obj.toString()).toString()));
        else srcDirs.add(new File(base.resolve("src").toString()));
        List<File> deps = new ArrayList<>();
        Object depsObj = json.get("dependencies");
        if (depsObj instanceof List)
            for (Object obj : (List<Object>) depsObj)
                if (obj != null) deps.add(new File(base.resolve("lib").resolve(obj.toString()).toString()));
        List<String> nativeTargets = new ArrayList<>();
        Object natTargsObj = getNested(json, "packaging", "nativeTargets");
        if (natTargsObj instanceof List)
            for (Object o : (List<Object>) natTargsObj)
                if (o != null) nativeTargets.add(o.toString());

        return new Config(name, jarName, version, author, description, mainClass, target, encoding,
                mainFile == null ? null : mainFile.toFile(),
                new File(base.resolve(outputDir).toString()),
                srcDirs, deps, nativeTargets);
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
        public static Object parse(String s) {
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

            Object parseValue() {
                skipWhitespace();
                char c = peek();
                if (c == '{') return parseObject();
                if (c == '[') return parseArray();
                if (c == '"') return parseString();
                if (c == 't' || c == 'f') return parseBoolean();
                if (c == 'n') { parseNull(); return null; }
                return parseNumber();
            }

            Map<String, Object> parseObject() {
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

            List<Object> parseArray() {
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

            String parseString() {
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

            Boolean parseBoolean() {
                if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                throw new ConfigException("Invalid token for boolean at pos " + i);
            }

            void parseNull() {
                if (s.startsWith("null", i)) { i += 4; return; }
                throw new ConfigException("Invalid token at pos " + i);
            }

            Number parseNumber() {
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

            void expect(char c) {
                skipWhitespace();
                char n = next();
                if (n != c) throw new ConfigException("Expected '" + c + "' but found '" + n + "' at pos " + i);
            }
        }
    }
}
