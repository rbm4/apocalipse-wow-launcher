package cloud.apocalipse.wow.launcher.torrent.jlibtorrent;

import cloud.apocalipse.wow.launcher.torrent.TorrentDownloadService;
import cloud.apocalipse.wow.launcher.torrent.TorrentException;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgress;
import cloud.apocalipse.wow.launcher.torrent.TorrentProgressListener;
import cloud.apocalipse.wow.launcher.torrent.TorrentState;
import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.StorageMode;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.StateUpdateAlert;
import com.frostwire.jlibtorrent.alerts.TrackerErrorAlert;
import com.frostwire.jlibtorrent.alerts.TrackerReplyAlert;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class JlibtorrentDownloadService implements TorrentDownloadService {
    private static final String SESSION_STATE_FILE = "libtorrent-session.state";
    private static final long BLOCK_PROGRESS_EMIT_INTERVAL_NANOS = 500_000_000L;
    private static final long ESTIMATED_BLOCK_BYTES = 16L * 1024L;

    private final Object lock = new Object();
    private SessionManager session;
    private CountDownLatch activeFinished;
    private AtomicReference<Throwable> activeFailure;
    private final List<String> fixedPeerSpecs;

    public JlibtorrentDownloadService() {
        this("");
    }

    public JlibtorrentDownloadService(String fixedPeers) {
        this.fixedPeerSpecs = parseFixedPeerSpecs(fixedPeers);
    }

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
        AtomicReference<TorrentStatus> lastStatus = new AtomicReference<>();
        SessionManager activeSession = new SessionManager();
        synchronized (lock) {
            session = activeSession;
            activeFinished = finished;
            activeFailure = failure;
        }

        TorrentInfo torrentInfo = new TorrentInfo(torrentFile.toFile());
        long totalSize = Math.max(0, torrentInfo.totalSize());
        TorrentDiagnostics diagnostics = new TorrentDiagnostics(listener);
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
                    diagnostics.emitOnce("added", "Client download registered.");
                    listener.onProgress(new TorrentProgress(TorrentState.DOWNLOADING, 0, 0, totalSize, 0, 0, 0, 0, 0, 0, 0, 0, 0));
                    return;
                }
                if (type == AlertType.STATE_UPDATE) {
                    StateUpdateAlert stateUpdate = (StateUpdateAlert) alert;
                    if (!stateUpdate.status().isEmpty()) {
                        TorrentStatus status = stateUpdate.status().getFirst();
                        lastStatus.set(status);
                        diagnostics.reportStatus(status);
                        reportStatus(listener, status, 0, totalSize);
                    }
                    return;
                }
                if (type == AlertType.BLOCK_FINISHED) {
                    BlockProgressSnapshot blockProgress = diagnostics.reportBlockFinished((BlockFinishedAlert) alert);
                    TorrentStatus status = lastStatus.get();
                    if (status != null && status.progress() <= 0) {
                        if (hasVisibleTransferMetrics(status)) {
                            reportStatus(listener, status, blockProgress.finishedBlocks(), totalSize);
                        } else if (blockProgress.shouldReportProgress()) {
                            reportBlockProgress(listener, blockProgress, totalSize);
                        }
                    } else if (blockProgress.shouldReportProgress()) {
                        reportBlockProgress(listener, blockProgress, totalSize);
                    }
                    activeSession.postTorrentUpdates();
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
                    listener.onProgress(new TorrentProgress(TorrentState.COMPLETE, 1, totalSize, totalSize, 0, 0, 0, totalSize, 0, 0, 0, 0, 0));
                    finished.countDown();
                    return;
                }
                if (type == AlertType.TORRENT_ERROR) {
                    failure.compareAndSet(null, new TorrentException(alert.message()));
                    finished.countDown();
                }
            }
        });

        listener.onProgress(new TorrentProgress(TorrentState.STARTING, 0, 0, totalSize, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        try {
            activeSession.start(sessionParams(stateDirectory, listener));
            activeSession.startDht();
            diagnostics.emitOnce("diskPreparing", "Preparing disk allocation for client files...");
            List<TcpEndpoint> fixedPeers = resolveFixedPeers(listener);
            reportFixedPeers(fixedPeers, diagnostics);
            addTorrent(
                    activeSession,
                    torrentInfo,
                    destination.toFile(),
                    fixedPeers,
                    new com.frostwire.jlibtorrent.swig.torrent_flags_t()
            );
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

    private static void addTorrent(
            SessionManager session,
            TorrentInfo torrentInfo,
            File destination,
            List<TcpEndpoint> fixedPeers,
            com.frostwire.jlibtorrent.swig.torrent_flags_t flags
    ) {
        AddTorrentParams params = AddTorrentParams.createInstance();
        params.torrentInfo(torrentInfo);
        params.savePath(destination.getAbsolutePath());
        params.storageMode(StorageMode.STORAGE_MODE_SPARSE);
        if (fixedPeers != null && !fixedPeers.isEmpty()) {
            params.peers(fixedPeers);
        }
        params.flags(flags == null ? new com.frostwire.jlibtorrent.swig.torrent_flags_t() : flags);
        session.swig().async_add_torrent(params.swig());
    }

    private List<TcpEndpoint> resolveFixedPeers(TorrentProgressListener listener) {
        List<TcpEndpoint> peers = new ArrayList<>();
        for (String peerSpec : fixedPeerSpecs) {
            int separator = peerSpec.lastIndexOf(':');
            if (separator <= 0 || separator == peerSpec.length() - 1) {
                listener.onStatusMessage("Fixed seed peer was ignored because its address is invalid.");
                continue;
            }
            String host = peerSpec.substring(0, separator).trim();
            String portText = peerSpec.substring(separator + 1).trim();
            try {
                int port = Integer.parseInt(portText);
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    peers.add(new TcpEndpoint(address.getHostAddress(), port));
                }
            } catch (Exception exc) {
                listener.onStatusMessage("Fixed seed peer could not be resolved: " + peerSpec);
            }
        }
        return peers;
    }

    private static List<String> parseFixedPeerSpecs(String fixedPeers) {
        if (fixedPeers == null || fixedPeers.isBlank()) {
            return List.of();
        }
        List<String> peers = new ArrayList<>();
        for (String peer : fixedPeers.split("[,;\\s]+")) {
            String trimmed = peer.trim();
            if (!trimmed.isEmpty()) {
                peers.add(trimmed);
            }
        }
        return List.copyOf(peers);
    }

    private static void reportFixedPeers(List<TcpEndpoint> fixedPeers, TorrentDiagnostics diagnostics) {
        if (!fixedPeers.isEmpty()) {
            diagnostics.emitOnce("fixedPeers", "Using " + fixedPeers.size() + " fixed seed peer endpoint(s).");
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

    private static void forceAnnounce(TorrentHandle handle, TorrentDiagnostics diagnostics, boolean allTrackers) {
        if (handle == null) {
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

    private static void reportStatus(TorrentProgressListener listener, TorrentStatus status) {
        reportStatus(listener, status, 0, 0);
    }

    private static void reportStatus(TorrentProgressListener listener, TorrentStatus status, long finishedBlocks, long totalSize) {
        long totalBytes = firstPositive(status.totalWanted(), totalSize);
        long payloadDownloadedBytes = status.totalPayloadDownload();
        long downloadedBytes = firstPositive(status.totalDone(), payloadDownloadedBytes);
        listener.onProgress(new TorrentProgress(
                TorrentState.DOWNLOADING,
                clamp(status.progress()),
                downloadedBytes,
                totalBytes,
                status.downloadRate(),
                status.downloadPayloadRate(),
                status.numPeers(),
                payloadDownloadedBytes,
                status.totalFailedBytes(),
                status.totalRedundantBytes(),
                status.numSeeds(),
                status.numConnections(),
                finishedBlocks
        ));
    }

    private static void reportBlockProgress(TorrentProgressListener listener, BlockProgressSnapshot blockProgress, long totalSize) {
        long estimatedDownloadedBytes = totalSize > 0
                ? Math.min(blockProgress.estimatedDownloadedBytes(), totalSize)
                : blockProgress.estimatedDownloadedBytes();
        listener.onProgress(new TorrentProgress(
                TorrentState.DOWNLOADING,
                0,
                estimatedDownloadedBytes,
                totalSize,
                0,
                blockProgress.estimatedRateBytesPerSecond(),
                0,
                estimatedDownloadedBytes,
                0,
                0,
                0,
                0,
                blockProgress.finishedBlocks()
        ));
    }

    private static long firstPositive(long primary, long fallback) {
        return primary > 0 ? primary : Math.max(fallback, 0);
    }

    private static boolean hasVisibleTransferMetrics(TorrentStatus status) {
        return status.totalPayloadDownload() > 0
                || status.downloadRate() > 0
                || status.downloadPayloadRate() > 0;
    }

    private record BlockProgressSnapshot(
            long finishedBlocks,
            long estimatedDownloadedBytes,
            long estimatedRateBytesPerSecond,
            boolean shouldReportProgress
    ) {
    }

    private static final class TorrentDiagnostics {
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
        private long finishedBlocks;
        private long lastForceAnnounceNanos;
        private long firstBlockNanos;
        private long lastBlockProgressNanos;

        private TorrentDiagnostics(TorrentProgressListener listener) {
            this.listener = listener;
        }

        private void reportStatus(TorrentStatus status) {
            lastStatus = status;
            reportState(status.state());
            if (status.numPeers() > 0 && status.progress() <= 0) {
                emitOccasionally("peersWaiting", "Found peers. Waiting for downloadable pieces...");
            }
            if (visibleDownloadRate(status) > 0 && status.progress() <= 0) {
                emitOnce("rateBeforeProgress", "Receiving data. Progress will move after pieces verify.");
            }
            if (status.totalPayloadDownload() > 0 && status.progress() <= 0) {
                emitOccasionally("payloadBeforeProgress",
                        "Received " + formatBytes(status.totalPayloadDownload())
                                + " before verified progress, " + formatRate(visibleDownloadRate(status))
                                + ", " + status.numSeeds() + " seed" + (status.numSeeds() == 1 ? "" : "s")
                                + ", " + status.numConnections() + " connection" + (status.numConnections() == 1 ? "" : "s") + ".");
            }
        }

        private BlockProgressSnapshot reportBlockFinished(BlockFinishedAlert alert) {
            finishedBlocks++;
            long now = System.nanoTime();
            if (firstBlockNanos == 0L) {
                firstBlockNanos = now;
            }
            long estimatedDownloadedBytes = finishedBlocks * ESTIMATED_BLOCK_BYTES;
            long elapsedNanos = Math.max(BLOCK_PROGRESS_EMIT_INTERVAL_NANOS, now - firstBlockNanos);
            long estimatedRate = finishedBlocks <= 1
                    ? 0
                    : (long) (estimatedDownloadedBytes / (elapsedNanos / 1_000_000_000.0));
            boolean shouldReportProgress = lastBlockProgressNanos == 0L
                    || now - lastBlockProgressNanos >= BLOCK_PROGRESS_EMIT_INTERVAL_NANOS;
            if (shouldReportProgress) {
                lastBlockProgressNanos = now;
            }
            if (lastStatus == null || lastStatus.progress() <= 0) {
                emitOccasionally("blocksFinished",
                        "Downloaded " + finishedBlocks + " block" + (finishedBlocks == 1 ? "" : "s")
                                + " before verified piece progress.");
            }
            return new BlockProgressSnapshot(finishedBlocks, estimatedDownloadedBytes, estimatedRate, shouldReportProgress);
        }

        private void reportTorrentAdded(TorrentHandle handle) {
            try {
                emitOnce("trackersLoaded", "Loaded " + handle.trackers().size() + " tracker entries from torrent.");
            } catch (RuntimeException ignored) {
                // Tracker diagnostics should never block the download.
            }
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
                            + ", " + lastStatus.numSeeds() + " seed" + (lastStatus.numSeeds() == 1 ? "" : "s")
                            + ", " + lastStatus.numConnections() + " connection" + (lastStatus.numConnections() == 1 ? "" : "s")
                            + ", " + dhtNodes + " discovery nodes"
                            + ", " + formatRate(visibleDownloadRate(lastStatus))
                            + ", " + formatBytes(lastStatus.totalPayloadDownload()) + " received"
            );
            if (lastStatus.numPeers() == 0 && dhtNodes > 0) {
                emitOccasionally("noPeersYet", "Peer discovery is online, but no downloadable sources have connected yet.");
            }
            reportTrackerSummary();
        }

        private void maybeForceAnnounce(TorrentHandle handle) {
            if (lastStatus == null || lastStatus.progress() > 0 || handle == null) {
                return;
            }
            long now = System.nanoTime();
            if (lastForceAnnounceNanos != 0L && now - lastForceAnnounceNanos < TRACKER_SUMMARY_INTERVAL_NANOS) {
                return;
            }
            lastForceAnnounceNanos = now;
            forceAnnounce(handle, this, false);
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

        private static String formatBytes(long bytes) {
            if (bytes <= 0) {
                return "0 KB";
            }
            double kib = bytes / 1024.0;
            if (kib < 1024) {
                return String.format("%.0f KB", kib);
            }
            double mib = kib / 1024.0;
            if (mib < 1024) {
                return String.format("%.1f MB", mib);
            }
            return String.format("%.1f GB", mib / 1024.0);
        }

        private static long visibleDownloadRate(TorrentStatus status) {
            return Math.max(Math.max(status.downloadRate(), status.downloadPayloadRate()), 0);
        }
    }
}
