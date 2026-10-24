package kanvas.dependency;

import com.sun.net.httpserver.HttpServer;
import kanvas.project.Dependency;
import kanvas.project.Dependency.Scope;
import kanvas.project.Project;
import kanvas.project.ProjectLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static kanvas.TestSupport.writeFile;
import static kanvas.TestSupport.writeProject;
import static org.junit.jupiter.api.Assertions.*;

class DependencyResolverTest {
    @TempDir Path tmp;
    private Path m2;
    private String previousRepository;
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    // Isolate Maven's local repository, and serve folders under tmp as fake Maven repositories.
    @BeforeEach
    void setUp() throws Exception {
        m2 = tmp.resolve("m2");
        previousRepository = System.setProperty("maven.repo.local", m2.toString());
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            Path file = tmp.resolve(exchange.getRequestURI().getPath().substring(1));
            if (exchange.getRequestURI().getPath().startsWith("/broken/")) exchange.sendResponseHeaders(500, -1);
            else if (!Files.isRegularFile(file)) exchange.sendResponseHeaders(404, -1);
            else {
                byte[] bytes = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        if (previousRepository == null) System.clearProperty("maven.repo.local");
        else System.setProperty("maven.repo.local", previousRepository);
    }

    private String repository(String name) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + name + "/"; }

    private Project project(List<String> repositories, String dependencies) throws Exception {
        String list = String.join(", ", repositories.stream().map(url -> "\"" + url + "\"").toList());
        return ProjectLoader.loadProject(writeProject(tmp.resolve("p"),
            "name = \"p\"\nversion = \"1\"\nrepositories = [" + list + "]\n[modules]\nsrcDirs = [\"src\"]\n[dependencies]\n" + dependencies));
    }

    private Project project(String dependencies) throws Exception { return project(List.of(repository("remote")), dependencies); }

    // Publishes g:a:v to tmp/remote: a POM with the given body, and a JAR unless the packaging is pom.
    private void publish(String coordinate, String body) throws Exception {
        String[] p = coordinate.split(":");
        Path dir = tmp.resolve("remote/" + p[0].replace('.', '/') + "/" + p[1] + "/" + p[2]);
        withChecksum(writeFile(dir.resolve(p[1] + "-" + p[2] + ".pom"), "<project><groupId>" + p[0] + "</groupId><artifactId>" + p[1]
            + "</artifactId><version>" + p[2] + "</version>" + body + "</project>"));
        if (!body.contains("<packaging>pom</packaging>")) withChecksum(writeFile(dir.resolve(p[1] + "-" + p[2] + ".jar"), coordinate));
    }

    // Writes file.sha256 next to the file, as Maven Central does.
    private static Path withChecksum(Path file) throws Exception {
        writeFile(file.resolveSibling(file.getFileName() + ".sha256"), RemoteRepository.hash(file, "SHA-256") + "  " + file.getFileName());
        return file;
    }
    private static String deps(String... dependencies) { return "<dependencies>" + String.join("", dependencies) + "</dependencies>"; }

    private static String dep(String coordinate, String... extra) {
        String[] p = coordinate.split(":");
        return "<dependency><groupId>" + p[0] + "</groupId><artifactId>" + p[1] + "</artifactId>"
            + (p.length > 2 ? "<version>" + p[2] + "</version>" : "") + String.join("", extra) + "</dependency>";
    }

    private List<ResolvedDependency> resolve(Project project) throws Exception { return new DependencyResolver(project, line -> {}).resolve(); }

    // "g:a:v [scope]" for each resolved dependency, in classpath order.
    private static List<String> describe(List<ResolvedDependency> resolved) {
        return resolved.stream().map(r -> r.dependency() instanceof Dependency.Maven m
            ? m.coordinate() + (m.scope() == Scope.COMPILE ? "" : " " + m.scope().label()) : r.dependency().key()).toList();
    }

    @Test
    void mavenRepositoryUsesMavensLayout() {
        Path root = tmp.resolve("repo");
        assertEquals(root.resolve("com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"),
            new MavenRepository(root).jar(new Dependency.Maven("com.google.code.gson", "gson", "2.10.1")));
    }

