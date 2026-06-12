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
        Path dir = (targetDir != null) ? Path.of(targetDir) : defaultInstallDir();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            Files.createDirectories(dir);
            if (isWindows) installWindows(dir, jarPath);
            else installUnix(dir, jarPath);
        } catch (IOException e) { throw new KanvasException("Install failed: " + e.getMessage(), e);
        }
        System.out.println(Text.style("Installed kanvas to: " + dir.toAbsolutePath(), "green"));
        printPathInstructions(dir, isWindows);
    }

    private static void installWindows(Path dir, Path jarPath) throws IOException {
        Path bat = dir.resolve("kanvas.bat");
        String content = "@echo off\r\njava --enable-native-access=ALL-UNNAMED -jar \"" + jarPath.toAbsolutePath() + "\" %*\r\n";
        if (Files.exists(bat) && Files.readString(bat).equals(content)) {
            System.out.println(Text.style("Already up to date: " + bat.toAbsolutePath(), "green"));
            return;
        }
        Files.writeString(bat, content);
        System.out.println(Text.style("Created: " + bat.toAbsolutePath(), "green"));
    }

    private static void installUnix(Path dir, Path jarPath) throws IOException {
        Path script = dir.resolve("kanvas");
        String content = "#!/usr/bin/env sh\njava --enable-native-access=ALL-UNNAMED -jar \"" + jarPath.toAbsolutePath() + "\" \"$@\"\n";
        if (Files.exists(script) && Files.readString(script).equals(content)) {
            System.out.println(Text.style("Already up to date: " + script.toAbsolutePath(), "green"));
            return;
        }
        Files.writeString(script, content);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
            ));
        } catch (UnsupportedOperationException e) { System.out.println(Text.style("Warning: Could not set executable permissions on " + script.toAbsolutePath() + ": " + e.getMessage(), "yellow"));
        }
        System.out.println(Text.style("Created: " + script.toAbsolutePath(), "green"));
    }

    private static Path defaultInstallDir() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            String localAppData = System.getenv("LOCALAPPDATA");
            return Path.of((localAppData != null) ? localAppData : System.getProperty("user.home"), "Programs", "Kanvas");
        }
        return Path.of(System.getProperty("user.home"), ".local", "bin");
    }

    private static Path findJar() throws KanvasException {
        try { return Path.of(KanvasInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        } catch (URISyntaxException e) { throw new KanvasException("Could not determine JAR location: " + e.getMessage(), e);
        }
    }

    private static void printPathInstructions(Path dir, boolean isWindows) {
        String abs = dir.toAbsolutePath().toString();
        System.out.println();
        if (isWindows) {
            System.out.printf("To use 'kanvas' from any terminal, add the install directory to your PATH.\n");
            System.out.printf("Run this once in PowerShell:\n\n");
            System.out.printf("  [Environment]::SetEnvironmentVariable(\"PATH\", $env:PATH + \";\" + \"" + abs + "\", \"User\")\n\n");
            System.out.printf("Then restart your terminal.\n");
        } else {
            System.out.printf("To use 'kanvas' from any terminal, ensure " + abs + " is on your PATH.\n");
            System.out.printf("Add this to ~/.bashrc or ~/.zshrc if needed:\n\n");
            System.out.printf("  export PATH=\"$PATH:" + abs + "\"\n\n");
            System.out.printf("Then run: source ~/.bashrc\n");
        }
    }
}
