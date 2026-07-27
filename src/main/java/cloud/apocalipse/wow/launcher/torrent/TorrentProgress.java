package cloud.apocalipse.wow.launcher.torrent;

public record TorrentProgress(
        TorrentState state,
        double progress,
        long downloadedBytes,
        long totalBytes,
        long downloadRateBytesPerSecond,
        int peers
) {
}
