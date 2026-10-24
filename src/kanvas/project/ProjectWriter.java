package kanvas.project;

import kanvas.project.Project.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public final class ProjectWriter {
    private ProjectWriter() {}

    public static String write(Project project) {
        Path dir = project.scriptDir();
        RuntimeSettings runtime = project.runtimeSettings();
        CompilerSettings compiler = project.compilerSettings();
        PackagingSettings packaging = project.packagingSettings();
        StringBuilder toml = new StringBuilder();

        line(toml, "configVersion", String.valueOf(project.configVersion()));
        line(toml, "name", string(project.name()));
        line(toml, "version", string(project.version()));
        line(toml, "author", string(project.author()));
        line(toml, "description", string(project.description()));
        if (runtime.mainClass() != null) line(toml, "mainClass", string(runtime.mainClass()));
        line(toml, "classpath", paths(dir, runtime.classpath()));
        line(toml, "repositories", strings(project.repositories().stream().map(URI::toString).toList()));

        toml.append("\n[dependencies]\n");
        for (Dependency dependency : project.dependencies()) line(toml, dependencyKey(dependency), dependencyValue(dependency, dir));

        toml.append("\n[modules]\n");
        line(toml, "srcDirs", paths(dir, project.sourceRoots()));
        line(toml, "resourceDirs", paths(dir, project.resourceRoots()));
        line(toml, "buildDir", string(relative(dir, project.buildLayout().root())));

        toml.append("\n[compiler]\n");
        line(toml, "target", String.valueOf(compiler.target()));
        line(toml, "encoding", string(compiler.encoding()));
        line(toml, "flags", strings(compiler.flags()));

        toml.append("\n[runtime]\n");
        line(toml, "jvmArgs", strings(runtime.jvmArgs()));
        line(toml, "programArgs", strings(runtime.programArgs()));
        line(toml, "env", inlineTable(runtime.envVars()));

        toml.append("\n[packaging]\n");
        line(toml, "jarName", string(packaging.jarName()));
        if (packaging.icon() != null) line(toml, "icon", string(relative(dir, packaging.icon())));
        line(toml, "nativeTargets", strings(packaging.nativeTargets()));
        line(toml, "format", string(packaging.format().name().toLowerCase(Locale.ROOT).replace('_', '-')));
        return toml.toString();
    }

    public static String dependencyKey(Dependency dependency) { return key(dependency.key()); }

    public static String dependencyValue(Dependency dependency, Path dir) {
        return switch (dependency) {
            case Dependency.Maven maven -> maven.scope() == Dependency.Scope.COMPILE && maven.natives().isEmpty() ? string(maven.version())
                : "{ version = " + string(maven.version())
                    + (maven.scope() == Dependency.Scope.COMPILE ? "" : ", scope = " + string(maven.scope().label()))
                    + (maven.natives().isEmpty() ? "" : ", natives = " + inlineTable(maven.natives())) + " }";
            case Dependency.Local local -> "{ path = " + string(relative(dir, local.path()))
                + (local.scope() == Dependency.Scope.COMPILE ? "" : ", scope = " + string(local.scope().label())) + " }";
        };
    }

    private static void line(StringBuilder toml, String key, String value) { toml.append(key).append(" = ").append(value).append('\n'); }

    private static String relative(Path dir, Path path) {
        try {
            if (path.startsWith(dir)) {
                String relative = dir.relativize(path).toString().replace('\\', '/');
                return relative.isEmpty() ? "." : relative;
            }
        } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Cannot relativize " + path + " against " + dir, e); }
        return path.toString().replace('\\', '/');
    }

    private static String paths(Path dir, List<Path> paths) {
        return strings(paths.stream().map(path -> relative(dir, path)).toList());
    }

    private static String strings(List<String> values) {
        StringJoiner array = new StringJoiner(", ", "[", "]");
        for (String value : values) array.add(string(value));
        return array.toString();
    }

    private static String inlineTable(Map<String, String> values) {
        if (values.isEmpty()) return "{}";
        StringJoiner table = new StringJoiner(", ", "{ ", " }");
        for (String key : new TreeSet<>(values.keySet())) table.add(key(key) + " = " + string(values.get(key)));
        return table.toString();
    }

    private static String key(String key) {
        return !key.isEmpty() && key.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 128 || c == '_' || c == '-') ? key : string(key);
    }

    private static String string(String value) {
        StringBuilder s = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                case '\b' -> s.append("\\b");
                case '\f' -> s.append("\\f");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\t' -> s.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F) s.append(String.format("\\u%04X", (int) c));
                    else s.append(c);
                }
            }
        }
        return s.append('"').toString();
    }
}
