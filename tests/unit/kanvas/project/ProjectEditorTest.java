package kanvas.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectEditorTest {
    private static final String CONFIG = """
        name = "p" # the name
        version = "1"

        [dependencies]
        # Maven: "group:artifact" = "version"
        "com.google.code.gson:gson" = "2.10.1"
          utils = { path = "lib/utils.jar" }

        [modules]
        srcDirs = ["src"]
        """;

    @Test
    void addsAfterTheLastEntryAndKeepsEverythingElse() throws Exception {
        String edited = ProjectEditor.setDependency(CONFIG, "org.joml:joml", "\"org.joml:joml\"", "\"1.10.5\"");
        assertEquals(CONFIG.replace("utils = { path = \"lib/utils.jar\" }\n", "utils = { path = \"lib/utils.jar\" }\n\"org.joml:joml\" = \"1.10.5\"\n"), edited);
    }

    @Test
    void replacesAnExistingEntryKeepingItsIndent() throws Exception {
        assertEquals(CONFIG.replace("\"2.10.1\"", "\"2.11.0\""),
            ProjectEditor.setDependency(CONFIG, "com.google.code.gson:gson", "\"com.google.code.gson:gson\"", "\"2.11.0\""));
        assertEquals(CONFIG.replace("  utils = { path = \"lib/utils.jar\" }", "  utils = { path = \"lib/utils.jar\", scope = \"runtime\" }"),
            ProjectEditor.setDependency(CONFIG, "utils", "utils", "{ path = \"lib/utils.jar\", scope = \"runtime\" }"));
    }

    @Test
    void createsTheTableWhenThereIsNone() throws Exception {
        assertEquals("name = \"p\"\n\n[dependencies]\n\"g:a\" = \"1\"\n", ProjectEditor.setDependency("name = \"p\"\n\n", "g:a", "\"g:a\"", "\"1\""));
    }

    @Test
    void removesOnlyThatLineAndKeepsWindowsLineEndings() throws Exception {
        String windows = CONFIG.replace("\n", "\r\n");
        assertEquals(windows.replace("\"com.google.code.gson:gson\" = \"2.10.1\"\r\n", ""), ProjectEditor.removeDependency(windows, "com.google.code.gson:gson"));
        String message = assertThrows(KanvasProjectException.class, () -> ProjectEditor.removeDependency(CONFIG, "g:missing")).getMessage();
        assertTrue(message.contains("'g:missing' isn't in [dependencies]"), message);
    }

    @Test
    void refusesTheOlderArrayForm() {
        String message = assertThrows(KanvasProjectException.class,
            () -> ProjectEditor.setDependency("name = \"p\"\ndependencies = [\"gson.jar\"]\n[modules]\n", "g:a", "\"g:a\"", "\"1\"")).getMessage();
        assertTrue(message.contains("older `dependencies = [...]` array"), message);
    }
}
