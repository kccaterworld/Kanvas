package kanvas.runtime;

import kanvas.config.*;
import kanvas.gui.KanvasScript;
import kanvas.KanvasException;
import kanvas.builder.BuildManager;

import java.nio.file.*;
import java.io.*;

public class KanvasRunner {
    public static void run(Path configPath) throws KanvasException {
        BuildManager buildManager = new BuildManager(configPath);
        Config config = buildManager.getConfig();
        buildManager.build();
        String mainClass = config.getMainClass();
        if (mainClass == null || mainClass.isBlank())
            throw new KanvasException("No mainClass set in kanvas.toml. Please specify the main class to run.");

        String sketchClasses = config.getOutput().toPath().resolve("classes").toAbsolutePath().toString();
        String runtimeClasses = runtimeClasspath();
        String classpath = runtimeClasses.isEmpty()
            ? sketchClasses
            : sketchClasses + File.pathSeparator + runtimeClasses;

        // Allow short names like "Main" — resolve to kanvas.generated.Main
        String resolvedClass = mainClass.contains(".") ? mainClass : "kanvas.generated." + mainClass;

        int exitCode = -1;
        try {
            exitCode = new ProcessBuilder("java", "-cp", classpath, resolvedClass)
                .inheritIO().start().waitFor();
        } catch (IOException e) { throw new KanvasException("Failed to start the application process: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KanvasException("Application process was interrupted: " + e.getMessage(), e);
        }
        if (exitCode != 0) throw new KanvasException("Application exited with non-zero exit code: " + exitCode);
    }

    /** Build and launch without blocking — sketch runs alongside the caller. */
    public static Process launch(Path configPath) throws KanvasException {
        BuildManager buildManager = new BuildManager(configPath);
        Config config = buildManager.getConfig();
        buildManager.build();
        String mainClass = config.getMainClass();
        if (mainClass == null || mainClass.isBlank())
            throw new KanvasException("No mainClass set in kanvas.toml. Please specify the main class to run.");

        String sketchClasses = config.getOutput().toPath().resolve("classes").toAbsolutePath().toString();
        String runtimeClasses = runtimeClasspath();
        String classpath = runtimeClasses.isEmpty()
            ? sketchClasses
            : sketchClasses + File.pathSeparator + runtimeClasses;
        String resolvedClass = mainClass.contains(".") ? mainClass : "kanvas.generated." + mainClass;

        try {
            return new ProcessBuilder("java", "-cp", classpath, resolvedClass)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        } catch (IOException e) {
            throw new KanvasException("Failed to start the application process: " + e.getMessage(), e);
        }
    }

    // Finds the JAR or class directory that contains the Kanvas runtime classes.
    private static String runtimeClasspath() {
        try {
            java.net.URL location = KanvasScript.class.getProtectionDomain().getCodeSource().getLocation();
            return Path.of(location.toURI()).toAbsolutePath().toString();
        } catch (Exception e) {
            return "";
        }
    }
}
