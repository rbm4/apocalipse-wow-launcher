package cloud.apocalipse.wow.launcher.torrent;

@FunctionalInterface
public interface TorrentProgressListener {
    void onProgress(TorrentProgress progress);

    default void onStatusMessage(String message) {
    }
}
