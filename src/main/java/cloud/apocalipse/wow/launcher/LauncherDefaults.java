package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class LauncherDefaults {
    private static final String RESOURCE = "/launcher.properties";

    private final String manifestUrl;
    private final String wowExecutable;

    private LauncherDefaults(String manifestUrl, String wowExecutable) {
        this.manifestUrl = manifestUrl;
        this.wowExecutable = wowExecutable;
    }

    static LauncherDefaults load() {
        Properties properties = new Properties();
        try (InputStream in = LauncherDefaults.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // Defaults below keep the launcher usable even if resources are missing.
        }
        return new LauncherDefaults(
                properties.getProperty("manifest.url", "https://wow.apocalipse.cloud/patches/manifest.json"),
                properties.getProperty("wow.executable", "Wow.exe")
        );
    }

    String manifestUrl() {
        return manifestUrl;
    }

    String wowExecutable() {
        return wowExecutable;
    }
}
