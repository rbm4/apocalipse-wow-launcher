package cloud.apocalipse.wow.launcher.torrent.jlibtorrent;

import cloud.apocalipse.wow.launcher.torrent.TorrentDownloadService;
import cloud.apocalipse.wow.launcher.torrent.TorrentException;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgress;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgressListener;
import cloud.apocalipse.wow.launcher.torrent.TorrentState;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.StateUpdateAlert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class JlibtorrentDownloadService implements TorrentDownloadService {
    private final Object lock = new Object();
    private SessionManager session;
    private CountDownLatch activeFinished;
    private AtomicReference<Throwable> activeFailure;

    @Override
    public void download(Path torrentFile, Path destination, TorrentProgressListener listener) throws Exception {
        if (!Files.isRegularFile(torrentFile)) {
            throw new TorrentException("Torrent file was not found: " + torrentFile);
        }
        Files.createDirectories(destination);

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SessionManager activeSession = new SessionManager();
        synchronized (lock) {
            session = activeSession;
            activeFinished = finished;
            activeFailure = failure;
        }

        activeSession.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return null;
            }

            @Override
            public void alert(Alert<?> alert) {
                AlertType type = alert.type();
                if (type == AlertType.ADD_TORRENT) {
                    ((AddTorrentAlert) alert).handle().resume();
                    listener.onProgress(new TorrentProgress(TorrentState.DOWNLOADING, 0, 0, -1, 0, 0));
                    return;
                }
                if (type == AlertType.STATE_UPDATE) {
                    StateUpdateAlert stateUpdate = (StateUpdateAlert) alert;
                    if (!stateUpdate.status().isEmpty()) {
                        reportStatus(listener, stateUpdate.status().getFirst());
                    }
                    return;
                }
                if (type == AlertType.TORRENT_FINISHED) {
                    listener.onProgress(new TorrentProgress(TorrentState.COMPLETE, 1, -1, -1, 0, 0));
                    finished.countDown();
                    return;
                }
                if (type == AlertType.TORRENT_ERROR) {
                    failure.compareAndSet(null, new TorrentException(alert.message()));
                    finished.countDown();
                }
            }
        });

        listener.onProgress(new TorrentProgress(TorrentState.STARTING, 0, 0, -1, 0, 0));
        try {
            activeSession.start(sessionParams());
            activeSession.startDht();
            activeSession.download(new TorrentInfo(torrentFile.toFile()), destination.toFile());
            while (!finished.await(1, TimeUnit.SECONDS)) {
                activeSession.postTorrentUpdates();
                Throwable error = failure.get();
                if (error != null) {
                    throw new TorrentException("Torrent download failed.", error);
                }
            }
            Throwable error = failure.get();
            if (error != null) {
                throw new TorrentException("Torrent download failed.", error);
            }
        } finally {
            stopSession(activeSession, false);
            synchronized (lock) {
                if (session == activeSession) {
                    session = null;
                    activeFinished = null;
                    activeFailure = null;
                }
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (session != null) {
                if (activeFailure != null) {
                    activeFailure.compareAndSet(null, new TorrentException("Client download cancelled."));
                }
                if (activeFinished != null) {
                    activeFinished.countDown();
                }
                stopSession(session, true);
                session = null;
                activeFinished = null;
                activeFailure = null;
            }
        }
    }

    private static void stopSession(SessionManager session, boolean cancelling) {
        try {
            session.stop();
        } catch (RuntimeException exc) {
            if (!cancelling) {
                throw exc;
            }
        }
    }

    private static double clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static SessionParams sessionParams() {
        SettingsPack settings = new SettingsPack();
        settings.setEnableDht(true);
        settings.setEnableLsd(true);
        settings.setDhtBootstrapNodes("router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881");
        settings.connectionsLimit(200);
        settings.activeDownloads(1);
        return new SessionParams(settings);
    }

    private static void reportStatus(TorrentProgressListener listener, TorrentStatus status) {
        listener.onProgress(new TorrentProgress(
                TorrentState.DOWNLOADING,
                clamp(status.progress()),
                status.totalDone(),
                status.totalWanted(),
                status.downloadRate(),
                status.numPeers()
        ));
    }
}
