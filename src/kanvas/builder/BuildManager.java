package kanvas.builder;

import kanvas.preprocess.KanvasPreprocessor;
import kanvas.project.*;
import kanvas.KanvasException;

import java.util.*;
import java.util.stream.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BuildManager {
    private static final int RUNTIME_RELEASE = 21;

    private final Project project;

    public BuildManager(Project project) {
        this.project = project;
    }

    public boolean buildIfNeeded() throws KanvasException {
        String fingerprint = fingerprint();
        if (Files.isDirectory(project.buildLayout().classes()) && fingerprint.equals(storedFingerprint())) return false;
        build(fingerprint);
        return true;
    }

    public void build() throws KanvasException { build(fingerprint()); }

    private void build(String fingerprint) throws KanvasException {
        if (!CompileTool.checkCanRun()) throw new MissingDependencyException("No Java compiler available. Make sure to run Kanvas with a JDK, not a JRE.");
        ProjectLoader.validateProject(project);
        Project.BuildLayout layout = project.buildLayout();
        Path fingerprintFile = fingerprintFile();
        try { Files.deleteIfExists(fingerprintFile);
        } catch (IOException e) { throw new KanvasException("Failed to delete " + fingerprintFile + ": " + e.getMessage(), e); }
        for (Path dir : List.of(layout.generated(), layout.classes(), layout.resources())) deleteDirectory(dir, "modules.buildDir");
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

        copyResources(layout.resources());
        try {
            Files.createDirectories(fingerprintFile.getParent());
            Files.writeString(fingerprintFile, fingerprint);
        } catch (IOException e) { throw new KanvasException("Failed to write " + fingerprintFile + ": " + e.getMessage(), e); }
    }

    // assets/textures/player.png turns into build/resources/textures/player.png, loaded as "textures/player.png".
    private void copyResources(Path target) throws KanvasException {
        Map<Path, Path> copied = new HashMap<>();
        for (Path root : project.resourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    Path relative = root.relativize(file), destination = target.resolve(relative.toString());
                    Path previous = copied.putIfAbsent(relative, file);
                    if (previous != null) throw new KanvasException("Two resource folders both contain " + relative.toString().replace(File.separatorChar, '/') + ": " + previous + " and " + file);
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            } catch (IOException | UncheckedIOException e) { throw new KanvasException("Failed to copy resources from " + root + ": " + e.getMessage(), e); }
        }
    }

    private String fingerprint() throws KanvasException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) { throw new KanvasException("SHA-256 is not available", e); }
        update(digest, "config", ProjectWriter.write(project).getBytes(StandardCharsets.UTF_8));
        try {
            for (Path root : project.sourceRoots()) hashTree(digest, "source", root);
            for (Path root : project.resourceRoots()) hashTree(digest, "resource", root);
            for (Path entry : compileClasspath()) hashTree(digest, "classpath", entry);
        } catch (IOException | UncheckedIOException e) { throw new KanvasException("Failed to read project files: " + e.getMessage(), e); }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hashTree(MessageDigest digest, String label, Path root) throws IOException {
        update(digest, label, root.toString().getBytes(StandardCharsets.UTF_8));
        if (Files.isRegularFile(root)) { update(digest, "file", Files.readAllBytes(root)); return; }
        if (!Files.isDirectory(root)) { update(digest, "missing", new byte[0]); return; }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                update(digest, "path", root.relativize(file).toString().replace(File.separatorChar, '/').getBytes(StandardCharsets.UTF_8));
                update(digest, "file", Files.readAllBytes(file));
            }
        }
    }

    private static void update(MessageDigest digest, String label, byte[] bytes) {
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private Path fingerprintFile() { return project.cacheDir().resolve("build.hash"); }

    private String storedFingerprint() {
        try { return Files.readString(fingerprintFile()).trim();
        } catch (IOException e) { return null; }
    }

    public Path packageProject() throws KanvasException {
        Project.PACKAGE_FORMAT format = project.packagingSettings().format();
        if (format != Project.PACKAGE_FORMAT.THIN)
            throw new KanvasException("packaging.format = \"" + format.name().toLowerCase(Locale.ROOT).replace('_', '-') + "\" is not supported yet. Only \"thin\" JARs can be packaged for now.");
        buildIfNeeded();

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

    // Deletes the build directory and the cache.
    public void clean() throws KanvasException {
        deleteDirectory(project.buildLayout().root(), "modules.buildDir");
        cleanCache();
    }

    public void cleanCache() throws KanvasException {
        deleteDirectory(project.cacheDir(), null);
    }

    private void deleteDirectory(Path dir, String setting) throws KanvasException {
        Path projectDir = project.scriptDir().toAbsolutePath().normalize();
        Path target = dir.toAbsolutePath().normalize();
        String refusal = null;
        if (target.equals(projectDir)) refusal = "it is the project directory";
        else if (!target.startsWith(projectDir)) refusal = "it is outside the project directory";
        else for (Path root : protectedRoots())
            if (root.toAbsolutePath().normalize().startsWith(target)) { refusal = "it contains " + projectDir.relativize(root.toAbsolutePath().normalize()); break; }
        if (refusal != null)
            throw new KanvasException("Refusing to delete " + target + " because " + refusal + "." + (setting == null ? "" : " Check " + setting + " in kanvas.toml."));

        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> files = Files.walk(target)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        } catch (IOException | UncheckedIOException e) { throw new KanvasException("Failed to delete " + target + ": " + e.getMessage(), e); }
    }

    private List<Path> protectedRoots() {
        List<Path> roots = new ArrayList<>(project.sourceRoots());
        roots.addAll(project.resourceRoots());
        roots.addAll(project.dependencyRoots());
        roots.add(project.scriptDir().resolve(ProjectLoader.CONFIG_FILE));
        return roots;
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
