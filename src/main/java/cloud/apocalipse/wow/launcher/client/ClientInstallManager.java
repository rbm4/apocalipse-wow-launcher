package cloud.apocalipse.wow.launcher.client;

import cloud.apocalipse.wow.launcher.PatchProgress;
import cloud.apocalipse.wow.launcher.torrent.TorrentDownloadService;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgress;
import cloud.apocalipse.wow.launcher.torrent.TorrentState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class ClientInstallManager {
    private static final String TORRENT_RESOURCE = "/WoWBrasil.torrent";
    private static final int WOW_EXE_SEARCH_DEPTH = 4;
    private static final long PROGRESS_EMIT_INTERVAL_NANOS = 1_000_000_000L;
    private static final int SPEED_BUCKET_BYTES_PER_SECOND = 64 * 1024;

    private final ClientInstallPaths paths;
    private final TorrentDownloadService torrentDownloadService;
    private volatile boolean cancelRequested;
    private volatile Path activeDownloadDestination;
    private long lastProgressEmitNanos;
    private int lastProgressPercent = -1;
    private long lastSpeedBucket = -1;
    private String lastProgressMessage = "";

    public ClientInstallManager(ClientInstallPaths paths, TorrentDownloadService torrentDownloadService) {
        this.paths = paths;
        this.torrentDownloadService = torrentDownloadService;
    }

    public Path installFromBundledTorrent(PatchProgress progress) throws Exception {
        cancelRequested = false;
        resetProgressThrottle();
        Path destination = paths.defaultClientDirectory();
        Path existing = findWowDirectory(destination);
        if (existing != null) {
            progress.message("Client already installed: " + existing);
            progress.progress(1);
            return existing;
        }

        progress.message("Preparing client download...");
        progress.progress(0);
        Files.createDirectories(destination);
        activeDownloadDestination = destination;
        Path torrentFile = copyBundledTorrentToTemp();
        try {
            torrentDownloadService.download(torrentFile, destination, torrentProgress -> reportTorrentProgress(progress, torrentProgress));
            checkCancelled();
        } catch (Exception exc) {
            if (cancelRequested) {
                throw new IOException("Client download cancelled.", exc);
            }
            throw exc;
        } finally {
            torrentDownloadService.stop();
            Files.deleteIfExists(torrentFile);
            if (cancelRequested) {
                progress.message("Cleaning cancelled client download...");
                deleteDownloadedAssets(destination);
            }
            activeDownloadDestination = null;
        }

        progress.message("Verifying client files...");
        progress.progress(0.98);
        Path wowDirectory = findWowDirectory(destination);
        if (wowDirectory == null) {
            throw new IOException("Downloaded client does not contain Wow.exe in " + destination);
        }
        if (!Files.isDirectory(wowDirectory.resolve("Data"))) {
            throw new IOException("Downloaded client Data folder was not found in " + wowDirectory);
        }
        progress.message("Client download complete: " + wowDirectory);
        progress.progress(1);
        return wowDirectory;
    }

    public void cancelInstall() {
        cancelRequested = true;
        torrentDownloadService.stop();
    }

    private Path copyBundledTorrentToTemp() throws IOException {
        Path torrentFile = Files.createTempFile("apocalipse-wow-client", ".torrent");
        try (InputStream in = ClientInstallManager.class.getResourceAsStream(TORRENT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled torrent resource was not found: " + TORRENT_RESOURCE);
            }
            Files.copy(in, torrentFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return torrentFile;
    }

    private void reportTorrentProgress(PatchProgress progress, TorrentProgress torrentProgress) {
        String message = switch (torrentProgress.state()) {
            case STARTING -> "Starting client download...";
            case DOWNLOADING -> torrentProgress.progress() <= 0
                    ? "Download initializing..."
                    : "Downloading client... " + formatPercent(torrentProgress.progress())
                    + " at " + formatRate(torrentProgress.downloadRateBytesPerSecond());
            case VERIFYING -> "Verifying client download...";
            case COMPLETE -> "Client torrent download complete.";
        };
        double scaledProgress = Math.max(0, Math.min(0.97, torrentProgress.progress() * 0.97));
        if (!shouldEmitProgress(torrentProgress, message)) {
            return;
        }
        progress.progress(scaledProgress);
        progress.message(message);
    }

    private boolean shouldEmitProgress(TorrentProgress torrentProgress, String message) {
        if (torrentProgress.state() != TorrentState.DOWNLOADING) {
            lastProgressMessage = message;
            lastProgressEmitNanos = System.nanoTime();
            return true;
        }

        long now = System.nanoTime();
        int percent = (int) Math.floor(torrentProgress.progress() * 100);
        long speedBucket = Math.max(0, torrentProgress.downloadRateBytesPerSecond()) / SPEED_BUCKET_BYTES_PER_SECOND;
        boolean changed = percent != lastProgressPercent
                || speedBucket != lastSpeedBucket
                || !message.equals(lastProgressMessage);
        boolean intervalElapsed = now - lastProgressEmitNanos >= PROGRESS_EMIT_INTERVAL_NANOS;
        if (!changed && !intervalElapsed) {
            return false;
        }

        lastProgressPercent = percent;
        lastSpeedBucket = speedBucket;
        lastProgressMessage = message;
        lastProgressEmitNanos = now;
        return true;
    }

    private Path findWowDirectory(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return null;
        }
        if (Files.isRegularFile(root.resolve("Wow.exe"))) {
            return root;
        }
        try (var stream = Files.walk(root, WOW_EXE_SEARCH_DEPTH)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equalsIgnoreCase("Wow.exe"))
                    .map(Path::getParent)
                    .min(Comparator.comparingInt(path -> path.getNameCount()))
                    .orElse(null);
        }
    }

    private void checkCancelled() throws IOException {
        if (cancelRequested || Thread.currentThread().isInterrupted()) {
            cancelRequested = true;
            throw new IOException("Client download cancelled.");
        }
    }

    private void deleteDownloadedAssets(Path destination) throws IOException {
        Path activeDestination = activeDownloadDestination;
        if (activeDestination == null || !activeDestination.equals(destination) || !Files.exists(destination)) {
            return;
        }
        try (var stream = Files.walk(destination)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String formatPercent(double value) {
        return Math.round(value * 100) + "%";
    }

    private void resetProgressThrottle() {
        lastProgressEmitNanos = 0;
        lastProgressPercent = -1;
        lastSpeedBucket = -1;
        lastProgressMessage = "";
    }

    private static String formatRate(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0 KB/s";
        }
        double kib = bytesPerSecond / 1024.0;
        if (kib < 1024) {
            return String.format("%.0f KB/s", kib);
        }
        return String.format("%.1f MB/s", kib / 1024.0);
    }
}
