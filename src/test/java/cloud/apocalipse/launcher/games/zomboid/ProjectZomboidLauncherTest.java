package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.config.ZomboidLaunchMode;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidOptimization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectZomboidLauncherTest {
    private static final List<String> VIEWPOINT_OVERRIDES = List.of(
            "-Dapocbr.optimization.characterStats.enabled=false",
            "-Dapocbr.optimization.luaDispatch.enabled=false",
            "-Dapocbr.telemetry.lua.enabled=false",
            "-Dapocbr.lua.enabled=false");

    @Test
    void optimizedLaunchEnablesSteamBeforeStartingTheGameMainClass() {
        List<String> command = ProjectZomboidLauncher.agentCommand(Path.of("javaw.exe"),
                Path.of("projectzomboid.jar"), List.of(Path.of("apocbr-agent.jar"), Path.of("ZombieBuddy.jar")), List.of("-Xmx8G"),
                ZomboidLaunchMode.STANDARD, Set.of(ZomboidOptimization.CLIENT_CHUNK_MAP), Set.of());

        assertTrue(command.subList(1, command.indexOf("-cp")).contains("-Dzomboid.steam=1"));
    }

    @Test
    void viewpointStartsBothAgentsInOrderWithCompatibilityOverrides() {
        Set<ZomboidOptimization> selected = Set.of(
                ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH,
                ZomboidOptimization.CLIENT_CHUNK_MAP);
        Path java = Path.of("javaw.exe");
        Path game = Path.of("projectzomboid.jar");
        Path agent = Path.of("apocbr-agent.jar");
        Path zombieBuddy = Path.of("ZombieBuddy.jar");
        List<String> heap = List.of("-Xmx8G");
        String viewpointHash = "a".repeat(64);

        List<String> viewpoint = ProjectZomboidLauncher.agentCommand(
                java, game, List.of(agent, zombieBuddy), heap, ZomboidLaunchMode.VIEWPOINT, selected,
                Set.of("Viewpoint:3809306528:" + viewpointHash));
        List<String> visibleLoot = ProjectZomboidLauncher.agentCommand(
                java, game, List.of(agent, zombieBuddy), heap, ZomboidLaunchMode.VIEWPOINT_VISIBLE_LOOT, selected,
                Set.of("Viewpoint:3809306528:" + viewpointHash));
        List<String> rightClick = ProjectZomboidLauncher.agentCommand(
                java, game, List.of(agent, zombieBuddy), heap, ZomboidLaunchMode.VIEWPOINT_RIGHT_CLICK, selected,
                Set.of("Viewpoint:3809306528:" + viewpointHash));
        String treePatchApproval = "ViewpointDisableIsometricUpkeep:3842290180:" + "b".repeat(64);
        List<String> treePatch = ProjectZomboidLauncher.agentCommand(
                java, game, List.of(agent, zombieBuddy), heap, ZomboidLaunchMode.VIEWPOINT_TREE_FLICKER_PATCH, selected,
                Set.of("Viewpoint:3809306528:" + viewpointHash, treePatchApproval));
        List<String> standard = ProjectZomboidLauncher.agentCommand(
                java, game, List.of(agent, zombieBuddy), heap, ZomboidLaunchMode.STANDARD, selected, Set.of());

        List<String> viewpointVmArgs = viewpoint.subList(1, viewpoint.indexOf("-cp"));
        assertEquals(List.of("-javaagent:" + agent.toAbsolutePath(), "-javaagent:" + zombieBuddy.toAbsolutePath()),
                viewpointVmArgs.stream().filter(argument -> argument.startsWith("-javaagent:")).toList());
        assertEquals(List.of("-javaagent:" + agent.toAbsolutePath(), "-javaagent:" + zombieBuddy.toAbsolutePath()),
                standard.stream().filter(argument -> argument.startsWith("-javaagent:")).toList());
        for (String override : VIEWPOINT_OVERRIDES) {
            String property = override.substring(0, override.indexOf('=') + 1);
            assertEquals(List.of(override), viewpointVmArgs.stream()
                    .filter(argument -> argument.startsWith(property)).toList());
        }
        assertTrue(viewpointVmArgs.contains("-Dapocbr.optimization.clientChunkMap.enabled=true"));
        assertTrue(viewpointVmArgs.contains("-Dapocbr.launcher.mods=ZombieBuddy,APOCALISEBR_PACK,Viewpoint,PZVoxelStudioViewpoint"));
        assertTrue(visibleLoot.contains("-Dapocbr.launcher.mods=ZombieBuddy,APOCALISEBR_PACK,Viewpoint,ViewpointVisibleLoot,PZVoxelStudioViewpoint"));
        assertTrue(visibleLoot.containsAll(VIEWPOINT_OVERRIDES));
        assertTrue(rightClick.contains("-Dapocbr.launcher.mods=ZombieBuddy,APOCALISEBR_PACK,Viewpoint,ViewpointVisibleLoot,ViewpointRightClick,PZVoxelStudioViewpoint"));
        assertTrue(rightClick.containsAll(VIEWPOINT_OVERRIDES));
        assertTrue(treePatch.contains("-Dapocbr.launcher.mods=ZombieBuddy,APOCALISEBR_PACK,Viewpoint,ViewpointVisibleLoot,"
                + "ViewpointRightClick,ViewpointDisableIsometricUpkeep,PZVoxelStudioViewpoint"));
        assertTrue(treePatch.contains("-Dapocbr.launcher.java.approvals=Viewpoint:3809306528:"
                + viewpointHash + "," + treePatchApproval));
        assertTrue(treePatch.containsAll(VIEWPOINT_OVERRIDES));
        assertTrue(standard.contains("-Dapocbr.launcher.mods=ZombieBuddy,APOCALISEBR_PACK"));
        assertTrue(viewpointVmArgs.contains("-Dapocbr.launcher.java.approvals=Viewpoint:3809306528:" + viewpointHash));
        assertFalse(viewpointVmArgs.stream().anyMatch(arg -> arg.startsWith("-Dapocbr.launcher.template=")));
        assertTrue(standard.contains("-Dapocbr.optimization.characterStats.enabled=true"));
        assertTrue(standard.contains("-Dapocbr.optimization.luaDispatch.enabled=true"));
        assertFalse(standard.contains("-Dapocbr.telemetry.lua.enabled=false"));
    }

    @Test
    void optimizedLaunchReportsClosedSteamBeforeCheckingTheRuntimeOrAgent(@TempDir Path installation)
            throws IOException {
        Files.createFile(installation.resolve("ProjectZomboid64.exe"));
        var cache = new AgentReleaseCache(installation.resolve("agent-cache"), AgentReleaseCache.Agent.APOCBR);
        var buddyCache = new AgentReleaseCache(installation.resolve("buddy-cache"), AgentReleaseCache.Agent.ZOMBIE_BUDDY);
        var workshop = new ViewpointWorkshop(installation, (ids, progress) -> fail("Workshop started with Steam closed"));
        var launcher = new ProjectZomboidLauncher(installation, cache, buddyCache, workshop, () -> false);

        assertThrows(ProjectZomboidLauncher.SteamNotRunningException.class,
                () -> launcher.launch(installation, ZomboidLaunchMode.STANDARD, Set.of(ZomboidOptimization.CLIENT_CHUNK_MAP),
                        status -> fail("Agent resolution started with Steam closed"),
                        progress -> fail("Workshop started with Steam closed")));
        assertThrows(ProjectZomboidLauncher.SteamNotRunningException.class,
                () -> launcher.launch(installation, ZomboidLaunchMode.VIEWPOINT, Set.of(),
                        status -> fail("Agent resolution started with Steam closed"),
                        progress -> fail("Workshop started with Steam closed")));
    }
}
