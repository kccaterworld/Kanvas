package kanvas.dependency;

import kanvas.project.Dependency;

import java.nio.file.Path;
import java.util.Objects;

public record ResolvedDependency(Dependency dependency, Path path, String classifier, String platform) {
    public ResolvedDependency {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(path, "path");
    }

    public ResolvedDependency(Dependency dependency, Path path) { this(dependency, path, null, null); }

    public boolean isNative() { return platform != null; }
}
