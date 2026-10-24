package kanvas.builder;

import kanvas.KanvasException;
import kanvas.TestSupport;
import kanvas.preprocess.KanvasPreprocessor;
import kanvas.project.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.ToolProvider;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static kanvas.TestSupport.writeFile;
import static kanvas.TestSupport.writeProject;
import static org.junit.jupiter.api.Assertions.*;

class BuildManagerTest {
    private static final String JAVA_APP = "name = \"app\"\nversion = \"1\"\nmainClass = \"App\"\n%s[modules]\nsrcDirs = [\"src\"]\n[compiler]\ntarget = %d\n";

    @TempDir Path tmp;

    static List<Path> fixtureProjects() { return TestSupport.fixtureProjects(); }

    private static Project build(Path config) throws KanvasException {
        Project project = ProjectLoader.loadProject(config);
        new BuildManager(project).build();
        return project;
    }

    private static Path javaApp(Path dir, int target, String topLevel, String source) throws Exception {
        writeFile(dir.resolve("src/App.java"), source);
        return writeProject(dir, JAVA_APP.formatted(topLevel, target));
    }

    private static int classFileMajorVersion(Path classFile) throws Exception {
        byte[] bytes = Files.readAllBytes(classFile);
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }

    @ParameterizedTest
    @MethodSource("fixtureProjects")
    void buildsEveryFixtureProject(Path fixture) throws Exception {
        Path copy = TestSupport.copyProject(fixture, tmp.resolve(fixture.getFileName().toString()));
        Project project = build(copy.resolve("kanvas.toml"));
        Project.BuildLayout layout = project.buildLayout();

        for (Path dir : List.of(layout.generated(), layout.classes(), layout.resources(), layout.output())) assertTrue(Files.isDirectory(dir), dir.toString());
        assertFalse(Files.exists(layout.classes().resolve("classes")), "build/classes/classes should not exist");
        try (Stream<Path> sources = Files.walk(copy.resolve("src"))) {
            for (Path kvs : sources.filter(file -> file.toString().endsWith(".kvs")).toList()) {
                String path = KanvasPreprocessor.qualifiedNameFor(copy.resolve("src"), kvs, StandardCharsets.UTF_8).replace('.', '/');
                assertTrue(Files.isRegularFile(layout.generated().resolve(path + ".java")), path + ".java");
                assertTrue(Files.isRegularFile(layout.classes().resolve(path + ".class")), path + ".class");
            }
        }
    }

    @Test
    void compilerTargetChangesTheClassFileVersion() throws Exception {
        String source = "public class App { public static void main(String[] args) {} }";
        build(javaApp(tmp.resolve("17"), 17, "", source));
        build(javaApp(tmp.resolve("21"), 21, "", source));
        assertEquals(61, classFileMajorVersion(tmp.resolve("17/build/classes/App.class")));
        assertEquals(65, classFileMajorVersion(tmp.resolve("21/build/classes/App.class")));
    }

