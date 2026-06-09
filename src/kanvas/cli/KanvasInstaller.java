package kanvas.cli;

import kanvas.KanvasException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class KanvasInstaller {
    public static void install(String targetDir) throws KanvasException {
        Path jarPath = findJar();
        Path dir = targetDir != null
            ? Path.of(targetDir)
            : defaultInstallDir();

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        try {
            Files.createDirectories(dir);
            if (isWindows) installWindows(dir, jarPath);
            else installUnix(dir, jarPath);
        } catch (IOException e) {
            throw new KanvasException("Install failed: " + e.getMessage(), e);
        }

        System.out.println(Text.style("Installed kanvas to: " + dir.toAbsolutePath(), "green"));
        printPathInstructions(dir, isWindows);
    }

    private static void installWindows(Path dir, Path jarPath) throws IOException {
        Path bat = dir.resolve("kanvas.bat");
        String content = "@echo off\r\njava -jar \"" + jarPath.toAbsolutePath() + "\" %*\r\n";
        Files.writeString(bat, content);
        System.out.println(Text.style("Created: " + bat.toAbsolutePath(), "green"));
    }

    private static void installUnix(Path dir, Path jarPath) throws IOException {
        Path script = dir.resolve("kanvas");
        String content = "#!/usr/bin/env sh\njava -jar \"" + jarPath.toAbsolutePath() + "\" \"$@\"\n";
        Files.writeString(script, content);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
            ));
        } catch (UnsupportedOperationException ignored) {}
        System.out.println(Text.style("Created: " + script.toAbsolutePath(), "green"));
    }

    private static Path defaultInstallDir() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String base = localAppData != null ? localAppData : System.getProperty("user.home");
            return Path.of(base, "Programs", "Kanvas");
        }
        return Path.of(System.getProperty("user.home"), ".local", "bin");
    }

    private static Path findJar() throws KanvasException {
        try {
            java.net.URL location = KanvasInstaller.class.getProtectionDomain().getCodeSource().getLocation();
            return Path.of(location.toURI()).toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new KanvasException("Could not determine JAR location: " + e.getMessage(), e);
        }
    }

    private static void printPathInstructions(Path dir, boolean isWindows) {
        String abs = dir.toAbsolutePath().toString();
        System.out.println();
        if (isWindows) {
            System.out.println("To use 'kanvas' from any terminal, add the install directory to your PATH.");
            System.out.println("Run this once in PowerShell:");
            System.out.println();
            System.out.println("  [Environment]::SetEnvironmentVariable(\"PATH\", $env:PATH + \";\" + \"" + abs + "\", \"User\")");
            System.out.println();
            System.out.println("Then restart your terminal.");
        } else {
            System.out.println("To use 'kanvas' from any terminal, ensure " + abs + " is on your PATH.");
            System.out.println("Add this to ~/.bashrc or ~/.zshrc if needed:");
            System.out.println();
            System.out.println("  export PATH=\"$PATH:" + abs + "\"");
            System.out.println();
            System.out.println("Then run: source ~/.bashrc");
        }
    }
}
