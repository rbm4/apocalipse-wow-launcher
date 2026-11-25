package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.core.FileHashes;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class AgentReleaseCache {
    private static final String REPOSITORY = "/rbm4/apocalipsebr-zomboid-patches";
    private static final URI LATEST = URI.create("https://api.github.com/repos" + REPOSITORY + "/releases/latest");
    private static final URI DOWNLOADS = URI.create("https://github.com" + REPOSITORY + "/releases/download/");
    private static final String ASSET_NAME = "apocbr-agent.jar";
    private static final String PREMAIN = "cloud.apocalipse.pz.agent.ApocBrAgent";
    private static final Pattern SHA256 = Pattern.compile("sha256:([a-f0-9]{64})");

    public enum Status { CHECKING, DOWNLOADING, VERIFIED }

    private final Path directory;
    private final URI latest;
    private final URI downloads;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public AgentReleaseCache(Path directory) {
        this(directory, LATEST, DOWNLOADS);
    }

    AgentReleaseCache(Path directory, URI latest, URI downloads) {
        this.directory = directory;
        this.latest = latest;
        this.downloads = downloads;
    }

    public Path resolve(Consumer<Status> progress) throws IOException, InterruptedException {
        progress.accept(Status.CHECKING);
        Asset asset = fetchLatest();
        String hash = asset.sha256();
        Path cached = directory.resolve("apocbr-agent-" + hash + ".jar");
        if (matches(cached, asset, hash)) {
            validateAgent(cached);
            progress.accept(Status.VERIFIED);
            return cached;
        }

        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "agent-", ".part");
        try {
            progress.accept(Status.DOWNLOADING);
            HttpRequest request = HttpRequest.newBuilder(URI.create(asset.downloadUrl()))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            requireSuccess(response, "GitHub agent download");
            if (!matches(temporary, asset, hash)) {
                throw new IOException("The downloaded ApocBR agent does not match GitHub's SHA-256 or asset size.");
            }
            validateAgent(temporary);
            try {
                Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                // Another launcher may have verified and opened this same release while we downloaded it.
                if (!matches(cached, asset, hash)) {
                    throw ex;
                }
            }
            progress.accept(Status.VERIFIED);
            return cached;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Asset fetchLatest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(latest)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response, "GitHub latest release request");

        Release release;
        try {
            release = new Gson().fromJson(response.body(), Release.class);
        } catch (JsonParseException ex) {
            throw new IOException("GitHub returned invalid release metadata.", ex);
        }
        if (release == null || release.assets() == null) {
            throw new IOException("GitHub release metadata is missing its assets.");
        }
        for (Asset asset : release.assets()) {
            if (asset != null && ASSET_NAME.equals(asset.name()) && "uploaded".equals(asset.state())) {
                if (asset.size() <= 0 || asset.downloadUrl() == null
                        || !asset.downloadUrl().startsWith(downloads.toString())
                        || !asset.downloadUrl().endsWith("/" + ASSET_NAME)) {
                    throw new IOException("GitHub returned invalid ApocBR agent asset metadata.");
                }
                return asset;
            }
        }
        throw new IOException("GitHub release " + release.tag() + " has no " + ASSET_NAME + " asset.");
    }

    private static boolean matches(Path file, Asset asset, String hash) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == asset.size() && FileHashes.sha256(file).equals(hash);
    }

    private static void validateAgent(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            var manifest = jar.getManifest();
            if (manifest == null || !PREMAIN.equals(manifest.getMainAttributes().getValue("Premain-Class"))
                    || jar.getJarEntry(PREMAIN.replace('.', '/') + ".class") == null) {
                throw new IOException("The GitHub asset is missing the ApocBR Java agent entry point.");
            }
        }
    }

    private static void requireSuccess(HttpResponse<?> response, String operation) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException(operation + " failed with HTTP " + response.statusCode() + ".");
        }
    }

    private record Release(@SerializedName("tag_name") String tag, List<Asset> assets) {
    }

    private record Asset(String name, String state, long size, String digest,
                         @SerializedName("browser_download_url") String downloadUrl) {
        String sha256() throws IOException {
            var matcher = SHA256.matcher(digest == null ? "" : digest);
            if (!matcher.matches()) {
                throw new IOException("GitHub did not provide a valid SHA-256 for the ApocBR agent.");
            }
            return matcher.group(1);
        }
    }
}