    @Test
    void findsMavenJarsInTheProjectCacheThenTheLocalRepository() throws Exception {
        Project project = project("\"com.example:lib\" = \"1.0\"\n");
        Dependency lib = project.dependencies().getFirst();
        Path cached = project.cacheDir().resolve("maven/com/example/lib/1.0/lib-1.0.jar");
        Path local = m2.toAbsolutePath().normalize().resolve("com/example/lib/1.0/lib-1.0.jar");
        assertEquals(List.of(cached, local), new DependencyResolver(project, line -> {}).candidates(lib));

        writeFile(local, "jar");
        assertEquals(List.of(local), resolve(project).stream().map(ResolvedDependency::path).toList());
        writeFile(cached, "jar");
        assertEquals(List.of(cached), resolve(project).stream().map(ResolvedDependency::path).toList(), "the project cache wins");
        assertEquals(List.of(), requests, "a JAR on disk without a POM is used as is, with no downloads");
    }

    @Test
    void downloadsMissingJarsAndPomsIntoTheProjectCache() throws Exception {
        publish("com.example:lib:1.0", "");
        Project project = project("\"com.example:lib\" = \"1.0\"\n");
        List<String> log = new ArrayList<>();

        Path cached = project.cacheDir().resolve("maven/com/example/lib/1.0/lib-1.0.jar");
        assertEquals(cached, new DependencyResolver(project, log::add).resolve().getFirst().path());
        assertEquals("com.example:lib:1.0", Files.readString(cached));
        assertTrue(Files.isRegularFile(cached.resolveSibling("lib-1.0.pom")));
        assertEquals(List.of("Downloading com.example:lib:1.0 (pom) from " + repository("remote"),
            "Downloading com.example:lib:1.0 (jar) from " + repository("remote")), log);
        try (var files = Files.list(cached.getParent())) {
            assertEquals(List.of("lib-1.0.jar", "lib-1.0.jar.repository", "lib-1.0.pom", "lib-1.0.pom.repository"),
                files.map(file -> file.getFileName().toString()).sorted().toList(), "no temp files left; each download records its repository");
        }

        requests.clear();
        resolve(project);
        assertEquals(List.of(), requests, "the second resolve uses the cache");
    }

    @Test
    void triesEachRepositoryInOrder() throws Exception {
        writeFile(tmp.resolve("remote-b/com/example/lib/1.0/lib-1.0.jar"), "from b");
        Project project = project(List.of(repository("broken"), repository("empty"), repository("remote-b")), "\"com.example:lib\" = \"1.0\"\n");
        assertEquals("from b", Files.readString(resolve(project).getFirst().path()));
        assertEquals(List.of("/broken/com/example/lib/1.0/lib-1.0.pom", "/empty/com/example/lib/1.0/lib-1.0.pom", "/remote-b/com/example/lib/1.0/lib-1.0.pom",
            "/broken/com/example/lib/1.0/lib-1.0.jar", "/empty/com/example/lib/1.0/lib-1.0.jar", "/remote-b/com/example/lib/1.0/lib-1.0.jar",
            "/remote-b/com/example/lib/1.0/lib-1.0.jar.sha256", "/remote-b/com/example/lib/1.0/lib-1.0.jar.sha1"), requests, "the checksum is looked up as .sha256, then .sha1");
    }

    @Test
    void reportsWhatWasTriedWhenADownloadFails() throws Exception {
        Project project = project(List.of(repository("broken"), repository("empty")), "\"com.example:missing\" = \"2\"\n");
        String message = assertThrows(DependencyException.class, () -> resolve(project)).getMessage();
        assertTrue(message.contains("Dependency com.example:missing:2 not found")
            && message.contains(repository("broken") + "com/example/missing/2/missing-2.jar (HTTP 500)")
            && message.contains(repository("empty") + "com/example/missing/2/missing-2.jar (not found)")
            && message.contains("add the repository that hosts it to `repositories`"), message);
        assertFalse(Files.exists(project.cacheDir().resolve("maven/com/example/missing/2/missing-2.jar")));
    }

    @Test
    void reportsEveryMissingDependencyAtOnce() throws Exception {
        Project project = project("\"com.example:a\" = \"1\"\nutils = { path = \"lib/utils.jar\" }\n");
        String message = assertThrows(DependencyException.class, () -> resolve(project)).getMessage();
        assertTrue(message.contains("has 2 missing dependencies") && message.contains("com.example:a:1")
            && message.contains("Dependency 'utils' not found at"), message);
    }

    @Test
    void keepsDeclarationOrder() throws Exception {
        Project project = project("b = { path = \"b.jar\" }\n\"com.example:a\" = \"1\"\n");
        Path b = writeFile(tmp.resolve("p/b.jar"), "b");
        Path a = writeFile(project.cacheDir().resolve("maven/com/example/a/1/a-1.jar"), "a");
        assertEquals(List.of(b.toAbsolutePath().normalize(), a), resolve(project).stream().map(ResolvedDependency::path).toList());
    }

