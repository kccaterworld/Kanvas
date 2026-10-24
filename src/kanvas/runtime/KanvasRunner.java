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

    // build then assemble: java <jvmArgs> -Dkanvas.sketchPath=<project dir> -cp <classpath> <main class> <programArgs>
    private static ProcessBuilder buildCommand(Project project) throws KanvasException {
        BuildManager buildManager = new BuildManager(project);
        buildManager.build();
        Project.RuntimeSettings runtime = project.runtimeSettings();
        if (runtime.mainClass() == null) throw new KanvasException("No mainClass set in kanvas.toml. Please specify the main class to run.");

        List<String> classpath = new ArrayList<>();
        classpath.add(project.buildLayout().classes().toString());
        for (Path entry : buildManager.compileClasspath()) classpath.add(entry.toString());

        List<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(runtime.jvmArgs());
        command.add("-Dkanvas.sketchPath=" + project.scriptDir());
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(project.launchClass());
        command.addAll(runtime.programArgs());

        ProcessBuilder process = new ProcessBuilder(command);
        process.environment().putAll(runtime.envVars());
        return process;
    }
}
