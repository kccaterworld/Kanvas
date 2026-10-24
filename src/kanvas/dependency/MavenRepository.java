package kanvas.dependency;

import kanvas.project.Dependency;
import kanvas.project.Project;

import java.nio.file.Path;
import java.util.List;

public record MavenRepository(Path root) {
    public static List<MavenRepository> local(Project project) {
        String override = System.getProperty("maven.repo.local");
        Path m2 = override != null && !override.isBlank() ? Path.of(override) : Path.of(System.getProperty("user.home"), ".m2", "repository");
        return List.of(new MavenRepository(project.cacheDir().resolve("maven")), new MavenRepository(m2.toAbsolutePath().normalize()));
    }

    public Path jar(Dependency.Maven dependency) { return artifact(dependency, "jar"); }

    private Path artifact(Dependency.Maven dependency, String extension) {
        Path dir = root;
        for (String part : dependency.group().split("\\.")) dir = dir.resolve(part);
        return dir.resolve(dependency.artifact()).resolve(dependency.version())
            .resolve(dependency.artifact() + "-" + dependency.version() + "." + extension);
    }
}
