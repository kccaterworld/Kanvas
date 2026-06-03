package kanvas.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

public final class StartupScreen {
    private StartupScreen() {
    }

    public enum Theme {
        NONE("none", "white", "white", "white", "white"),
        NEON("neon", "bright_cyan", "bright_magenta", "bright_white", "bright_blue"),
        RETRO("retro", "bright_green", "bright_yellow", "bright_white", "green"),
        MONO("mono", "white", "bright_black", "bright_white", "white");

        public final String name, primaryColor, accentColor , titleColor, borderColor;

        Theme(String name, String primaryColor, String accentColor, String titleColor, String borderColor) {
            this.name = name;
            this.primaryColor = primaryColor;
            this.accentColor = accentColor;
            this.titleColor = titleColor;
            this.borderColor = borderColor;
        }

        public static Theme fromString(String s) {
            if (s == null) return NEON;
            switch (s.toLowerCase()) {
                case "retro": return RETRO;
                case "mono": return MONO;
                case "neon": return NEON;
                case "none": return NONE;
                default: return NEON;
            }
        }
    }

    public static Theme themeFromArgs(String[] args) {
        if (args == null || args.length == 0) return Theme.NEON;
        String candidate = args[0].trim();
        if (candidate.startsWith("--theme=")) candidate = candidate.substring("--theme=".length()).trim();
        return Theme.fromString(candidate);
    }

    public static String render(Theme theme) {
        StringBuilder screen = new StringBuilder();
        List<String> logoLines;
        try (InputStream stream = StartupScreen.class.getResourceAsStream("/kanvas/assets/text/logo.txt")) {
            if (stream != null) logoLines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            else logoLines = Files.readAllLines(Paths.get("kanvas", "assets", "text", "logo.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) { logoLines = Arrays.asList("Failed to load logo."); }
        for (String line : logoLines) screen.append(Text.style(line, theme.borderColor, theme.accentColor)).append("\n");
        screen.append(Text.style("Welcome to Kanvas CLI", theme.titleColor, "bold")).append("\n");
        screen.append("\n");
        screen.append(Text.style("1) create\tCreate a new Kanvas project", theme.primaryColor)).append("\n");
        screen.append(Text.style("2) run\t\tRun an existing Kanvas project", theme.primaryColor)).append("\n");
        screen.append(Text.style("3) \"filename\"\tRun or create a Kanvas project", theme.primaryColor)).append("\n");
        screen.append(Text.style("4) help\t\tShow available commands", theme.primaryColor)).append("\n");
        screen.append(Text.style("5) quit\t\tExit the application", theme.primaryColor)).append("\n");
        screen.append("\n");
        return screen.toString();
    }
}
