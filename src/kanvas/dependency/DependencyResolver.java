package kanvas.dependency;

import kanvas.project.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class DependencyResolver {
    private final Project project;
    private final List<MavenRepository> repositories;

    public DependencyResolver(Project project) {
        this.project = project;
        this.repositories = MavenRepository.local(project);
    }

    public List<Path> candidates(Dependency dependency) {
        return switch (dependency) {
            case Dependency.Local local -> List.of(local.path());
            case Dependency.Maven maven -> repositories.stream().map(repository -> repository.jar(maven)).toList();
        };
    }

    public List<ResolvedDependency> resolve() throws DependencyException {
        List<ResolvedDependency> resolvedDependencies = new ArrayList<>();
        List<String> missingDependencies = new ArrayList<>();
        for (Dependency dependency : project.dependencies()) {
            List<Path> candidates = candidates(dependency);
            Path found = candidates.stream().filter(Files::exists).findFirst().orElse(null);
            if (found != null) resolvedDependencies.add(new ResolvedDependency(dependency, found));
            else missingDependencies.add(describeMissing(dependency, candidates));
        }
        if (missingDependencies.size() == 1) throw new DependencyException("Project '" + project.name() + "': " + missingDependencies.getFirst());
        if (!missingDependencies.isEmpty()) throw new DependencyException("Project '" + project.name() + "' has " + missingDependencies.size() + " missing dependencies:\n  - " + String.join("\n  - ", missingDependencies));
        return resolvedDependencies;
    }

    public List<Path> classpath() throws DependencyException {
        return resolve().stream().map(ResolvedDependency::path).toList();
    }

    private static String describeMissing(Dependency dependency, List<Path> candidates) {
        return switch (dependency) {
            case Dependency.Local local -> "Dependency '" + local.name() + "' not found at " + local.path();
            case Dependency.Maven maven -> "Dependency " + maven.coordinate() + " not found. Looked in:"
                + candidates.stream().map(path -> "\n      " + path).collect(Collectors.joining())
                + "\n    Kanvas can't download from Maven yet. Fetch it with `mvn dependency:get -Dartifact=" + maven.coordinate() + "`, or put the JAR at the first path.";
        };
    }
}
