package kanvas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class TestSupport {
    public static final Path EXAMPLES = Path.of("examples");
    public static final Path TEST_PROJECTS = Path.of("tests", "projects");
    public static final Path TEMPLATES = Path.of("src", "kanvas", "assets", "templates");

    private TestSupport() {}

    public static List<Path> fixtureProjects() {
        List<Path> projects = new ArrayList<>(childDirectories(EXAMPLES));
        projects.addAll(childDirectories(TEST_PROJECTS));
        return projects;
    }

    public static List<Path> fixtureConfigs() {
        List<Path> configs = new ArrayList<>();
        for (Path project : fixtureProjects()) configs.add(project.resolve("kanvas.toml"));
        for (Path template : childDirectories(TEMPLATES)) configs.add(template.resolve("kanvas.toml"));
        return configs;
    }

    public static Path writeProject(Path dir, String toml) throws IOException {
        Files.createDirectories(dir.resolve("src"));
        return Files.writeString(dir.resolve("kanvas.toml"), toml);
    }

    public static Path writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    public static Path copyProject(Path source, Path target) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(path -> !source.relativize(path).startsWith("build")).toList()) {
                Path destination = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) Files.createDirectories(destination);
                else Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    private static List<Path> childDirectories(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
