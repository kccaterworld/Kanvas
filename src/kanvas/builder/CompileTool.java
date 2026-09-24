package kanvas.builder;

import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** CompileTool
 * The Kanvas compile manager.
 * Currently just a stub for BuildManager to call, eventually will wrap JavaCompiler and handle error reporting, etc.
 * Hopefully will also help with cross-compilation and other advanced features in the future.
 * I think there's graph theory involved in dependency resolution
 * fah
 */
public class CompileTool {
    public static void compile(Path source, Path outputDir) throws Exception { compile(source, outputDir, null); }
    public static void compile(Path source, Path outputDir, List<Path> classpath) throws Exception {
        compileAll(List.of(source), outputDir, classpath);
    }

    /**
     * Compiles several source files together in a single javac invocation.
     * This lets generated classes resolve each other (e.g. a main window
     * referencing a secondary window class from another .kvs file).
     */
    public static void compileAll(List<Path> sources, Path outputDir, List<Path> classpath) throws Exception {
        if (sources == null || sources.isEmpty()) return;
        if (!checkCanRun()) throw new KanvasCompileException("No Java compiler available. Make sure to run Kanvas with a JDK, not a JRE.");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(outputDir.toAbsolutePath().toString());
        if (classpath != null && !classpath.isEmpty()) {
            args.add("-cp");
            args.add(classpath.stream()
                .map(p -> p.toAbsolutePath().toString())
                .reduce((a, b) -> a + java.io.File.pathSeparator + b).get());
        }
        for (Path source : sources) args.add(source.toAbsolutePath().toString());
        boolean status = 0 == compiler.run(null, null, null, args.toArray(new String[0]));
        if (!status) throw new KanvasCompileException("Compilation failed for " + sources);
    }
    public static boolean checkCanRun() { return ToolProvider.getSystemJavaCompiler() != null; }

}
