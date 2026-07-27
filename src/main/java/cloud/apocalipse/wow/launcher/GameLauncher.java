package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GameLauncher {
    private final String executableName;
    private Process runningProcess;

    GameLauncher(String executableName) {
        this.executableName = executableName;
    }

    synchronized boolean isRunning() {
        if (runningProcess == null) {
            return false;
        }
        if (runningProcess.isAlive()) {
            return true;
        }
        runningProcess = null;
        return false;
    }

    void launch(Path wowDirectory, List<String> arguments, Runnable onExit) throws IOException {
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
}
