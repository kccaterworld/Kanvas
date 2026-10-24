package kanvas.cli;

import kanvas.KanvasException;
import kanvas.project.*;
import kanvas.builder.BuildManager;
import kanvas.runtime.KanvasRunner;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            runCommand(new String[] { "install" });
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

    private static final List<String> COMMANDS = List.of("create", "list-types", "templates", "config", "build", "run", "install", "package", "clean", "test", "help");
    private static final Map<String, String> FAILURE_PREFIXES = Map.of(
        "create", "Create failed: ",
        "config", "Config failed: ",
        "build", "Build failed: ",
        "run", "Run failed: ",
        "install", "Install failed: ",
        "package", "Package failed: ",
        "clean", "Clean failed: ",
        "test", "Test failed: "
    );

    private static void runCommand(String[] args) {
        try { dispatch(args); }
        catch (KanvasException e) {
            System.out.println(Text.style(FAILURE_PREFIXES.getOrDefault(args[0], "") + e.getMessage(), "red"));
            System.exit(1);
        }
    }

    private static void dispatch(String[] args) throws KanvasException {
        switch (args[0]) {
            case "create": {
                Arguments create = parseArguments(args, 1, Set.of("type"), Set.of("force"));
                if (create.positional().isEmpty()) throw new KanvasException("Missing project name. Usage: kanvas create <name> [--type <template>] [--force]");
                String name = create.positional().getFirst(), template = templateName(create.flags().getOrDefault("type", "kanvas-sketch"));
                boolean force = create.switches().contains("force");
                List<Path> existing = ProjectCreator.existingFiles(name, template, ".");
                if (!force && !existing.isEmpty()) force = confirmOverwrite(name, existing);
                ProjectCreator.createProject(name, template, ".", force);
            } break;
            case "list-types": case "templates": {
                parseArguments(args, 0, Set.of(), Set.of());
                printTemplateTypes();
            } break;
            case "config": {
                String project = ".";
                int overrideStart = 1;
                if (args.length > 1 && !args[1].startsWith("--")) {
                    project = args[1];
                    overrideStart = 2;
                }
                Map<String, String> overrides = parseOverrides(args, overrideStart);
                System.out.print(ProjectWriter.write(ProjectLoader.loadProject(ProjectLoader.locateProject(Paths.get(project)), overrides)));
            } break;
            case "build": {
                parseArguments(args, 0, Set.of(), Set.of());
                new BuildManager(loadProject()).build();
            } break;
            case "run": {
                parseArguments(args, 0, Set.of(), Set.of());
                Project project = loadProject();
                new BuildManager(project).buildIfNeeded();
                KanvasRunner.run(project);
            } break;
            case "install": {
                KanvasInstaller.install(parseArguments(args, 0, Set.of("dir"), Set.of()).flags().get("dir"));
            } break;
            case "package": {
                parseArguments(args, 0, Set.of(), Set.of());
                new BuildManager(loadProject()).packageProject();
            } break;
            case "clean": { // Delete `build/` and `.kanvas/cache` directories
                parseArguments(args, 0, Set.of(), Set.of());
                new BuildManager(loadProject()).clean();
            } break;
            case "help": case "-h": case "--help": {
                printHelp();
            } break;
            default: {
                throw new KanvasException("Unknown command '" + args[0] + "'" + closest(args[0], COMMANDS).map(command -> ". Did you mean '" + command + "'?").orElse("") + " (see `kanvas help` for the commands)");
            }
        }
    }

    private record Arguments(List<String> positional, Map<String, String> flags, Set<String> switches) {}

    private static Arguments parseArguments(String[] args, int maxPositional, Set<String> flags, Set<String> switches) throws KanvasException {
        String command = args[0];
        List<String> positional = new ArrayList<>(), errors = new ArrayList<>();
        Map<String, String> values = new HashMap<>();
        Set<String> switchesSet = new HashSet<>();
        Set<String> options = new HashSet<>(flags);
        options.addAll(switches);
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                if (positional.size() < maxPositional) positional.add(args[i]);
                else errors.add("Unexpected argument '" + args[i] + "'" + (command.equals("build") ? " (build profiles are not supported yet)" : ""));
                continue;
            }
            String key = args[i].substring(2), value = null;
            int equals = key.indexOf('=');
            if (equals >= 0) {
                value = key.substring(equals + 1);
                key = key.substring(0, equals);
            }
            if (switches.contains(key)) {
                if (value != null) errors.add("--" + key + " doesn't take a value");
                else switchesSet.add(key);
                continue;
            }
            if (value == null && i + 1 < args.length && !args[i + 1].startsWith("--")) value = args[++i];
            if (!flags.contains(key)) errors.add(options.isEmpty() ? "`kanvas " + command + "` has no options, found --" + key : unknownOption(key, options));
            else if (value == null || value.isBlank()) errors.add("--" + key + " needs a value");
            else values.put(key, value);
        }
        throwProblems(errors);
        return new Arguments(positional, values, switchesSet);
    }

    // check with user before overwrite proj
    private static boolean confirmOverwrite(String name, List<Path> existing) throws KanvasException {
        String files = existing.stream().map(file -> "  " + file).collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        Console console = System.console();
        if (console == null)
            throw new KanvasException("Project '" + name + "' already exists. These files would be overwritten:" + System.lineSeparator() + files + System.lineSeparator() + "Run again with --force to overwrite them.");
        System.out.println(Text.style("Project '" + name + "' already exists. These files will be replaced with fresh copies from the template:", "yellow"));
        System.out.println(files);
        String answer = console.readLine("Overwrite them? [y/N] ");
        if (answer != null && answer.trim().matches("(?i)y|yes")) return true;
        throw new KanvasException("Cancelled. Nothing was changed.");
    }

    private static String unknownOption(String key, Collection<String> flags) {
        return "Unknown option --" + key + closest(key, flags).map(flag -> ". Did you mean --" + flag + "?").orElse("");
    }

    private static void throwProblems(List<String> errors) throws KanvasException {
        if (errors.size() == 1) throw new KanvasException(errors.get(0) + " (see `kanvas help`)");
        if (!errors.isEmpty()) throw new KanvasException(errors.size() + " problems with the arguments (see `kanvas help`):\n  - " + String.join("\n  - ", errors));
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
    private static final Map<String, String> OVERRIDE_FLAGS = Map.ofEntries(
        Map.entry("name", "name"), Map.entry("version", "version"),
        Map.entry("author", "author"), Map.entry("description", "description"),
        Map.entry("main-class", "mainClass"),
        Map.entry("classpath", "classpath"), Map.entry("dependencies", "dependencies"),
        Map.entry("target", "compiler.target"), Map.entry("encoding", "compiler.encoding"),
        Map.entry("src-dirs", "modules.srcDirs"), Map.entry("resource-dirs", "modules.resourceDirs"), Map.entry("build-dir", "modules.buildDir"),
        Map.entry("jar-name", "packaging.jarName"), Map.entry("icon", "packaging.icon"),
        Map.entry("native-targets", "packaging.nativeTargets"), Map.entry("format", "packaging.format"));
    private static final Map<String, String> REMOVED_FLAGS = Map.of(
        "output-dir", "--output-dir was replaced by --build-dir",
        "package-version", "--package-version was removed: packaging uses the project version");

    // toss bad flags
    private static Map<String, String> parseOverrides(String[] args, int start) throws KanvasException {
        Map<String, String> overrides = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (int i = start; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                errors.add("Unexpected argument '" + args[i] + "'");
                continue;
            }
            String key = args[i].substring(2), value;
            int equals = key.indexOf('=');
            if (equals >= 0) {
                value = key.substring(equals + 1);
                key = key.substring(0, equals);
            }
            else value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "";
            if (OVERRIDE_FLAGS.containsKey(key)) overrides.put(OVERRIDE_FLAGS.get(key), value);
            else if (REMOVED_FLAGS.containsKey(key)) errors.add(REMOVED_FLAGS.get(key));
            else errors.add(unknownOption(key, OVERRIDE_FLAGS.keySet()));
        }
        throwProblems(errors);
        return overrides;
    }

    // suggest close flags or commands
    private static Optional<String> closest(String typed, Collection<String> candidates) {
        String best = null;
        int bestDistance = 3;
        for (String candidate : new TreeSet<>(candidates)) {
            int distance = editDistance(typed, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1], current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
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
            else System.out.println(Files.readString(Paths.get("resources", "assets", "text", "help.txt"), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException("Failed to read help.txt", e); }
    }
}
