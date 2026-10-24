package kanvas.dependency;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MavenMetadata {
    private static final Pattern VERSION = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");
    private static final Pattern PRE_RELEASE = Pattern.compile("(?i).*(snapshot|alpha|beta|\\brc|[.-]cr|milestone|[.-]m\\d|preview|[.-]ea\\b).*");

    private MavenMetadata() {}

    static String latestRelease(String xml) {
        List<String> releases = new ArrayList<>();
        Matcher matcher = VERSION.matcher(xml);
        while (matcher.find()) {
            String version = matcher.group(1);
            if (!PRE_RELEASE.matcher(version).matches() && kanvas.project.Dependency.Maven.problem("g", "a", version) == null) releases.add(version);
        }
        return MavenVersion.highest(releases);
    }
}
