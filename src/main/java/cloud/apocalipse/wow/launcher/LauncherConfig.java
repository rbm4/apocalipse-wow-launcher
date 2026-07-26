package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

final class LauncherConfig {
    private static final String WOW_DIR = "wow.dir";
    private static final String MANIFEST_URL = "manifest.url";

    private final Path configPath;
    private final LauncherDefaults defaults;
    private final Properties properties = new Properties();

    LauncherConfig(LauncherDefaults defaults) {
        this.defaults = defaults;
        this.configPath = appDataDir().resolve("launcher.properties");
        load();
    }

    Optional<Path> wowDirectory() {
        String value = properties.getProperty(WOW_DIR, "").trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(Path.of(value));
    }

    void setWowDirectory(Path wowDirectory) {
        properties.setProperty(WOW_DIR, wowDirectory.toAbsolutePath().normalize().toString());
    }

    String manifestUrl() {
        return properties.getProperty(MANIFEST_URL, defaults.manifestUrl()).trim();
    }

    void setManifestUrl(String manifestUrl) {
        properties.setProperty(MANIFEST_URL, manifestUrl.trim());
    }

    void save() throws IOException {
        Files.createDirectories(configPath.getParent());
        try (OutputStream out = Files.newOutputStream(configPath)) {
            properties.store(out, "Apocalipse WoW Launcher");
        }
    }

    private void load() {
        properties.setProperty(MANIFEST_URL, defaults.manifestUrl());
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        } catch (IOException ignored) {
            // A bad config file should not prevent the launcher from starting.
        }
    }

    private static Path appDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "ApocalipseWoWLauncher");
        }
        return Path.of(System.getProperty("user.home"), ".apocalipse-wow-launcher");
    }
}
