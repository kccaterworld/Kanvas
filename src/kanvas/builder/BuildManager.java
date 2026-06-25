package kanvas.builder;

import kanvas.config.*;
import kanvas.preprocess.KanvasPreprocessor;
import kanvas.KanvasException;

import java.util.*;
import java.util.stream.*;
import java.io.*;
import java.nio.file.*;

public class BuildManager {
    private final Config config;
    private Path configPath;

    public BuildManager(Path configPath) throws KanvasException {
        this.configPath = configPath;
        this.config = ConfigLoader.loadConfig(configPath.toFile());
    }

    private void compileFile(Path file, Path outputDir, List<Path> classpath) throws KanvasException {
        try { CompileTool.compile(file, outputDir, classpath);
        } catch (MissingDependencyException e) { throw new KanvasException("Missing dependency for " + file + ": " + e.getMessage(), e);
        } catch (KanvasCompileException e) { throw new KanvasException("Failed to compile " + file + ": " + e.getMessage(), e);
        } catch (Exception e) { throw new KanvasException("Congrats, you found an unforseen error in " + file, e); }
    }

    public void build() throws KanvasException {
        if (!CompileTool.checkCanRun()) throw new MissingDependencyException("No Java compiler available. Make sure to run Kanvas with a JDK, not a JRE.");
        List<Path> generatedFiles = KanvasPreprocessor.preprocess(configPath.toFile()),
            classpath = runtimeClasspath();
        Path outputDir = config.getOutput().toPath().resolve("classes");
        List<File> srcDirs = config.getSourceDirectories(),
            javaFiles = new ArrayList<>();
        for (Path generatedFile : generatedFiles) compileFile(generatedFile, outputDir, classpath);
        for (File srcDir : srcDirs) {
            try (Stream<Path> paths = Files.walk(srcDir.toPath())) {
                javaFiles.addAll(paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(".java"))
                    .collect(Collectors.toList()));
            } catch (Exception e) { throw new KanvasException("Error occurred while walking directory: " + srcDir.getPath(), e); }
        }
        for (File javaFile : javaFiles) compileFile(javaFile.toPath(), outputDir, classpath);
    }

    private static List<Path> runtimeClasspath() {
        try {
            return List.of(Path.of(BuildManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath());
        } catch (Exception e) { return List.of(); }
    }

    public Config getConfig() { return config; }
}