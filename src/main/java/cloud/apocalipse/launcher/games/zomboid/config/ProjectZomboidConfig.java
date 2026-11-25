package cloud.apocalipse.launcher.games.zomboid.config;

import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class ProjectZomboidConfig {
    private static final String GAME_DIRECTORY = "project-zomboid.dir";

    private final Path configPath;
    private final Properties properties = new Properties();

    public ProjectZomboidConfig(Path appDataDirectory) {
        configPath = appDataDirectory.resolve("project-zomboid.properties");
        load();
    }

    public Optional<Path> gameDirectory() {
        String value = properties.getProperty(GAME_DIRECTORY, "").trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(value));
        } catch (InvalidPathException exc) {
            StartupLog.write("Saved Project Zomboid directory is invalid.", exc);
            return Optional.empty();
        }
    }

    public void setGameDirectory(Path directory) {
        properties.setProperty(GAME_DIRECTORY, directory.toAbsolutePath().normalize().toString());
    }

    public void clearGameDirectory() {
        properties.remove(GAME_DIRECTORY);
    }

    public Set<ZomboidOptimization> optimizations() {
        var enabled = EnumSet.noneOf(ZomboidOptimization.class);
        for (ZomboidOptimization optimization : ZomboidOptimization.values()) {
            if (optimization.availableOnClient() && Boolean.parseBoolean(properties.getProperty(optimization.property()))) {
                enabled.add(optimization);
            }
        }
        return enabled;
    }

    public void saveOptimization(ZomboidOptimization optimization, boolean enabled) throws IOException {
        String key = optimization.property();
        Object previous = properties.setProperty(key, Boolean.toString(enabled));
        try {
            save();
        } catch (IOException ex) {
            if (previous == null) {
                properties.remove(key);
            } else {
                properties.put(key, previous);
            }
            throw ex;
        }
    }

    public void save() throws IOException {
        Files.createDirectories(configPath.getParent());
        Path temporary = Files.createTempFile(configPath.getParent(), "project-zomboid-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                properties.store(out, "Apocalipse BR Launcher - Project Zomboid");
            }
            try {
                Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void load() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        } catch (IOException exc) {
            StartupLog.write("Could not load Project Zomboid settings.", exc);
        }
    }
}
