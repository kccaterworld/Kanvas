package kanvas.preprocess;

import kanvas.config.*;
import kanvas.KanvasException;

import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.*;

public class KanvasPreprocessor {
    private static final String GENERATED_PACKAGE = Preprocessor.DEFAULT_PACKAGE;

    public static List<String> preprocess(File configFile) throws KanvasException {
        Config config = ConfigLoader.loadConfig(configFile);
        Path generatedDir = config.getOutput().toPath()
            .resolve("generated")
            .resolve(GENERATED_PACKAGE.replace('.', File.separatorChar));
        List<File> kvsFiles = new ArrayList<>();
        for (File srcDir : config.getSourceDirectories()) {
            if (!(srcDir.exists() && srcDir.isDirectory())) throw new ConfigException("Source directory does not exist or is not a directory: " + srcDir.getPath());
            try (Stream<Path> paths = Files.walk(srcDir.toPath())) {
                kvsFiles.addAll(paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(".kvs"))
                    .collect(Collectors.toList()));
            } catch (Exception e) { throw new KanvasException("Error occurred while walking directory: " + srcDir.getPath(), e); }
        }
        List<String> generatedJavaFiles = new ArrayList<>();
        for (File kvsFile : kvsFiles) {
            String generatedJavaFile = new Preprocessor(kvsFile).transpile();
            generatedJavaFiles.add(generatedJavaFile);
            if (!generatedDir.toFile().exists()) {
                try { Files.createDirectories(generatedDir); }
                catch (Exception e) { throw new KanvasException("Error occurred while creating output directory: " + generatedDir, e); }
            }
            try {
                Files.writeString(generatedDir.resolve(getName(kvsFile)), generatedJavaFile);
            } catch (Exception e) { throw new KanvasException("Error occurred while writing generated file: " + generatedDir.resolve(getName(kvsFile)), e); }
        }
        return generatedJavaFiles;
    }

    private static String getName(File kvsFile) {
        return Preprocessor.classNameFor(kvsFile.getName().replaceFirst("[.][^.]+$", "")) + ".java";
    }
}
