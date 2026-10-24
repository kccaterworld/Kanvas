package kanvas.builder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class KanvasJarTool {
    private static final String SERVICES = "META-INF/services/";
    private static final Pattern DROPPED = Pattern.compile("(?i)META-INF/(INDEX\\.LIST|SIG-[^/]*|[^/]*\\.(SF|RSA|DSA|EC))|(META-INF/versions/\\d+/)?module-info\\.class");

    private interface Content { void writeTo(OutputStream out) throws IOException; }
    private record Source(String origin, Content content) {}

    public static void createJar(Path jar, Manifest manifest, List<Path> roots) throws IOException {
        createJar(jar, manifest, roots, List.of());
    }

    public static void createJar(Path jar, Manifest manifest, List<Path> roots, List<Path> merged) throws IOException {
        Map<String, Source> entries = new TreeMap<>();
        Map<String, List<Source>> services = new TreeMap<>();
        List<JarFile> open = new ArrayList<>();
        Manifest output = new Manifest(manifest);
        try {
            for (Path root : roots) addTree(root, false, entries, services);
            for (Path path : merged) {
                if (Files.isDirectory(path)) { addTree(path, true, entries, services); continue; }
                JarFile file = new JarFile(path.toFile());
                open.add(file);
                Manifest own = file.getManifest();
                if (own != null && "true".equalsIgnoreCase(own.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE)))
                    output.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
                for (JarEntry entry : Collections.list(file.entries())) {
                    if (entry.isDirectory() || DROPPED.matcher(entry.getName()).matches()) continue;
                    add(entry.getName(), new Source(path.toString(), out -> { try (InputStream in = file.getInputStream(entry)) { in.transferTo(out); } }), entries, services);
                }
            }
            write(jar, output, entries, services);
        } finally {
            for (JarFile file : open) file.close();
        }
    }

    private static void addTree(Path root, boolean mergedSource, Map<String, Source> entries, Map<String, List<Source>> services) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                if (mergedSource && DROPPED.matcher(name).matches()) continue;
                add(name, new Source(root.toString(), out -> Files.copy(file, out)), entries, services);
            }
        } catch (UncheckedIOException e) { throw e.getCause(); }
    }

    private static void add(String name, Source source, Map<String, Source> entries, Map<String, List<Source>> services) throws IOException {
        if (name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) return;
        if (name.startsWith(SERVICES)) { services.computeIfAbsent(name, key -> new ArrayList<>()).add(source); return; }
        Source previous = entries.putIfAbsent(name, source);
        if (previous != null) throw new IOException(name + " is in both " + previous.origin() + " and " + source.origin());
    }

    private static void write(Path jar, Manifest manifest, Map<String, Source> entries, Map<String, List<Source>> services) throws IOException {
        Map<String, Content> all = new TreeMap<>();
        entries.forEach((name, source) -> all.put(name, source.content()));
        services.forEach((name, sources) -> all.put(name, out -> {
            for (Source source : sources) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                source.content().writeTo(bytes);
                out.write(bytes.toByteArray());
                if (bytes.size() > 0 && bytes.toByteArray()[bytes.size() - 1] != '\n') out.write('\n');
            }
        }));
        Set<String> directories = new TreeSet<>();
        for (String name : all.keySet())
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) directories.add(name.substring(0, slash + 1));

        Files.createDirectories(jar.toAbsolutePath().getParent());
        Path partial = jar.resolveSibling(jar.getFileName() + ".partial");
        try (OutputStream file = Files.newOutputStream(partial); JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(entry(JarFile.MANIFEST_NAME));
            manifest.write(out);
            out.closeEntry();
            for (String directory : directories) {
                if (directory.equals("META-INF/")) continue; // written implicitly with the manifest
                out.putNextEntry(entry(directory));
                out.closeEntry();
            }
            for (Map.Entry<String, Content> entry : all.entrySet()) {
                out.putNextEntry(entry(entry.getKey()));
                entry.getValue().writeTo(out);
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
