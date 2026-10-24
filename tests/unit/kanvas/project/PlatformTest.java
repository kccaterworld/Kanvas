package kanvas.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {
    private static final Set<String> LWJGL = Set.of("windows", "windows-arm64", "linux", "macos", "macos-arm64");

    @Test
    void parsesOsAndOptionalArchitecture() {
        assertEquals(new Platform("macos", "arm64"), Platform.parse("macos-arm64"));
        assertEquals(new Platform("linux", null), Platform.parse("linux"));
        for (String bad : List.of("Windows", "linux-sparc", "bsd", "windows-", "")) assertNull(Platform.parse(bad), bad);
        assertNotNull(Platform.parse(Platform.current().id()));
    }

    @Test
    void aSpecificPlatformUsesItsOwnKeyThenItsOs() {
        assertEquals(List.of("macos-arm64"), Platform.parse("macos-arm64").select(LWJGL));
        assertEquals(List.of("linux"), Platform.parse("linux-arm64").select(LWJGL));
        assertEquals(List.of("windows"), Platform.parse("windows-x64").select(LWJGL));
        assertEquals(List.of(), Platform.parse("linux-x64").select(Set.of("windows")));
    }

    @Test
    void aPlainOsTakesEveryKeyForThatOs() {
        assertEquals(List.of("macos", "macos-arm64"), Platform.parse("macos").select(LWJGL));
        assertEquals(List.of("windows", "windows-arm64"), Platform.parse("windows").select(LWJGL));
    }
}
