package kanvas.dependency;

import kanvas.project.Dependency;

import java.nio.file.Path;
import java.util.Objects;

public record ResolvedDependency(Dependency dependency, Path path) {
    public ResolvedDependency {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(path, "path");
    }
}
