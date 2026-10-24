package kanvas.project;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectEditor {
    private static final Pattern HEADER = Pattern.compile("\\s*\\[\\s*dependencies\\s*]\\s*(#.*)?");
    private static final Pattern ANY_HEADER = Pattern.compile("\\s*\\[.*");
    private static final Pattern ENTRY = Pattern.compile("(\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[A-Za-z0-9_-]+)\\s*=.*");
    private static final Pattern LEGACY_ARRAY = Pattern.compile("\\s*dependencies\\s*=.*");

    private ProjectEditor() {}

    public static String setDependency(String toml, String key, String tomlKey, String value) throws KanvasProjectException {
        String newline = toml.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(toml.split("\r?\n", -1)));
        int header = header(lines);
        if (header < 0) {
            while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
            lines.addAll(List.of("", "[dependencies]", tomlKey + " = " + value, ""));
            return String.join(newline, lines);
        }
        int end = end(lines, header), existing = find(lines, header, end, key);
        if (existing >= 0) {
            Matcher matcher = ENTRY.matcher(lines.get(existing));
            String indent = matcher.matches() ? matcher.group(1) : "";
            lines.set(existing, indent + tomlKey + " = " + value);
        } else {
            int last = header;
            for (int i = header + 1; i < end; i++) if (!lines.get(i).isBlank()) last = i;
            lines.add(last + 1, tomlKey + " = " + value);
        }
        return String.join(newline, lines);
    }

    public static String removeDependency(String toml, String key) throws KanvasProjectException {
        String newline = toml.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(toml.split("\r?\n", -1)));
        int header = header(lines);
        int existing = header < 0 ? -1 : find(lines, header, end(lines, header), key);
        if (existing < 0) throw new KanvasProjectException("'" + key + "' isn't in [dependencies]");
        lines.remove(existing);
        return String.join(newline, lines);
    }

    private static int header(List<String> lines) throws KanvasProjectException {
        for (int i = 0; i < lines.size(); i++) {
            if (HEADER.matcher(lines.get(i)).matches()) return i;
            if (ANY_HEADER.matcher(lines.get(i)).matches()) break;
            if (LEGACY_ARRAY.matcher(lines.get(i)).matches())
                throw new KanvasProjectException("kanvas.toml uses the older `dependencies = [...]` array. Move its entries into a [dependencies] table first (see docs/config.md).");
        }
        for (int i = 0; i < lines.size(); i++) if (HEADER.matcher(lines.get(i)).matches()) return i;
        return -1;
    }

    private static int end(List<String> lines, int header) {
        for (int i = header + 1; i < lines.size(); i++) if (ANY_HEADER.matcher(lines.get(i)).matches()) return i;
        return lines.size();
    }

    private static int find(List<String> lines, int header, int end, String key) {
        for (int i = header + 1; i < end; i++) {
            Matcher matcher = ENTRY.matcher(lines.get(i));
            if (matcher.matches() && unquote(matcher.group(2)).equals(key)) return i;
        }
        return -1;
    }

    private static String unquote(String key) {
        if (key.startsWith("'")) return key.substring(1, key.length() - 1);
        if (key.startsWith("\"")) return key.substring(1, key.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return key;
    }
}
