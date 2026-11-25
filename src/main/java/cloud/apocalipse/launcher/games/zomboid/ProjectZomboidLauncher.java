package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.config.ZomboidJvmSettings;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidOptimization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ProjectZomboidLauncher {
    private static final String STEAM_VM_ARGUMENT = "-Dzomboid.steam=1";

    private static final List<String> CLIENT_VM_ARGUMENTS = List.of(
            "-Djava.awt.headless=true",
            "--enable-native-access=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            STEAM_VM_ARGUMENT,
            "-Dzomboid.znetlog=1",
            "-Djava.library.path=win64/;.",
            "-XX:-CreateCoredumpOnCrash",
            "-XX:-OmitStackTraceInFastThrow");

    private static final List<String> UNSELECTED_AGENT_FEATURES = List.of(
            "-Dapocbr.postgres.enabled=false",
            "-Dapocbr.lua.enabled=false",
            "-Dapocbr.npc.enabled=false",
            "-Dapocbr.restart.enabled=false",
            "-Dapocbr.networkDiagnostics.enabled=false",
            "-Dapocbr.hotfixes.xmlCaseInsensitive.enabled=false",
            "-Dapocbr.hotfixes.optionalAnimationFolders.enabled=false",
            "-Dapocbr.hotfixes.assetLogs.enabled=false");

    private final AgentReleaseCache agentCache;
    private final Path consoleLog;
    private final BooleanSupplier steamRunning;

    public ProjectZomboidLauncher(Path appDataDirectory, AgentReleaseCache agentCache) {
        this(appDataDirectory, agentCache, ProjectZomboidLauncher::isSteamRunning);
    }

    ProjectZomboidLauncher(Path appDataDirectory, AgentReleaseCache agentCache, BooleanSupplier steamRunning) {
        this.agentCache = agentCache;
        this.consoleLog = appDataDirectory.resolve("project-zomboid-console.log");
        this.steamRunning = steamRunning;
    }

    public record Launch(Process process, Optional<Path> consoleLog) {
    }

    public static final class SteamNotRunningException extends IOException {
        private SteamNotRunningException() {
            super("The Steam client must be running to launch Project Zomboid with ApocBR optimizations.");
        }
    }

    public static Optional<Path> findExecutable(Path directory) {
        Path executable64 = directory.resolve("ProjectZomboid64.exe");
        if (Files.isRegularFile(executable64)) {
            return Optional.of(executable64);
        }
        Path executable32 = directory.resolve("ProjectZomboid32.exe");
        return Files.isRegularFile(executable32) ? Optional.of(executable32) : Optional.empty();
    }

    public Launch launch(Path directory, Set<ZomboidOptimization> optimizations,
                         Consumer<AgentReleaseCache.Status> progress) throws IOException, InterruptedException {
        Path workingDirectory = directory.toAbsolutePath().normalize();
        Path executable = findExecutable(workingDirectory).orElseThrow(
                () -> new IOException("Project Zomboid executable was not found in " + workingDirectory));
        if (optimizations.stream().noneMatch(ZomboidOptimization::availableOnClient)) {
            Process process = new ProcessBuilder(nativeCommand(executable))
                    .directory(workingDirectory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return new Launch(process, Optional.empty());
        }

        if (!steamRunning.getAsBoolean()) {
            throw new SteamNotRunningException();
        }
        Path java = workingDirectory.resolve("jre64/bin/javaw.exe");
        Path gameJar = workingDirectory.resolve("projectzomboid.jar");
        if (!Files.isRegularFile(java) || !Files.isRegularFile(gameJar)) {
            throw new IOException("ApocBR optimizations require the Build 42 game JAR and bundled 64-bit Java runtime.");
        }
        List<String> heap = new ZomboidJvmSettings(workingDirectory).heapArguments();
        Path agent = agentCache.resolve(progress);
        ProcessBuilder builder = new ProcessBuilder(agentCommand(java, gameJar, agent, heap, optimizations))
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(consoleLog.toFile());
        // JVM options inherited from the shell could otherwise override the selected agent settings.
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        return new Launch(builder.start(), Optional.of(consoleLog));
    }

    private static boolean isSteamRunning() {
        try (var processes = ProcessHandle.allProcesses()) {
            return processes.flatMap(process -> process.info().command().stream())
                    .map(command -> Path.of(command).getFileName())
                    .anyMatch(name -> name != null && "steam.exe".equalsIgnoreCase(name.toString()));
        }
    }

    static List<String> nativeCommand(Path executable) {
        // The native launcher treats arguments before -- as JVM options.
        return List.of(executable.toString(), STEAM_VM_ARGUMENT, "--");
    }

    static List<String> agentCommand(Path java, Path gameJar, Path agent, List<String> heap,
                                     Set<ZomboidOptimization> enabled) {
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.addAll(CLIENT_VM_ARGUMENTS);
        command.addAll(heap);
        command.addAll(UNSELECTED_AGENT_FEATURES);
        for (ZomboidOptimization optimization : ZomboidOptimization.values()) {
            command.add(optimization.vmArgument(enabled.contains(optimization)));
        }
        command.add("-javaagent:" + agent.toAbsolutePath());
        command.add("-cp");
        command.add(gameJar.toString());
        command.add("zombie.gameStates.MainScreenState");
        return command;
    }
}
