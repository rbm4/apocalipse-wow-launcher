package cloud.apocalipse.launcher;

import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class LauncherPreferences {
    private static final String SELECTED_GAME = "selected.game";

    private final Path preferencesPath;
    private final Properties properties = new Properties();

    public LauncherPreferences(Path appDataDirectory) {
        preferencesPath = appDataDirectory.resolve("launcher-preferences.properties");
        load();
    }

    public Optional<LauncherGame> selectedGame() {
        return LauncherGame.fromId(properties.getProperty(SELECTED_GAME, "").trim());
    }

    public void setSelectedGame(LauncherGame game) throws IOException {
        properties.setProperty(SELECTED_GAME, game.id());
        Files.createDirectories(preferencesPath.getParent());
        try (OutputStream out = Files.newOutputStream(preferencesPath)) {
            properties.store(out, "Apocalipse BR Launcher");
        }
    }

    private void load() {
        if (!Files.isRegularFile(preferencesPath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(preferencesPath)) {
            properties.load(in);
        } catch (IOException exc) {
            StartupLog.write("Could not load launcher preferences.", exc);
        }
    }
}
