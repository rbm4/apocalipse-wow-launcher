package cloud.apocalipse.launcher;

import cloud.apocalipse.launcher.games.wow.WowLauncher;
import cloud.apocalipse.launcher.games.wow.config.LauncherConfig;
import cloud.apocalipse.launcher.games.wow.config.LauncherDefaults;
import cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher;
import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import cloud.apocalipse.launcher.infrastructure.torrent.JlibtorrentDownloadService;
import cloud.apocalipse.launcher.infrastructure.update.LauncherReleases;
import cloud.apocalipse.launcher.infrastructure.web.PersistentCookieStore;
import cloud.apocalipse.launcher.ui.LauncherView;
import javafx.application.Application;
import javafx.stage.Stage;

public final class LauncherApp extends Application {
    private final LauncherDefaults defaults = LauncherDefaults.load();
    private final LauncherConfig config = new LauncherConfig(defaults);
    private final WowLauncher wow = new WowLauncher(
            defaults,
            new JlibtorrentDownloadService(defaults.torrentFixedPeers()),
            config.appDataDirectory().resolve("torrent")
    );

    @Override
    public void start(Stage stage) {
        try {
            StartupLog.write("Starting launcher.", null);
            PersistentCookieStore.install(config.appDataDirectory().resolve("website-cookies.properties"));
            var appData = config.appDataDirectory();
            var agentCache = new AgentReleaseCache(appData.resolve("zomboid-agent"), AgentReleaseCache.Agent.APOCBR);
            var zombieBuddyCache = new AgentReleaseCache(appData.resolve("zombiebuddy-agent"),
                    AgentReleaseCache.Agent.ZOMBIE_BUDDY);
            new LauncherView(config, wow, new ProjectZomboidLauncher(appData, agentCache, zombieBuddyCache),
                    new LauncherReleases(appData.resolve("updates")),
                    getHostServices()::showDocument).show(stage);
        } catch (RuntimeException exc) {
            StartupLog.write("Launcher failed during JavaFX start().", exc);
            throw exc;
        }
    }
}
