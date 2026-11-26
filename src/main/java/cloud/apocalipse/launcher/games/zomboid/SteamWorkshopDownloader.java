package cloud.apocalipse.launcher.games.zomboid;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

final class SteamWorkshopDownloader {
    private static final String APP_ID = "108600";
    private static final URI INSTALLER = URI.create("https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip");
    private static final Pattern ITEM_PROGRESS = Pattern.compile(
            "\\[AppID 108600] Download item ([0-9]+) (requested by app|result : OK)$");
    private final Path installation;

    enum Status { CHECKING, VERIFIED }

    SteamWorkshopDownloader(Path installation) {
        this.installation = installation.toAbsolutePath();
    }

    Path download(List<String> items, BiConsumer<String, Status> progress) throws IOException, InterruptedException {
        Path executable = installation.resolve("steamcmd.exe");
        if (!Files.isRegularFile(executable)) {
            install(executable);
        }

        Path log = installation.resolve("workshop-download.log");
        Path content = installation.resolve("steamapps/workshop/content").resolve(APP_ID);
        List<String> command = command(executable, items);
        var workshopLog = new WorkshopLog(installation.resolve("logs/workshop_log.txt"), items, content, progress);
        int exit = run(command, log, workshopLog);
        if (exit == 7) {
            // The Windows bootstrapper may restart itself after a SteamCMD update.
            exit = run(command, log, workshopLog);
        }
        String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        List<String> unconfirmed = items.stream()
                .filter(id -> !output.contains("Success. Downloaded item " + id + " to")
                        || !Files.isDirectory(content.resolve(id).resolve("mods")))
                .toList();
        if (exit != 0 || !unconfirmed.isEmpty()) {
            throw new IOException("SteamCMD exited with code " + exit + ". Unconfirmed Workshop items: "
                    + unconfirmed + ". Download log: " + log);
        }
        // Console output is buffered. Use it as a fallback only after SteamCMD confirms each item.
        items.forEach(workshopLog::verified);
        return content;
    }

    static List<String> command(Path executable, List<String> items) {
        List<String> command = new ArrayList<>(List.of(executable.toString(), "+login", "anonymous"));
        for (String item : items) {
            command.addAll(List.of("+workshop_download_item", APP_ID, item, "validate"));
        }
        command.add("+quit");
        return command;
    }

    private int run(List<String> command, Path log, WorkshopLog workshopLog)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(installation.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(6);
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                workshopLog.poll();
                if (System.nanoTime() - deadline >= 0) {
                    throw new IOException("SteamCMD timed out. Download log: " + log);
                }
            }
            workshopLog.poll();
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    static final class WorkshopLog {
        private final Path path;
        private final Set<String> items;
        private final Path content;
        private final BiConsumer<String, Status> progress;
        private final Set<String> checking = new HashSet<>();
        private final Set<String> verified = new HashSet<>();
        private long offset;
        private Object fileKey;

        WorkshopLog(Path path, List<String> items, Path content, BiConsumer<String, Status> progress) {
            this.path = path;
            this.items = Set.copyOf(items);
            this.content = content;
            this.progress = progress;
            try {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                offset = attributes.size();
                fileKey = attributes.fileKey();
            } catch (IOException ignored) {
                // SteamCMD creates the workshop log on its first run.
            }
        }

        void poll() {
            try {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                if (offset > attributes.size() || !Objects.equals(fileKey, attributes.fileKey())) {
                    offset = 0;
                }
                fileKey = attributes.fileKey();
                if (offset == attributes.size()) {
                    return;
                }
                try (var log = new RandomAccessFile(path.toFile(), "r")) {
                    log.seek(offset);
                    for (String line; (line = log.readLine()) != null; ) {
                        var match = ITEM_PROGRESS.matcher(line);
                        if (match.find() && items.contains(match.group(1))) {
                            String id = match.group(1);
                            if ("requested by app".equals(match.group(2))) {
                                checking(id);
                            } else if (Files.isDirectory(content.resolve(id).resolve("mods"))) {
                                verified(id);
                            }
                        }
                    }
                    offset = log.getFilePointer();
                }
            } catch (IOException ignored) {
                // Diagnostics may be unavailable during rotation. The final console log still verifies the update.
            }
        }

        private void checking(String id) {
            if (checking.add(id)) {
                progress.accept(id, Status.CHECKING);
            }
        }

        void verified(String id) {
            checking(id);
            if (verified.add(id)) {
                progress.accept(id, Status.VERIFIED);
            }
        }
    }

    private void install(Path executable) throws IOException, InterruptedException {
        Files.createDirectories(installation);
        Path archive = Files.createTempFile(installation, "steamcmd-", ".zip");
        Path unpacked = Files.createTempFile(installation, "steamcmd-", ".exe");
        try {
            HttpRequest request = HttpRequest.newBuilder(INSTALLER).timeout(Duration.ofMinutes(2)).GET().build();
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
                if (response.statusCode() != 200) {
                    throw new IOException("Valve SteamCMD download failed with HTTP " + response.statusCode() + ".");
                }
            }
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if ("steamcmd.exe".equalsIgnoreCase(entry.getName()) && !entry.isDirectory()) {
                        Files.copy(zip, unpacked, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                }
            }
            if (Files.size(unpacked) == 0) {
                throw new IOException("Valve's SteamCMD archive is missing steamcmd.exe.");
            }
            Files.move(unpacked, executable);
        } finally {
            Files.deleteIfExists(unpacked);
            Files.deleteIfExists(archive);
        }
    }
}
