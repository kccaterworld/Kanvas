package kanvas.builder;

import kanvas.preprocess.KanvasPreprocessor;
import kanvas.project.*;
import kanvas.KanvasException;

import java.util.*;
import java.util.stream.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

public class BuildManager {
    private static final int RUNTIME_RELEASE = 21;

    private final Project project;

    public BuildManager(Project project) {
        this.project = project;
    }

    public void build() throws KanvasException {
        if (!CompileTool.checkCanRun()) throw new MissingDependencyException("No Java compiler available. Make sure to run Kanvas with a JDK, not a JRE.");
        ProjectLoader.validateProject(project);
        Project.BuildLayout layout = project.buildLayout();
        for (Path dir : List.of(layout.generated(), layout.classes(), layout.resources(), layout.output())) {
            try { Files.createDirectories(dir);
            } catch (IOException e) { throw new KanvasException("Failed to create build directory " + dir + ": " + e.getMessage(), e); }
        }

        List<Path> generatedFiles = KanvasPreprocessor.preprocess(project),
            classpath = compileClasspath();
        int target = project.compilerSettings().target();
        if (!generatedFiles.isEmpty() && target < RUNTIME_RELEASE)
            throw new KanvasException("compiler.target = " + target + " is too old for Kanvas sketches. The Kanvas runtime needs Java " + RUNTIME_RELEASE + " or newer.");
        Path outputDir = layout.classes();

        List<Path> sources = new ArrayList<>(generatedFiles);
        for (Path srcDir : project.sourceRoots()) {
            try (Stream<Path> paths = Files.walk(srcDir)) {
                sources.addAll(paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList());
            } catch (IOException | UncheckedIOException e) { throw new KanvasException("Error occurred while walking directory: " + srcDir, e); }
        }

        try { CompileTool.compileAll(sources, outputDir, classpath, project.compilerSettings());
        } catch (MissingDependencyException e) { throw new KanvasException("Missing dependency: " + e.getMessage(), e);
        } catch (KanvasCompileException e) { throw new KanvasException("Failed to compile " + e.getMessage(), e);
        } catch (Exception e) { throw new KanvasException("Congrats, you found an unforseen error while compiling", e); }
    }

    public Path packageProject() throws KanvasException {
        Project.PACKAGE_FORMAT format = project.packagingSettings().format();
        if (format != Project.PACKAGE_FORMAT.THIN)
            throw new KanvasException("packaging.format = \"" + format.name().toLowerCase(Locale.ROOT).replace('_', '-') + "\" is not supported yet. Only \"thin\" JARs can be packaged for now.");
        build();

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Created-By"), "Kanvas");
        String mainClass = project.launchClass();
        if (mainClass != null) attributes.put(Attributes.Name.MAIN_CLASS, mainClass);

        Project.BuildLayout layout = project.buildLayout();
        Path jar = layout.output().resolve(project.packagingSettings().jarName());
        try { KanvasJarTool.createJar(jar, manifest, List.of(layout.classes(), layout.resources()));
        } catch (IOException e) { throw new KanvasException("Failed to create " + jar + ": " + e.getMessage(), e); }
        return jar;
    }

    public List<Path> compileClasspath() {
        List<Path> classpath = new ArrayList<>(kanvasRuntime());
        classpath.addAll(project.userClasspath());
        return classpath;
    }

    public static List<Path> kanvasRuntime() {
        try {
            return List.of(Path.of(BuildManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath());
        } catch (Exception e) { return List.of(); }
    }

    public Project getProject() { return project; }
}
