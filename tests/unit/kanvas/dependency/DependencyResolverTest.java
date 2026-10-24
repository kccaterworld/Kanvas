package kanvas.dependency;

import com.sun.net.httpserver.HttpServer;
import kanvas.project.Dependency;
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

    // Isolate Maven's local repository, and serve tmp/remote-a and tmp/remote-b as two fake Maven repositories.
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

    private Project project(String dependencies) throws Exception { return project(List.of(repository("remote-a")), dependencies); }

    @Test
    void mavenRepositoryUsesMavensLayout() {
        Path root = tmp.resolve("repo");
        assertEquals(root.resolve("com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"),
            new MavenRepository(root).jar(new Dependency.Maven("com.google.code.gson", "gson", "2.10.1")));
    }

    @Test
    void findsMavenJarsInTheProjectCacheThenTheLocalRepository() throws Exception {
        Project project = project("\"com.example:lib\" = \"1.0\"\n");
        DependencyResolver resolver = new DependencyResolver(project, line -> {});
        Dependency lib = project.dependencies().getFirst();
        Path cached = project.cacheDir().resolve("maven/com/example/lib/1.0/lib-1.0.jar");
        Path local = m2.toAbsolutePath().normalize().resolve("com/example/lib/1.0/lib-1.0.jar");
        assertEquals(List.of(cached, local), resolver.candidates(lib));

        writeFile(local, "jar");
        assertEquals(List.of(new ResolvedDependency(lib, local)), resolver.resolve());
        writeFile(cached, "jar");
        assertEquals(List.of(cached), resolver.classpath(), "the project cache wins");
        assertEquals(List.of(), requests, "nothing is downloaded when the JAR is on disk");
    }

    @Test
    void downloadsMissingJarsAndPomsIntoTheProjectCache() throws Exception {
        writeFile(tmp.resolve("remote-a/com/example/lib/1.0/lib-1.0.jar"), "jar bytes");
        writeFile(tmp.resolve("remote-a/com/example/lib/1.0/lib-1.0.pom"), "<project/>");
        Project project = project("\"com.example:lib\" = \"1.0\"\n");
        List<String> log = new ArrayList<>();

        Path cached = project.cacheDir().resolve("maven/com/example/lib/1.0/lib-1.0.jar");
        assertEquals(List.of(cached), new DependencyResolver(project, log::add).classpath());
        assertEquals("jar bytes", Files.readString(cached));
        assertEquals("<project/>", Files.readString(cached.resolveSibling("lib-1.0.pom")));
        assertEquals(List.of("Downloading com.example:lib:1.0 from " + repository("remote-a")), log);
        try (var files = Files.list(cached.getParent())) {
            assertEquals(List.of("lib-1.0.jar", "lib-1.0.pom"), files.map(file -> file.getFileName().toString()).sorted().toList(), "no temp files left");
        }

        requests.clear();
        new DependencyResolver(project, log::add).resolve();
        assertEquals(List.of(), requests, "the second resolve uses the cache");
    }

    @Test
    void triesEachRepositoryInOrder() throws Exception {
        writeFile(tmp.resolve("remote-b/com/example/lib/1.0/lib-1.0.jar"), "from b");
        Project project = project(List.of(repository("broken"), repository("remote-a"), repository("remote-b")), "\"com.example:lib\" = \"1.0\"\n");
        Path jar = new DependencyResolver(project, line -> {}).classpath().getFirst();
        assertEquals("from b", Files.readString(jar));
        assertEquals(List.of("/broken/com/example/lib/1.0/lib-1.0.jar", "/remote-a/com/example/lib/1.0/lib-1.0.jar",
            "/remote-b/com/example/lib/1.0/lib-1.0.jar", "/remote-b/com/example/lib/1.0/lib-1.0.pom"), requests);
    }

    @Test
    void reportsWhatWasTriedWhenADownloadFails() throws Exception {
        Project project = project(List.of(repository("broken"), repository("remote-a")), "\"com.example:missing\" = \"2\"\n");
        String message = assertThrows(DependencyException.class, () -> new DependencyResolver(project, line -> {}).resolve()).getMessage();
        assertTrue(message.contains("Dependency com.example:missing:2 not found")
            && message.contains(repository("broken") + "com/example/missing/2/missing-2.jar (HTTP 500)")
            && message.contains(repository("remote-a") + "com/example/missing/2/missing-2.jar (not found)")
            && message.contains("add the repository that hosts it to `repositories`"), message);
        assertFalse(Files.exists(project.cacheDir().resolve("maven/com/example/missing/2/missing-2.jar")));
    }

    @Test
    void reportsEveryMissingDependencyAtOnce() throws Exception {
        Project project = project("\"com.example:a\" = \"1\"\nutils = { path = \"lib/utils.jar\" }\n");
        String message = assertThrows(DependencyException.class, () -> new DependencyResolver(project, line -> {}).resolve()).getMessage();
        assertTrue(message.contains("has 2 missing dependencies") && message.contains("com.example:a:1")
            && message.contains("Dependency 'utils' not found at"), message);
    }

    @Test
    void keepsDeclarationOrder() throws Exception {
        Project project = project("b = { path = \"b.jar\" }\n\"com.example:a\" = \"1\"\n");
        Path b = writeFile(tmp.resolve("p/b.jar"), "b");
        Path a = writeFile(project.cacheDir().resolve("maven/com/example/a/1/a-1.jar"), "a");
        assertEquals(List.of(b.toAbsolutePath().normalize(), a), new DependencyResolver(project, line -> {}).classpath());
    }
}