    @Test
    void resolvesTransitiveDependencies() throws Exception {
        publish("g:a:1", deps(dep("g:b:1")));
        publish("g:b:1", deps(dep("g:c:1"), dep("g:a:1")));
        publish("g:c:1", "");
        assertEquals(List.of("g:a:1", "g:b:1", "g:c:1"), describe(resolve(project("\"g:a\" = \"1\"\n"))), "the cycle back to a is fine");
    }

    @Test
    void theHighestRequestedVersionWins() throws Exception {
        publish("g:x:1", deps(dep("g:json:2.0")));
        publish("g:y:1", deps(dep("g:json:2.5")));
        publish("g:json:2.0", deps(dep("g:old:1")));
        publish("g:json:2.5", deps(dep("g:util:1")));
        publish("g:old:1", "");
        publish("g:util:1", "");
        assertEquals(List.of("g:x:1", "g:y:1", "g:json:2.5", "g:util:1"), describe(resolve(project("\"g:x\" = \"1\"\n\"g:y\" = \"1\"\n"))),
            "json 2.0's dependencies are dropped along with it");
        assertFalse(requests.contains("/remote/g/json/2.0/json-2.0.jar"));
    }

    @Test
    void aDeclaredVersionAlwaysWins() throws Exception {
        publish("g:y:1", deps(dep("g:json:2.5")));
        publish("g:json:2.0", "");
        Project project = project("\"g:y\" = \"1\"\n\"g:json\" = \"2.0\"\n");
        assertEquals(List.of("g:y:1", "g:json:2.0"), describe(resolve(project)));
        assertEquals("""
            p
            +--- g:y:1
            |    \\--- g:json:2.5 -> 2.0 (declared in kanvas.toml)
            \\--- g:json:2.0
            """, new DependencyResolver(project, line -> {}).tree());
    }

    @Test
    void followsMavensScopeRules() throws Exception {
        publish("g:a:1", deps(dep("g:rt:1", "<scope>runtime</scope>"), dep("g:test:1", "<scope>test</scope>"),
            dep("g:prov:1", "<scope>provided</scope>"), dep("g:opt:1", "<optional>true</optional>"), dep("g:native:1", "<classifier>linux</classifier>")));
        publish("g:rt:1", deps(dep("g:rtchild:1")));
        publish("g:rtchild:1", "");
        publish("g:p:1", deps(dep("g:pchild:1"), dep("g:shared:1")));
        publish("g:pchild:1", "");
        publish("g:r:1", deps(dep("g:shared:1")));
        publish("g:shared:1", "");
        Project project = project("\"g:a\" = \"1\"\n\"g:p\" = { version = \"1\", scope = \"provided\" }\n\"g:r\" = { version = \"1\", scope = \"runtime\" }\n");
        assertEquals(List.of("g:a:1", "g:p:1 provided", "g:r:1 runtime", "g:rt:1 runtime", "g:pchild:1 provided", "g:shared:1", "g:rtchild:1 runtime"),
            describe(resolve(project)), "shared is needed to compile (via p) and to run (via r), so it's compile");
    }

    @Test
    void usesParentsPropertiesManagedVersionsAndBoms() throws Exception {
        publish("g:parent:1", "<packaging>pom</packaging><properties><dep.version>3.0</dep.version></properties>"
            + "<dependencyManagement>" + deps(dep("g:dep:${dep.version}")) + "</dependencyManagement>" + deps(dep("g:inherited:1")));
        publish("g:bom:1", "<packaging>pom</packaging><dependencyManagement>" + deps(dep("g:managed:4.0"), dep("g:dep:9.9")) + "</dependencyManagement>");
        publish("g:lib:1", "<parent><groupId>g</groupId><artifactId>parent</artifactId><version>1</version></parent>"
            + "<dependencyManagement>" + deps(dep("g:bom:1", "<type>pom</type><scope>import</scope>")) + "</dependencyManagement>"
            + deps(dep("g:dep"), dep("g:managed"), dep("g:self:${project.version}")));
        for (String coordinate : List.of("g:dep:3.0", "g:managed:4.0", "g:inherited:1", "g:self:1")) publish(coordinate, "");
        assertEquals(List.of("g:lib:1", "g:inherited:1", "g:dep:3.0", "g:managed:4.0", "g:self:1"), describe(resolve(project("\"g:lib\" = \"1\"\n"))),
            "the parent's managed version beats the imported BOM's");
    }

