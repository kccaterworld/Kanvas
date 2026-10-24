package kanvas.project;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

public sealed interface Dependency {
    String key();

    record Local(String name, Path path) implements Dependency {
        public Local {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
        }
        public String key() { return name; }
    }

    record Maven(String group, String artifact, String version) implements Dependency {
        private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");
        private static final Pattern ARTIFACT = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*");
        private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_+-][A-Za-z0-9_.+-]*");

        public Maven {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
        }
    
        public String key() { return group + ":" + artifact; }
        public String coordinate() { return key() + ":" + version; }

        public static String problem(String group, String artifact, String version) {
            if (!GROUP.matcher(group).matches()) return "'" + group + "' is not a valid Maven group";
            if (!ARTIFACT.matcher(artifact).matches()) return "'" + artifact + "' is not a valid Maven artifact";
            if (version.startsWith("[") || version.startsWith("(")) return "version ranges like '" + version + "' are not supported";
            if (!VERSION.matcher(version).matches()) return "'" + version + "' is not a valid version";
            return null;
        }
    }
}
