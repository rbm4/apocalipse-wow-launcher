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
import java.util.HashSet;
import java.util.Set;

public final class ClientInstallManager {
    private static final String TORRENT_RESOURCE = "/WoWBrasil.torrent";
    private static final int WOW_EXE_SEARCH_DEPTH = 4;
    private static final long PROGRESS_EMIT_INTERVAL_NANOS = 1_000_000_000L;
    private static final int SPEED_BUCKET_BYTES_PER_SECOND = 64 * 1024;
    private static final double DOWNLOAD_PHASE_WEIGHT = 0.94;
    private static final double VERIFY_PHASE_START = 0.94;
    private static final double VERIFY_WOW_EXE_FOUND = 0.97;
    private static final double VERIFY_DATA_FOUND = 0.99;

    private final ClientInstallPaths paths;
    private final TorrentDownloadService torrentDownloadService;
    private final Path torrentStateDirectory;
    private volatile boolean cancelRequested;
    private volatile Path activeDownloadDestination;
    private volatile Path warmupTorrentFile;
    private Set<Path> preexistingDownloadPaths = Set.of();
    private long lastProgressEmitNanos;
    private int lastProgressPercent = -1;
    private long lastSpeedBucket = -1;
    private String lastProgressMessage = "";

    public ClientInstallManager(ClientInstallPaths paths, TorrentDownloadService torrentDownloadService, Path torrentStateDirectory) {
        this.paths = paths;
        this.torrentDownloadService = torrentDownloadService;
        this.torrentStateDirectory = torrentStateDirectory.toAbsolutePath().normalize();
    }

    public Path installFromBundledTorrent(PatchProgress progress) throws Exception {
        return installFromBundledTorrent(paths.defaultClientDirectory(), progress);
    }

    public Path installFromBundledTorrent(Path destination, PatchProgress progress) throws Exception {
        cancelRequested = false;
        resetProgressThrottle();
        destination = destination.toAbsolutePath().normalize();
        Path existing = findWowDirectory(destination);
        if (existing != null) {
            progress.message("Client already installed: " + existing);
            progress.progress(1);
            return existing;
        }

        progress.message("Preparing client download...");
        progress.progress(0);
        Files.createDirectories(destination);
        preexistingDownloadPaths = snapshotExistingPaths(destination);
        activeDownloadDestination = destination;
        stopWarmup(false);
        Path torrentFile = copyBundledTorrentToTemp();
        try {
            torrentDownloadService.download(torrentFile, destination, torrentStateDirectory, new cloud.apocalipse.wow.launcher.torrent.TorrentProgressListener() {
                @Override
                public void onProgress(TorrentProgress torrentProgress) {
                    reportTorrentProgress(progress, torrentProgress);
                }

                @Override
                public void onStatusMessage(String message) {
                    progress.detail(message);
                }
            });
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
            preexistingDownloadPaths = Set.of();
        }

        progress.message("Verifying client files...");
        progress.progress(VERIFY_PHASE_START);
        Path wowDirectory = findWowDirectory(destination);
        if (wowDirectory == null) {
            throw new IOException("Downloaded client does not contain Wow.exe in " + destination);
        }
        progress.progress(VERIFY_WOW_EXE_FOUND);
        if (!Files.isDirectory(wowDirectory.resolve("Data"))) {
            throw new IOException("Downloaded client Data folder was not found in " + wowDirectory);
        }
        progress.progress(VERIFY_DATA_FOUND);
        progress.message("Client download complete: " + wowDirectory);
        progress.progress(1);
        return wowDirectory;
    }

    public void cancelInstall() {
        cancelRequested = true;
        Thread thread = new Thread(torrentDownloadService::stop, "client-download-cancel");
        thread.setDaemon(true);
        thread.start();
    }

    public void startWarmup(PatchProgress progress) throws IOException {
        Path torrentFile = copyBundledTorrentToTemp();
        warmupTorrentFile = torrentFile;
        try {
            torrentDownloadService.startWarmup(torrentFile, torrentStateDirectory, new cloud.apocalipse.wow.launcher.torrent.TorrentProgressListener() {
                @Override
                public void onProgress(TorrentProgress torrentProgress) {
                }

                @Override
                public void onStatusMessage(String message) {
                    progress.detail(message);
                }
            });
        } catch (Exception exc) {
            Files.deleteIfExists(torrentFile);
            warmupTorrentFile = null;
            throw new IOException("Client download warm-up failed.", exc);
        }
    }

