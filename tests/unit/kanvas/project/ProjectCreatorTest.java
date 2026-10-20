package kanvas.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectCreatorTest {
    @TempDir Path tmp;

    static List<String> templates() { return ProjectCreator.getTemplateTypes(); }

    @ParameterizedTest
    @MethodSource("templates")
    void createsALoadableProjectFromEveryTemplate(String template) throws Exception {
        ProjectCreator.createProject("demo-" + template, template, tmp.toString());
        Project project = ProjectLoader.loadProject(tmp.resolve("demo-" + template + "/kanvas.toml"));
        assertEquals("demo-" + template, project.name());
        assertEquals(System.getProperty("user.name", ""), project.author());
        assertDoesNotThrow(() -> ProjectLoader.validateProject(project));
    }

    @Test
    void refusesToOverwriteAnExistingProjectUnlessForced() throws Exception {
        ProjectCreator.createProject("demo", "java-app", tmp.toString());
        Path app = tmp.resolve("demo/src/App.java");
        Files.writeString(app, "// my edits");
        Files.writeString(tmp.resolve("demo/notes.txt"), "keep me");

        assertEquals(List.of(tmp.resolve("demo/kanvas.toml").normalize(), app.normalize()), ProjectCreator.existingFiles("demo", "java-app", tmp.toString()));
        String message = assertThrows(KanvasProjectException.class, () -> ProjectCreator.createProject("demo", "java-app", tmp.toString())).getMessage();
        assertTrue(message.contains("already exists"), message);
        assertEquals("// my edits", Files.readString(app));

        ProjectCreator.createProject("demo", "java-app", tmp.toString(), true);
        assertNotEquals("// my edits", Files.readString(app));
        assertEquals("keep me", Files.readString(tmp.resolve("demo/notes.txt")));
    }

    @Test
    void reportsNothingToOverwriteForANewProject() {
        assertEquals(List.of(), ProjectCreator.existingFiles("fresh", "kanvas-sketch", tmp.toString()));
    }

    @Test
    void rejectsBadNamesAndUnknownTemplates() {
        assertThrows(KanvasProjectException.class, () -> ProjectCreator.createProject("bad name!", "java-app", tmp.toString()));
        assertThrows(KanvasProjectException.class, () -> ProjectCreator.createProject("..", "java-app", tmp.toString()));
        String message = assertThrows(KanvasProjectException.class, () -> ProjectCreator.createProject("ok", "nope", tmp.toString())).getMessage();
        assertTrue(message.contains("Template 'nope' not found"), message);
    }
}
