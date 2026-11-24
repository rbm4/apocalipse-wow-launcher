package cloud.apocalipse.launcher.core.torrent;

@FunctionalInterface
public interface TorrentProgressListener {
    void onProgress(TorrentProgress progress);

    default void onStatusMessage(String message) {
    }
}
