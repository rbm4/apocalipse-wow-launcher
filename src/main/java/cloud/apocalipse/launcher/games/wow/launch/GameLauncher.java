package cloud.apocalipse.launcher.games.wow.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GameLauncher {
    private final String executableName;
    private Process runningProcess;

    public GameLauncher(String executableName) {
        this.executableName = executableName;
    }

    public synchronized boolean isRunning() {
        if (runningProcess == null) {
            return false;
        }
        if (runningProcess.isAlive()) {
            return true;
        }
        runningProcess = null;
        return false;
    }

    public void launch(Path wowDirectory, List<String> arguments, Runnable onExit) throws IOException {
        if (wowDirectory == null || !Files.isDirectory(wowDirectory)) {
            throw new IOException("Select a valid World of Warcraft folder.");
        }
        Path executable = wowDirectory.resolve(executableName);
        if (!Files.isRegularFile(executable)) {
            throw new IOException(executableName + " was not found in " + wowDirectory);
        }
        synchronized (this) {
            if (isRunning()) {
                throw new IOException(executableName + " is already running.");
            }
        }
        deleteCacheDirectory(wowDirectory);
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command)
                .directory(wowDirectory.toFile())
                .start();
        synchronized (this) {
            runningProcess = process;
        }
        process.onExit().thenRun(() -> {
            synchronized (this) {
                if (runningProcess == process) {
                    runningProcess = null;
                }
            }
            if (onExit != null) {
                onExit.run();
            }
        });
    }

    private void deleteCacheDirectory(Path wowDirectory) throws IOException {
        Path cacheDirectory = wowDirectory.resolve("Cache");
        if (!Files.exists(cacheDirectory)) {
            return;
        }
        if (!Files.isDirectory(cacheDirectory)) {
            throw new IOException("Cache path is not a directory: " + cacheDirectory);
        }
        try (var stream = Files.walk(cacheDirectory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
