package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class SteamInstallationDetector {
    private static final Pattern LIBRARY_PATH = steamValue("path");
    private static final Pattern INSTALL_DIRECTORY = steamValue("installdir");
    private static final String REGISTRY_QUERY = """
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [Text.Encoding]::UTF8
            $uninstall = 'Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 108600'
            foreach ($key in @("HKCU:\\Software\\$uninstall", "HKLM:\\SOFTWARE\\$uninstall",
                             "HKLM:\\SOFTWARE\\WOW6432Node\\$uninstall")) {
                $path = (Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue).InstallLocation
                if ($path) { [Console]::WriteLine('game=' + $path) }
            }
            $steam = Get-ItemProperty -LiteralPath 'HKCU:\\Software\\Valve\\Steam' -ErrorAction SilentlyContinue
            if ($steam.SteamPath) { [Console]::WriteLine('steam=' + $steam.SteamPath) }
            foreach ($key in @('HKLM:\\SOFTWARE\\WOW6432Node\\Valve\\Steam', 'HKLM:\\SOFTWARE\\Valve\\Steam')) {
                $path = (Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue).InstallPath
                if ($path) { [Console]::WriteLine('steam=' + $path) }
            }
            exit 0
            """;

    public Optional<Path> findInstallation() throws IOException, InterruptedException {
        String windows = System.getenv("SystemRoot");
        if (windows == null) {
            return Optional.empty();
        }
        Path powershell = Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        String command = Base64.getEncoder().encodeToString(REGISTRY_QUERY.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", command)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String registryEntries;
        try (var output = process.getInputStream()) {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new IOException("Timed out reading the Steam registry entries.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Steam registry query exited with code " + process.exitValue());
            }
            registryEntries = new String(output.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        var steamRoots = new LinkedHashSet<Path>();
        for (String entry : registryEntries.lines().toList()) {
            if (entry.startsWith("game=")) {
                Optional<Path> game = absolutePath(entry.substring("game=".length()))
                        .filter(SteamInstallationDetector::hasGameExecutable);
                if (game.isPresent()) {
                    return game;
                }
            } else if (entry.startsWith("steam=")) {
                absolutePath(entry.substring("steam=".length())).ifPresent(steamRoots::add);
            }
        }
        for (Path root : steamRoots) {
            Optional<Path> game = findInLibraries(root);
            if (game.isPresent()) {
                return game;
            }
        }
        return Optional.empty();
    }

    Optional<Path> findInLibraries(Path steamRoot) {
        var libraries = new LinkedHashSet<Path>();
        libraries.add(steamRoot);
        var paths = LIBRARY_PATH.matcher(readSteamFile(steamRoot.resolve("steamapps/libraryfolders.vdf")));
        while (paths.find()) {
            absolutePath(paths.group(1).replace("\\\\", "\\")).ifPresent(libraries::add);
        }
        for (Path library : libraries) {
            Path steamApps = library.resolve("steamapps");
            var folder = INSTALL_DIRECTORY.matcher(readSteamFile(steamApps.resolve("appmanifest_108600.acf")));
            if (folder.find()) {
                Path common = steamApps.resolve("common");
                try {
                    Path game = common.resolve(folder.group(1)).normalize();
                    if (common.equals(game.getParent()) && hasGameExecutable(game)) {
                        return Optional.of(game);
                    }
                } catch (InvalidPathException ignored) {
                    // An invalid folder in one manifest can be skipped while checking other libraries.
                }
            }
        }
        return Optional.empty();
    }

    private static String readSteamFile(Path file) {
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file);
            } catch (IOException exc) {
                StartupLog.write("Could not read Steam metadata at " + file, exc);
            }
        }
        return "";
    }

    private static Optional<Path> absolutePath(String value) {
        try {
            Path path = Path.of(value.strip()).normalize();
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException exc) {
            return Optional.empty();
        }
    }

    private static boolean hasGameExecutable(Path directory) {
        return ProjectZomboidLauncher.findExecutable(directory).isPresent();
    }

    private static Pattern steamValue(String key) {
        return Pattern.compile("(?im)^\\h*\"" + key + "\"\\h+\"([^\"\\r\\n]+)\"");
    }
}
