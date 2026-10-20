package kanvas.preprocess;

import kanvas.KanvasException;
import kanvas.project.KanvasProjectException;
import kanvas.project.Project;
import kanvas.preprocess.source.PreprocessException;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.*;

public class KanvasPreprocessor {
    private static final String GENERATED_PACKAGE = Preprocessor.DEFAULT_PACKAGE;

    public static List<Path> preprocess(Project project) throws KanvasException {
        Path projectDir = project.scriptDir();
        Charset encoding = Charset.forName(project.compilerSettings().encoding());
        Path generatedDir = project.buildLayout().generated()
            .resolve(GENERATED_PACKAGE.replace('.', File.separatorChar));
        List<File> kvsFiles = new ArrayList<>();
        for (Path srcDir : project.sourceRoots()) {
            if (!Files.isDirectory(srcDir)) throw new KanvasProjectException("Source directory does not exist or is not a directory: " + srcDir);
            try (Stream<Path> paths = Files.walk(srcDir)) {
                kvsFiles.addAll(paths.filter(Files::isRegularFile)
                    .map(Path::toFile).filter(file -> file.getName().endsWith(".kvs"))
                    .collect(Collectors.toList()));
            } catch (Exception e) { throw new KanvasException("Error occurred while walking directory: " + srcDir, e); }
        }
        List<Path> generatedJavaFiles = new ArrayList<>();
        for (File kvsFile : kvsFiles) {
            String className = Preprocessor.classNameFor(kvsFile.getName().replaceFirst("[.][^.]+$", ""));
            String generatedJavaFile = transpile(kvsFile, className, projectDir, encoding);
            if (!generatedDir.toFile().exists()) {
                try { Files.createDirectories(generatedDir); }
                catch (Exception e) { throw new KanvasException("Error occurred while creating output directory: " + generatedDir, e); }
            }
            Path target = generatedDir.resolve(className + ".java");
            try {
                Files.writeString(target, generatedJavaFile, encoding);
                generatedJavaFiles.add(target);
            } catch (Exception e) { throw new KanvasException("Error occurred while writing generated file: " + target, e); }
        }
        return generatedJavaFiles;
    }

    private static String transpile(File kvsFile, String className, Path projectDir, Charset encoding) throws KanvasException {
        String displayPath = displayPath(kvsFile, projectDir);
        String source;
        try { source = Files.readString(kvsFile.toPath(), encoding); }
        catch (Exception e) { throw new KanvasException("Failed to read " + displayPath, e); }
        try { return Preprocessor.transpile(source, GENERATED_PACKAGE, className); }
        catch (PreprocessException e) { throw new KanvasException(displayPath + ":" + e.pos() + ": " + e.detail(), e); }
    }

    private static String displayPath(File file, Path projectDir) {
        Path path = file.getAbsoluteFile().toPath();
        if (projectDir != null && path.startsWith(projectDir)) path = projectDir.relativize(path);
        return path.toString().replace(File.separatorChar, '/');
    }
}