    @Test
    void sketchesNeedTheRuntimesJavaRelease() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path config = writeProject(tmp, "name = \"s\"\nversion = \"1\"\nmainClass = \"Main\"\n[modules]\nsrcDirs = [\"src\"]\n[compiler]\ntarget = 17\n");
        String message = assertThrows(KanvasException.class, () -> build(config)).getMessage();
        assertTrue(message.contains("compiler.target = 17 is too old for Kanvas sketches"), message);
    }

    @Test
    void dependencyJarsAreOnTheCompileClasspath() throws Exception {
        Path library = writeFile(tmp.resolve("library/greet/Greeter.java"), "package greet; public class Greeter { public static String greet() { return \"hi\"; } }");
        Path libraryClasses = Files.createDirectories(tmp.resolve("library/classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-d", libraryClasses.toString(), library.toString()));
        Path jar = tmp.resolve("app/lib/greeter.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("greet/Greeter.class"));
            out.write(Files.readAllBytes(libraryClasses.resolve("greet/Greeter.class")));
            out.closeEntry();
        }

        Path config = javaApp(tmp.resolve("app"), 21, "dependencies = [\"greeter.jar\"]\n",
            "public class App { public static void main(String[] args) { System.out.println(greet.Greeter.greet()); } }");
        build(config);
        assertTrue(Files.isRegularFile(tmp.resolve("app/build/classes/App.class")));

        Files.delete(jar);
        String message = assertThrows(KanvasException.class, () -> build(config)).getMessage();
        assertTrue(message.contains("Dependency 'greeter.jar' not found"), message);
    }

    @Test
    void readsAndWritesSketchesInTheConfiguredEncoding() throws Exception {
        Files.createDirectories(tmp.resolve("src"));
        try (OutputStream out = Files.newOutputStream(tmp.resolve("src/main.kvs"))) {
            out.write("String word = \"café\";\nvoid setup() {}\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        Project project = build(writeProject(tmp, "name = \"e\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\n[compiler]\nencoding = \"ISO-8859-1\"\n"));
        String generated = Files.readString(project.buildLayout().generated().resolve("kanvas/generated/Main.java"), StandardCharsets.ISO_8859_1);
        assertTrue(generated.contains("café"), generated);
    }

    private static Path sketchProject(Path dir) throws Exception {
        return writeProject(dir, "name = \"s\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\n");
    }

    @Test
    void sketchPackagesComeFromTheDeclarationThenTheFolder() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() { new game.entities.Enemy(); new com.levels.Level(); }");
        writeFile(tmp.resolve("src/game/entities/enemy.kvs"), "void setup() {}");
        writeFile(tmp.resolve("src/other/level.kvs"), "package com.levels;\nvoid setup() {}");
        Path classes = build(sketchProject(tmp)).buildLayout().classes();

        for (String name : List.of("kanvas/generated/Main", "game/entities/Enemy", "com/levels/Level"))
            assertTrue(Files.isRegularFile(classes.resolve(name + ".class")), name);
        String generated = Files.readString(tmp.resolve("build/generated/game/entities/Enemy.java"));
        assertTrue(generated.startsWith("package game.entities;"), generated);
    }

    @Test
    void sketchesAndJavaReferenceEachOtherAcrossPackages() throws Exception {
        writeFile(tmp.resolve("src/game/main.kvs"), "import util.Helper;\nvoid setup() { Helper.twice(1); }");
        writeFile(tmp.resolve("src/util/Helper.java"), "package util; public class Helper { public static int twice(int x) { return 2 * x; } }");
        writeFile(tmp.resolve("src/util/Launcher.java"), "package util; public class Launcher { Class<?> sketch = game.Main.class; }");
        Path classes = build(sketchProject(tmp)).buildLayout().classes();
        for (String name : List.of("game/Main", "util/Helper", "util/Launcher"))
            assertTrue(Files.isRegularFile(classes.resolve(name + ".class")), name);
    }

    @Test
    void packagesARunnableJar() throws Exception {
        Path config = javaApp(tmp, 21, "", "public class App { public static void main(String[] args) { System.out.print(\"packaged\"); } }");
        Path jar = new BuildManager(ProjectLoader.loadProject(config)).packageProject();
        assertEquals(tmp.resolve("build/output/app.jar").toAbsolutePath().normalize(), jar.toAbsolutePath().normalize());

        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString())
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertEquals("packaged", output);
    }

    @Test
    void packagedSketchesNameTheGeneratedMainClass() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path config = writeProject(tmp, "name = \"s\"\nversion = \"1\"\nmainClass = \"Main\"\n[modules]\nsrcDirs = [\"src\"]\n[packaging]\njarName = \"sketch.jar\"\n");
        Path jar = new BuildManager(ProjectLoader.loadProject(config)).packageProject();
        assertEquals("sketch.jar", jar.getFileName().toString());
        try (JarFile file = new JarFile(jar.toFile())) {
            assertEquals("kanvas.generated.Main", file.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNotNull(file.getEntry("kanvas/generated/Main.class"));
        }
    }

    @Test
    void librariesPackageWithoutAMainClass() throws Exception {
        writeFile(tmp.resolve("src/lib/Util.java"), "package lib; public class Util {}");
        Path jar = new BuildManager(ProjectLoader.loadProject(sketchProject(tmp))).packageProject();
        try (JarFile file = new JarFile(jar.toFile())) {
            assertNull(file.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNotNull(file.getEntry("lib/Util.class"));
        }
    }

    // A library JAR with greet.Greeter, plus any extra entries.
    private Path greeterJar(Path jar, Map<String, String> extra) throws Exception {
        Path source = writeFile(tmp.resolve("greeter-src/greet/Greeter.java"), "package greet; public class Greeter { public static String greet() { return \"hi\"; } }");
        Path classes = Files.createDirectories(tmp.resolve("greeter-classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-d", classes.toString(), source.toString()));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("greet/Greeter.class", Files.readAllBytes(classes.resolve("greet/Greeter.class")));
        extra.forEach((name, content) -> entries.put(name, content.getBytes(StandardCharsets.UTF_8)));
        return writeJar(jar, entries);
    }

    private static Path writeJar(Path jar, Map<String, byte[]> entries) throws Exception {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private static final String GREETING_APP = "public class App { public static void main(String[] args) { System.out.print(greet.Greeter.greet() + \" \" + kanvas.libs.math.KMath.class.getSimpleName()); } }";

    private static String runJar(Path jar) throws Exception {
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString())
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    @Test
    void jarAndDepsPutsTheRuntimeAndDependenciesInLib() throws Exception {
        Path app = tmp.resolve("app");
        greeterJar(app.resolve("lib/greeter.jar"), Map.of());
        Path config = javaApp(app, 21, "dependencies = [\"greeter.jar\"]\n[packaging]\nformat = \"jar-and-deps\"\n", GREETING_APP);
        Path jar = new BuildManager(ProjectLoader.loadProject(config)).packageProject();

        Path lib = jar.resolveSibling("lib");
        assertTrue(Files.isRegularFile(lib.resolve("kanvas.jar")));
        assertTrue(Files.isRegularFile(lib.resolve("greeter.jar")));
        try (JarFile file = new JarFile(jar.toFile())) {
            assertEquals("lib/kanvas.jar lib/greeter.jar", file.getManifest().getMainAttributes().getValue("Class-Path"));
            assertNull(file.getEntry("greet/Greeter.class"), "dependencies stay in lib/");
        }
        assertEquals("hi KMath", runJar(jar));
    }

    @Test
    void fatMergesTheRuntimeAndDependencies() throws Exception {
        Path app = tmp.resolve("app");
        greeterJar(app.resolve("lib/greeter.jar"), Map.of("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: greet.Wrong\n",
            "META-INF/GREETER.SF", "signature", "META-INF/GREETER.RSA", "signature", "module-info.class", "module"));
        Path config = javaApp(app, 21, "dependencies = [\"greeter.jar\"]\n", GREETING_APP);
        Project jarAndDeps = ProjectLoader.loadProject(config, Map.of("packaging.format", "jar-and-deps"));
        new BuildManager(jarAndDeps).packageProject();

        Path jar = new BuildManager(ProjectLoader.loadProject(config, Map.of("packaging.format", "fat"))).packageProject();
        assertFalse(Files.exists(jar.resolveSibling("lib")), "lib/ from the earlier package is removed");
        try (JarFile file = new JarFile(jar.toFile())) {
            assertEquals("App", file.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNull(file.getManifest().getMainAttributes().getValue("Class-Path"));
            assertNotNull(file.getEntry("greet/Greeter.class"));
            assertNotNull(file.getEntry("kanvas/gui/KanvasScript.class"));
            for (String dropped : List.of("META-INF/GREETER.SF", "META-INF/GREETER.RSA", "module-info.class")) assertNull(file.getEntry(dropped), dropped);
        }
        assertEquals("hi KMath", runJar(jar));
    }

    @Test
    void fatMergesServiceFilesAndRejectsOtherDuplicates() throws Exception {
        Path app = tmp.resolve("app");
        writeJar(app.resolve("lib/a.jar"), Map.of("META-INF/services/demo.Plugin", "a.Plugin\n".getBytes(StandardCharsets.UTF_8)));
        writeJar(app.resolve("lib/b.jar"), Map.of("META-INF/services/demo.Plugin", "b.Plugin".getBytes(StandardCharsets.UTF_8)));
        Path config = javaApp(app, 21, "dependencies = [\"a.jar\", \"b.jar\"]\n[packaging]\nformat = \"fat\"\n", "public class App {}");
        Path jar = new BuildManager(ProjectLoader.loadProject(config)).packageProject();
        try (JarFile file = new JarFile(jar.toFile())) {
            String services = new String(file.getInputStream(file.getEntry("META-INF/services/demo.Plugin")).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("a.Plugin\nb.Plugin\n", services);
        }

        writeJar(app.resolve("lib/b.jar"), Map.of("config.properties", "b".getBytes(StandardCharsets.UTF_8)));
        writeJar(app.resolve("lib/a.jar"), Map.of("config.properties", "a".getBytes(StandardCharsets.UTF_8)));
        String message = assertThrows(KanvasException.class, () -> new BuildManager(ProjectLoader.loadProject(config)).packageProject()).getMessage();
        assertTrue(message.contains("config.properties is in both") && message.contains("a.jar") && message.contains("b.jar"), message);
    }

    @Test
    void fatSketchJarsContainTheRuntime() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path config = writeProject(tmp, "name = \"s\"\nversion = \"1\"\nmainClass = \"Main\"\n[modules]\nsrcDirs = [\"src\"]\n[packaging]\nformat = \"fat\"\n");
        Path jar = new BuildManager(ProjectLoader.loadProject(config)).packageProject();
        try (JarFile file = new JarFile(jar.toFile())) {
            assertEquals("kanvas.generated.Main", file.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNotNull(file.getEntry("kanvas/generated/Main.class"));
            assertNotNull(file.getEntry("kanvas/gui/KanvasScript.class"));
        }
    }

    @Test
    void launcherJarsAreNotSupportedYet() throws Exception {
        Path config = javaApp(tmp, 21, "[packaging]\nformat = \"launcher\"\n", "public class App {}");
        String message = assertThrows(KanvasException.class, () -> new BuildManager(ProjectLoader.loadProject(config)).packageProject()).getMessage();
        assertTrue(message.contains("packaging.format = \"launcher\" is not supported yet"), message);
    }

    @Test
    void buildIfNeededSkipsUnchangedProjects() throws Exception {
        Path sketch = writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path asset = writeFile(tmp.resolve("assets/a.txt"), "a");
        Path config = sketchProject(tmp);
        BuildManager manager = new BuildManager(ProjectLoader.loadProject(config));

        assertTrue(manager.buildIfNeeded(), "first build");
        assertFalse(manager.buildIfNeeded(), "nothing changed");

        Files.writeString(sketch, "void setup() { println(1); }");
        assertTrue(manager.buildIfNeeded(), "source changed");
        assertFalse(manager.buildIfNeeded());

        Files.writeString(asset, "b");
        assertTrue(manager.buildIfNeeded(), "resource changed");

        Files.writeString(config, Files.readString(config) + "[runtime]\njvmArgs = [\"-Xmx512m\"]\n");
        BuildManager reloaded = new BuildManager(ProjectLoader.loadProject(config));
        assertTrue(reloaded.buildIfNeeded(), "config changed");
        assertFalse(reloaded.buildIfNeeded());

        new BuildManager(ProjectLoader.loadProject(config)).clean();
        assertTrue(reloaded.buildIfNeeded(), "cleaned");
    }

    @Test
    void aFailedBuildIsNeverUpToDate() throws Exception {
        Path sketch = writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        BuildManager manager = new BuildManager(ProjectLoader.loadProject(sketchProject(tmp)));
        manager.build();

        Files.writeString(sketch, "void setup() { undefinedCall(); }");
        assertThrows(KanvasException.class, manager::buildIfNeeded);
        assertThrows(KanvasException.class, manager::buildIfNeeded, "still broken, so it must build again");
    }

    @Test
    void rebuildsDropOutputFromDeletedFiles() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path extra = writeFile(tmp.resolve("src/extra.kvs"), "void setup() {}");
        Path asset = writeFile(tmp.resolve("assets/old.txt"), "old");
        Project project = build(sketchProject(tmp));
        assertTrue(Files.isRegularFile(project.buildLayout().classes().resolve("kanvas/generated/Extra.class")));

        Files.delete(extra);
        Files.delete(asset);
        new BuildManager(project).build();
        assertFalse(Files.exists(project.buildLayout().classes().resolve("kanvas/generated/Extra.class")));
        assertFalse(Files.exists(project.buildLayout().generated().resolve("kanvas/generated/Extra.java")));
        assertFalse(Files.exists(project.buildLayout().resources().resolve("old.txt")));
    }

    @Test
    void resourcesAreCopiedAndPackagedAtTheJarRoot() throws Exception {
        writeFile(tmp.resolve("assets/text/greeting.txt"), "hello from assets");
        Path config = javaApp(tmp, 21, "", """
            public class App {
                public static void main(String[] args) throws Exception {
                    System.out.print(new String(App.class.getResourceAsStream("/text/greeting.txt").readAllBytes()));
                }
            }""");
        Project project = ProjectLoader.loadProject(config);
        Path jar = new BuildManager(project).packageProject();
        assertEquals("hello from assets", Files.readString(project.buildLayout().resources().resolve("text/greeting.txt")));

        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString())
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertEquals("hello from assets", output);
    }

    @Test
    void twoResourceFoldersCannotHoldTheSamePath() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        writeFile(tmp.resolve("assets/logo.png"), "a");
        writeFile(tmp.resolve("more/logo.png"), "b");
        Path config = writeProject(tmp, "name = \"s\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\nresourceDirs = [\"assets\", \"more\"]\n");
        String message = assertThrows(KanvasException.class, () -> build(config)).getMessage();
        assertTrue(message.contains("Two resource folders both contain logo.png"), message);
    }

    @Test
    void cleanDeletesTheBuildAndCacheButNotTheProject() throws Exception {
        Path sketch = writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Path config = sketchProject(tmp);
        Project project = build(config);
        writeFile(project.cacheDir().resolve("build.hash"), "abc");

        new BuildManager(project).clean();
        assertFalse(Files.exists(tmp.resolve("build")));
        assertFalse(Files.exists(project.cacheDir()));
        assertTrue(Files.isRegularFile(sketch));
        assertTrue(Files.isRegularFile(config));

        new BuildManager(project).clean(); // nothing left to delete
    }

    @Test
    void cleanCacheLeavesTheBuild() throws Exception {
        writeFile(tmp.resolve("src/main.kvs"), "void setup() {}");
        Project project = build(sketchProject(tmp));
        writeFile(project.cacheDir().resolve("build.hash"), "abc");

        new BuildManager(project).cleanCache();
        assertFalse(Files.exists(project.cacheDir()));
        assertTrue(Files.isDirectory(tmp.resolve("build/classes")));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "., the project directory", "src, contains src", "../outside, outside the project directory" })
    void cleanRefusesToDeleteProjectFiles(String buildDir, String reason) throws Exception {
        Path project = tmp.resolve("project");
        Path sketch = writeFile(project.resolve("src/main.kvs"), "void setup() {}");
        Path config = writeProject(project, "name = \"s\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\nbuildDir = \"" + buildDir + "\"\n");
        Path outside = writeFile(tmp.resolve("outside/keep.txt"), "keep");

        String message = assertThrows(KanvasException.class, () -> new BuildManager(ProjectLoader.loadProject(config)).clean()).getMessage();
        assertTrue(message.contains(reason) && message.contains("modules.buildDir"), message);
        for (Path file : List.of(sketch, config, outside)) assertTrue(Files.isRegularFile(file), file.toString());
    }

    @Test
    void sameFileNameInDifferentFoldersCoexists() throws Exception {
        writeFile(tmp.resolve("src/a/player.kvs"), "int a = 1;");
        writeFile(tmp.resolve("src/b/player.kvs"), "int b = 2;");
        Path classes = build(sketchProject(tmp)).buildLayout().classes();
        assertTrue(Files.isRegularFile(classes.resolve("a/Player.class")));
        assertTrue(Files.isRegularFile(classes.resolve("b/Player.class")));
    }

    @Test
    void twoSketchesCompilingToTheSameClassAreRejected() throws Exception {
        writeFile(tmp.resolve("src/a/player.kvs"), "void setup() {}");
        writeFile(tmp.resolve("src/b/player.kvs"), "package a;\nvoid setup() {}");
        String message = assertThrows(KanvasException.class, () -> build(sketchProject(tmp))).getMessage();
        assertTrue(message.contains("src/a/player.kvs") && message.contains("src/b/player.kvs") && message.contains("both generate a.Player"), message);
    }

    @Test
    void foldersThatAreNotPackageNamesNeedADeclaration() throws Exception {
        Path sketch = writeFile(tmp.resolve("src/my-stuff/thing.kvs"), "void setup() {}");
        String message = assertThrows(KanvasException.class, () -> build(sketchProject(tmp))).getMessage();
        assertTrue(message.contains("src/my-stuff/thing.kvs") && message.contains("'my-stuff' is not a valid Java package name"), message);

        Files.writeString(sketch, "package stuff;\nvoid setup() {}");
        build(sketchProject(tmp));
        assertTrue(Files.isRegularFile(tmp.resolve("build/classes/stuff/Thing.class")));
    }
}
