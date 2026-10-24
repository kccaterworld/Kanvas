package kanvas.project;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public sealed interface Dependency {
    String key();
    Scope scope();

    enum Scope {
        COMPILE(true, true), RUNTIME(false, true), PROVIDED(true, false);

        private final boolean compile, runtime;

        Scope(boolean compile, boolean runtime) {
            this.compile = compile;
            this.runtime = runtime;
        }

        public boolean onCompileClasspath() { return compile; }
        public boolean onRuntimeClasspath() { return runtime; }
        public String label() { return name().toLowerCase(Locale.ROOT); }

        public Scope union(Scope other) {
            boolean c = compile || other.compile, r = runtime || other.runtime;
            return c && r ? COMPILE : c ? PROVIDED : RUNTIME;
        }

        public static Scope parse(String value) {
            for (Scope scope : values()) if (scope.label().equals(value)) return scope;
            return null;
        }
    }

    record Local(String name, Path path, Scope scope) implements Dependency {
        public Local {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(scope, "scope");
        }
        public Local(String name, Path path) { this(name, path, Scope.COMPILE); }
        public String key() { return name; }
    }

    record Maven(String group, String artifact, String version, Scope scope, Map<String, String> natives) implements Dependency {
        private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");
        private static final Pattern ARTIFACT = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*");
        private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_+-][A-Za-z0-9_.+-]*");
        public static final Pattern CLASSIFIER = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*");

        public Maven {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(scope, "scope");
            natives = Collections.unmodifiableMap(new TreeMap<>(natives));
        }
        public Maven(String group, String artifact, String version, Scope scope) { this(group, artifact, version, scope, Map.of()); }
        public Maven(String group, String artifact, String version) { this(group, artifact, version, Scope.COMPILE); }

        public String key() { return group + ":" + artifact; }
        public String coordinate() { return key() + ":" + version; }
        public Maven withVersion(String version) { return new Maven(group, artifact, version, scope, natives); }
        public Maven withScope(Scope scope) { return new Maven(group, artifact, version, scope, natives); }

        public static String problem(String group, String artifact, String version) {
            if (group == null || !GROUP.matcher(group).matches()) return "'" + group + "' is not a valid Maven group";
            if (artifact == null || !ARTIFACT.matcher(artifact).matches()) return "'" + artifact + "' is not a valid Maven artifact";
            if (version == null) return "no version is given";
            if (isRange(version)) return "version ranges like '" + version + "' are not supported";
            if (!VERSION.matcher(version).matches()) return "'" + version + "' is not a valid version";
            return null;
        }

        public static boolean isRange(String version) { return version.startsWith("[") || version.startsWith("("); }
    }
}
