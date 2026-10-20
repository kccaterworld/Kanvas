package kanvas.cli;

import kanvas.KanvasException;
import kanvas.project.*;
import kanvas.builder.BuildManager;
import kanvas.runtime.KanvasRunner;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) throws KanvasException {
        if (args.length == 0) {
            KanvasInstaller.install(null);
            System.out.print(Text.buildAnsi("clear", "home"));
            System.out.print(StartupScreen.render(StartupScreen.Theme.NEON));
            return;
        }
        if (!args[0].startsWith("--theme")) {
            runCommand(args);
            return;
        }
        StartupScreen.Theme theme = StartupScreen.themeFromArgs(args[0]);
        String[] remaining = Arrays.copyOfRange(args, 1, args.length);
        System.out.print(Text.buildAnsi("clear", "home"));
        System.out.print(StartupScreen.render(theme));
        if (remaining.length > 0) runCommand(remaining);
    }

    private static void runCommand(String[] args) throws KanvasException {
        switch (args[0]) {
            case "create": {
                if (args.length < 2) throw new IllegalArgumentException("Usage: kanvas create <name> [--type <template>]");
                String type = "kanvas-sketch";
                for (int i = 2; i < args.length; i++) if ("--type".equals(args[i]) && i + 1 < args.length) type = args[++i];
                ProjectCreator.createProject(args[1], templateName(type), ".");
                break;
            }
            case "list-types": case "templates": printTemplateTypes(); break;
            case "config": {
                String project = ".";
                int overrideStart = 1;
                if (args.length > 1 && !args[1].startsWith("--")) {
                    project = args[1];
                    overrideStart = 2;
                }
                try { System.out.print(ProjectWriter.write(ProjectLoader.loadProject(ProjectLoader.locateProject(Paths.get(project)), parseOverrides(args, overrideStart)))); }
                catch (KanvasException e) { System.out.println(Text.style("Config failed: " + e.getMessage(), "red")); System.exit(1); }
                break;
            }
            case "build": {
                try { new BuildManager(loadProject()).build(); }
                catch (KanvasException e) { System.out.println(Text.style("Build failed: " + e.getMessage(), "red")); System.exit(1); }
                break;
            }
            case "run": {
                try { KanvasRunner.run(loadProject()); }
                catch (KanvasException e) { System.out.println(Text.style("Run failed: " + e.getMessage(), "red")); System.exit(1); }
                break;
            }
            case "install": {
                String dir = null;
                for (int i = 1; i < args.length; i++)
                    if ("--dir".equals(args[i]) && i + 1 < args.length) dir = args[++i];
                KanvasInstaller.install(dir);
                break;
            }
            case "help": case "-h": case "--help": printHelp(); break;
            default: throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    private static String templateName(String type) {
        return switch (type) {
            case "1" -> "kanvas-sketch";
            case "2" -> "java-app";
            case "3" -> "java-lib";
            case "4" -> "mixed-project";
            case "5" -> "empty";
            default -> type;
        };
    }

    // Map --flag to the kanvas.toml thing getting overrode.
    private static Map<String, String> parseOverrides(String[] args, int start) {
        Map<String, String> overrides = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String key = args[i].substring(2);
            String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "";
            String tomlKey = switch (key) {
                case "name", "version", "author", "description", "classpath", "dependencies" -> key;
                case "main-class" -> "mainClass";
                case "target" -> "compiler.target";
                case "encoding" -> "compiler.encoding";
                case "src-dirs" -> "modules.srcDirs";
                case "resource-dirs" -> "modules.resourceDirs";
                case "build-dir" -> "modules.buildDir";
                case "jar-name" -> "packaging.jarName";
                case "icon" -> "packaging.icon";
                case "native-targets" -> "packaging.nativeTargets";
                case "format" -> "packaging.format";
                case "output-dir" -> throw new IllegalArgumentException("--output-dir was replaced by --build-dir");
                case "package-version" -> throw new IllegalArgumentException("--package-version was removed: packaging uses the project version");
                default -> throw new IllegalArgumentException("Unknown override: --" + key);
            };
            overrides.put(tomlKey, value);
        }
        return overrides;
    }

    private static void printTemplateTypes() {
        System.out.println("\nProject Templates:");
        int i = 1;
        for (String templateType : ProjectCreator.getTemplateTypes())
            System.out.println("  " + (i++) + ". " + templateType);
    }

    // Wrapper for simple find project-ing
    private static Project loadProject() throws KanvasException {
        return ProjectLoader.loadProject(ProjectLoader.locateProject(Paths.get("")));
    }

    private static void printHelp() {
        try (InputStream helpStream = Main.class.getResourceAsStream("/kanvas/assets/text/help.txt")) {
            if (helpStream != null) System.out.println(new String(helpStream.readAllBytes(), StandardCharsets.UTF_8));
            else System.out.println(Files.readString(Paths.get("kanvas", "assets", "text", "help.txt"), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException("Failed to read help.txt", e); }
    }
}
