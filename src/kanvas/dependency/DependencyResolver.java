package kanvas.dependency;

import kanvas.project.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyResolver {
    private final Project project;
    private final List<MavenRepository> repositories;
    private final List<RemoteRepository> remotes;
    private final Consumer<String> log;
    private HttpClient client;

    public DependencyResolver(Project project) { this(project, System.out::println); }

    public DependencyResolver(Project project, Consumer<String> log) {
        this.project = project;
        this.repositories = MavenRepository.local(project);
        this.remotes = project.repositories().stream().map(RemoteRepository::new).toList();
        this.log = log;
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
            List<String> attempts = new ArrayList<>();
            if (found == null && dependency instanceof Dependency.Maven maven) found = download(maven, attempts);
            if (found != null) resolvedDependencies.add(new ResolvedDependency(dependency, found));
            else missingDependencies.add(describeMissing(dependency, candidates, attempts));
        }
        if (missingDependencies.size() == 1) throw new DependencyException("Project '" + project.name() + "': " + missingDependencies.getFirst());
        if (!missingDependencies.isEmpty()) throw new DependencyException("Project '" + project.name() + "' has " + missingDependencies.size() + " missing dependencies:\n  - " + String.join("\n  - ", missingDependencies));
        return resolvedDependencies;
    }

    public List<Path> classpath() throws DependencyException {
        return resolve().stream().map(ResolvedDependency::path).toList();
    }

    private Path download(Dependency.Maven maven, List<String> attempts) throws DependencyException {
        MavenRepository cache = repositories.getFirst();
        for (RemoteRepository remote : remotes) {
            log.accept("Downloading " + maven.coordinate() + " from " + remote.root());
            try {
                if (!remote.download(client(), maven, "jar", cache.jar(maven))) {
                    attempts.add(remote.url(maven, "jar") + " (not found)");
                    continue;
                }
                try { remote.download(client(), maven, "pom", cache.artifact(maven, "pom"));
                } catch (IOException e) { /* just ignore it */ }
                return cache.jar(maven);
            } catch (IOException e) {
                attempts.add(remote.url(maven, "jar") + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DependencyException("Interrupted while downloading " + maven.coordinate(), e);
            }
        }
        return null;
    }

    private HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        return client;
    }

    private static String describeMissing(Dependency dependency, List<Path> candidates, List<String> attempts) {
        return switch (dependency) {
            case Dependency.Local local -> "Dependency '" + local.name() + "' not found at " + local.path();
            case Dependency.Maven maven -> "Dependency " + maven.coordinate() + " not found. Looked in:"
                + candidates.stream().map(path -> "\n      " + path).collect(Collectors.joining())
                + (attempts.isEmpty() ? "\n    No repositories are listed in kanvas.toml." : "\n    Tried:" + attempts.stream().map(attempt -> "\n      " + attempt).collect(Collectors.joining()))
                + "\n    Check the coordinates, or add the repository that hosts it to `repositories` in kanvas.toml.";
        };
    }
}
