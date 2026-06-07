package kanvas.project;

import kanvas.KanvasException;
import kanvas.cli.Text;

import java.util.*;
import java.io.*;
import java.nio.file.*;


public class ProjectCreator {
    private static final List<String> TEMPLATE_ORDER = Arrays.asList(
        "kanvas-sketch", "java-app", "java-lib", "mixed-project", "empty"
    );
    private static final Map<String, List<String>> TEMPLATE_FILES = Map.of(
        "empty", Arrays.asList("kanvas.toml"),
        "kanvas-sketch", Arrays.asList("kanvas.toml", "src/main.kvs"),
        "java-app", Arrays.asList("kanvas.toml", "src/App.java"),
        "java-lib", Arrays.asList("kanvas.toml", "src/Library.java"),
        "mixed-project", Arrays.asList("kanvas.toml", "src/App.java", "src/main.kvs")
    );

    public static List<String> getTemplateTypes() {
        return TEMPLATE_ORDER;
    }

    public static void createProject(String projectName, String templateType, String outputPath) throws KanvasException { createProject(projectName, templateType, outputPath, false); }
    public static void createProject(String projectName, String templateType, String outputPath, boolean force) throws KanvasException {
        if (projectName == null || projectName.isBlank()) throw new IllegalArgumentException("Please provide a project name");
        if (projectName.equals(".") || projectName.equals("..") || !projectName.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Project name may only contain letters, numbers, '.', '_', and '-'");
        System.out.println(Text.style("Creating project: " + projectName, "green"));
        if (templateType == null || templateType.isBlank()) templateType = "kanvas-sketch";
        Path baseDir = Paths.get(outputPath == null ? "." : outputPath);
        if (!templateType.matches("[A-Za-z0-9_-]+") || !TEMPLATE_FILES.containsKey(templateType)) {
            System.out.printf("Template '%s' not found.\nAvailable templates: %s\n", templateType, String.join(", ", TEMPLATE_ORDER));
            System.exit(0);
        }
        Path template = Paths.get("kanvas", "assets", "templates", templateType);
        Path projectDir = baseDir.resolve(projectName);
        try {
            Files.createDirectories(projectDir);
            System.out.println(Text.style("Project directory created at: " + projectDir.toAbsolutePath(), "green"));
        } catch (IOException e) { throw new RuntimeException("Failed to create project directory: " + e.getMessage(), e); }
        if (Files.isDirectory(template)) {
            try (var stream = Files.walk(template)) {
                Iterator<Path> paths = stream.iterator();
                while (paths.hasNext()) copyDirectory(paths.next(), template, projectDir, force);
                System.out.println(Text.style("Project copied successfully!", "green"));
            } catch (IOException e) { throw new RuntimeException("Failed to create project from template: " + e.getMessage(), e); }
        } else copyBundledTemplate(templateType, projectDir);
        ensureProjectDirectories(projectDir);
        Path configPath = projectDir.resolve("kanvas.toml");
        try {
            String configContent = Files.readString(configPath)
                .replace("{{PROJECT_NAME}}", projectName)
                .replace("{{AUTHOR}}", System.getProperty("user.name", ""))
                .replace("{{MAIN_CLASS}}", mainClassFor(templateType));
            Files.writeString(configPath, configContent);
        } catch (IOException e) { throw new RuntimeException("Failed to update kanvas.toml: " + e.getMessage(), e); }
        System.out.println(Text.style("Project created at: " + projectDir.toAbsolutePath(), "green"));
    }

    private static void copyDirectory(Path source, Path template, Path projectDir, boolean force) throws KanvasException {
        try {
            Path relative = template.relativize(source);
            Path target = projectDir.resolve(relative);
            if (Files.isDirectory(source)) Files.createDirectories(target);
            else if (force) {
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                if (Files.exists(target)) throw new KanvasProjectException("Target already exists: " + target.toAbsolutePath());
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
            System.out.println(Text.style("\tCopied " + target.toAbsolutePath(), "green"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy: " + source + " -> " + e.getMessage(), e);
        }
    }

    private static void ensureProjectDirectories(Path projectDir) {
        try {
            Files.createDirectories(projectDir.resolve("src"));
            Files.createDirectories(projectDir.resolve("lib"));
            Files.createDirectories(projectDir.resolve("assets"));
            Files.createDirectories(projectDir.resolve("build"));
        } catch (IOException e) { throw new RuntimeException("Failed to create project structure: " + e.getMessage(), e); }
    }

    private static void copyBundledTemplate(String templateType, Path projectDir) {
        for (String file : TEMPLATE_FILES.get(templateType)) {
            String resource = "/kanvas/assets/templates/" + templateType + "/" + file;
            Path target = projectDir.resolve(file);
            try (InputStream input = ProjectCreator.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalArgumentException("Template resource not found: " + resource);
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { throw new RuntimeException("Failed to copy template resource: " + resource, e); }
        }
        System.out.println(Text.style("Project copied successfully!", "green"));
    }

    private static String mainClassFor(String templateType) {
        switch (templateType) {
            case "java-app": return "App";
            case "mixed-project": return "App";
            default: return "";
        }
    }
}