    public void stopWarmup() throws IOException {
        stopWarmup(true);
    }

    private void stopWarmup(boolean announceStop) throws IOException {
        torrentDownloadService.stopWarmup(announceStop);
        Path torrentFile = warmupTorrentFile;
        warmupTorrentFile = null;
        if (torrentFile != null) {
            Files.deleteIfExists(torrentFile);
        }
    }

    public Path defaultInstallDirectory() {
        return paths.defaultClientDirectory();
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
                    ? initializingMessage(torrentProgress)
                    : "Downloading client... " + formatPercent(displayProgress(torrentProgress))
                    + " at " + formatRate(visibleDownloadRate(torrentProgress));
            case VERIFYING -> "Verifying client download...";
            case COMPLETE -> "Client torrent download complete.";
        };
        double scaledProgress = Math.max(0, Math.min(DOWNLOAD_PHASE_WEIGHT, displayProgress(torrentProgress) * DOWNLOAD_PHASE_WEIGHT));
        progress.progress(scaledProgress);
        if (!shouldEmitProgress(torrentProgress, message)) {
            return;
        }
        progress.message(message);
    }

    private boolean shouldEmitProgress(TorrentProgress torrentProgress, String message) {
        if (torrentProgress.state() != TorrentState.DOWNLOADING) {
            lastProgressMessage = message;
            lastProgressEmitNanos = System.nanoTime();
            return true;
        }

        long now = System.nanoTime();
        int percent = (int) Math.floor(displayProgress(torrentProgress) * 100);
        long visibleRate = visibleDownloadRate(torrentProgress);
        long speedBucket = visibleRate / SPEED_BUCKET_BYTES_PER_SECOND;
        if (torrentProgress.progress() <= 0 && visibleRate <= 0) {
            speedBucket = Math.max(0, torrentProgress.payloadDownloadedBytes()) / (512L * 1024L);
        }
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
                Path relativePath = destination.relativize(path);
                if (!preexistingDownloadPaths.contains(relativePath)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Set<Path> snapshotExistingPaths(Path destination) throws IOException {
        if (!Files.exists(destination)) {
            return Set.of();
        }
        Set<Path> paths = new HashSet<>();
        try (var stream = Files.walk(destination)) {
            stream.forEach(path -> paths.add(destination.relativize(path)));
        }
        return paths;
    }

    private static String formatPercent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String initializingMessage(TorrentProgress torrentProgress) {
        long visibleRate = visibleDownloadRate(torrentProgress);
        if (visibleRate > 0 || torrentProgress.payloadDownloadedBytes() > 0) {
            return "Receiving client data... " + formatDownloadAmount(torrentProgress)
                    + " at " + formatRate(visibleRate);
        }
        return "Download initializing...";
    }

    private static String formatDownloadAmount(TorrentProgress torrentProgress) {
        long downloadedBytes = Math.max(torrentProgress.payloadDownloadedBytes(), torrentProgress.downloadedBytes());
        if (torrentProgress.totalBytes() > 0) {
            return formatBytes(downloadedBytes) + " / " + formatBytes(torrentProgress.totalBytes());
        }
        return formatBytes(downloadedBytes);
    }

    private static double displayProgress(TorrentProgress torrentProgress) {
        if (torrentProgress.totalBytes() > 0 && torrentProgress.payloadDownloadedBytes() > 0) {
            return Math.min(0.99, torrentProgress.payloadDownloadedBytes() / (double) torrentProgress.totalBytes());
        }
        if (torrentProgress.totalBytes() > 0 && torrentProgress.downloadedBytes() > 0) {
            return Math.min(0.99, torrentProgress.downloadedBytes() / (double) torrentProgress.totalBytes());
        }
        if (torrentProgress.progress() > 0) {
            return torrentProgress.progress();
        }
        if (torrentProgress.finishedBlocks() > 0) {
            return Math.min(0.005, torrentProgress.finishedBlocks() / 10_000.0);
        }
        return 0;
    }

    private static long visibleDownloadRate(TorrentProgress torrentProgress) {
        return Math.max(
                Math.max(torrentProgress.downloadRateBytesPerSecond(), torrentProgress.payloadDownloadRateBytesPerSecond()),
                0
        );
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

    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 KB";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format("%.0f KB", kib);
        }
        double mib = kib / 1024.0;
        if (mib < 1024) {
            return String.format("%.1f MB", mib);
        }
        return String.format("%.1f GB", mib / 1024.0);
    }
}
