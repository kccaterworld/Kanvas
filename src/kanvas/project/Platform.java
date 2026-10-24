package kanvas.project;

import java.util.*;

public record Platform(String os, String arch) {
    public static final List<String> SYSTEMS = List.of("windows", "linux", "macos");
    public static final List<String> ARCHITECTURES = List.of("x64", "arm64", "x86");

    public static Platform parse(String id) {
        if (id == null) return null;
        String[] parts = id.split("-", 2);
        if (!SYSTEMS.contains(parts[0])) return null;
        if (parts.length == 1) return new Platform(parts[0], null);
        return ARCHITECTURES.contains(parts[1]) ? new Platform(parts[0], parts[1]) : null;
    }

    public static Platform current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String os = name.contains("win") ? "windows" : name.contains("mac") || name.contains("darwin") ? "macos" : "linux";
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return new Platform(os, (arch.equals("aarch64") || arch.equals("arm64")) ? "arm64"
            : ((arch.equals("x86") || arch.matches("i[3-6]86")) ? "x86" : "x64"));
    }

    public String id() { return (arch == null) ? os : os + "-" + arch; }

    public List<String> select(Collection<String> keys) {
        if (arch == null) return keys.stream().filter(key -> key.equals(os) || key.startsWith(os + "-")).sorted().toList();
        if (keys.contains(id())) return List.of(id());
        return keys.contains(os) ? List.of(os) : List.of();
    }

    @Override
    public String toString() { return id(); }
}