    @Test
    void honoursExclusionsAndPomTypeDependencies() throws Exception {
        publish("g:a:1", deps(dep("g:b:1", "<exclusions><exclusion><groupId>g</groupId><artifactId>c</artifactId></exclusion></exclusions>"),
            dep("g:group:1", "<type>pom</type>")));
        publish("g:b:1", deps(dep("g:c:1"), dep("g:d:1")));
        publish("g:group:1", "<packaging>pom</packaging>" + deps(dep("g:member:1")));
        for (String coordinate : List.of("g:c:1", "g:d:1", "g:member:1")) publish(coordinate, "");
        assertEquals(List.of("g:a:1", "g:b:1", "g:d:1", "g:member:1"), describe(resolve(project("\"g:a\" = \"1\"\n"))));
        assertFalse(requests.contains("/remote/g/group/1/group-1.jar"), "a pom-packaged artifact has no JAR to fetch");
    }

    @Test
    void versionRangesNeedADeclaredVersion() throws Exception {
        publish("g:a:1", deps(dep("g:r:[1.0,2.0)")));
        publish("g:r:1.5", "");
        String message = assertThrows(DependencyException.class, () -> resolve(project("\"g:a\" = \"1\"\n"))).getMessage();
        assertTrue(message.contains("g:r:[1.0,2.0): version ranges") && message.contains("Declare \"g:r\" in kanvas.toml"), message);
        assertEquals(List.of("g:a:1", "g:r:1.5"), describe(resolve(project("\"g:a\" = \"1\"\n\"g:r\" = \"1.5\"\n"))));
    }

    @Test
    void rejectsUnsafeCoordinatesInPoms() throws Exception {
        publish("g:a:1", deps(dep("..:evil:1")));
        String message = assertThrows(DependencyException.class, () -> resolve(project("\"g:a\" = \"1\"\n"))).getMessage();
        assertTrue(message.contains("'..' is not a valid Maven group"), message);
    }

    @Test
    void treeShowsWhyEachVersionWasChosen() throws Exception {
        publish("g:x:1", deps(dep("g:json:2.0"), dep("g:log:1", "<scope>runtime</scope>")));
        publish("g:y:1", deps(dep("g:json:2.5"), dep("g:x:1")));
        for (String coordinate : List.of("g:json:2.5", "g:log:1")) publish(coordinate, "");
        Path utils = writeFile(tmp.resolve("p/lib/utils.jar"), "u");
        assertTrue(Files.isRegularFile(utils));
        Project project = project("\"g:x\" = \"1\"\n\"g:y\" = \"1\"\nutils = { path = \"lib/utils.jar\", scope = \"runtime\" }\n");
        assertEquals("""
            p
            +--- g:x:1
            |    +--- g:json:2.0 -> 2.5 (highest requested)
            |    \\--- g:log:1 [runtime]
            +--- g:y:1
            |    +--- g:json:2.5
            |    \\--- g:x:1 (*)
            \\--- utils (lib/utils.jar) [runtime]
            (*) listed above with its dependencies
            """, new DependencyResolver(project, line -> {}).tree());
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var files = Files.walk(dir)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
    }

    @Test
    void verifiesChecksumsAndWarnsWhenThereAreNone() throws Exception {
        publish("g:bad:1", "");
        Files.writeString(tmp.resolve("remote/g/bad/1/bad-1.jar"), "tampered"); // no longer matches its .sha256
        String message = assertThrows(DependencyException.class, () -> resolve(project("\"g:bad\" = \"1\"\n"))).getMessage();
        assertTrue(message.contains("checksum mismatch") && message.contains("bad-1.jar.sha256"), message);
        assertFalse(Files.exists(tmp.resolve("p/.kanvas/cache/maven/g/bad/1/bad-1.jar")), "a file that fails its checksum is never kept");

        Path sha1Only = writeFile(tmp.resolve("remote/g/old/1/old-1.jar"), "old");
        writeFile(tmp.resolve("remote/g/old/1/old-1.jar.sha1"), RemoteRepository.hash(sha1Only, "SHA-1"));
        writeFile(tmp.resolve("remote/g/none/1/none-1.jar"), "none");
        List<String> log = new ArrayList<>();
        new DependencyResolver(project("\"g:old\" = \"1\"\n\"g:none\" = \"1\"\n"), log::add).resolve();
        List<String> warnings = log.stream().filter(line -> line.startsWith("Warning")).toList();
        assertEquals(1, warnings.size(), log.toString());
        assertTrue(warnings.getFirst().contains("none-1.jar has no .sha256 or .sha1 checksum"), warnings.getFirst());
    }

