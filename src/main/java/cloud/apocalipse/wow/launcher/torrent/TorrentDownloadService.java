package cloud.apocalipse.wow.launcher.torrent;

import java.nio.file.Path;

public interface TorrentDownloadService {
    void download(Path torrentFile, Path destination, TorrentProgressListener listener) throws Exception;

    void stop();
}
