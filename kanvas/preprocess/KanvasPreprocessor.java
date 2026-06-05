package kanvas.preprocess;

import kanvas.config.Config;
import kanvas.config.ConfigException;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class KanvasPreprocessor {
    public List<File> preprocessProject(Config config) {
        List<File> generatedFiles = new ArrayList<>();
        if (config == null) throw new ConfigException("Config is required for preprocessing");

        Path generatedRoot = generatedRoot(config);
        Charset encoding = Charset.forName(config.getEncoding() == null ? "UTF-8" : config.getEncoding());
        Preprocessor preprocessor = new Preprocessor();
        List<File> srcDirs = config.getSourceDirectories();
        int kvsFileCount = (int)srcDirs.stream().filter(File::isDirectory).map(File::toPath)
                .flatMap(dirPath -> { try { return Files.walk(dirPath);  } catch (Exception e) { return java.util.stream.Stream.empty();  } })
                .filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".kvs"))
                .count();
        if ((kvsFileCount > 1) &&
            (srcDirs.stream().filter(File::isDirectory).map(File::toPath)
                .flatMap(dirPath -> { try { return Files.walk(dirPath); } catch (Exception e) { return java.util.stream.Stream.empty(); } })
                .filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals("Main.kvs"))
                .count() != 1)) throw new ConfigException("Multiple source directories with .kvs files found, but exactly one Main.kvs is required to determine the entry point. Please ensure there is exactly one Main.kvs file across all source directories.");
        if (kvsFileCount == 1 && srcDirs.stream().filter(File::isDirectory).map(File::toPath)
                .flatMap(dirPath -> { try { return Files.walk(dirPath); } catch (Exception e) { return java.util.stream.Stream.empty(); } })
                .filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals(config.getMainFile().toString()))
                .count() == 0) throw new ConfigException(String.format(".kvs file does not match specified main file. Expected: %s", config.getMainFile()));
        if (kvsFileCount == 0)return generatedFiles;

        try { Files.createDirectories(generatedRoot); }
        catch (IOException e) { throw new ConfigException("Failed to create generated source directory: " + generatedRoot, e); }

        for (File srcDirFile : srcDirs) {
            if (srcDirFile == null) continue;
            Path srcDir = srcDirFile.toPath();
            if (!Files.isDirectory(srcDir)) continue;
            try (var stream = Files.walk(srcDir)) {
                List<Path> kvsFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(KanvasPreprocessor::isKanvasSource)
                    .sorted()
                    .collect(Collectors.toList());
                for (Path kvsFile : kvsFiles) {
                    Path relative = srcDir.relativize(kvsFile);
                    String className = generatedClassName(kvsFile);
                    Path generatedFile = generatedRoot.resolve(relative).resolveSibling(className + ".java");
                    String source = Files.readString(kvsFile, encoding);
                    String generated = preprocessor.transpile(source, null, className);

                    if (generatedFile.getParent() != null) Files.createDirectories(generatedFile.getParent());
                    Files.writeString(generatedFile, generated, encoding);
                    generatedFiles.add(generatedFile.toFile());
                }
            } catch (IOException e) { throw new ConfigException("Failed to preprocess source directory: " + srcDir, e);
            } catch (RuntimeException e) { throw new ConfigException("Failed to preprocess source directory: " + srcDir + ": " + e.getMessage(), e); }
        }
        return generatedFiles;
    }

    private static Path generatedRoot(Config config) {
        File output = config.getOutput();
        if (output == null) return Paths.get("build", "generated");

        Path outputPath = output.toPath();
        Path buildDir = outputPath.getParent();
        if (buildDir == null) buildDir = outputPath;
        return buildDir.resolve("generated");
    }

    private static boolean isKanvasSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".kvs") || name.endsWith(".kanvas");
    }

    private static String generatedClassName(Path sourceFile) {
        String name = sourceFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        StringBuilder className = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (className.length() == 0 && Character.isDigit(c)) className.append('_');
                className.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else capitalizeNext = true;
        }
        if (className.length() == 0) className.append("Kanvas");
        className.append("_Generated");
        return className.toString();
    }
}
