package kanvas.processor;

import java.util.*;
import java.util.stream.Collectors;

public class Text {
    public static final Map<String, String> ANSI_CODES = new HashMap<>(Map.ofEntries(
        Map.entry("CSI", "\u001B["),
        Map.entry("RESET", "\u001B[0m"),
        Map.entry("NORMAL", "\u001B[22m"),
        Map.entry("CLEAR", "\u001B[2J"),
        Map.entry("HOME", "\u001B[H"),
        Map.entry("BOLD", "\u001B[1m"),
        Map.entry("ITALIC", "\u001B[3m"),
        Map.entry("UNDERLINE", "\u001B[4m"),
        Map.entry("STRIKETHROUGH", "\u001B[9m"),
        Map.entry("BLACK", "\u001B[30m"),
        Map.entry("RED", "\u001B[31m"),
        Map.entry("GREEN", "\u001B[32m"),
        Map.entry("YELLOW", "\u001B[33m"),
        Map.entry("BLUE", "\u001B[34m"),
        Map.entry("MAGENTA", "\u001B[35m"),
        Map.entry("CYAN", "\u001B[36m"),
        Map.entry("WHITE", "\u001B[37m"),
        Map.entry("BRIGHT_BLACK", "\u001B[90m"),
        Map.entry("BRIGHT_RED", "\u001B[91m"),
        Map.entry("BRIGHT_GREEN", "\u001B[92m"),
        Map.entry("BRIGHT_YELLOW", "\u001B[93m"),
        Map.entry("BRIGHT_BLUE", "\u001B[94m"),
        Map.entry("BRIGHT_MAGENTA", "\u001B[95m"),
        Map.entry("BRIGHT_CYAN", "\u001B[96m"),
        Map.entry("BRIGHT_WHITE", "\u001B[97m"),
        Map.entry("BG_BLACK", "\u001B[40m"),
        Map.entry("BG_RED", "\u001B[41m"),
        Map.entry("BG_GREEN", "\u001B[42m"),
        Map.entry("BG_YELLOW", "\u001B[43m"),
        Map.entry("BG_BLUE", "\u001B[44m"),
        Map.entry("BG_MAGENTA", "\u001B[45m"),
        Map.entry("BG_CYAN", "\u001B[46m"),
        Map.entry("BG_WHITE", "\u001B[47m")
    ));

    public static String buildAnsi(String... codes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (ANSI_CODES.containsKey(codes[i].toUpperCase())) {
                sb.append(ANSI_CODES.get(codes[i].toUpperCase()));
            }
        }
        return sb.toString();
    }

    public static String style(String text, String... codes) {
        return buildAnsi(codes) + text + ANSI_CODES.get("RESET");
    }

    public static String joinLines(String... lines) {
        return Arrays.stream(lines).collect(Collectors.joining(System.lineSeparator()));
    }
}
