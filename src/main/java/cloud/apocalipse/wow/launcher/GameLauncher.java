package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class GameLauncher {
    private final String executableName;

    GameLauncher(String executableName) {
        this.executableName = executableName;
    }

    void launch(Path wowDirectory) throws IOException {
        if (wowDirectory == null || !Files.isDirectory(wowDirectory)) {
            throw new IOException("Select a valid World of Warcraft folder.");
        }
        Path executable = wowDirectory.resolve(executableName);
        if (!Files.isRegularFile(executable)) {
            throw new IOException(executableName + " was not found in " + wowDirectory);
        }
        new ProcessBuilder(executable.toString())
                .directory(wowDirectory.toFile())
                .start();
    }
}
