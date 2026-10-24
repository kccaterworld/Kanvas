package kanvas.builder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;

public class KanvasJarTool {
    public static void createJar(Path jar, Manifest manifest, List<Path> roots) throws IOException {
        Map<String, Path> entries = new TreeMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> !path.equals(root)).toList()) {
                    String name = root.relativize(file).toString().replace('\\', '/') + (Files.isDirectory(file) ? "/" : "");
                    if (name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) continue;
                    Path previous = entries.putIfAbsent(name, file);
                    if (previous != null && !name.endsWith("/")) throw new IOException(name + " is in both " + root + " and " + previous.getParent());
                }
            } catch (UncheckedIOException e) { throw e.getCause(); }
        }

        Files.createDirectories(jar.toAbsolutePath().getParent());
        Path partial = jar.resolveSibling(jar.getFileName() + ".partial");
        try (OutputStream file = Files.newOutputStream(partial); JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(entry(JarFile.MANIFEST_NAME));
            manifest.write(out);
            out.closeEntry();
            for (Map.Entry<String, Path> entry : entries.entrySet()) {
                out.putNextEntry(entry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) Files.copy(entry.getValue(), out);
                out.closeEntry();
            }
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        Files.move(partial, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static JarEntry entry(String name) {
        JarEntry entry = new JarEntry(name);
        entry.setTime(315532800000L); // Static entry time, something about Maven standard
        return entry;
    }
}
