package kanvas.project;

import kanvas.project.Project.*;

import java.io.IOException;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public class ProjectLoader {
    public static final String CONFIG_FILE = "kanvas.toml";
    public static final int SUPPORTED_CONFIG_VERSION = 1;

    public static Path locateProject(Path startDir) throws KanvasProjectException {
        Path start = startDir.toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(CONFIG_FILE);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new KanvasProjectException("No " + CONFIG_FILE + " found in " + start + " or any parent directory");
    }

    public static Project loadProject(Path configPath) throws KanvasProjectException {
        Path config = configPath.toAbsolutePath().normalize(), scriptDir = config.getParent();
        String content;
        try { content = Files.readString(config, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) { throw new KanvasProjectException(CONFIG_FILE + " not found at " + config);
        } catch (IOException e) { throw new KanvasProjectException("Failed to read " + config + ": " + e.getMessage(), e); }

        List<String> errors = new ArrayList<>();
        Table root = new Table(TOMLParser.parse(content, configPath.toString()), "", errors);

        int configVersion = root.integer("configVersion", SUPPORTED_CONFIG_VERSION);
        if (!errors.isEmpty()) throw problems(configPath.toString(), errors);
        if (configVersion != SUPPORTED_CONFIG_VERSION)
            throw new KanvasProjectException(configPath + ": configVersion = " + configVersion + " is not supported. This version of Kanvas reads configVersion = " + SUPPORTED_CONFIG_VERSION + ".");

        String name = root.requiredString("name");
        String version = root.requiredString("version");
        root.string("author", null);
        root.string("description", null);
        String mainClass = blankToNull(root.string("mainClass", null));
        List<Path> classpath = resolveAll(scriptDir, root.strings("classpath"), "classpath", errors);
        List<String> dependencies = root.strings("dependencies");
        if (root.has("profiles")) errors.add("profiles are not supported yet");

        Table modules = root.table("modules");
        List<Path> sourceRoots = resolveAll(scriptDir, modules.requiredStrings("srcDirs"), "modules.srcDirs", errors);
        List<Path> resourceRoots = resolveAll(scriptDir, modules.strings("resourceDirs", List.of("assets")), "modules.resourceDirs", errors);
        Path buildDir = resolve(scriptDir, modules.string("buildDir", Project.DEFAULT_ROOT), "modules.buildDir", errors);
        modules.ignore("outputDir"); // superseded by buildDir; still present in older kanvas.toml files

        Table compiler = root.table("compiler");
        int target = compiler.integer("target", Project.DEFAULT_TARGET);
        if (target < 1) errors.add("compiler.target must be a positive Java release number, found " + target);
        String encoding = compiler.string("encoding", Project.DEFAULT_ENCODING);
        if (encoding != null && !isSupportedCharset(encoding)) errors.add("compiler.encoding \"" + encoding + "\" is not a supported character set");
        List<String> flags = compiler.strings("flags");

        Table runtime = root.table("runtime");
        List<String> jvmArgs = runtime.strings("jvmArgs");
        List<String> programArgs = runtime.strings("programArgs");
        Map<String, String> env = runtime.stringMap("env");

        Table packaging = root.table("packaging");
        String jarName = packaging.string("jarName", name == null ? null : name + ".jar");
        if (jarName != null && jarName.isBlank()) errors.add("packaging.jarName must not be empty");
        String iconName = blankToNull(packaging.string("icon", null));
        Path icon = iconName == null ? null : resolve(scriptDir, iconName, "packaging.icon", errors);
        List<String> nativeTargets = packaging.strings("nativeTargets");
        PACKAGE_FORMAT format = packageFormat(packaging.string("format", null), errors);
        packaging.ignore("version"); // duplicates the top-level version

        for (Table table : List.of(root, modules, compiler, runtime, packaging)) table.reportUnknownKeys();
        if (!errors.isEmpty()) throw problems(configPath.toString(), errors);

        Path libDir = scriptDir.resolve("lib");
        List<Path> runtimeClasspath = new ArrayList<>(classpath);
        for (String dependency : dependencies) runtimeClasspath.add(libDir.resolve(dependency).normalize());

        return new Project(name, version, configVersion, scriptDir,
            List.of(),
            sourceRoots, resourceRoots, List.of(libDir),
            BuildLayout.under(buildDir),
            dependencies,
            Map.of(),
            new CompilerSettings(target, encoding, flags),
            new RuntimeSettings(mainClass, runtimeClasspath, jvmArgs, env, programArgs),
            new PackagingSettings(jarName, icon, nativeTargets, format));
    }

    public static void validateProject(Project project) throws KanvasProjectException {
        List<String> errors = new ArrayList<>();
        for (Path sourceRoot : project.sourceRoots())
            if (!Files.isDirectory(sourceRoot)) errors.add("Source directory not found: " + sourceRoot);
        for (Path entry : project.runtimeSettings().classpath())
            if (!Files.exists(entry)) errors.add("Classpath entry not found: " + entry);
        Path icon = project.packagingSettings().icon();
        if (icon != null && !Files.isRegularFile(icon)) errors.add("Icon not found: " + icon);
        if (!errors.isEmpty()) throw problems("Project '" + project.name() + "'", errors);
    }

    private static PACKAGE_FORMAT packageFormat(String value, List<String> errors) {
        if (value == null) return PACKAGE_FORMAT.THIN;
        try { return PACKAGE_FORMAT.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            errors.add("packaging.format must be one of \"thin\", \"fat\", \"jar-and-deps\" or \"launcher\", found \"" + value + "\"");
            return PACKAGE_FORMAT.THIN;
        }
    }

    private static boolean isSupportedCharset(String name) {
        try { return Charset.isSupported(name);
        } catch (IllegalCharsetNameException e) { return false; }
    }

    private static Path resolve(Path scriptDir, String path, String key, List<String> errors) {
        try { return scriptDir.resolve(path).normalize();
        } catch (InvalidPathException e) {
            errors.add(key + " contains an invalid path \"" + path + "\": " + e.getReason());
            return scriptDir;
        }
    }

    private static List<Path> resolveAll(Path scriptDir, List<String> paths, String key, List<String> errors) {
        List<Path> resolved = new ArrayList<>();
        for (String path : paths) resolved.add(resolve(scriptDir, path, key, errors));
        return resolved;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static KanvasProjectException problems(String subject, List<String> errors) {
        if (errors.size() == 1) return new KanvasProjectException(subject + ": " + errors.get(0));
        StringBuilder message = new StringBuilder(subject + " has " + errors.size() + " problems:");
        for (String error : errors) message.append("\n  - ").append(error);
        return new KanvasProjectException(message.toString());
    }

    private static final class Table {
        private final Map<String, Object> values;
        private final String prefix;
        private final List<String> errors;
        private final Set<String> known = new HashSet<>();

        public Table(Map<String, Object> values, String prefix, List<String> errors) {
            this.values = values;
            this.prefix = prefix;
            this.errors = errors;
        }

        public boolean has(String key) {
            known.add(key);
            return values.containsKey(key);
        }

        public void ignore(String key) { known.add(key); }

        public String string(String key, String fallback) {
            Object value = take(key);
            if (value == null) return fallback;
            if (value instanceof String s) return s;
            return mismatch(key, "a string", value, fallback);
        }

        public String requiredString(String key) {
            String value = string(key, null);
            if (!values.containsKey(key)) errors.add(path(key) + " is required");
            else if (value != null && value.isBlank()) errors.add(path(key) + " must not be empty");
            return value;
        }

        public int integer(String key, int fallback) {
            Object value = take(key);
            if (value == null) return fallback;
            try {
                if (value instanceof Long l) return Math.toIntExact(l);
                if (value instanceof String s && s.trim().matches("[+-]?\\d+")) return Integer.parseInt(s.trim());
            } catch (ArithmeticException | NumberFormatException e) { /* out of int range: reported below */ }
            return mismatch(key, "a whole number", value, fallback);
        }

        public List<String> strings(String key) { return strings(key, List.of()); }

        public List<String> strings(String key, List<String> fallback) {
            Object value = take(key);
            if (value == null) return fallback;
            if (!(value instanceof List<?> list)) return mismatch(key, "an array of strings", value, fallback);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof String s) result.add(s);
                else errors.add(path(key) + "[" + i + "] must be a string, found " + render(list.get(i)));
            }
            return result;
        }

        public List<String> requiredStrings(String key) {
            List<String> result = strings(key);
            if (!values.containsKey(key)) errors.add(path(key) + " is required");
            else if (values.get(key) instanceof List<?> list && list.isEmpty()) errors.add(path(key) + " must list at least one entry");
            return result;
        }

        public Map<String, String> stringMap(String key) {
            Object value = take(key);
            if (value == null) return Map.of();
            if (!(value instanceof Map<?, ?> map)) return mismatch(key, "a table of strings", value, Map.of());
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof String s) result.put(entry.getKey().toString(), s);
                else errors.add(path(key) + "." + entry.getKey() + " must be a string, found " + render(entry.getValue()));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        public Table table(String key) {
            Object value = take(key);
            if (value instanceof Map<?, ?> map) return new Table((Map<String, Object>) map, path(key), errors);
            if (value != null) mismatch(key, "a table", value, null);
            return new Table(Map.of(), path(key), errors);
        }

        public void reportUnknownKeys() {
            for (String key : values.keySet())
                if (!known.contains(key)) errors.add("Unknown key '" + path(key) + "'");
        }

        private Object take(String key) {
            known.add(key);
            return values.get(key);
        }

        private <T> T mismatch(String key, String expected, Object found, T fallback) {
            errors.add(path(key) + " must be " + expected + ", found " + render(found));
            return fallback;
        }

        private String path(String key) { return prefix.isEmpty() ? key : prefix + "." + key; }

        private static String render(Object value) {
            if (value instanceof String s) return "\"" + s + "\"";
            if (value instanceof Map<?, ?>) return "a table";
            return String.valueOf(value);
        }
    }
}