    @Test
    void writesKanvasLockAndReproducesItsClasspathFromScratch() throws Exception {
        publish("g:a:1", deps(dep("g:b:1")));
        publish("g:b:1", "");
        Project project = project("\"g:a\" = \"1\"\n");
        List<String> first = describe(resolve(project));
        String lock = Files.readString(project.lockFile());
        assertTrue(lock.contains("coordinate = \"g:a:1\"") && lock.contains("coordinate = \"g:b:1\"")
            && lock.contains("repository = \"" + repository("remote") + "\"") && lock.contains("sha256 = \""), lock);

        // The lock pins versions even if a library's POM changes, and an empty cache is rebuilt without reading any POM.
        publish("g:a:1", deps(dep("g:b:2")));
        publish("g:b:2", "");
        deleteRecursively(project.cacheDir());
        requests.clear();
        assertEquals(first, describe(resolve(project)));
        assertTrue(requests.stream().noneMatch(request -> request.endsWith(".pom")), requests.toString());
        assertEquals(lock, Files.readString(project.lockFile()), "an up-to-date lock isn't rewritten");
    }

    @Test
    void aChangedFileFailsTheLockCheck() throws Exception {
        publish("g:a:1", "");
        Project project = project("\"g:a\" = \"1\"\n");
        Files.writeString(resolve(project).getFirst().path(), "changed");
        String message = assertThrows(DependencyException.class, () -> resolve(project)).getMessage();
        assertTrue(message.contains("doesn't match kanvas.lock"), message);
    }

    @Test
    void changingDependenciesUpdatesTheLockUnlessLocked() throws Exception {
        publish("g:a:1", "");
        publish("g:c:1", "");
        Project project = project("\"g:a\" = \"1\"\n");
        String message = assertThrows(DependencyException.class, () -> new DependencyResolver(project, line -> {}, true).resolve()).getMessage();
        assertTrue(message.contains("kanvas.lock is missing"), message);
        resolve(project);
        new DependencyResolver(project, line -> {}, true).resolve();

        Project changed = project("\"g:a\" = \"1\"\n\"g:c\" = \"1\"\n");
        message = assertThrows(DependencyException.class, () -> new DependencyResolver(changed, line -> {}, true).resolve()).getMessage();
        assertTrue(message.contains("kanvas.lock is out of date"), message);
        resolve(changed);
        assertTrue(Files.readString(changed.lockFile()).contains("g:c:1"));

        Project none = project("");
        resolve(none);
        assertFalse(Files.exists(none.lockFile()), "with no Maven dependencies there is no lock");
    }

    @Test
    void resolvesNativeJarsForEveryDeclaredPlatform() throws Exception {
        publish("org.lwjgl:lwjgl:3", "");
        for (String classifier : List.of("natives-windows", "natives-linux", "natives-macos"))
            withChecksum(writeFile(tmp.resolve("remote/org/lwjgl/lwjgl/3/lwjgl-3-" + classifier + ".jar"), classifier));
        Project project = project("\"org.lwjgl:lwjgl\" = { version = \"3\", natives = { windows = \"natives-windows\", linux = \"natives-linux\", macos = \"natives-macos\" } }\n");
        List<ResolvedDependency> resolved = resolve(project);
        assertEquals(Arrays.asList(null, "linux", "macos", "windows"), resolved.stream().map(ResolvedDependency::platform).toList());
        assertTrue(resolved.stream().skip(1).allMatch(r -> r.dependency().scope() == Scope.RUNTIME && r.path().toString().endsWith(r.classifier() + ".jar")));
        String lock = Files.readString(project.lockFile());
        assertTrue(lock.contains("classifier = \"natives-linux\"") && lock.contains("platform = \"linux\""), lock);
        assertTrue(new DependencyResolver(project, line -> {}).tree().contains("org.lwjgl:lwjgl:3 [natives: linux, macos, windows]"));
    }

    @Test
    void findsTheLatestRelease() throws Exception {
        writeFile(tmp.resolve("remote/g/lib/maven-metadata.xml"), "<metadata><versioning><versions><version>1.9</version><version>1.10</version>"
            + "<version>2.0-rc1</version><version>2.0-SNAPSHOT</version></versions></versioning></metadata>");
        assertEquals("1.10", new DependencyResolver(project(""), line -> {}).latestVersion("g", "lib"));
        String message = assertThrows(DependencyException.class, () -> new DependencyResolver(project(""), line -> {}).latestVersion("g", "missing")).getMessage();
        assertTrue(message.contains("No release of g:missing was found"), message);
    }
}
