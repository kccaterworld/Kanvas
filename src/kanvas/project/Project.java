package kanvas.project;

import java.util.*;
import java.nio.file.Path;

public record Project(String name, String version, int configVersion,

        List<Path> entryPoints,
        List<Path> sourceRoots, List<Path> resourceRoots, List<Path> dependencyRoots,
        BuildLayout buildLayout,
        List<String> dependencyDeclarations,
        Map<String, Profile> profiles,

        CompilerSettings compilerSettings, RuntimeSettings runtimeSettings, PackagingSettings packagingSettings
) {
    public static final int DEFAULT_TARGET = 21;
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String DEFAULT_ROOT = "build";

    public Project {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (configVersion < 1) throw new IllegalArgumentException("configVersion must be positive, got " + configVersion);
        entryPoints = List.copyOf(entryPoints);
        sourceRoots = List.copyOf(sourceRoots);
        resourceRoots = List.copyOf(resourceRoots);
        dependencyRoots = List.copyOf(dependencyRoots);
        Objects.requireNonNull(buildLayout, "buildLayout");
        dependencyDeclarations = List.copyOf(dependencyDeclarations);
        profiles = Map.copyOf(profiles);
        Objects.requireNonNull(compilerSettings, "compilerSettings");
        Objects.requireNonNull(runtimeSettings, "runtimeSettings");
        Objects.requireNonNull(packagingSettings, "packagingSettings");
    }

    public record BuildLayout(Path root,
            Path generated, Path classes,
            Path resources, Path output) {
        public BuildLayout {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(generated, "generated");
            Objects.requireNonNull(classes, "classes");
            Objects.requireNonNull(resources, "resources");
            Objects.requireNonNull(output, "output");
        }
        public static BuildLayout standard(Path projectDir) { return under(projectDir.resolve(DEFAULT_ROOT)); }
        public static BuildLayout under(Path root) {
            return new BuildLayout(root,
                root.resolve("generated"),
                root.resolve("classes"),
                root.resolve("resources"),
                root.resolve("output"));
        }
    }

    public record CompilerSettings(int target,
            String encoding, List<String> flags) {
        public CompilerSettings {
            if (target < 1) throw new IllegalArgumentException("target must be positive, got " + target);
            Objects.requireNonNull(encoding, "encoding");
            flags = List.copyOf(flags);
        }
        public static CompilerSettings defaults() { return new CompilerSettings(DEFAULT_TARGET, DEFAULT_ENCODING, List.of()); }
    }

    // mainClass is null for projects with nothing to run, such as libraries
    public record RuntimeSettings(String mainClass, List<Path> classpath,
            List<String> jvmArgs, Map<String, String> envVars, List<String> programArgs) {
        public RuntimeSettings {
            classpath = List.copyOf(classpath);
            jvmArgs = List.copyOf(jvmArgs);
            envVars = Map.copyOf(envVars);
            programArgs = List.copyOf(programArgs);
        }
        public static RuntimeSettings of(String mainClass) { return new RuntimeSettings(mainClass, List.of(), List.of(), Map.of(), List.of()); }
    }

    public enum PACKAGE_FORMAT { THIN, FAT, JAR_AND_DEPS, LAUNCHER }
    public record PackagingSettings(String jarName, Path icon,
            List<String> nativeTargets, PACKAGE_FORMAT format) {
        public PackagingSettings {
            Objects.requireNonNull(jarName, "jarName");
            nativeTargets = List.copyOf(nativeTargets);
            Objects.requireNonNull(format, "format");
        }
        public static PackagingSettings defaults(String projectName) { return new PackagingSettings(projectName + ".jar", null, List.of(), PACKAGE_FORMAT.THIN); }
    }
}
