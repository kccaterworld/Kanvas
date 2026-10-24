package kanvas.dependency;

import kanvas.dependency.DependencyGraph.Node;
import kanvas.dependency.DependencyGraph.Selected;
import kanvas.project.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyResolver {
    private static final String LOCAL = "local";

    private final Project project;
    private final List<MavenRepository> repositories;
    private final List<RemoteRepository> remotes;
    private final Consumer<String> log;
    private final boolean locked;
    private HttpClient client;
    private List<ResolvedDependency> resolved;

    public DependencyResolver(Project project) { this(project, System.out::println, false); }

    public DependencyResolver(Project project, Consumer<String> log) { this(project, log, false); }

    public DependencyResolver(Project project, Consumer<String> log, boolean locked) {
        this.project = project;
        this.repositories = MavenRepository.local(project);
        this.remotes = project.repositories().stream().map(RemoteRepository::new).toList();
        this.log = log;
        this.locked = locked;
    }

    public List<Path> candidates(Dependency dependency) {
        return switch (dependency) {
            case Dependency.Local local -> List.of(local.path());
            case Dependency.Maven maven -> repositories.stream().map(repository -> repository.jar(maven)).toList();
        };
    }

    public List<ResolvedDependency> resolve() throws DependencyException {
        if (resolved != null) return resolved;
        List<Dependency.Maven> declared = declaredMaven();
        String inputs = inputs(declared);
        Lockfile lock = Lockfile.read(project.lockFile());

        List<ResolvedDependency> maven;
        List<String> missing = new ArrayList<>();
        Lockfile update = null;
        if (declared.isEmpty()) {
            maven = List.of();
            if (lock != null && !locked) deleteLock();
        } else if (lock != null && lock.inputs().equals(inputs)) maven = fromLock(lock, missing);
        else if (locked) throw new DependencyException(lock == null
            ? "kanvas.lock is missing. Build once without --locked to create it."
            : "kanvas.lock is out of date with kanvas.toml's [dependencies] or repositories. Build once without --locked to update it.");
        else {
            List<Lockfile.Entry> entries = new ArrayList<>();
            maven = fresh(declared, entries, missing);
            update = new Lockfile(inputs, entries);
        }

        List<ResolvedDependency> remaining = new ArrayList<>(maven), result = new ArrayList<>();
        for (Dependency dependency : project.dependencies()) {
            if (dependency instanceof Dependency.Local local) {
                if (Files.exists(local.path())) result.add(new ResolvedDependency(local, local.path()));
                else missing.add("Dependency '" + local.name() + "' not found at " + local.path());
                continue;
            }
            for (Iterator<ResolvedDependency> it = remaining.iterator(); it.hasNext(); ) {
                ResolvedDependency entry = it.next();
                if (entry.dependency().key().equals(dependency.key())) { result.add(entry); it.remove(); }
            }
        }
        result.addAll(remaining);
        failIfMissing(missing);
        if (update != null) writeLock(update);
        resolved = List.copyOf(result);
        return resolved;
    }

    /** The dependency tree, showing what each dependency pulled in and why each version was chosen. Always walks the POMs. */
    public String tree() throws DependencyException {
        List<Dependency.Maven> declared = declaredMaven();
        List<Node> tree = DependencyGraph.resolve(declared, new Poms(this::findPom)).tree();
        Set<String> declaredKeys = new HashSet<>();
        for (Dependency.Maven dependency : declared) declaredKeys.add(dependency.key());
        StringBuilder out = new StringBuilder(project.name() + "\n");
        List<Object> roots = new ArrayList<>();
        Iterator<Node> mavenRoots = tree.iterator();
        for (Dependency dependency : project.dependencies()) roots.add(dependency instanceof Dependency.Local local ? local : mavenRoots.next());
        boolean[] repeated = { false };
        for (int i = 0; i < roots.size(); i++) render(out, roots.get(i), "", i == roots.size() - 1, declaredKeys, repeated);
        if (repeated[0]) out.append("(*) listed above with its dependencies\n");
        return out.toString();
    }

    private void render(StringBuilder out, Object item, String indent, boolean last, Set<String> declared, boolean[] repeated) {
        // ASCII, like Gradle, so the tree shows correctly in any console encoding.
        out.append(indent).append(last ? "\\--- " : "+--- ");
        if (item instanceof Dependency.Local local) {
            out.append(local.name()).append(" (").append(project.scriptDir().relativize(local.path()).toString().replace('\\', '/')).append(')')
                .append(scopeLabel(local.scope())).append('\n');
            return;
        }
        Node node = (Node) item;
        out.append(node.key()).append(':').append(node.requested() == null ? "?" : node.requested());
        if (!node.selected().equals(node.requested()))
            out.append(" -> ").append(node.selected()).append(declared.contains(node.key()) ? " (declared in kanvas.toml)" : " (highest requested)");
        out.append(scopeLabel(node.scope()));
        if (node.declared()) {
            Map<String, String> natives = nativesOf(node.key());
            if (!natives.isEmpty()) out.append(" [natives: ").append(String.join(", ", natives.keySet())).append(']');
        }
        if (node.repeated()) { out.append(" (*)"); repeated[0] = true; }
        out.append('\n');
        for (int i = 0; i < node.children().size(); i++)
            render(out, node.children().get(i), indent + (last ? "     " : "|    "), i == node.children().size() - 1, declared, repeated);
    }

    private static String scopeLabel(Dependency.Scope scope) { return scope == Dependency.Scope.COMPILE ? "" : " [" + scope.label() + "]"; }

    private List<Dependency.Maven> declaredMaven() {
        return project.dependencies().stream().filter(Dependency.Maven.class::isInstance).map(Dependency.Maven.class::cast).toList();
    }

    private Map<String, String> nativesOf(String key) {
        for (Dependency.Maven dependency : declaredMaven()) if (dependency.key().equals(key)) return dependency.natives();
        return Map.of();
    }

    private List<ResolvedDependency> fresh(List<Dependency.Maven> declared, List<Lockfile.Entry> entries, List<String> missing) throws DependencyException {
        DependencyGraph.Result graph = DependencyGraph.resolve(declared, new Poms(this::findPom));
        Map<String, Selected> selected = new LinkedHashMap<>();
        for (Selected entry : graph.selected()) selected.put(entry.coordinate().key(), entry);

        List<ResolvedDependency> result = new ArrayList<>();
        for (Selected entry : selected.values()) {
            if (!entry.hasJar()) continue;
            Dependency.Maven coordinate = entry.coordinate();
            add(fetch(coordinate, null, null, missing), coordinate, null, null, result, entries);
            for (Dependency.Maven dependency : declared) {
                if (!dependency.key().equals(coordinate.key())) continue;
                Dependency.Maven runtime = coordinate.withScope(Dependency.Scope.RUNTIME);
                for (Map.Entry<String, String> natives : dependency.natives().entrySet())
                    add(fetch(runtime, natives.getValue(), null, missing), runtime, natives.getValue(), natives.getKey(), result, entries);
            }
        }
        return result;
    }

    private void add(Path file, Dependency.Maven coordinate, String classifier, String platform,
                     List<ResolvedDependency> result, List<Lockfile.Entry> entries) throws DependencyException {
        if (file == null) return;
        result.add(new ResolvedDependency(coordinate, file, classifier, platform));
        entries.add(new Lockfile.Entry(coordinate, classifier, platform, source(file), sha256(file)));
    }

    private List<ResolvedDependency> fromLock(Lockfile lock, List<String> missing) throws DependencyException {
        List<ResolvedDependency> result = new ArrayList<>();
        for (Lockfile.Entry entry : lock.entries()) {
            Path file = fetch(entry.coordinate(), entry.classifier(), entry.repository(), missing);
            if (file == null) continue;
            String actual = sha256(file);
            if (!actual.equals(entry.sha256()))
                throw new DependencyException(file + " doesn't match kanvas.lock (SHA-256 " + actual + ", locked " + entry.sha256() + "). "
                    + "It may be damaged or tampered with. Delete it to download it again" + (file.startsWith(repositories.getFirst().root()) ? "." : ", or delete kanvas.lock if you trust it."));
            result.add(new ResolvedDependency(entry.coordinate(), file, entry.classifier(), entry.platform()));
        }
        return result;
    }

    private Path fetch(Dependency.Maven coordinate, String classifier, String preferred, List<String> missing) throws DependencyException {
        List<Path> candidates = repositories.stream().map(repository -> repository.artifact(coordinate, classifier, "jar")).toList();
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return candidate;
        List<String> attempts = new ArrayList<>();
        Path file = download(coordinate, classifier, "jar", preferred, attempts);
        if (file == null) missing.add("Dependency " + coordinate.coordinate() + (classifier == null ? "" : " (" + classifier + ")") + " not found. Looked in:"
            + candidates.stream().map(path -> "\n      " + path).collect(Collectors.joining())
            + (attempts.isEmpty() ? "\n    No repositories are listed in kanvas.toml." : "\n    Tried:" + attempts.stream().map(attempt -> "\n      " + attempt).collect(Collectors.joining()))
            + "\n    Check the coordinates, or add the repository that hosts it to `repositories` in kanvas.toml.");
        return file;
    }

    private void failIfMissing(List<String> missing) throws DependencyException {
        if (missing.size() == 1) throw new DependencyException("Project '" + project.name() + "': " + missing.getFirst());
        if (!missing.isEmpty()) throw new DependencyException("Project '" + project.name() + "' has " + missing.size() + " missing dependencies:\n  - " + String.join("\n  - ", missing));
    }

    private Path findPom(String group, String artifact, String version) throws DependencyException {
        String problem = Dependency.Maven.problem(group, artifact, version);
        if (problem != null) throw new DependencyException("A POM refers to " + group + ":" + artifact + ":" + version + ", which can't be used: " + problem);
        Dependency.Maven coordinate = new Dependency.Maven(group, artifact, version);
        for (MavenRepository repository : repositories) {
            Path pom = repository.artifact(coordinate, "pom");
            if (Files.isRegularFile(pom)) return pom;
        }
        for (MavenRepository repository : repositories) if (Files.isRegularFile(repository.jar(coordinate))) return null;
        return download(coordinate, null, "pom", null, new ArrayList<>());
    }

    private Path download(Dependency.Maven coordinate, String classifier, String extension, String preferred, List<String> attempts) throws DependencyException {
        Path target = repositories.getFirst().artifact(coordinate, classifier, extension);
        String path = MavenRepository.relativePath(coordinate, classifier, extension);
        List<RemoteRepository> order = new ArrayList<>(remotes);
        if (preferred != null && preferred.startsWith("http")) {
            RemoteRepository locked = new RemoteRepository(URI.create(preferred));
            order.remove(locked);
            order.addFirst(locked);
        }
        for (RemoteRepository remote : order) {
            log.accept("Downloading " + coordinate.coordinate() + (classifier == null ? "" : " " + classifier) + " (" + extension + ") from " + remote.root());
            try {
                if (remote.download(client(), path, target, log)) {
                    Files.writeString(sourceFile(target), remote.root().toString());
                    return target;
                }
                attempts.add(remote.url(path) + " (not found)");
            } catch (IOException e) {
                attempts.add(remote.url(path) + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DependencyException("Interrupted while downloading " + coordinate.coordinate(), e);
            }
        }
        return null;
    }

    public String latestVersion(String group, String artifact) throws DependencyException {
        for (RemoteRepository remote : remotes) {
            try {
                String metadata = remote.fetch(client(), MavenRepository.metadataPath(group, artifact));
                if (metadata == null) continue;
                String version = MavenMetadata.latestRelease(metadata);
                if (version != null) return version;
            } catch (IOException e) {
                log.accept("Warning: couldn't read versions of " + group + ":" + artifact + " from " + remote.root() + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DependencyException("Interrupted while looking up " + group + ":" + artifact, e);
            }
        }
        throw new DependencyException("No release of " + group + ":" + artifact + " was found in " + project.repositories()
            + ". Check the name, or give a version: kanvas add " + group + ":" + artifact + ":<version>");
    }

    private String source(Path file) throws DependencyException {
        try {
            Path recorded = sourceFile(file);
            return Files.isRegularFile(recorded) ? Files.readString(recorded).trim() : LOCAL;
        } catch (IOException e) { throw new DependencyException("Couldn't read " + sourceFile(file) + ": " + e.getMessage(), e); }
    }

    private static Path sourceFile(Path file) { return file.resolveSibling(file.getFileName() + ".repository"); }

    private static String sha256(Path file) throws DependencyException {
        try { return RemoteRepository.hash(file, "SHA-256");
        } catch (IOException e) { throw new DependencyException("Couldn't read " + file + ": " + e.getMessage(), e); }
    }

    private String inputs(List<Dependency.Maven> declared) throws DependencyException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder text = new StringBuilder("lock " + Lockfile.VERSION + "\n");
            for (Dependency.Maven dependency : declared)
                text.append(dependency.coordinate()).append(' ').append(dependency.scope().label()).append(' ').append(dependency.natives()).append('\n');
            for (URI repository : project.repositories()) text.append(repository).append('\n');
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new DependencyException("SHA-256 is not available", e); }
    }

    private void writeLock(Lockfile lock) throws DependencyException {
        Path file = project.lockFile();
        String content = lock.write();
        try {
            if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content)) return;
            Path partial = Files.createTempFile(file.toAbsolutePath().getParent(), "kanvas.lock", ".part");
            Files.writeString(partial, content, StandardCharsets.UTF_8);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new DependencyException("Couldn't write " + file + ": " + e.getMessage(), e); }
    }

    private void deleteLock() throws DependencyException {
        try { Files.deleteIfExists(project.lockFile());
        } catch (IOException e) { throw new DependencyException("Couldn't delete " + project.lockFile() + ": " + e.getMessage(), e); }
    }

    private HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        return client;
    }
}
