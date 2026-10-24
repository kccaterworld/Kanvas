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

    public Path artifact(Dependency.Maven dependency, String extension) { return root.resolve(relativePath(dependency, extension)); }

    public static String relativePath(Dependency.Maven dependency, String extension) {
        return dependency.group().replace('.', '/') + "/" + dependency.artifact() + "/" + dependency.version() + "/"
            + dependency.artifact() + "-" + dependency.version() + "." + extension;
    }
}
