package kanvas.runtime;

import kanvas.KanvasException;
import kanvas.builder.BuildManager;
import kanvas.project.Project;

import java.nio.file.*;
import java.io.*;
import java.util.*;

public class KanvasRunner {
    public static void run(Project project) throws KanvasException {
        int exitCode = -1;
        try { exitCode = buildCommand(project).inheritIO().start().waitFor();
        } catch (IOException e) { throw new KanvasException("Failed to start the application process: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KanvasException("Application process was interrupted: " + e.getMessage(), e);
        }
        if (exitCode != 0) throw new KanvasException("Application exited with non-zero exit code: " + exitCode);
    }

    public static Process launch(Project project) throws KanvasException {
        try { return buildCommand(project)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        } catch (IOException e) { throw new KanvasException("Failed to start the application process: " + e.getMessage(), e); }
    }

    // Launches what's already built (see BuildManager.buildIfNeeded):
    // java <jvmArgs> -Dkanvas.sketchPath=<project dir> -cp <classes;resources;classpath> <main class> <programArgs>
    private static ProcessBuilder buildCommand(Project project) throws KanvasException {
        Project.RuntimeSettings runtime = project.runtimeSettings();
        if (runtime.mainClass() == null) throw new KanvasException("No mainClass set in kanvas.toml. Please specify the main class to run.");
        Project.BuildLayout layout = project.buildLayout();
        if (!Files.isDirectory(layout.classes())) throw new KanvasException("The project hasn't been built yet. Run `kanvas build` first.");

        List<String> classpath = new ArrayList<>();
        classpath.add(layout.classes().toString());
        classpath.add(layout.resources().toString());
        for (Path entry : new BuildManager(project).runtimeClasspath()) classpath.add(entry.toString());

        List<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(runtime.jvmArgs());
        command.add("-Dkanvas.sketchPath=" + project.scriptDir());
        if (Files.isDirectory(layout.natives())) command.add("-Djava.library.path=" + layout.natives());
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(project.launchClass());
        command.addAll(runtime.programArgs());

        ProcessBuilder process = new ProcessBuilder(command);
        process.environment().putAll(runtime.envVars());
        return process;
    }
}
