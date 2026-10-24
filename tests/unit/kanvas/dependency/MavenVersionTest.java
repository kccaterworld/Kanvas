package kanvas.dependency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenVersionTest {
    @Test
    void ordersVersionsLikeMaven() {
        List<String> ordered = List.of("1.0-alpha1", "1.0-alpha2", "1.0-beta", "1.0-m1", "1.0-rc1", "1.0-SNAPSHOT", "1.0", "1.0-sp1",
            "1.0.1", "1.2", "1.9", "1.10", "2.0-rc", "2.0", "10");
        List<String> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled, new java.util.Random(7));
        shuffled.sort((a, b) -> new MavenVersion(a).compareTo(new MavenVersion(b)));
        assertEquals(ordered, shuffled);
    }

    @Test
    void trailingZerosAndReleaseQualifiersDontMatter() {
        for (String same : List.of("1", "1.0", "1.0.0", "1-final", "1.0-GA", "1.0.0.RELEASE"))
            assertEquals(new MavenVersion("1"), new MavenVersion(same), same);
        assertEquals("1.10", MavenVersion.highest(List.of("1.9", "1.10", "1.2")));
    }
}
