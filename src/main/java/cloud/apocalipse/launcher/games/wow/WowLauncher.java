package cloud.apocalipse.launcher.games.wow;

import cloud.apocalipse.launcher.core.OperationProgress;
import cloud.apocalipse.launcher.core.torrent.TorrentDownloadService;
import cloud.apocalipse.launcher.games.wow.client.ClientInstallManager;
import cloud.apocalipse.launcher.games.wow.client.ClientInstallPaths;
import cloud.apocalipse.launcher.games.wow.community.NewsClient;
import cloud.apocalipse.launcher.games.wow.community.NewsPost;
import cloud.apocalipse.launcher.games.wow.community.RealmStatus;
import cloud.apocalipse.launcher.games.wow.community.RealmStatusClient;
import cloud.apocalipse.launcher.games.wow.config.LauncherDefaults;
import cloud.apocalipse.launcher.games.wow.launch.GameLauncher;
import cloud.apocalipse.launcher.games.wow.patch.PatchManager;
import cloud.apocalipse.launcher.games.wow.patch.PatchManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WowLauncher {
    private static final long MIN_CLIENT_DOWNLOAD_SPACE_BYTES = 17L * 1024L * 1024L * 1024L;

    private final LauncherDefaults defaults;
    private final PatchManager patchManager = new PatchManager();
    private final GameLauncher gameLauncher;
    private final ClientInstallManager clientInstallManager;
    private final RealmStatusClient realmStatusClient;
    private final NewsClient newsClient;

    public WowLauncher(LauncherDefaults defaults, TorrentDownloadService torrentDownloadService, Path torrentStateDirectory) {
        this.defaults = defaults;
        this.gameLauncher = new GameLauncher(defaults.wowExecutable());
        this.clientInstallManager = new ClientInstallManager(new ClientInstallPaths(), torrentDownloadService, torrentStateDirectory);
        this.realmStatusClient = new RealmStatusClient();
        this.newsClient = new NewsClient();
    }

    public boolean hasClientExecutable(Path directory) {
        return Files.isDirectory(directory) && Files.isRegularFile(directory.resolve(defaults.wowExecutable()));
    }

    public PatchManifest synchronize(Path directory, OperationProgress progress) throws IOException, InterruptedException {
        return patchManager.synchronize(directory, defaults.manifestUrl(), progress);
    }

    public Path defaultInstallDirectory() {
        return clientInstallManager.defaultInstallDirectory();
    }

    public InstallSpace checkInstallSpace(Path directory) throws IOException {
        return new InstallSpace(Files.getFileStore(directory).getUsableSpace(), MIN_CLIENT_DOWNLOAD_SPACE_BYTES);
    }

    public Path installClient(Path directory, OperationProgress progress) throws Exception {
        return clientInstallManager.installFromBundledTorrent(directory, progress);
    }

    public void cancelClientInstall() {
        clientInstallManager.cancelInstall();
    }

    public boolean isClientInstallCancelled(Throwable error) {
        while (error != null) {
            if ("Client download cancelled.".equals(error.getMessage())) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }

    public boolean isGameRunning() {
        return gameLauncher.isRunning();
    }

    public void launch(Path directory, List<String> arguments, Runnable onExit) throws IOException {
        gameLauncher.launch(directory, arguments, onExit);
    }

    public RealmStatus fetchRealmStatus() throws IOException, InterruptedException {
        return realmStatusClient.fetch(defaults.realmStatusUrl());
    }

    public List<NewsPost> fetchNews(int limit) throws IOException, InterruptedException {
        return newsClient.fetchLatest(defaults.newsUrl(), limit);
    }

    public record InstallSpace(long availableBytes, long requiredBytes) {
        public boolean isSufficient() {
            return availableBytes >= requiredBytes;
        }
    }
}
