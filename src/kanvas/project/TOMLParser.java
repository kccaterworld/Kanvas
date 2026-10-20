package kanvas.project;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

/**
 * TOML 1.0 parser. Parsing only: no file I/O and no knowledge of the Kanvas schema.
 *
 * Tables become {@link LinkedHashMap}s (key order preserved), arrays become {@link ArrayList}s, and
 * leaf values are {@code String}, {@code Long}, {@code Double}, {@code Boolean}, {@link OffsetDateTime},
 * {@link LocalDateTime}, {@link LocalDate} or {@link LocalTime}.
 *
 * Errors read {@code kanvas.toml:14:9: message}.
 */
public final class TOMLParser {
    private static final Pattern DECIMAL_INT = Pattern.compile("[+-]?(0|[1-9](_?[0-9])*)");
    private static final Pattern HEXADECIMAL_INT = Pattern.compile("0x[0-9A-Fa-f](_?[0-9A-Fa-f])*");
    private static final Pattern OCTAL_INT = Pattern.compile("0o[0-7](_?[0-7])*");
    private static final Pattern BINARY_INT = Pattern.compile("0b[01](_?[01])*");
    private static final Pattern FLOAT = Pattern.compile("[+-]?(0|[1-9](_?[0-9])*)(\\.[0-9](_?[0-9])*)?([eE][+-]?[0-9](_?[0-9])*)?");
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:[Tt ](\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)([Zz]|[+-]\\d{2}:\\d{2})?)?");
    private static final Pattern LOCAL_TIME = Pattern.compile("\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?");

    private final String src, sourceName;
    private int pos;

    private final Map<String, Object> root = new LinkedHashMap<>();
    private final Set<Object> headerTables = identitySet(),
        dottedTables = identitySet(),
        frozenTables = identitySet(),
        tableArrays = identitySet();

    private TOMLParser(String src, String sourceName) {
        this.src = src;
        this.sourceName = sourceName;
    }

    public static Map<String, Object> parse(String source) throws KanvasProjectException { return parse(source, "<toml>"); }
    public static Map<String, Object> parse(String source, String sourceName) throws KanvasProjectException {
        String s = source.startsWith("﻿") ? source.substring(1) : source;
        return new TOMLParser(s, sourceName).document();
    }

    private Map<String, Object> document() throws KanvasProjectException {
        Map<String, Object> current = root;
        while (!atEnd()) {
            skipWhitespace();
            if (atEnd()) break;
            char c = peek();
            if (c == '[') current = peek(1) == '[' ? arrayTableHeader() : tableHeader();
            else if (c != '#' && c != '\n' && c != '\r') keyValue(current);
            endOfLine();
        }
        return root;
    }

    private Map<String, Object> tableHeader() throws KanvasProjectException {
        int start = pos++;
        skipWhitespace();
        List<String> keys = key();
        skipWhitespace();
        expect(']', "Expected ']' to close table header");
        Map<String, Object> parent = walkHeader(keys, start);
        String last = keys.getLast();
        Object existing = parent.get(last);
        if (existing == null) {
            Map<String, Object> table = new LinkedHashMap<>();
            headerTables.add(table);
            parent.put(last, table);
            return table;
        }
        // A table created implicitly by an earlier header ([a.b] creates a) may be defined once later.
        if (existing instanceof Map<?, ?> m && !headerTables.contains(m) && !dottedTables.contains(m) && !frozenTables.contains(m)) {
            headerTables.add(m);
            return asTable(m);
        }
        throw error("Table [" + dotted(keys) + "] is already defined", start);
    }

    private Map<String, Object> arrayTableHeader() throws KanvasProjectException {
        int start = pos;
        pos += 2;
        skipWhitespace();
        List<String> keys = key();
        skipWhitespace();
        if (!src.startsWith("]]", pos)) throw error("Expected ']]' to close array-of-tables header, found " + describe());
        pos += 2;
        Map<String, Object> parent = walkHeader(keys, start);
        String last = keys.getLast();
        Object existing = parent.get(last);
        Map<String, Object> table = new LinkedHashMap<>();
        headerTables.add(table);
        if (existing == null) {
            List<Object> array = new ArrayList<>();
            tableArrays.add(array);
            parent.put(last, array);
            array.add(table);
        }
        else if (existing instanceof List<?> l && tableArrays.contains(l)) asArray(l).add(table);
        else throw error("Cannot append to [[" + dotted(keys) + "]]: '" + dotted(keys) + "' is already defined as something else", start);
        return table;
    }

    private Map<String, Object> walkHeader(List<String> keys, int start) throws KanvasProjectException {
        Map<String, Object> table = root;
        for (int i = 0; i < keys.size() - 1; i++) {
            Object next = table.get(keys.get(i));
            if (next == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                table.put(keys.get(i), created);
                table = created;
            }
            else if (next instanceof List<?> l && tableArrays.contains(l)) table = asTable(l.getLast());
            else if (next instanceof Map<?, ?> m && !frozenTables.contains(m)) table = asTable(m);
            else throw error("Cannot define [" + dotted(keys) + "]: '" + dotted(keys.subList(0, i + 1)) + "' is not a table", start);
        }
        return table;
    }

    private void keyValue(Map<String, Object> table) throws KanvasProjectException {
        int start = pos;
        List<String> keys = key();
        skipWhitespace();
        expect('=', "Expected '=' after key '" + dotted(keys) + "'");
        skipWhitespace();
        put(table, keys, value(), start);
    }

    private void put(Map<String, Object> table, List<String> keys, Object value, int start) throws KanvasProjectException {
        for (int i = 0; i < keys.size() - 1; i++) {
            Object next = table.get(keys.get(i));
            if (next == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                dottedTables.add(created);
                table.put(keys.get(i), created);
                table = created;
            }
            else if (next instanceof Map<?, ?> m && dottedTables.contains(m) && !frozenTables.contains(m)) table = asTable(m);
            else throw error("Cannot assign '" + dotted(keys) + "': '" + dotted(keys.subList(0, i + 1)) + "' is already defined", start);
        }
        String last = keys.getLast();
        if (table.containsKey(last)) throw error("Duplicate key '" + dotted(keys) + "'", start);
        table.put(last, value);
    }

    private List<String> key() throws KanvasProjectException {
        List<String> keys = new ArrayList<>();
        while (true) {
            keys.add(simpleKey());
            skipWhitespace();
            if (peek() != '.') return keys;
            pos++;
            skipWhitespace();
        }
    }

    private String simpleKey() throws KanvasProjectException {
        char c = peek();
        if (c == '"' || c == '\'') {
            if (src.startsWith(c == '"' ? "\"\"\"" : "'''", pos)) throw error("Multi-line strings cannot be used as keys");
            return c == '"' ? basicString() : literalString();
        }
        int start = pos;
        while (!atEnd() && isBareKeyChar(peek())) pos++;
        if (start == pos) throw error("Expected a key, found " + describe());
        return src.substring(start, pos);
    }

    private Object value() throws KanvasProjectException {
        return switch (peek()) {
            case '"' -> src.startsWith("\"\"\"", pos) ? multilineBasicString() : basicString();
            case '\'' -> src.startsWith("'''", pos) ? multilineLiteralString() : literalString();
            case '[' -> array();
            case '{' -> inlineTable();
            default -> scalar();
        };
    }

    private List<Object> array() throws KanvasProjectException {
        int start = pos++;
        List<Object> list = new ArrayList<>();
        while (true) {
            skipWhitespaceNewlinesAndComments();
            if (atEnd()) throw error("Unterminated array", start);
            if (peek() == ']') { pos++; return list; }
            list.add(value());
            skipWhitespaceNewlinesAndComments();
            if (atEnd()) throw error("Unterminated array", start);
            if (peek() == ',') pos++;
            else if (peek() == ']') { pos++; return list; }
            else throw error("Expected ',' or ']' in array, found " + describe());
        }
    }

    private Map<String, Object> inlineTable() throws KanvasProjectException {
        pos++;
        Map<String, Object> table = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') { pos++; freeze(table); return table; }
        while (true) {
            skipWhitespace();
            int start = pos;
            List<String> keys = key();
            skipWhitespace();
            expect('=', "Expected '=' after key '" + dotted(keys) + "'");
            skipWhitespace();
            put(table, keys, value(), start);
            skipWhitespace();
            if (peek() == ',') {
                pos++;
                skipWhitespace();
                if (peek() == '}') throw error("Trailing comma is not allowed in an inline table");
            }
            else if (peek() == '}') { pos++; freeze(table); return table; }
            else if (atEnd() || peek() == '\n' || peek() == '\r') throw error("Inline tables must be on a single line");
            else throw error("Expected ',' or '}' in inline table, found " + describe());
        }
    }

    private void freeze(Map<String, Object> table) {
        frozenTables.add(table);
        for (Object v : table.values()) if (v instanceof Map<?, ?> m) freeze(asTable(m));
    }

    private Object scalar() throws KanvasProjectException {
        int start = pos;
        Matcher m = DATE_TIME.matcher(src).region(pos, src.length());
        if (m.lookingAt()) {
            pos = m.end();
            return dateTime(m, start);
        }
        m = LOCAL_TIME.matcher(src).region(pos, src.length());
        if (m.lookingAt()) {
            pos = m.end();
            try { return LocalTime.parse(truncateFraction(m.group()));
            } catch (DateTimeParseException e) { throw error("Invalid time '" + m.group() + "'", start); }
        }
        while (!atEnd() && isTokenChar(peek())) pos++;
        String token = src.substring(start, pos);
        if (token.isEmpty()) throw error("Expected a value, found " + describe());
        switch (token) {
            case "true": return Boolean.TRUE;
            case "false": return Boolean.FALSE;
            case "inf", "+inf": return Double.POSITIVE_INFINITY;
            case "-inf": return Double.NEGATIVE_INFINITY;
            case "nan", "+nan", "-nan": return Double.NaN;
        }
        String digits = token.replace("_", "");
        try {
            if (DECIMAL_INT.matcher(token).matches()) return Long.parseLong(digits);
            if (HEXADECIMAL_INT.matcher(token).matches()) return Long.parseLong(digits.substring(2), 16);
            if (OCTAL_INT.matcher(token).matches()) return Long.parseLong(digits.substring(2), 8);
            if (BINARY_INT.matcher(token).matches()) return Long.parseLong(digits.substring(2), 2);
        } catch (NumberFormatException e) { throw error("Integer '" + token + "' is out of range", start); }
        if (FLOAT.matcher(token).matches()) return Double.parseDouble(digits);
        throw error("Invalid value '" + token + "'", start);
    }

    private Object dateTime(Matcher m, int start) throws KanvasProjectException {
        String date = m.group(1), time = m.group(2), offset = m.group(3);
        try {
            if (time == null) return LocalDate.parse(date);
            String dateTime = date + "T" + truncateFraction(time);
            return offset == null ? LocalDateTime.parse(dateTime) : OffsetDateTime.parse(dateTime + offset.toUpperCase());
        } catch (DateTimeParseException e) { throw error("Invalid date-time '" + m.group() + "'", start); }
    }

    private static String truncateFraction(String time) {
        int dot = time.indexOf('.');
        return dot >= 0 && time.length() - dot - 1 > 9 ? time.substring(0, dot + 10) : time;
    }

    private String basicString() throws KanvasProjectException {
        int start = pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd() || peek() == '\n' || peek() == '\r') throw error("Unterminated string (use \"\"\" for multi-line strings)", start);
            char c = peek();
            if (c == '"') { pos++; return sb.toString(); }
            if (c == '\\') escape(sb);
            else if (isControl(c)) throw error("Control character " + describe() + " must be escaped");
            else { sb.append(c); pos++; }
        }
    }

    private String multilineBasicString() throws KanvasProjectException {
        int start = pos;
        pos += 3;
        skipNewline();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) throw error("Unterminated multi-line string", start);
            char c = peek();
            if (src.startsWith("\"\"\"", pos)) {
                closeMultiline('"', sb);
                return sb.toString();
            }
            else if (c == '\\' && isLineEndingBackslash()) {
                // A backslash at the end of a line trims the newline and all leading whitespace after it.
                pos++;
                while (!atEnd() && (peek() == ' ' || peek() == '\t' || peek() == '\n' || src.startsWith("\r\n", pos))) pos++;
            }
            else if (c == '\\') escape(sb);
            else appendMultilineChar(sb);
        }
    }

    private String literalString() throws KanvasProjectException {
        int start = pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd() || peek() == '\n' || peek() == '\r') throw error("Unterminated literal string (use ''' for multi-line strings)", start);
            char c = peek();
            if (c == '\'') { pos++; return sb.toString(); }
            if (isControl(c)) throw error("Control character " + describe() + " is not allowed in a literal string");
            sb.append(c);
            pos++;
        }
    }

    private String multilineLiteralString() throws KanvasProjectException {
        int start = pos;
        pos += 3;
        skipNewline();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) throw error("Unterminated multi-line literal string", start);
            if (src.startsWith("'''", pos)) {
                closeMultiline('\'', sb);
                return sb.toString();
            }
            else appendMultilineChar(sb);
        }
    }

    private void closeMultiline(char quote, StringBuilder sb) throws KanvasProjectException {
        int run = 0;
        while (pos + run < src.length() && src.charAt(pos + run) == quote) run++;
        if (run > 5) throw error("Too many consecutive quotes in multi-line string");
        sb.append(String.valueOf(quote).repeat(run - 3));
        pos += run;
    }

    private void appendMultilineChar(StringBuilder sb) throws KanvasProjectException {
        char c = peek();
        if (c == '\n') { sb.append('\n'); pos++; }
        else if (src.startsWith("\r\n", pos)) { sb.append('\n'); pos += 2; }
        else if (isControl(c)) throw error("Control character " + describe() + " is not allowed in a string");
        else { sb.append(c); pos++; }
    }

    private boolean isLineEndingBackslash() {
        int i = pos + 1;
        while (i < src.length() && (src.charAt(i) == ' ' || src.charAt(i) == '\t')) i++;
        return i < src.length() && (src.charAt(i) == '\n' || src.startsWith("\r\n", i));
    }

    private void escape(StringBuilder sb) throws KanvasProjectException {
        int start = pos++;
        if (atEnd()) throw error("Unterminated escape sequence", start);
        char e = src.charAt(pos++);
        switch (e) {
            case 'b' -> sb.append('\b');
            case 't' -> sb.append('\t');
            case 'n' -> sb.append('\n');
            case 'f' -> sb.append('\f');
            case 'r' -> sb.append('\r');
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case 'u' -> sb.appendCodePoint(unicodeEscape(4, start));
            case 'U' -> sb.appendCodePoint(unicodeEscape(8, start));
            default -> throw error("Invalid escape sequence '\\" + e + "'", start);
        }
    }

    private int unicodeEscape(int length, int start) throws KanvasProjectException {
        if (pos + length > src.length()) throw error("Incomplete unicode escape", start);
        String hex = src.substring(pos, pos + length);
        for (char h : hex.toCharArray())
            if (Character.digit(h, 16) < 0) throw error("Invalid unicode escape '\\" + src.charAt(start + 1) + hex + "'", start);
        long codePoint = Long.parseLong(hex, 16);
        if (codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF))
            throw error("Unicode escape '\\" + src.charAt(start + 1) + hex + "' is not a valid scalar value", start);
        pos += length;
        return (int) codePoint;
    }

    private void skipWhitespace() {
        while (!atEnd() && (peek() == ' ' || peek() == '\t')) pos++;
    }

    private void skipNewline() {
        if (peek() == '\n') pos++;
        else if (src.startsWith("\r\n", pos)) pos += 2;
    }

    private void skipComment() throws KanvasProjectException {
        pos++;
        while (!atEnd() && peek() != '\n' && !src.startsWith("\r\n", pos)) {
            if (isControl(peek())) throw error("Control character " + describe() + " is not allowed in a comment");
            pos++;
        }
    }

    private void skipWhitespaceNewlinesAndComments() throws KanvasProjectException {
        while (true) {
            skipWhitespace();
            if (peek() == '#') skipComment();
            else if (peek() == '\n' || src.startsWith("\r\n", pos)) skipNewline();
            else return;
        }
    }

    private void endOfLine() throws KanvasProjectException {
        skipWhitespace();
        if (peek() == '#') skipComment();
        if (atEnd()) return;
        if (peek() != '\n' && !src.startsWith("\r\n", pos)) throw error("Expected end of line, found " + describe());
        skipNewline();
    }

    private boolean atEnd() { return pos >= src.length(); }
    private char peek() { return atEnd() ? '\0' : src.charAt(pos); }
    private char peek(int offset) { return pos + offset < src.length() ? src.charAt(pos + offset) : '\0'; }

    private void expect(char c, String message) throws KanvasProjectException {
        if (peek() != c || atEnd()) throw error(message + ", found " + describe());
        pos++;
    }

    private String describe() {
        if (atEnd()) return "end of file";
        char c = peek();
        if (c == '\r' && peek(1) != '\n') return "a carriage return without a line feed";
        if (c == '\n' || c == '\r') return "end of line";
        if (isControl(c)) return String.format("U+%04X", (int) c);
        return "'" + c + "'";
    }

    private static boolean isBareKeyChar(char c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-'; }
    private static boolean isTokenChar(char c) { return isBareKeyChar(c) || c == '+' || c == '.'; }
    private static boolean isControl(char c) { return (c < 0x20 && c != '\t') || c == 0x7F; }

    private static String dotted(List<String> keys) {
        StringJoiner joiner = new StringJoiner(".");
        for (String k : keys) {
            boolean bare = !k.isEmpty() && k.chars().allMatch(c -> isBareKeyChar((char) c));
            joiner.add(bare ? k : "\"" + k.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return joiner.toString();
    }

    private KanvasProjectException error(String message) { return error(message, pos); }
    private KanvasProjectException error(String message, int at) {
        int line = 1, column = 1;
        for (int i = 0; i < at && i < src.length(); i++) {
            if (src.charAt(i) == '\n') { line++; column = 1; }
            else column++;
        }
        return new KanvasProjectException(sourceName + ":" + line + ":" + column + ": " + message);
    }

    private static Set<Object> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asTable(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object o) { return (List<Object>) o; }
}
