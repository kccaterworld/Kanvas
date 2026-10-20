package kanvas.builder;

import kanvas.KanvasException;
import kanvas.TestSupport;
import kanvas.project.Project;
import kanvas.project.ProjectLoader;
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
                String className = kanvas.preprocess.Preprocessor.classNameFor(kvs.getFileName().toString().replaceFirst("[.][^.]+$", ""));
                assertTrue(Files.isRegularFile(layout.generated().resolve("kanvas/generated/" + className + ".java")), className + ".java");
                assertTrue(Files.isRegularFile(layout.classes().resolve("kanvas/generated/" + className + ".class")), className + ".class");
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
}
