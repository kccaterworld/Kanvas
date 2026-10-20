package kanvas.builder;

import kanvas.preprocess.KanvasPreprocessor;
import kanvas.project.*;
import kanvas.KanvasException;

import java.util.*;
import java.util.stream.*;
import java.io.*;
import java.nio.file.*;

public class BuildManager {
    private static final int RUNTIME_RELEASE = 21;

    private final Project project;

    public BuildManager(Project project) {
        this.project = project;
    }

    private void compileFile(Path file, Path outputDir, List<Path> classpath) throws KanvasException {
        try { CompileTool.compile(file, outputDir, classpath, project.compilerSettings());
        } catch (MissingDependencyException e) { throw new KanvasException("Missing dependency for " + file + ": " + e.getMessage(), e);
        } catch (KanvasCompileException e) { throw new KanvasException("Failed to compile " + file + ": " + e.getMessage(), e);
        } catch (Exception e) { throw new KanvasException("Congrats, you found an unforseen error in " + file, e); }
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
        List<File> javaFiles = new ArrayList<>();

        try { CompileTool.compileAll(generatedFiles, outputDir, classpath, project.compilerSettings());
        } catch (MissingDependencyException e) { throw new KanvasException("Missing dependency for generated files: " + e.getMessage(), e);
        } catch (KanvasCompileException e) { throw new KanvasException("Failed to compile " + e.getMessage(), e);
        } catch (Exception e) { throw new KanvasException("Congrats, you found an unforseen error in generated files", e); }

        for (Path srcDir : project.sourceRoots()) {
            try (Stream<Path> paths = Files.walk(srcDir)) {
                javaFiles.addAll(paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(".java"))
                    .collect(Collectors.toList()));
            } catch (Exception e) { throw new KanvasException("Error occurred while walking directory: " + srcDir, e); }
        }
        for (File javaFile : javaFiles) compileFile(javaFile.toPath(), outputDir, classpath);
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
