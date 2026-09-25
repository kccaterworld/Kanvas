package kanvas.project;

import java.util.*;
import java.nio.file.Path;

@SuppressWarnings("unused")

public class Project {
    private String name;
    private int version;
    private int config_version;

    private List<Path> entry_points;
    private List<Path> source_roots;
    private List<Path> resource_roots;
    private List<Path> dependency_roots;
    private List<Path> output_roots;
    private Path generated_source_root;

    private record CompilerSettings(
        int target,
        String encoding,
        List<String> flags
    ) {}
    private record runtime_settings(
        String main_class,
        List<Path> classpath,
        List<String> jvm_args,
        Map<String, String> env_vars,
        List<String> program_args
    ) {}
    enum PACKAGE_FORMAT { THIN, FAT, JAR_AND_DEPS, LAUNCHER }
    private record packaging_settings(
        String jar_name,
        Path icon,
        List<String> native_targets,
        PACKAGE_FORMAT format
    ) {}

    private List<String> dependency_declarations;
    private Map<String, Profile> profiles;
}
