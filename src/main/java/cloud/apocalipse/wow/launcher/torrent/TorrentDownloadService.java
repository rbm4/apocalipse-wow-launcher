package cloud.apocalipse.wow.launcher.torrent;

import java.nio.file.Path;

public interface TorrentDownloadService {
    void download(Path torrentFile, Path destination, TorrentProgressListener listener) throws Exception;

    default void download(Path torrentFile, Path destination, Path stateDirectory, TorrentProgressListener listener) throws Exception {
        download(torrentFile, destination, listener);
    }

    default void startWarmup(Path torrentFile, Path stateDirectory, TorrentProgressListener listener) throws Exception {
    }

    default void stopWarmup() {
    }

    default void stopWarmup(boolean announceStop) {
        stopWarmup();
    }

    void stop();
}
