package cloud.apocalipse.launcher.core.torrent;

public record TorrentProgress(
        TorrentState state,
        double progress,
        long downloadedBytes,
        long totalBytes,
        long downloadRateBytesPerSecond,
        long payloadDownloadRateBytesPerSecond,
        int peers,
        long payloadDownloadedBytes,
        long failedBytes,
        long redundantBytes,
        int seeds,
        int connections,
        long finishedBlocks
) {
}
