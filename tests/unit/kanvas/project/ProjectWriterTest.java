package kanvas.project;

import kanvas.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.*;

import static kanvas.TestSupport.writeProject;
import static org.junit.jupiter.api.Assertions.*;

class ProjectWriterTest {
    @TempDir Path tmp;

    static List<Path> fixtureConfigs() { return TestSupport.fixtureConfigs(); }

    // `kanvas config` output must load back into the same Project, and writing that again must not change it.
    private static void assertRoundTrips(Path config) throws Exception {
        Project original = ProjectLoader.loadProject(config);
        String written = ProjectWriter.write(original);
        Project reloaded = ProjectLoader.parseProject(written, config, Map.of());
        assertEquals(original, reloaded, written);
        assertEquals(written, ProjectWriter.write(reloaded));
    }

    @ParameterizedTest
    @MethodSource("fixtureConfigs")
    void roundTripsEveryConfigInTheRepository(Path config) throws Exception {
        assertRoundTrips(config);
    }

    @Test
    void roundTripsEveryField() throws Exception {
        String outside = tmp.toAbsolutePath().normalize().getParent().resolve("elsewhere/thing.jar").toString().replace('\\', '/');
        Path config = writeProject(tmp.resolve("project"), """
            name = "odd \\"quoted\\" name"
            version = "1"
            author = "Ada"
            description = "tab\\there\\nnewline é"
            mainClass = "com.example.Main"
            classpath = ["%s", "../sibling", "libs/extra"]
            dependencies = ["gson.jar"]
            [modules]
            srcDirs = ["src", "gen"]
            resourceDirs = []
            buildDir = "."
            [compiler]
            target = 17
            encoding = "ISO-8859-1"
            flags = ["-Xlint:all", "-g"]
            [runtime]
            jvmArgs = ["-Xmx1g"]
            programArgs = ["a b", "c"]
            env = { "weird key" = "v", b = "2", a = "1" }
            [packaging]
            jarName = "custom.jar"
            icon = "art/icon.png"
            nativeTargets = ["linux", "windows"]
            format = "launcher"
            """.formatted(outside));
        assertRoundTrips(config);
    }

    @Test
    void writesPathsRelativeToTheProjectWithForwardSlashes() throws Exception {
        Path config = writeProject(tmp, "name = \"p\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src/main/kanvas\"]\n");
        String written = ProjectWriter.write(ProjectLoader.loadProject(config));
        assertTrue(written.contains("srcDirs = [\"src/main/kanvas\"]"), written);
        assertTrue(written.contains("buildDir = \"build\""), written);
        assertTrue(written.startsWith("configVersion = 1\n"), written);
    }

    @Test
    void sortsEnvironmentVariablesSoOutputIsStable() throws Exception {
        Path config = writeProject(tmp, "name = \"p\"\nversion = \"1\"\n[modules]\nsrcDirs = [\"src\"]\n[runtime]\nenv = { z = \"1\", a = \"2\" }\n");
        assertTrue(ProjectWriter.write(ProjectLoader.loadProject(config)).contains("env = { a = \"2\", z = \"1\" }"));
    }
}
