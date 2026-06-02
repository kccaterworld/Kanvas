package kanvas.processor;

import java.util.*;
import java.nio.charset.StandardCharsets;
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
                    System.out.println("\nProject Types:");
                    System.out.println("  1. kanvas-sketch");
                    System.out.println("  2. java-app");
                    System.out.println("  3. java-lib");
                    System.out.println("  4. mixed-project");
                    System.out.print("Select type: ");
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
            default: return type;
        }
    }

    private static void printHelp() {
        try (InputStream helpStream = Main.class.getResourceAsStream("/kanvas/assets/text/help.txt")) {
            if (helpStream == null) throw new IllegalStateException("help.txt not found");
            System.out.println(new String(helpStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException("Failed to read help.txt", e); }
    }
}
