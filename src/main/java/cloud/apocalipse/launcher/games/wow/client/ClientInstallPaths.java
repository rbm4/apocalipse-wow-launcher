package cloud.apocalipse.launcher.games.wow.client;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientInstallPaths {
    private static final String CLIENT_FOLDER_NAME = "World of Warcraft 3.3.5a";

    public Path defaultClientDirectory() {
        Path installDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path preferred = installDirectory.resolve(CLIENT_FOLDER_NAME);
        if (isUsableParent(installDirectory)) {
            return preferred;
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path fallbackRoot = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".apocalipse-wow-launcher")
                : Path.of(localAppData, "ApocalipseWoWLauncher");
        return fallbackRoot.resolve("clients").resolve(CLIENT_FOLDER_NAME);
    }

    private static boolean isUsableParent(Path directory) {
        try {
            if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
                return false;
            }
            Path probe = Files.createTempFile(directory, ".apoc-wow-write-test", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
