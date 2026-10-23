package kanvas.preprocess;

import kanvas.KanvasException;
import kanvas.project.KanvasProjectException;
import kanvas.project.Project;
import kanvas.preprocess.ast.KanvasSyntaxTree.CompilationUnit;
import kanvas.preprocess.source.PreprocessException;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class KanvasPreprocessor {

    public static List<Path> preprocess(Project project) throws KanvasException {
        Path projectDir = project.scriptDir();
        Charset encoding = Charset.forName(project.compilerSettings().encoding());
        Path generatedRoot = project.buildLayout().generated();
        Map<String, Path> written = new LinkedHashMap<>();
        List<Path> generatedJavaFiles = new ArrayList<>();
        for (Path srcDir : project.sourceRoots()) {
            if (!Files.isDirectory(srcDir)) throw new KanvasProjectException("Source directory does not exist or is not a directory: " + srcDir);
            for (Path kvsFile : kvsFiles(srcDir)) {
                String displayPath = displayPath(kvsFile, projectDir);
                String javaCode;
                CompilationUnit sketch = parse(kvsFile, displayPath, encoding);
                String packageName = packageFor(srcDir, kvsFile, sketch, displayPath);
                String className = classNameFor(kvsFile);
                String qualifiedName = packageName + "." + className;
                Path previous = written.putIfAbsent(qualifiedName, kvsFile);

                if (previous != null) throw new KanvasException(displayPath + " and " + displayPath(previous, projectDir) + " both generate " + qualifiedName + ". Rename one of them or give it a different package.");
                try { javaCode = Preprocessor.generate(sketch, packageName, className);
                } catch (PreprocessException e) { throw new KanvasException(displayPath + ":" + e.pos() + ": " + e.detail(), e); }
                Path target = generatedRoot.resolve(packageName.replace('.', File.separatorChar)).resolve(className + ".java");
                try {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, javaCode, encoding);
                } catch (IOException e) { throw new KanvasException("Error occurred while writing generated file: " + target, e); }
                generatedJavaFiles.add(target);
            }
        }
        return generatedJavaFiles;
    }

    public static String qualifiedNameFor(Path sourceRoot, Path kvsFile, Charset encoding) {
        String declared = null;
        try { declared = declaredPackage(Preprocessor.parse(Files.readString(kvsFile, encoding)));
        } catch (IOException e) { return null;
        } catch (PreprocessException e) { /* guess from folder, itll get caught anyways when build */ }
        String packageName = declared != null ? declared : folderPackage(sourceRoot, kvsFile);
        return (packageName == null || !SourceVersion.isName(packageName)) ? null : packageName + "." + classNameFor(kvsFile);
    }

    public static String classNameFor(Path kvsFile) {
        return Preprocessor.classNameFor(kvsFile.getFileName().toString().replaceFirst("[.][^.]+$", ""));
    }

    private static String packageFor(Path sourceRoot, Path kvsFile, CompilationUnit sketch, String displayPath) throws KanvasException {
        String declared = declaredPackage(sketch);
        if (declared != null) return declared;
        String packageName = folderPackage(sourceRoot, kvsFile);
        if (!SourceVersion.isName(packageName))
            throw new KanvasException(displayPath + ": the folder path '" + packageName.replace('.', '/') + "' is not a valid Java package name. "
                + "Rename the folder or add a package declaration to the file.");
        return packageName;
    }

    private static String declaredPackage(CompilationUnit sketch) {
        return sketch.packageDecl() == null ? null : sketch.packageDecl().name();
    }

    private static String folderPackage(Path sourceRoot, Path kvsFile) {
        Path folder = sourceRoot.relativize(kvsFile).getParent();
        if (folder == null) return Preprocessor.DEFAULT_PACKAGE;
        StringJoiner name = new StringJoiner(".");
        for (Path part : folder) name.add(part.toString());
        return name.toString();
    }

    private static List<Path> kvsFiles(Path srcDir) throws KanvasException {
        try (Stream<Path> paths = Files.walk(srcDir)) {
            return paths.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(".kvs")).sorted().collect(Collectors.toList());
        } catch (IOException | UncheckedIOException e) { throw new KanvasException("Error occurred while walking directory: " + srcDir, e); }
    }

    private static CompilationUnit parse(Path kvsFile, String displayPath, Charset encoding) throws KanvasException {
        String source;
        try { source = Files.readString(kvsFile, encoding);
        } catch (IOException e) { throw new KanvasException("Failed to read " + displayPath, e); }
        try { return Preprocessor.parse(source);
        } catch (PreprocessException e) { throw new KanvasException(displayPath + ":" + e.pos() + ": " + e.detail(), e); }
    }

    private static String displayPath(Path file, Path projectDir) {
        Path path = file.toAbsolutePath();
        if (projectDir != null && path.startsWith(projectDir)) path = projectDir.relativize(path);
        return path.toString().replace(File.separatorChar, '/');
    }
}
