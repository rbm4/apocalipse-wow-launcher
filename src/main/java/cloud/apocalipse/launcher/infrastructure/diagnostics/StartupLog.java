package cloud.apocalipse.launcher.infrastructure.diagnostics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class StartupLog {
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private StartupLog() {
    }

    public static void write(String message, Throwable throwable) {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path directory = localAppData == null || localAppData.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".apocalipse-wow-launcher")
                    : Path.of(localAppData, "ApocalipseWoWLauncher");
            Files.createDirectories(directory);
            StringBuilder entry = new StringBuilder()
                    .append(LocalTime.now().format(LOG_TIME_FORMAT))
                    .append(" ")
                    .append(message)
                    .append(System.lineSeparator());
            if (throwable != null) {
                StringWriter stackTrace = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stackTrace));
                entry.append(stackTrace);
            }
            Files.writeString(
                    directory.resolve("launcher.log"),
                    entry.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Last-resort diagnostics should never prevent the launcher from opening.
        }
    }
}
