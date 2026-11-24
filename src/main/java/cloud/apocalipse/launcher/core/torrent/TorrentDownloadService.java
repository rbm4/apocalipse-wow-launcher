package cloud.apocalipse.launcher.core.torrent;

import java.nio.file.Path;

public interface TorrentDownloadService {
    void download(Path torrentFile, Path destination, TorrentProgressListener listener) throws Exception;

    default void download(Path torrentFile, Path destination, Path stateDirectory, TorrentProgressListener listener) throws Exception {
        download(torrentFile, destination, listener);
    }

    void stop();
}
