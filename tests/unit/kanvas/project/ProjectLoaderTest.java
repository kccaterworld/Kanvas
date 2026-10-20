package kanvas.project;

import kanvas.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.util.*;

import static kanvas.TestSupport.writeFile;
import static kanvas.TestSupport.writeProject;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLoaderTest {
    private static final String MINIMAL = "name = \"m\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\n";

    @TempDir Path tmp;

    static List<Path> fixtureConfigs() { return TestSupport.fixtureConfigs(); }

    @ParameterizedTest
    @MethodSource("fixtureConfigs")
    void loadsEveryConfigInTheRepository(Path config) throws Exception {
        Project project = ProjectLoader.loadProject(config);
        Path dir = config.toAbsolutePath().normalize().getParent();
        assertEquals(1, project.configVersion());
        assertEquals(dir, project.scriptDir());
        assertEquals(dir.resolve("build/classes"), project.buildLayout().classes());
        assertEquals(dir.resolve("build/generated"), project.buildLayout().generated());
        assertEquals(List.of(dir.resolve("src")), project.sourceRoots());
    }

    @Test
    void readsEveryField() throws Exception {
        Path config = writeProject(tmp, """
            configVersion = 1
            name = "demo"
            version = "1.2.3"
            author = "Ada"
            description = "A demo"
            mainClass = "com.example.Main"
            classpath = ["libs/extra"]
            dependencies = ["gson.jar"]
            [modules]
            srcDirs = ["src", "gen"]
            resourceDirs = ["res"]
            buildDir = "out"
            [compiler]
            target = 17
            encoding = "ISO-8859-1"
            flags = ["-Xlint:all"]
            [runtime]
            jvmArgs = ["-Xmx1g"]
            programArgs = ["--fast"]
            env = { MODE = "debug" }
            [packaging]
            jarName = "custom.jar"
            icon = "icon.png"
            nativeTargets = ["linux"]
            format = "jar-and-deps"
            """);
        Project project = ProjectLoader.loadProject(config);
        Path dir = tmp.toAbsolutePath().normalize();

        assertEquals("demo", project.name());
        assertEquals("1.2.3", project.version());
        assertEquals("Ada", project.author());
        assertEquals("A demo", project.description());
        assertEquals(List.of(dir.resolve("src"), dir.resolve("gen")), project.sourceRoots());
        assertEquals(List.of(dir.resolve("res")), project.resourceRoots());
        assertEquals(dir.resolve("out"), project.buildLayout().root());
        assertEquals(dir.resolve("out/output"), project.buildLayout().output());
        assertEquals(new Project.CompilerSettings(17, "ISO-8859-1", List.of("-Xlint:all")), project.compilerSettings());

        Project.RuntimeSettings runtime = project.runtimeSettings();
        assertEquals("com.example.Main", runtime.mainClass());
        assertEquals(List.of(dir.resolve("libs/extra")), runtime.classpath());
        assertEquals(List.of("-Xmx1g"), runtime.jvmArgs());
        assertEquals(List.of("--fast"), runtime.programArgs());
        assertEquals(Map.of("MODE", "debug"), runtime.envVars());

        assertEquals(List.of("gson.jar"), project.dependencyDeclarations());
        assertEquals(List.of(dir.resolve("lib/gson.jar")), project.dependencyClasspath());
        assertEquals(List.of(dir.resolve("libs/extra"), dir.resolve("lib/gson.jar")), project.userClasspath());

        assertEquals(new Project.PackagingSettings("custom.jar", dir.resolve("icon.png"), List.of("linux"), Project.PACKAGE_FORMAT.JAR_AND_DEPS), project.packagingSettings());
    }

    @Test
    void fillsInDefaults() throws Exception {
        Project project = ProjectLoader.loadProject(writeProject(tmp, MINIMAL));
        Path dir = tmp.toAbsolutePath().normalize();
        assertEquals(ProjectLoader.SUPPORTED_CONFIG_VERSION, project.configVersion());
        assertEquals("", project.author());
        assertNull(project.runtimeSettings().mainClass());
        assertEquals(List.of(dir.resolve("assets")), project.resourceRoots());
        assertEquals(Project.BuildLayout.standard(dir), project.buildLayout());
        assertEquals(Project.CompilerSettings.defaults(), project.compilerSettings());
        assertEquals(Project.PackagingSettings.defaults("m"), project.packagingSettings());
    }

    @Test
    void acceptsTargetWrittenAsAString() throws Exception {
        assertEquals(17, ProjectLoader.loadProject(writeProject(tmp, MINIMAL + "[compiler]\ntarget = \"17\"\n")).compilerSettings().target());
    }

    @Test
    void loadingTheSameFileTwiceGivesEqualProjects() throws Exception {
        Path config = writeProject(tmp, MINIMAL);
        assertEquals(ProjectLoader.loadProject(config), ProjectLoader.loadProject(config));
    }

    @Test
    void reportsEveryProblemAtOnce() throws Exception {
        Path config = writeProject(tmp, """
            name = ""
            version = 3
            [modules]
            srcDirs = []
            srcDir = ["src"]
            [compiler]
            target = "twenty-one"
            encoding = "NOPE-42"
            [packaging]
            format = "zip"
            """);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(config)).getMessage();
        assertTrue(message.contains("has 7 problems"), message);
        for (String expected : List.of("name must not be empty", "version must be a string, found 3", "modules.srcDirs must list at least one entry",
                "compiler.target must be a whole number, found \"twenty-one\"", "compiler.encoding \"NOPE-42\" is not a supported character set",
                "packaging.format must be one of", "Unknown key 'modules.srcDir'"))
            assertTrue(message.contains(expected), "missing '" + expected + "' in:\n" + message);
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(writeProject(tmp, ""))).getMessage();
        assertTrue(message.contains("name is required") && message.contains("version is required") && message.contains("modules.srcDirs is required"), message);
    }

    @Test
    void rejectsUnsupportedConfigVersion() throws Exception {
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(writeProject(tmp, "configVersion = 2\n" + MINIMAL))).getMessage();
        assertTrue(message.contains("configVersion = 2 is not supported"), message);
    }

    @Test
    void rejectsProfilesUntilTheyAreSupported() throws Exception {
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(writeProject(tmp, MINIMAL + "[profiles.release]\nx = 1\n"))).getMessage();
        assertTrue(message.contains("profiles are not supported yet"), message);
    }

    @Test
    void ignoresLegacyKeys() throws Exception {
        Project project = ProjectLoader.loadProject(writeProject(tmp, MINIMAL.replace("[modules]\n", "[modules]\noutputDir = \"build/classes\"\n") + "[packaging]\nversion = \"9\"\n"));
        assertEquals(tmp.toAbsolutePath().normalize().resolve("build/classes"), project.buildLayout().classes());
    }

    @Test
    void reportsTomlSyntaxErrorsWithAPosition() throws Exception {
        Path config = writeProject(tmp, "name = \"x\nversion = \"1\"");
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(config)).getMessage();
        assertTrue(message.contains(config + ":1:8:"), message);
    }

    @Test
    void reportsAMissingFile() {
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(tmp.resolve("nope/kanvas.toml"))).getMessage();
        assertTrue(message.contains("kanvas.toml not found"), message);
    }

    @Test
    void appliesOverrides() throws Exception {
        Path config = writeProject(tmp, MINIMAL + "[packaging]\nicon = \"icon.png\"\n");
        Path dir = tmp.toAbsolutePath().normalize();
        Project project = ProjectLoader.loadProject(config, Map.of(
            "compiler.target", "19", "modules.srcDirs", "a, b", "packaging.icon", "", "name", "renamed", "compiler.encoding", "UTF-16"));
        assertEquals(19, project.compilerSettings().target());
        assertEquals("UTF-16", project.compilerSettings().encoding());
        assertEquals(List.of(dir.resolve("a"), dir.resolve("b")), project.sourceRoots());
        assertNull(project.packagingSettings().icon());
        assertEquals("renamed", project.name());
        assertEquals("renamed.jar", project.packagingSettings().jarName());
    }

    @Test
    void checksOverridesLikeFileValues() throws Exception {
        Path config = writeProject(tmp, MINIMAL);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(config, Map.of("compiler.target", "abc", "modules.srcDirs", ""))).getMessage();
        assertTrue(message.contains("compiler.target must be a whole number") && message.contains("modules.srcDirs must list at least one entry"), message);
    }

    @Test
    void findsEntryPoints() throws Exception {
        Path config = writeProject(tmp, "mainClass = \"MySketch\"\n" + MINIMAL);
        Path dir = tmp.toAbsolutePath().normalize();
        Path sketch = writeFile(dir.resolve("src/nested/my-sketch.kvs"), "void setup() {}");
        Path javaMain = writeFile(dir.resolve("src/com/example/App.java"), "package com.example; public class App {}");

        assertEquals(List.of(sketch), ProjectLoader.loadProject(config).entryPoints());
        assertEquals(List.of(sketch), ProjectLoader.loadProject(config, Map.of("mainClass", "kanvas.generated.MySketch")).entryPoints());
        assertEquals(List.of(javaMain), ProjectLoader.loadProject(config, Map.of("mainClass", "com.example.App")).entryPoints());
        assertEquals(List.of(), ProjectLoader.loadProject(config, Map.of("mainClass", "org.library.Main")).entryPoints());
        assertEquals(List.of(), ProjectLoader.loadProject(config, Map.of("mainClass", "")).entryPoints());
    }

    @Test
    void locatesTheProjectFromANestedDirectory() throws Exception {
        Path config = writeProject(tmp, MINIMAL);
        Path nested = Files.createDirectories(tmp.resolve("src/deep/er"));
        assertEquals(config.toAbsolutePath().normalize(), ProjectLoader.locateProject(nested));
    }

    @Test
    void reportsWhenNoProjectIsFound() throws Exception {
        Path empty = Files.createDirectories(tmp.resolve("nothing-here"));
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.locateProject(empty)).getMessage();
        assertTrue(message.contains("No kanvas.toml found"), message);
    }

    @Test
    void validatesTheFilesystem() throws Exception {
        Path config = writeProject(tmp, """
            name = "v"
            version = "1"
            classpath = ["libs/extra"]
            dependencies = ["gson.jar"]
            [modules]
            srcDirs = ["src", "gen"]
            [packaging]
            icon = "icon.png"
            """);
        Project project = ProjectLoader.loadProject(config);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.validateProject(project)).getMessage();
        assertTrue(message.contains("has 4 problems"), message);
        assertTrue(message.contains("Source directory not found") && message.contains("Classpath entry not found")
            && message.contains("Dependency 'gson.jar' not found") && message.contains("Icon not found"), message);

        Files.createDirectories(tmp.resolve("gen"));
        Files.createDirectories(tmp.resolve("libs/extra"));
        writeFile(tmp.resolve("lib/gson.jar"), "");
        writeFile(tmp.resolve("icon.png"), "");
        assertDoesNotThrow(() -> ProjectLoader.validateProject(project));
    }
}
