package kanvas.dependency;

import kanvas.project.Dependency;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;

public record RemoteRepository(URI root) {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public URI url(Dependency.Maven dependency, String extension) {
        return root.resolve(MavenRepository.relativePath(dependency, extension));
    }

    public boolean download(HttpClient client, Dependency.Maven dependency, String extension, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path partial = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(url(dependency, extension)).timeout(TIMEOUT).header("User-Agent", "Kanvas").GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() == 404) return false;
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } finally {
            Files.deleteIfExists(partial);
        }
    }
}
