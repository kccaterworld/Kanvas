package kanvas.dependency;

import kanvas.dependency.DependencyGraph.Node;
import kanvas.dependency.DependencyGraph.Selected;
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
    private List<ResolvedDependency> resolved;
    private List<Node> tree;

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
        if (resolved != null) return resolved;
        List<Dependency.Maven> declared = project.dependencies().stream()
            .filter(Dependency.Maven.class::isInstance).map(Dependency.Maven.class::cast).toList();
        DependencyGraph.Result graph = DependencyGraph.resolve(declared, new Poms(this::findPom));

        Map<String, Selected> selected = new LinkedHashMap<>();
        for (Selected entry : graph.selected()) selected.put(entry.coordinate().key(), entry);
        List<Dependency> ordered = new ArrayList<>();
        for (Dependency dependency : project.dependencies()) {
            if (dependency instanceof Dependency.Local) ordered.add(dependency);
            else if (selected.remove(dependency.key()) instanceof Selected entry && entry.hasJar()) ordered.add(entry.coordinate());
        }
        for (Selected entry : selected.values()) if (entry.hasJar()) ordered.add(entry.coordinate());

        List<ResolvedDependency> result = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Dependency dependency : ordered) {
            List<Path> candidates = candidates(dependency);
            Path found = candidates.stream().filter(Files::exists).findFirst().orElse(null);
            List<String> attempts = new ArrayList<>();
            if (found == null && dependency instanceof Dependency.Maven maven) found = download(maven, "jar", attempts);
            if (found != null) result.add(new ResolvedDependency(dependency, found));
            else missing.add(describeMissing(dependency, candidates, attempts));
        }
        if (missing.size() == 1) throw new DependencyException("Project '" + project.name() + "': " + missing.getFirst());
        if (!missing.isEmpty()) throw new DependencyException("Project '" + project.name() + "' has " + missing.size() + " missing dependencies:\n  - " + String.join("\n  - ", missing));
        resolved = List.copyOf(result);
        tree = graph.tree();
        return resolved;
    }

    public String tree() throws DependencyException {
        resolve();
        Set<String> declared = new HashSet<>();
        for (Dependency dependency : project.dependencies()) if (dependency instanceof Dependency.Maven) declared.add(dependency.key());
        StringBuilder out = new StringBuilder(project.name() + "\n");
        List<Object> roots = new ArrayList<>();
        Iterator<Node> mavenRoots = tree.iterator();
        for (Dependency dependency : project.dependencies()) roots.add((dependency instanceof Dependency.Local local) ? local : mavenRoots.next());
        boolean[] repeated = { false };
        for (int i = 0; i < roots.size(); i++) render(out, roots.get(i), "", i == roots.size() - 1, declared, repeated);
        if (repeated[0]) out.append("(*) listed above with its dependencies\n");
        return out.toString();
    }

    private void render(StringBuilder out, Object item, String indent, boolean last, Set<String> declared, boolean[] repeated) {
        out.append(indent).append(last ? "\\--- " : "+--- ");
        if (item instanceof Dependency.Local local) {
            out.append(local.name()).append(" (").append(project.scriptDir().relativize(local.path()).toString().replace('\\', '/')).append(')')
                .append(scopeLabel(local.scope())).append('\n');
            return;
        }
        Node node = (Node) item;
        out.append(node.key()).append(':').append((node.requested() == null) ? "?" : node.requested());
        if (!node.selected().equals(node.requested()))
            out.append(" -> ").append(node.selected()).append(declared.contains(node.key()) ? " (declared in kanvas.toml)" : " (highest requested)");
        out.append(scopeLabel(node.scope()));
        if (node.repeated()) { out.append(" (*)"); repeated[0] = true; }
        out.append('\n');
        for (int i = 0; i < node.children().size(); i++)
            render(out, node.children().get(i), indent + (last ? "     " : "|    "), i == node.children().size() - 1, declared, repeated);
    }

    private static String scopeLabel(Dependency.Scope scope) { return scope == Dependency.Scope.COMPILE ? "" : " [" + scope.label() + "]"; }

    private Path findPom(String group, String artifact, String version) throws DependencyException {
        String problem = Dependency.Maven.problem(group, artifact, version);
        if (problem != null) throw new DependencyException("A POM refers to " + group + ":" + artifact + ":" + version + ", which can't be used: " + problem);
        Dependency.Maven coordinate = new Dependency.Maven(group, artifact, version);
        for (MavenRepository repository : repositories) {
            Path pom = repository.artifact(coordinate, "pom");
            if (Files.isRegularFile(pom)) return pom;
        }
        for (MavenRepository repository : repositories) if (Files.isRegularFile(repository.jar(coordinate))) return null;
        return download(coordinate, "pom", new ArrayList<>());
    }

    private Path download(Dependency.Maven maven, String extension, List<String> attempts) throws DependencyException {
        Path target = repositories.getFirst().artifact(maven, extension);
        for (RemoteRepository remote : remotes) {
            log.accept("Downloading " + maven.coordinate() + " (" + extension + ") from " + remote.root());
            try {
                if (remote.download(client(), maven, extension, target)) return target;
                attempts.add(remote.url(maven, extension) + " (not found)");
            } catch (IOException e) { attempts.add(remote.url(maven, extension) + " (" + ((e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage()) + ")");
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
