package kanvas.project;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import kanvas.cli.Text;


public class ProjectCreator {
    private static final List<String> TEMPLATE_ORDER = Arrays.asList(
        "kanvas-sketch", "java-app", "java-lib", "mixed-project", "empty"
    );
    private static final Map<String, List<String>> TEMPLATE_FILES = Map.of(
        "empty", Arrays.asList("kanvas.json"),
        "kanvas-sketch", Arrays.asList("kanvas.json", "src/main.kvs"),
        "java-app", Arrays.asList("kanvas.json", "src/App.java"),
        "java-lib", Arrays.asList("kanvas.json", "src/Library.java"),
        "mixed-project", Arrays.asList("kanvas.json", "src/App.java", "src/main.kvs")
    );

    public static List<String> getTemplateTypes() {
        return TEMPLATE_ORDER;
    }

    public static void createProject(String projectName, String templateType, String outputPath) {
        if (projectName == null || projectName.isBlank()) throw new IllegalArgumentException("Please provide a project name");
        if (projectName.equals(".") || projectName.equals("..") || !projectName.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Project name may only contain letters, numbers, '.', '_', and '-'");
        System.out.println(Text.style("Creating project: " + projectName, "green"));
        if (templateType == null || templateType.isBlank()) templateType = "kanvas-sketch";
        Path baseDir = Paths.get(outputPath == null ? "." : outputPath);
        if (!templateType.matches("[A-Za-z0-9_-]+")) {
            System.out.printf("Invalid template name '%s'. Using default template: %s\n", templateType, "kanvas-sketch");
            templateType = "kanvas-sketch";
        }
        if (!TEMPLATE_FILES.containsKey(templateType)) {
            System.out.printf("Template '%s' not found. Using default template: %s\n", templateType, "kanvas-sketch");
            templateType = "kanvas-sketch";
        }
        Path template = Paths.get("kanvas", "assets", "templates", templateType);
        Path projectDir = baseDir.resolve(projectName);
        try {
            Files.createDirectories(projectDir);
            System.out.println(Text.style("Project directory created at: " + projectDir.toAbsolutePath(), "green"));
        } catch (IOException e) { throw new RuntimeException("Failed to create project directory: " + e.getMessage(), e); }
        if (Files.isDirectory(template)) {
            try (var stream = Files.walk(template)) {
                stream.forEach(source -> {
                    try {
                        Path relative = template.relativize(source);
                        Path target = projectDir.resolve(relative);
                        if (Files.isDirectory(source)) Files.createDirectories(target);
                        else {
                            if (target.getParent() != null) Files.createDirectories(target.getParent());
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        System.out.println(Text.style("\tCopied " + target.toAbsolutePath(), "green"));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to copy: " + source + " -> " + e.getMessage(), e);
                    }
                });
                System.out.println(Text.style("Project copied successfully!", "green"));
            } catch (IOException e) { throw new RuntimeException("Failed to create project from template: " + e.getMessage(), e); }
        } else copyBundledTemplate(templateType, projectDir);
        ensureProjectDirectories(projectDir);
        Path configPath = projectDir.resolve("kanvas.json");
        try {
            String configContent = Files.readString(configPath)
                .replace("{{PROJECT_NAME}}", projectName)
                .replace("{{AUTHOR}}", System.getProperty("user.name", ""))
                .replace("{{MAIN_CLASS}}", mainClassFor(templateType));
            Files.writeString(configPath, configContent);
        } catch (IOException e) { throw new RuntimeException("Failed to update kanvas.json: " + e.getMessage(), e); }
        System.out.println(Text.style("Project created at: " + projectDir.toAbsolutePath(), "green"));
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
