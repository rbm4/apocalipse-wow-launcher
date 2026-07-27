package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

final class PatchManager {
    static final String MANAGED_PATCH_NAME = "patch-A.MPQ";
    private static final String MANAGED_PATCH_CLIENT_PATH = "Data/" + MANAGED_PATCH_NAME;
    private static final String REALMLIST_CONTENT = "set realmlist wow.apocalipse.cloud" + System.lineSeparator();

    private final ManifestClient manifestClient;
    private final HttpClient httpClient;

    PatchManager() {
        this.manifestClient = new ManifestClient();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    PatchManifest synchronize(Path wowDirectory, String manifestUrl, PatchProgress progress)
            throws IOException, InterruptedException {
        validateWowDirectory(wowDirectory);
        enforceRealmlist(wowDirectory);
        progress.message("Fetching patch manifest...");
        progress.progress(0.05);

        PatchManifest manifest = manifestClient.fetch(manifestUrl);
        if (manifest.files().isEmpty()) {
            progress.message("Manifest version " + manifest.version() + " has no files.");
            progress.progress(1);
            return manifest;
        }

        PatchFile file = manifest.files().getFirst();
        synchronizeFile(wowDirectory, manifestUrl, file, progress, 0.05, 0.9);
        cleanupOldManagedPatches(wowDirectory, manifest);
        progress.progress(1);
        progress.message("Patch verification complete. Version " + manifest.version() + " is installed.");
        return manifest;
    }

    private void enforceRealmlist(Path wowDirectory) throws IOException {
        Path target = resolveRealmlistPath(wowDirectory);
        Files.createDirectories(target.getParent());
        Files.writeString(target, REALMLIST_CONTENT);
    }

    private Path resolveRealmlistPath(Path wowDirectory) {
        List<Path> candidates = List.of(
                wowDirectory.resolve("Data").resolve("enUS").resolve("realmlist.wtf"),
                wowDirectory.resolve("Data").resolve("enUS").resolve("realmlist"),
                wowDirectory.resolve("Data").resolve("enUS").resolve("reamlist"),
                wowDirectory.resolve("realmlist.wtf")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private void synchronizeFile(
            Path wowDirectory,
            String manifestUrl,
            PatchFile file,
            PatchProgress progress,
            double baseProgress,
            double fileWeight
    ) throws IOException, InterruptedException {
        Path target = resolveClientPath(wowDirectory, MANAGED_PATCH_CLIENT_PATH);
        if (isCurrent(target, file.sha256())) {
            progress.message("Already current: " + MANAGED_PATCH_NAME);
            progress.progress(baseProgress + fileWeight);
            return;
        }

        progress.message("Downloading patch...");
        Files.createDirectories(target.getParent());
        Path tempFile = Files.createTempFile(target.getParent(), MANAGED_PATCH_NAME, ".download");
        try {
            download(resolveDownloadUri(manifestUrl, file.downloadUrl()), tempFile, file.size(), progress, baseProgress, fileWeight * 0.75);
            String actualHash = FileHashes.sha256(tempFile);
            if (!actualHash.equalsIgnoreCase(file.sha256())) {
                throw new IOException("SHA-256 mismatch for " + file.name() + ": expected " + file.sha256() + ", got " + actualHash);
            }
            installDownloadedPatch(tempFile, target);
            progress.progress(baseProgress + fileWeight);
            progress.message("Installed " + MANAGED_PATCH_NAME);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void installDownloadedPatch(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException ignored) {
            // Some client folders are on filesystems where Java cannot do an atomic replace.
        } catch (FileSystemException ex) {
            throw patchInstallException(tempFile, target, ex);
        }

        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (FileSystemException ex) {
            throw patchInstallException(tempFile, target, ex);
        }
    }

    private IOException patchInstallException(Path tempFile, Path target, FileSystemException cause) {
        String reason = cause.getReason();
        String message = "Could not replace " + target
                + ". Close World of Warcraft, torrent clients, sync tools, or antivirus scans that may be using "
                + MANAGED_PATCH_NAME
                + ", then try again. Temporary download: " + tempFile;
        if (reason != null && !reason.isBlank()) {
            message += ". Windows reported: " + reason;
        }
        return new IOException(message, cause);
    }

    private boolean isCurrent(Path target, String expectedHash) throws IOException {
        if (!Files.isRegularFile(target) || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return FileHashes.sha256(target).equalsIgnoreCase(expectedHash);
    }

    private void download(
            URI uri,
            Path target,
            long expectedSize,
            PatchProgress progress,
            double baseProgress,
            double weight
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode() + " for " + uri);
        }

        long total = expectedSize > 0 ? expectedSize : response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long written = 0;
        byte[] buffer = new byte[1024 * 256];
        try (InputStream in = response.body();
             var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (total > 0) {
                    progress.progress(baseProgress + weight * Math.min(1.0, written / (double) total));
                }
            }
        }
    }

    private void cleanupOldManagedPatches(Path wowDirectory, PatchManifest manifest) throws IOException {
        Path dataDir = wowDirectory.resolve("Data");
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (var stream = Files.list(dataDir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (isManagedPatchName(name) && !name.equalsIgnoreCase(MANAGED_PATCH_NAME)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean isManagedPatchName(String name) {
        return name.equalsIgnoreCase(MANAGED_PATCH_NAME)
                || (name.startsWith("patch-apocalipse-") && name.endsWith(".mpq"));
    }

    private static void validateWowDirectory(Path wowDirectory) throws IOException {
        if (wowDirectory == null || !Files.isDirectory(wowDirectory)) {
            throw new IOException("Select a valid World of Warcraft folder.");
        }
        if (!Files.isRegularFile(wowDirectory.resolve("Wow.exe"))) {
            throw new IOException("Wow.exe was not found in " + wowDirectory);
        }
        if (!Files.isDirectory(wowDirectory.resolve("Data"))) {
            throw new IOException("Data folder was not found in " + wowDirectory);
        }
    }

    private static Path resolveClientPath(Path wowDirectory, String clientPath) throws IOException {
        String normalized = clientPath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IOException("Unsafe client path in manifest: " + clientPath);
        }
        Path root = wowDirectory.toAbsolutePath().normalize();
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Manifest path escapes WoW directory: " + clientPath);
        }
        return target;
    }

    private static URI resolveDownloadUri(String manifestUrl, String downloadUrl) {
        URI downloadUri = URI.create(downloadUrl);
        if (downloadUri.isAbsolute()) {
            return downloadUri;
        }
        return URI.create(manifestUrl).resolve(downloadUri);
    }
}
