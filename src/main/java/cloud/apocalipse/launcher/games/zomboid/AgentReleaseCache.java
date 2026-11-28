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
    private static final Pattern SHA256 = Pattern.compile("sha256:([a-f0-9]{64})");

    public enum Agent {
        APOCBR("ApocBR", "rbm4/apocalipsebr-zomboid-patches", "apocbr-agent.jar",
                "cloud.apocalipse.pz.agent.ApocBrAgent"),
        ZOMBIE_BUDDY("Apocalipse [BR] ZombieBuddy", "ApocalipseBr/ZombieBuddy", "ZombieBuddy.jar",
                "me.zed_0xff.zombie_buddy.Agent");

        private final String name;
        private final String repository;
        private final String assetName;
        private final String premain;

        Agent(String name, String repository, String assetName, String premain) {
            this.name = name;
            this.repository = repository;
            this.assetName = assetName;
            this.premain = premain;
        }

        public String displayName() {
            return name;
        }
    }

    public enum Status { CHECKING, DOWNLOADING, VERIFIED }
    public record Progress(Agent agent, Status status) {}

    private final Path directory;
    private final Agent agent;
    private final URI latest;
    private final URI downloads;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public AgentReleaseCache(Path directory, Agent agent) {
        this(directory, agent, URI.create("https://api.github.com/repos/" + agent.repository + "/releases/latest"),
                URI.create("https://github.com/" + agent.repository + "/releases/download/"));
    }

    AgentReleaseCache(Path directory, Agent agent, URI latest, URI downloads) {
        this.directory = directory;
        this.agent = agent;
        this.latest = latest;
        this.downloads = downloads;
    }

    public Path resolve(Consumer<Progress> progress) throws IOException, InterruptedException {
        progress.accept(new Progress(agent, Status.CHECKING));
        Asset asset = fetchLatest();
        String hash = asset.sha256(agent);
        String fileStem = agent.assetName.substring(0, agent.assetName.length() - ".jar".length());
        Path cached = directory.resolve(fileStem + "-" + hash + ".jar");
        if (matches(cached, asset, hash)) {
            validateAgent(cached);
            progress.accept(new Progress(agent, Status.VERIFIED));
            return cached;
        }

        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "agent-", ".part");
        try {
            progress.accept(new Progress(agent, Status.DOWNLOADING));
            HttpRequest request = HttpRequest.newBuilder(URI.create(asset.downloadUrl()))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            requireSuccess(response, agent.displayName() + " agent download");
            if (!matches(temporary, asset, hash)) {
                throw new IOException("The downloaded " + agent.displayName()
                        + " agent does not match GitHub's SHA-256 or asset size.");
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
            progress.accept(new Progress(agent, Status.VERIFIED));
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
        requireSuccess(response, agent.displayName() + " latest GitHub release request");

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
            if (asset != null && agent.assetName.equals(asset.name()) && "uploaded".equals(asset.state())) {
                if (asset.size() <= 0 || asset.downloadUrl() == null
                        || !asset.downloadUrl().startsWith(downloads.toString())
                        || !asset.downloadUrl().endsWith("/" + agent.assetName)) {
                    throw new IOException("GitHub returned invalid " + agent.displayName() + " agent asset metadata.");
                }
                return asset;
            }
        }
        throw new IOException("GitHub release " + release.tag() + " has no " + agent.assetName + " asset.");
    }

    private static boolean matches(Path file, Asset asset, String hash) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == asset.size() && FileHashes.sha256(file).equals(hash);
    }

    private void validateAgent(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            var manifest = jar.getManifest();
            if (manifest == null || !agent.premain.equals(manifest.getMainAttributes().getValue("Premain-Class"))
                    || jar.getJarEntry(agent.premain.replace('.', '/') + ".class") == null) {
                throw new IOException("The GitHub asset is missing the " + agent.displayName() + " Java agent entry point.");
            }
            if (agent == Agent.ZOMBIE_BUDDY
                    && (jar.getJarEntry("me/zed_0xff/zombie_buddy/LauncherModTemplate.class") == null
                        || jar.getJarEntry("me/zed_0xff/zombie_buddy/LauncherModTrust$Approval.class") == null)) {
                throw new IOException("A versão publicada do " + agent.displayName()
                        + " ainda não aceita a aprovação de mods pelo launcher.");
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
        String sha256(Agent agent) throws IOException {
            var matcher = SHA256.matcher(digest == null ? "" : digest);
            if (!matcher.matches()) {
                throw new IOException("GitHub did not provide a valid SHA-256 for the " + agent.displayName() + " agent.");
            }
            return matcher.group(1);
        }
    }
}
