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

        assertEquals(List.of(new Dependency.Local("gson.jar", dir.resolve("lib/gson.jar"))), project.dependencies());

        assertEquals(new Project.PackagingSettings("custom.jar", dir.resolve("icon.png"), List.of("linux"), Project.PACKAGE_FORMAT.JAR_AND_DEPS), project.packagingSettings());
    }

    @Test
    void readsTheDependenciesTable() throws Exception {
        Path config = writeProject(tmp, MINIMAL + """
            [dependencies]
            "com.google.code.gson:gson" = "2.10.1"
            "org.joml:joml" = { version = "1.10.5" }
            utils = { path = "libs/utils.jar" }
            shared = { path = "../shared/classes" }
            """);
        Path dir = tmp.toAbsolutePath().normalize();
        assertEquals(List.of(
            new Dependency.Maven("com.google.code.gson", "gson", "2.10.1"),
            new Dependency.Maven("org.joml", "joml", "1.10.5"),
            new Dependency.Local("utils", dir.resolve("libs/utils.jar")),
            new Dependency.Local("shared", dir.getParent().resolve("shared/classes"))), ProjectLoader.loadProject(config).dependencies());
    }

    @Test
    void readsNativesAndChecksPlatformNames() throws Exception {
        Path config = writeProject(tmp, MINIMAL + """
            [packaging]
            nativeTargets = ["windows", "macos-arm64"]
            [dependencies]
            "org.lwjgl:lwjgl" = { version = "3.3.3", natives = { windows = "natives-windows", macos-arm64 = "natives-macos-arm64" } }
            """);
        Project project = ProjectLoader.loadProject(config);
        assertEquals(Map.of("windows", "natives-windows", "macos-arm64", "natives-macos-arm64"), ((Dependency.Maven) project.dependencies().getFirst()).natives());
        assertTrue(ProjectWriter.write(project).contains("\"org.lwjgl:lwjgl\" = { version = \"3.3.3\", natives = { macos-arm64 = \"natives-macos-arm64\", windows = \"natives-windows\" } }"));

        Path bad = writeProject(tmp, MINIMAL + """
            [packaging]
            nativeTargets = ["amiga"]
            [dependencies]
            "g:a" = { version = "1", natives = { solaris = "natives-solaris", linux = "../evil" } }
            local = { path = "x.jar", natives = { linux = "natives-linux" } }
            """);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(bad)).getMessage();
        for (String expected : List.of("packaging.nativeTargets has unknown platform 'amiga'", "has unknown platform 'solaris'",
                "natives.linux must be a classifier", "dependencies.\"local\".natives only works for Maven dependencies"))
            assertTrue(message.contains(expected), expected + "\n" + message);
    }

    @Test
    void readsAndWritesScopes() throws Exception {
        Path config = writeProject(tmp, MINIMAL + """
            [dependencies]
            "org.slf4j:slf4j-simple" = { version = "2.0.9", scope = "runtime" }
            api = { path = "lib/api.jar", scope = "provided" }
            "com.google.code.gson:gson" = { version = "2.10.1", scope = "compile" }
            """);
        Project project = ProjectLoader.loadProject(config);
        Path dir = tmp.toAbsolutePath().normalize();
        assertEquals(List.of(
            new Dependency.Maven("org.slf4j", "slf4j-simple", "2.0.9", Dependency.Scope.RUNTIME),
            new Dependency.Local("api", dir.resolve("lib/api.jar"), Dependency.Scope.PROVIDED),
            new Dependency.Maven("com.google.code.gson", "gson", "2.10.1")), project.dependencies());

        String written = ProjectWriter.write(project);
        assertTrue(written.contains("\"org.slf4j:slf4j-simple\" = { version = \"2.0.9\", scope = \"runtime\" }")
            && written.contains("api = { path = \"lib/api.jar\", scope = \"provided\" }")
            && written.contains("\"com.google.code.gson:gson\" = \"2.10.1\"\n"), written);
    }

    @Test
    void theLegacyArrayAndOverrideAcceptCoordinatesAndLibFiles() throws Exception {
        Path config = writeProject(tmp, "dependencies = [\"org.joml:joml:1.10.5\", \"gson.jar\"]\n" + MINIMAL);
        Path dir = tmp.toAbsolutePath().normalize();
        List<Dependency> expected = List.of(new Dependency.Maven("org.joml", "joml", "1.10.5"), new Dependency.Local("gson.jar", dir.resolve("lib/gson.jar")));
        assertEquals(expected, ProjectLoader.loadProject(config).dependencies());
        assertEquals(expected, ProjectLoader.loadProject(writeProject(tmp, MINIMAL), Map.of("dependencies", "org.joml:joml:1.10.5, gson.jar")).dependencies());
    }

    @Test
    void repositoriesDefaultToMavenCentralAndNeedHttps() throws Exception {
        assertEquals(List.of(Project.MAVEN_CENTRAL), ProjectLoader.loadProject(writeProject(tmp, MINIMAL)).repositories());

        Path config = writeProject(tmp, "repositories = [\"https://jitpack.io\", \"http://localhost:8080/repo/\"]\n" + MINIMAL);
        assertEquals(List.of(java.net.URI.create("https://jitpack.io/"), java.net.URI.create("http://localhost:8080/repo/")),
            ProjectLoader.loadProject(config).repositories(), "a trailing slash is added");

        Path bad = writeProject(tmp, "repositories = [\"http://example.com/maven\", \"ftp://example.com\", \"not a url\"]\n" + MINIMAL);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(bad)).getMessage();
        assertTrue(message.contains("\"http://example.com/maven\" uses http; use https") && message.contains("\"ftp://example.com\" must be an https:// URL")
            && message.contains("\"not a url\" is not a valid URL"), message);
    }

    @Test
    void rejectsBadDependencies() throws Exception {
        Path config = writeProject(tmp, MINIMAL + """
            [dependencies]
            utils = "1.0"
            "a:b" = { version = "1", path = "x.jar" }
            "a:b:c" = "1"
            "../evil:x" = "1"
            "g:ranged" = "[1.0,2.0)"
            "g:noversion" = { }
            local = { path = "x.jar", scope = "test" }
            """);
        String message = assertThrows(KanvasProjectException.class, () -> ProjectLoader.loadProject(config)).getMessage();
        for (String expected : List.of(
                "dependencies.\"utils\" needs { path = \"...\" }, or a \"group:artifact\" key with a version",
                "dependencies.\"a:b\" has both version and path",
                "dependencies.\"a:b:c\" must be written \"group:artifact\"",
                "'../evil' is not a valid Maven group",
                "version ranges like '[1.0,2.0)' are not supported",
                "dependencies.\"g:noversion\" needs a version",
                "dependencies.\"local\".scope must be \"compile\", \"runtime\" or \"provided\", found \"test\""))
            assertTrue(message.contains(expected), expected + "\n" + message);
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
        Path sketch = writeFile(dir.resolve("src/my-sketch.kvs"), "void setup() {}");
        Path nested = writeFile(dir.resolve("src/game/my-sketch.kvs"), "void setup() {}");
        Path declared = writeFile(dir.resolve("src/other/level.kvs"), "package com.levels;\nvoid setup() {}");
        Path javaMain = writeFile(dir.resolve("src/com/example/App.java"), "package com.example; public class App {}");

        assertEquals(List.of(sketch), ProjectLoader.loadProject(config).entryPoints());
        assertEquals(List.of(sketch), ProjectLoader.loadProject(config, Map.of("mainClass", "kanvas.generated.MySketch")).entryPoints());
        assertEquals(List.of(nested), ProjectLoader.loadProject(config, Map.of("mainClass", "game.MySketch")).entryPoints());
        assertEquals(List.of(declared), ProjectLoader.loadProject(config, Map.of("mainClass", "com.levels.Level")).entryPoints());
        assertEquals(List.of(), ProjectLoader.loadProject(config, Map.of("mainClass", "other.Level")).entryPoints());
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
        assertTrue(message.contains("has 3 problems"), message);
        assertTrue(message.contains("Source directory not found") && message.contains("Classpath entry not found")
            && message.contains("Icon not found"), message);

        Files.createDirectories(tmp.resolve("gen"));
        Files.createDirectories(tmp.resolve("libs/extra"));
        writeFile(tmp.resolve("icon.png"), "");
        assertDoesNotThrow(() -> ProjectLoader.validateProject(project));
    }
}
