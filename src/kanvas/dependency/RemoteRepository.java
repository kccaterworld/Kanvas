package kanvas.dependency;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;

public record RemoteRepository(URI root) {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public URI url(String relativePath) { return root.resolve(relativePath); }

    public boolean download(HttpClient client, String relativePath, Path target, Consumer<String> warn) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path partial = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            HttpResponse<Path> response = client.send(request(url(relativePath)), HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() == 404) return false;
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            verify(client, relativePath, partial, warn);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    public String fetch(HttpClient client, String relativePath) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request(url(relativePath)), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url(relativePath));
        return response.body();
    }

    private void verify(HttpClient client, String relativePath, Path file, Consumer<String> warn) throws IOException, InterruptedException {
        for (String algorithm : new String[] { "sha256", "sha1" }) {
            String published = fetch(client, relativePath + "." + algorithm);
            if (published == null) continue;
            String expected = published.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            String actual = hash(file, algorithm.equals("sha256") ? "SHA-256" : "SHA-1");
            if (!expected.equals(actual)) throw new IOException("checksum mismatch: " + url(relativePath) + "." + algorithm + " says " + expected + " but the file's is " + actual);
            return;
        }
        warn.accept("Warning: " + url(relativePath) + " has no .sha256 or .sha1 checksum, so it can't be verified. Its SHA-256 is recorded in kanvas.lock.");
    }

    public static String hash(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int read; (read = in.read(buffer)) > 0; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(algorithm + " is not available", e); }
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", "Kanvas").GET().build();
    }
}
