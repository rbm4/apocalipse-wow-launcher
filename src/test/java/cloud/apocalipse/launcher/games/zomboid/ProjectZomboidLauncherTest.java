package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.config.ZomboidOptimization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectZomboidLauncherTest {
    @Test
    void nativeLaunchPassesSteamModeAsAJvmOption() {
        Path executable = Path.of("Project Zomboid", "ProjectZomboid64.exe");

        assertEquals(List.of(executable.toString(), "-Dzomboid.steam=1", "--"),
                ProjectZomboidLauncher.nativeCommand(executable));
    }

    @Test
    void optimizedLaunchEnablesSteamBeforeStartingTheGameMainClass() {
        List<String> command = ProjectZomboidLauncher.agentCommand(Path.of("javaw.exe"),
                Path.of("projectzomboid.jar"), Path.of("apocbr-agent.jar"), List.of("-Xmx8G"),
                Set.of(ZomboidOptimization.CLIENT_CHUNK_MAP));

        assertTrue(command.subList(1, command.indexOf("-cp")).contains("-Dzomboid.steam=1"));
    }

    @Test
    void optimizedLaunchReportsClosedSteamBeforeCheckingTheRuntimeOrAgent(@TempDir Path installation)
            throws IOException {
        Files.createFile(installation.resolve("ProjectZomboid64.exe"));
        var cache = new AgentReleaseCache(installation.resolve("agent-cache"));
        var launcher = new ProjectZomboidLauncher(installation, cache, () -> false);

        assertThrows(ProjectZomboidLauncher.SteamNotRunningException.class,
                () -> launcher.launch(installation, Set.of(ZomboidOptimization.CLIENT_CHUNK_MAP),
                        status -> fail("Agent resolution started with Steam closed")));
    }
}
