package cloud.apocalipse.launcher.core.torrent;

public final class TorrentException extends Exception {
    public TorrentException(String message) {
        super(message);
    }

    public TorrentException(String message, Throwable cause) {
        super(message, cause);
    }
}
