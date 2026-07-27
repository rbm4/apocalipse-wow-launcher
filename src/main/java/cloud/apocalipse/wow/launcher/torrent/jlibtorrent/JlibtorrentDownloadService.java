package cloud.apocalipse.wow.launcher.torrent.jlibtorrent;

import cloud.apocalipse.wow.launcher.torrent.TorrentDownloadService;
import cloud.apocalipse.wow.launcher.torrent.TorrentException;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgress;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgressListener;
import cloud.apocalipse.wow.launcher.torrent.TorrentState;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.StorageMode;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.StateUpdateAlert;
import com.frostwire.jlibtorrent.alerts.TrackerErrorAlert;
import com.frostwire.jlibtorrent.alerts.TrackerReplyAlert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class JlibtorrentDownloadService implements TorrentDownloadService {
    private static final String SESSION_STATE_FILE = "libtorrent-session.state";
    private static final String WARMUP_CACHE_DIRECTORY = "warmup-cache";
    private static final long WARMUP_POLL_INTERVAL_MILLIS = 5_000L;
    private static final long WARMUP_SAVE_INTERVAL_NANOS = 60_000_000_000L;

    private final Object lock = new Object();
    private final Object warmupLock = new Object();
    private SessionManager session;
    private CountDownLatch activeFinished;
    private AtomicReference<Throwable> activeFailure;
    private SessionManager warmupSession;
    private Thread warmupThread;
    private volatile boolean warmupStopRequested;
    private volatile boolean warmupAnnounceStop;

    @Override
    public void download(Path torrentFile, Path destination, TorrentProgressListener listener) throws Exception {
        download(torrentFile, destination, null, listener);
    }

    @Override
    public void download(Path torrentFile, Path destination, Path stateDirectory, TorrentProgressListener listener) throws Exception {
        if (!Files.isRegularFile(torrentFile)) {
            throw new TorrentException("Torrent file was not found: " + torrentFile);
        }
        Files.createDirectories(destination);

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<TorrentHandle> handle = new AtomicReference<>();
        SessionManager activeSession = new SessionManager();
        synchronized (lock) {
            session = activeSession;
            activeFinished = finished;
            activeFailure = failure;
        }

        WarmupDiagnostics diagnostics = new WarmupDiagnostics(listener);
        activeSession.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return null;
            }

            @Override
            public void alert(Alert<?> alert) {
                AlertType type = alert.type();
                if (type == AlertType.ADD_TORRENT) {
                    TorrentHandle torrentHandle = ((AddTorrentAlert) alert).handle();
                    handle.set(torrentHandle);
                    torrentHandle.resume();
                    diagnostics.reportTorrentAdded(torrentHandle);
                    diagnostics.emitOnce("added", "Client download registered. Warming up network...");
                    listener.onProgress(new TorrentProgress(TorrentState.DOWNLOADING, 0, 0, -1, 0, 0));
                    return;
                }
                if (type == AlertType.STATE_UPDATE) {
                    StateUpdateAlert stateUpdate = (StateUpdateAlert) alert;
                    if (!stateUpdate.status().isEmpty()) {
                        TorrentStatus status = stateUpdate.status().getFirst();
                        diagnostics.reportStatus(status);
                        reportStatus(listener, status);
                    }
                    return;
                }
                if (type == AlertType.LISTEN_SUCCEEDED) {
                    diagnostics.emitOnce("listening", "Network listener ready.");
                    return;
                }
                if (type == AlertType.DHT_BOOTSTRAP) {
                    diagnostics.emitOnce("dht", "Peer discovery network ready.");
                    return;
                }
                if (type == AlertType.TRACKER_REPLY) {
                    TrackerReplyAlert trackerReply = (TrackerReplyAlert) alert;
                    diagnostics.reportTrackerReply(trackerReply.numPeers());
                    return;
                }
                if (type == AlertType.TRACKER_ERROR) {
                    diagnostics.reportTrackerError((TrackerErrorAlert) alert);
                    return;
                }
                if (type == AlertType.PEER_CONNECT) {
                    diagnostics.emitOnce("peerConnect", "Connected to a download peer.");
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
            activeSession.start(sessionParams(stateDirectory, listener));
            activeSession.startDht();
            diagnostics.emitOnce("diskPreparing", "Preparing disk allocation for client files...");
            activeSession.download(new TorrentInfo(torrentFile.toFile()), destination.toFile());
            forceAnnounce(handle.get(), diagnostics, true);
            while (!finished.await(1, TimeUnit.SECONDS)) {
                activeSession.postTorrentUpdates();
                diagnostics.reportSession(activeSession);
                diagnostics.maybeForceAnnounce(handle.get());
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
            saveState(activeSession, stateDirectory, listener, true);
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
    public void startWarmup(Path torrentFile, Path stateDirectory, TorrentProgressListener listener) throws Exception {
        if (!Files.isRegularFile(torrentFile)) {
            throw new TorrentException("Torrent file was not found: " + torrentFile);
        }
        Files.createDirectories(stateDirectory);

        synchronized (warmupLock) {
            if (warmupSession != null && warmupSession.isRunning()) {
                return;
            }
            warmupStopRequested = false;
            warmupAnnounceStop = true;
            SessionManager activeWarmupSession = new SessionManager();
            AtomicReference<TorrentHandle> warmupHandle = new AtomicReference<>();
            warmupSession = activeWarmupSession;
            WarmupDiagnostics diagnostics = new WarmupDiagnostics(listener);
            activeWarmupSession.addListener(new AlertListener() {
                @Override
                public int[] types() {
                    return null;
                }

                @Override
                public void alert(Alert<?> alert) {
                    AlertType type = alert.type();
                    if (type == AlertType.ADD_TORRENT) {
                        TorrentHandle torrentHandle = ((AddTorrentAlert) alert).handle();
                        warmupHandle.set(torrentHandle);
                        torrentHandle.resume();
                        diagnostics.emitOnce("warmupTorrentAdded", "Client torrent registered for source discovery.");
                        forceAnnounce(torrentHandle, diagnostics, true);
                        return;
                    }
                    if (type == AlertType.STATE_UPDATE) {
                        StateUpdateAlert stateUpdate = (StateUpdateAlert) alert;
                        if (!stateUpdate.status().isEmpty()) {
                            diagnostics.reportStatus(stateUpdate.status().getFirst());
                        }
                        return;
                    }
                    if (type == AlertType.LISTEN_SUCCEEDED) {
                        diagnostics.emitOnce("listening", "Network listener ready.");
                        return;
                    }
                    if (type == AlertType.DHT_BOOTSTRAP) {
                        diagnostics.emitOnce("dht", "Peer discovery network ready.");
                        return;
                    }
                    if (type == AlertType.DHT_GET_PEERS) {
                        diagnostics.emitOnce("dhtLookup", "Peer discovery lookup is active for the client download.");
                        return;
                    }
                    if (type == AlertType.TRACKER_REPLY) {
                        TrackerReplyAlert trackerReply = (TrackerReplyAlert) alert;
                        diagnostics.reportTrackerReply(trackerReply.numPeers());
                        return;
                    }
                    if (type == AlertType.TRACKER_ERROR) {
                        diagnostics.reportTrackerError((TrackerErrorAlert) alert);
                        return;
                    }
                    if (type == AlertType.PEER_CONNECT) {
                        diagnostics.emitOnce("peerConnect", "Connected to a download peer.");
                        return;
                    }
                    if (type == AlertType.SESSION_ERROR) {
                        diagnostics.emitOnce("warmupError", "Peer discovery warm-up reported a network error.");
                    }
                }
            });

            warmupThread = new Thread(
                    () -> runWarmup(activeWarmupSession, torrentFile, stateDirectory, diagnostics, listener, warmupHandle),
                    "apocalipse-wow-torrent-warmup"
            );
            warmupThread.setDaemon(true);
            warmupThread.start();
        }
    }

    @Override
    public void stopWarmup() {
        stopWarmup(true);
    }

    @Override
    public void stopWarmup(boolean announceStop) {
        SessionManager activeWarmupSession;
        Thread activeWarmupThread;
        synchronized (warmupLock) {
            warmupStopRequested = true;
            warmupAnnounceStop = announceStop;
            activeWarmupSession = warmupSession;
            activeWarmupThread = warmupThread;
        }
        if (activeWarmupThread != null && activeWarmupThread != Thread.currentThread()) {
            activeWarmupThread.interrupt();
            try {
                activeWarmupThread.join(2_000);
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
            }
        }
        if (activeWarmupThread != null && activeWarmupThread.isAlive() && activeWarmupSession != null) {
            stopSession(activeWarmupSession, true);
        }
        synchronized (warmupLock) {
            if (warmupSession == activeWarmupSession) {
                warmupSession = null;
                warmupThread = null;
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

    private void runWarmup(
            SessionManager activeWarmupSession,
            Path torrentFile,
            Path stateDirectory,
            WarmupDiagnostics diagnostics,
            TorrentProgressListener listener,
            AtomicReference<TorrentHandle> warmupHandle
    ) {
        long lastSaveNanos = 0L;
        try {
            TorrentInfo torrentInfo = new TorrentInfo(torrentFile.toFile());
            diagnostics.reportTorrentInfo(torrentInfo);
            activeWarmupSession.start(sessionParams(stateDirectory, listener));
            activeWarmupSession.startDht();
            Path warmupCache = stateDirectory.resolve(WARMUP_CACHE_DIRECTORY);
            Files.createDirectories(warmupCache);
            activeWarmupSession.download(
                    torrentInfo,
                    warmupCache.toFile(),
                    null,
                    Priority.array(Priority.IGNORE, torrentInfo.numFiles()),
                    java.util.List.of(),
                    TorrentFlags.UPLOAD_MODE
            );
            activeWarmupSession.dhtGetPeers(torrentInfo.infoHashV1(), 1);
            diagnostics.emitOnce("warmupStarted", "Peer discovery warm-up started.");
            while (!warmupStopRequested) {
                activeWarmupSession.postSessionStats();
                activeWarmupSession.postDhtStats();
                activeWarmupSession.postTorrentUpdates();
                diagnostics.reportSession(activeWarmupSession);
                long now = System.nanoTime();
                if (lastSaveNanos == 0L || now - lastSaveNanos >= WARMUP_SAVE_INTERVAL_NANOS) {
                    saveState(activeWarmupSession, stateDirectory, listener, false);
                    lastSaveNanos = now;
                }
                activeWarmupSession.dhtGetPeers(torrentInfo.infoHashV1(), 1);
                forcePeerDiscovery(warmupHandle.get(), diagnostics);
                Thread.sleep(WARMUP_POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException | ExceptionInInitializerError exc) {
            listener.onStatusMessage("Peer discovery warm-up stopped after a network error.");
        } finally {
            boolean announceShutdown = warmupAnnounceStop;
            saveState(activeWarmupSession, stateDirectory, listener, announceShutdown);
            stopSession(activeWarmupSession, true);
            if (announceShutdown) {
                diagnostics.emitOnce("warmupStopped", "Peer discovery warm-up stopped.");
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

    private static SessionParams sessionParams(Path stateDirectory, TorrentProgressListener listener) {
        SessionParams params = readState(stateDirectory, listener);
        SettingsPack settings = params.getSettings();
        if (settings == null) {
            settings = new SettingsPack();
        }
        settings.setEnableDht(true);
        settings.setEnableLsd(true);
        settings.setDhtBootstrapNodes("dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,router.bt.ouinet.work:6881");
        settings.connectionsLimit(200);
        settings.activeDownloads(1);
        params.setSettings(settings);
        return params;
    }

    private static SessionParams readState(Path stateDirectory, TorrentProgressListener listener) {
        if (stateDirectory == null) {
            return new SessionParams();
        }
        Path stateFile = stateDirectory.resolve(SESSION_STATE_FILE);
        if (!Files.isRegularFile(stateFile)) {
            return new SessionParams();
        }
        try {
            listener.onStatusMessage("Loaded saved peer discovery state.");
            return new SessionParams(Files.readAllBytes(stateFile));
        } catch (Exception exc) {
            listener.onStatusMessage("Saved peer discovery state could not be loaded.");
            return new SessionParams();
        }
    }

    private static void saveState(SessionManager session, Path stateDirectory, TorrentProgressListener listener, boolean report) {
        if (stateDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(stateDirectory);
            Files.write(stateDirectory.resolve(SESSION_STATE_FILE), session.saveState());
            if (report) {
                listener.onStatusMessage("Saved peer discovery state.");
            }
        } catch (Exception exc) {
            if (report) {
                listener.onStatusMessage("Peer discovery state could not be saved.");
            }
        }
    }

    private static void forceAnnounce(TorrentHandle handle, WarmupDiagnostics diagnostics, boolean allTrackers) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        try {
            int trackers = handle.trackers().size();
            if (trackers == 0) {
                handle.forceReannounce();
            } else if (allTrackers) {
                for (int index = 0; index < trackers; index++) {
                    handle.forceReannounce(0, index);
                }
            } else {
                handle.forceReannounce();
            }
            handle.forceDHTAnnounce();
            diagnostics.emitOnce("forceAnnounce", "Requested immediate announce to all trackers and peer discovery.");
        } catch (RuntimeException exc) {
            diagnostics.emitOnce("forceAnnounceFailed", "Could not request immediate peer discovery announce.");
        }
    }

    private static void forcePeerDiscovery(TorrentHandle handle, WarmupDiagnostics diagnostics) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        try {
            handle.forceDHTAnnounce();
            diagnostics.emitOnce("forceDhtAnnounce", "Requested immediate peer discovery announce.");
        } catch (RuntimeException exc) {
            diagnostics.emitOnce("forceAnnounceFailed", "Could not request immediate peer discovery announce.");
        }
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

    private static final class WarmupDiagnostics {
        private static final long OCCASIONAL_INTERVAL_NANOS = 30_000_000_000L;
        private static final long TRACKER_SUMMARY_INTERVAL_NANOS = 120_000_000_000L;

        private final TorrentProgressListener listener;
        private final java.util.Set<String> emitted = new java.util.HashSet<>();
        private final java.util.Map<String, Long> lastOccasionalEmit = new java.util.HashMap<>();
        private TorrentStatus lastStatus;
        private TorrentStatus.State lastState;
        private int trackerErrors;
        private int trackerReplies;
        private int trackerPeers;
        private long lastForceAnnounceNanos;

        private WarmupDiagnostics(TorrentProgressListener listener) {
            this.listener = listener;
        }

        private void reportStatus(TorrentStatus status) {
            lastStatus = status;
            reportState(status.state());
            if (status.numPeers() > 0 && status.progress() <= 0) {
                emitOccasionally("peersWaiting", "Found peers. Waiting for downloadable pieces...");
            }
            if (status.downloadRate() > 0 && status.progress() <= 0) {
                emitOnce("rateBeforeProgress", "Receiving data. Progress will move after pieces verify.");
            }
        }

        private void reportTorrentAdded(TorrentHandle handle) {
            try {
                emitOnce("trackersLoaded", "Loaded " + handle.trackers().size() + " tracker entries from torrent.");
            } catch (RuntimeException ignored) {
                // Tracker diagnostics should never block the download.
            }
        }

        private void reportTorrentInfo(TorrentInfo torrentInfo) {
            try {
                emitOnce("trackersLoaded", "Loaded " + torrentInfo.trackers().size() + " tracker entries from torrent.");
            } catch (RuntimeException ignored) {
                // Tracker diagnostics should never block warm-up.
            }
            emitOnce("warmupTorrent", "Warming peer discovery for bundled client torrent.");
        }

        private void reportSession(SessionManager session) {
            if (lastStatus == null || lastStatus.progress() > 0) {
                return;
            }
            long dhtNodes = session.dhtNodes();
            if (dhtNodes > 0) {
                emitOccasionally("dhtNodes", "Peer discovery nodes: " + dhtNodes);
            }
            java.util.List<String> endpoints = session.listenEndpoints();
            if (!endpoints.isEmpty()) {
                emitOnce("listenEndpoint", "Listening for peer connections on " + String.join(", ", endpoints));
            }
            emitOccasionally(
                    "health",
                    "Download health: " + lastStatus.numPeers()
                            + " peer" + (lastStatus.numPeers() == 1 ? "" : "s")
                            + ", " + dhtNodes + " discovery nodes"
                            + ", " + formatRate(lastStatus.downloadRate())
            );
            if (lastStatus.numPeers() == 0 && dhtNodes > 0) {
                emitOccasionally("noPeersYet", "Peer discovery is online, but no downloadable sources have connected yet.");
            }
            reportTrackerSummary();
        }

        private void maybeForceAnnounce(TorrentHandle handle) {
            if (lastStatus == null || lastStatus.progress() > 0 || handle == null || !handle.isValid()) {
                return;
            }
            long now = System.nanoTime();
            if (lastForceAnnounceNanos != 0L && now - lastForceAnnounceNanos < TRACKER_SUMMARY_INTERVAL_NANOS) {
                return;
            }
            lastForceAnnounceNanos = now;
            forcePeerDiscovery(handle, this);
        }

        private void reportState(TorrentStatus.State state) {
            if (state != null && state != lastState) {
                lastState = state;
                emitOccasionally("state", "Download engine state: " + state.name());
            }
        }

        private void reportTrackerReply(int peers) {
            trackerReplies++;
            trackerPeers += Math.max(0, peers);
            if (peers > 0) {
                emitOnce("trackerPeers", "Tracker found " + peers + " peer" + (peers == 1 ? "." : "s."));
            } else {
                emitOccasionally("trackerNoPeers", "Tracker answered. Waiting for available peers...");
            }
        }

        private void reportTrackerError(TrackerErrorAlert error) {
            trackerErrors++;
            if (trackerErrors == 1) {
                listener.onStatusMessage("A tracker was unavailable. Trying other trackers and peer discovery...");
                return;
            }
            reportTrackerSummary();
        }

        private void reportTrackerSummary() {
            long now = System.nanoTime();
            long last = lastOccasionalEmit.getOrDefault("trackerSummary", 0L);
            if (last != 0L && now - last < TRACKER_SUMMARY_INTERVAL_NANOS) {
                return;
            }
            lastOccasionalEmit.put("trackerSummary", now);
            listener.onStatusMessage("Tracker health: "
                    + trackerReplies + " replies, "
                    + trackerPeers + " reported sources, "
                    + trackerErrors + " unavailable trackers.");
        }

        private void emitOnce(String key, String message) {
            if (emitted.add(key)) {
                listener.onStatusMessage(message);
            }
        }

        private void emitOccasionally(String key, String message) {
            long now = System.nanoTime();
            long last = lastOccasionalEmit.getOrDefault(key, 0L);
            if (last == 0L || now - last >= OCCASIONAL_INTERVAL_NANOS) {
                lastOccasionalEmit.put(key, now);
                listener.onStatusMessage(message);
            }
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
    }
}
