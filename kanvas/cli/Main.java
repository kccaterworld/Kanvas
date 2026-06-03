package kanvas.cli;

import kanvas.config.ConfigLoader;
import kanvas.project.ProjectCreator;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && !args[0].startsWith("--theme")) {
            runCommand(args);
            return;
        }
        System.out.print(Text.buildAnsi("clear", "home"));
        System.out.print(StartupScreen.render(StartupScreen.themeFromArgs(args)));
            System.out.print("Option: ");
            try (Scanner input = new Scanner(System.in)) {
            String choice = input.nextLine().trim();
            switch (choice) {
                case "1": case "create": {
                    printTemplateTypes();
                    System.out.print("Select template: ");
                    String type = templateName(input.nextLine().trim());
                    System.out.print("Project name: ");
                    String name = input.nextLine();
                    ProjectCreator.createProject(name, type, ".");
                break; }
                case "2": case "run": {
                    System.out.println(Text.style("Run command selected.", "green"));
                break; }
                case "4": case "help": {
                    printHelp();
                main(args); }
                case "5": case "exit": case "quit": {
                    System.out.println(Text.style("Exiting Kanvas CLI.", "yellow"));
                    System.exit(0);
                };
                case "3": default: {
                    System.out.println(Text.style("Invalid choice. Please try again.", "red"));
                    main(args);
                };
            }
        }
        catch (IllegalArgumentException e) {
            System.out.println(Text.style("Error: " + e.getMessage(), "red"));
            main(args);
        }
    }

    private static void runCommand(String[] args) {
        switch (args[0]) {
            case "create": {
                if (args.length < 2) throw new IllegalArgumentException("Usage: kanvas create <name> [--type <template>]");
                String type = "kanvas-sketch";
                for (int i = 2; i < args.length; i++)
                    if ("--type".equals(args[i]) && i + 1 < args.length) type = args[++i];
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
                System.out.print(ConfigLoader.loadConfig(project, parseOverrides(args, overrideStart)).toJSONString());
                break;
            }
            case "run": System.out.println(Text.style("Run command selected.", "green")); break;
            case "help": case "-h": case "--help": printHelp(); break;
            default: throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    private static String templateName(String type) {
        switch (type) {
            case "1": return "kanvas-sketch";
            case "2": return "java-app";
            case "3": return "java-lib";
            case "4": return "mixed-project";
            case "5": return "empty";
            default: return type;
        }
    }

    private static Map<String, String> parseOverrides(String[] args, int start) {
        Map<String, String> overrides = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String key = args[i].substring(2);
            String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "";
            switch (key) {
                case "name": overrides.put("name", value); break;
                case "version": overrides.put("version", value); break;
                case "author": overrides.put("author", value); break;
                case "description": overrides.put("description", value); break;
                case "main-class": overrides.put("mainClass", value); break;
                case "target": overrides.put("target", value); break;
                case "encoding": overrides.put("encoding", value); break;
                case "output-dir": overrides.put("outputDir", value); break;
                case "src-dirs": overrides.put("srcDirs", value); break;
                case "classpath": overrides.put("classpath", value); break;
                case "dependencies": overrides.put("dependencies", value); break;
                case "jar-name": overrides.put("jarName", value); break;
                case "package-version": overrides.put("packageVersion", value); break;
                case "icon": overrides.put("icon", value); break;
                case "native-targets": overrides.put("nativeTargets", value); break;
                default: throw new IllegalArgumentException("Unknown override: --" + key);
            }
        }
        return overrides;
    }

    private static void printTemplateTypes() {
        System.out.println("\nProject Templates:");
        int i = 1;
        for (String templateType : ProjectCreator.getTemplateTypes())
            System.out.println("  " + (i++) + ". " + templateType);
    }

    private static void printHelp() {
        try (InputStream helpStream = Main.class.getResourceAsStream("/kanvas/assets/text/help.txt")) {
            if (helpStream != null) System.out.println(new String(helpStream.readAllBytes(), StandardCharsets.UTF_8));
            else System.out.println(Files.readString(Paths.get("kanvas", "assets", "text", "help.txt"), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException("Failed to read help.txt", e); }
    }
}
