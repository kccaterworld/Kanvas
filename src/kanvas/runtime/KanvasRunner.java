package kanvas.runtime;

import kanvas.config.*;
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
        int exitCode = -1;
        try {
            exitCode = new ProcessBuilder(
                "java",
                "-cp", config.getOutput().toPath().resolve("classes").toAbsolutePath().toString(),
                mainClass
            ).inheritIO().start().waitFor();
        } catch (IOException e) { throw new KanvasException("Failed to start the application process: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KanvasException("Application process was interrupted: " + e.getMessage(), e);
        }
        if (exitCode != 0) throw new KanvasException("Application exited with non-zero exit code: " + exitCode);
    }
}
