package cloud.apocalipse.launcher.infrastructure.update;

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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LauncherReleases {
    private static final String REPOSITORY = "/rbm4/apocalipse-wow-launcher";
    private static final URI LATEST = URI.create("https://api.github.com/repos" + REPOSITORY + "/releases/latest");
    private static final URI DOWNLOADS = URI.create("https://github.com" + REPOSITORY + "/releases/download/");
    private static final String TAG_PREFIX = "launcher-v";
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})");
    private static final Pattern DIGEST = Pattern.compile("sha256:([0-9a-f]{64})");
    private static final long MAX_MSI_SIZE = 512L * 1024 * 1024;

    private final Path directory;
    private final HttpClient client;
    private final URI latest;
    private final URI downloads;

    public LauncherReleases(Path directory) {
        this(directory, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build(), LATEST, DOWNLOADS);
    }

    LauncherReleases(Path directory, HttpClient client, URI latest, URI downloads) {
        this.directory = directory;
        this.client = client;
        this.latest = latest;
        this.downloads = downloads;
    }

    public Optional<Update> check(String installedVersion) throws IOException, InterruptedException {
        var installed = version(installedVersion);
        if (installed.isEmpty()) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder(latest)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(20)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response);

        Release release;
        try {
            release = new Gson().fromJson(response.body(), Release.class);
        } catch (JsonParseException ex) {
            throw new IOException("GitHub returned invalid launcher release metadata.", ex);
        }
        if (release == null || release.draft() || release.prerelease()
                || release.tag() == null || !release.tag().startsWith(TAG_PREFIX)) {
            return Optional.empty();
        }
        String available = release.tag().substring(TAG_PREFIX.length());
        var parsed = version(available);
        if (parsed.isEmpty() || parsed.get() <= installed.get()) {
            return Optional.empty();
        }

        String name = "ApocalipseWoW-" + available + ".msi";
        URI download = downloads.resolve(release.tag() + "/" + name);
        var assets = release.assets() == null ? List.<Asset>of() : release.assets().stream()
                .filter(Objects::nonNull).filter(asset -> name.equals(asset.name())).toList();
        if (assets.size() != 1) {
            return Optional.empty();
        }
        Asset asset = assets.getFirst();
        if (!"uploaded".equals(asset.state())) {
            return Optional.empty();
        }
        var digest = DIGEST.matcher(Objects.toString(asset.digest(), ""));
        if (asset.size() <= 0 || asset.size() > MAX_MSI_SIZE || !download.toString().equals(asset.downloadUrl())
                || !digest.matches()) {
            throw new IOException("GitHub returned invalid launcher installer metadata.");
        }
        var page = URI.create("https://github.com" + REPOSITORY + "/releases/tag/" + release.tag());
        return Optional.of(new Update(available, page, download, asset.size(), digest.group(1)));
    }

    public Path download(Update update) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        Path cached = directory.resolve("ApocalipseWoW-" + update.version() + "-" + update.sha256() + ".msi");
        if (matches(cached, update)) {
            return cached;
        }

        Path temporary = Files.createTempFile(directory, "launcher-", ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(update.download())
                    .timeout(Duration.ofMinutes(10)).build();
            var response = client.send(request, info ->
                    info.statusCode() == 200 && info.headers().firstValueAsLong("Content-Length")
                            .orElse(-1L) == update.size()
                            ? HttpResponse.BodySubscribers.ofFile(temporary)
                            : HttpResponse.BodySubscribers.replacing(temporary));
            requireSuccess(response);
            if (!matches(temporary, update)) {
                throw new IOException("The downloaded launcher MSI failed size or SHA-256 verification.");
            }
            try {
                Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                if (!matches(cached, update)) {
                    throw ex;
                }
            }
            return cached;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Optional<Long> version(String value) {
        var matcher = VERSION.matcher(Objects.toString(value, ""));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int build = Integer.parseInt(matcher.group(3));
        if (major > 255 || minor > 255 || build > 65535) {
            return Optional.empty();
        }
        return Optional.of((major * 256L + minor) * 65536L + build);
    }

    private static boolean matches(Path path, Update update) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) == update.size()
                && FileHashes.sha256(path).equals(update.sha256());
    }

    private static void requireSuccess(HttpResponse<?> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Launcher update request failed with HTTP " + response.statusCode() + ".");
        }
    }

    public record Update(String version, URI page, URI download, long size, String sha256) {
    }

    private record Release(@SerializedName("tag_name") String tag, boolean draft, boolean prerelease,
                           List<Asset> assets) {
    }

    private record Asset(String name, String state, long size, String digest,
                         @SerializedName("browser_download_url") String downloadUrl) {
    }
}
