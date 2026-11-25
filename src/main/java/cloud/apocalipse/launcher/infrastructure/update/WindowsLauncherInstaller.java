package cloud.apocalipse.launcher.infrastructure.update;

import cloud.apocalipse.launcher.LauncherMain;

import java.io.IOException;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class WindowsLauncherInstaller {
    private final Path executable;
    private final String publisherSha256;

    private WindowsLauncherInstaller(Path executable, String publisherSha256) {
        this.executable = executable;
        this.publisherSha256 = publisherSha256;
    }

    public static Optional<WindowsLauncherInstaller> current() throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")
                || !Boolean.getBoolean("apocalipse.launcher.msi")) {
            return Optional.empty();
        }
        var executable = ProcessHandle.current().info().command().map(Path::of);
        if (executable.isEmpty() || !"ApocalipseWoW.exe".equalsIgnoreCase(executable.get().getFileName().toString())) {
            return Optional.empty();
        }
        if (!(LauncherMain.class.getResource("LauncherMain.class").openConnection() instanceof JarURLConnection jar)) {
            return Optional.empty();
        }
        String publisher = jar.getMainAttributes().getValue("Launcher-Update-Publisher-Sha256");
        if (publisher == null || !publisher.matches("[a-fA-F0-9]{64}")) {
            return Optional.empty();
        }
        return Optional.of(new WindowsLauncherInstaller(executable.get(), publisher.toLowerCase(Locale.ROOT)));
    }

    public void prepare(Path msi, LauncherReleases.Update update, String failureMessage, String rebootMessage)
            throws IOException, InterruptedException {
        String command;
        try (var script = WindowsLauncherInstaller.class.getResourceAsStream("/launcher-update.ps1")) {
            if (script == null) {
                throw new IOException("Windows updater script is missing.");
            }
            command = Base64.getEncoder().encodeToString(
                    new String(script.readAllBytes(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_16LE));
        }
        Path installer = msi.toAbsolutePath();
        Path attempt = Files.createTempDirectory(installer.getParent(), "install-");
        Path ready = attempt.resolve("ready");
        Path diagnostic = attempt.resolve("helper.log");
        Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        var builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", command)
                .directory(attempt.toFile()).redirectErrorStream(true).redirectOutput(diagnostic.toFile());
        var environment = builder.environment();
        environment.put("APOC_UPDATE_MSI", installer.toString());
        environment.put("APOC_UPDATE_HASH", update.sha256());
        environment.put("APOC_UPDATE_VERSION", update.version());
        environment.put("APOC_UPDATE_SIGNER", publisherSha256);
        environment.put("APOC_UPDATE_EXE", executable.toString());
        environment.put("APOC_UPDATE_PID", Long.toString(ProcessHandle.current().pid()));
        environment.put("APOC_UPDATE_FAILURE", failureMessage);
        environment.put("APOC_UPDATE_REBOOT", rebootMessage);
        Process helper = builder.start();
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        try {
            while (System.nanoTime() < deadline && !helper.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (Files.exists(ready)) {
                    return;
                }
            }
        } catch (InterruptedException ex) {
            helper.destroyForcibly();
            throw ex;
        }
        helper.destroyForcibly();
        throw new IOException("Windows updater could not prepare the MSI. See " + diagnostic);
    }
}
