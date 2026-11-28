package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher.WorkshopProgress;
import cloud.apocalipse.launcher.core.FileHashes;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidLaunchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewpointWorkshopTest {
    @TempDir
    Path temporary;
    private Path content;
    private Path metadata;
    private Path cache;
    private Path staged;
    private Path modelMod;
    private ViewpointWorkshop workshop;

    @BeforeEach
    void prepareSteamCache() throws IOException {
        content = Files.createDirectories(temporary.resolve("steamapps/workshop/content/108600"));
        metadata = content.getParent().getParent().resolve("appworkshop_108600.acf");
        cache = temporary.resolve("Zomboid");
        staged = cache.resolve("Workshop/Apocalipse Launcher - Viewpoint");
        createMod("3619862853", "ZombieBuddy", "42");
        Path viewpoint = createMod("3809306528", "Viewpoint", "common");
        Files.writeString(viewpoint.resolve("common/mod.info"), "id=Viewpoint\njavaJarFile=media/java/client/Viewpoint.jar");
        Files.createDirectories(viewpoint.resolve("42/media/java/client"));
        Files.writeString(viewpoint.resolve("42/media/java/client/Viewpoint.jar"), "fixture");
        modelMod = createMod("3810302175", "PZVoxelStudioViewpoint", "42.20.4");
        writeManifests("3");
        workshop = new ViewpointWorkshop(cache, (ids, progress) -> {
            assertEquals(List.of("3619862853", "3809306528", "3810302175"), ids);
            for (String id : ids) {
                progress.accept(id, SteamWorkshopDownloader.Status.CHECKING);
                progress.accept(id, SteamWorkshopDownloader.Status.VERIFIED);
            }
            return content;
        });
    }

    @Test
    void requestsEachWorkshopItemAnonymouslyAndValidatesIt() {
        Path steamcmd = Path.of("steamcmd.exe");
        assertEquals(List.of("steamcmd.exe", "+login", "anonymous",
                        "+workshop_download_item", "108600", "3619862853", "validate",
                        "+workshop_download_item", "108600", "3809306528", "validate",
                        "+workshop_download_item", "108600", "3810302175", "validate", "+quit"),
                SteamWorkshopDownloader.command(steamcmd, List.of("3619862853", "3809306528", "3810302175")));
    }

    @Test
    void reportsNewSteamCmdLogEventsDuringTheDownloadWithoutRepeatingOlderEvents() throws IOException {
        Path log = temporary.resolve("workshop_log.txt");
        Files.writeString(log, "[AppID 108600] Download item 3619862853 result : OK\n");
        List<String> events = new ArrayList<>();
        var progress = new SteamWorkshopDownloader.WorkshopLog(log, List.of("3619862853", "3809306528"),
                content, (id, status) -> events.add(id + "=" + status));

        progress.poll();
        assertTrue(events.isEmpty());
        Files.writeString(log, "[AppID 108600] Download item 3619862853 requested by app\n",
                StandardOpenOption.APPEND);
        progress.poll();
        assertEquals(List.of("3619862853=CHECKING"), events);
        Files.writeString(log, """
                [AppID 108600] Download item 3619862853 result : OK
                [AppID 108600] Download item 3809306528 requested by app
                [AppID 108600] Download item 123 result : OK
                """, StandardOpenOption.APPEND);
        progress.poll();
        progress.verified("3619862853");
        progress.verified("3809306528");

        assertEquals(List.of("3619862853=CHECKING", "3619862853=VERIFIED",
                "3809306528=CHECKING", "3809306528=VERIFIED"), events);
    }

    @Test
    void reportsEachModBeforeStagingAndReusesTheExistingStageWithoutAnInstallMessage() throws Exception {
        var events = new ArrayList<WorkshopProgress>();
        workshop.synchronize(events::add, ZomboidLaunchMode.VIEWPOINT);
        assertEquals(List.of(
                new WorkshopProgress(WorkshopProgress.Status.PREPARING, "Viewpoint", 0, 3),
                new WorkshopProgress(WorkshopProgress.Status.CHECKING, "Apocalipse [BR] ZombieBuddy", 1, 3),
                new WorkshopProgress(WorkshopProgress.Status.VERIFIED, "Apocalipse [BR] ZombieBuddy", 1, 3),
                new WorkshopProgress(WorkshopProgress.Status.CHECKING, "Viewpoint", 2, 3),
                new WorkshopProgress(WorkshopProgress.Status.VERIFIED, "Viewpoint", 2, 3),
                new WorkshopProgress(WorkshopProgress.Status.CHECKING, "PZVoxelStudioViewpoint", 3, 3),
                new WorkshopProgress(WorkshopProgress.Status.VERIFIED, "PZVoxelStudioViewpoint", 3, 3),
                new WorkshopProgress(WorkshopProgress.Status.STAGING, "Viewpoint", 3, 3)), events);

        events.clear();
        workshop.synchronize(events::add, ZomboidLaunchMode.VIEWPOINT);
        assertEquals(WorkshopProgress.Status.VERIFIED, events.getLast().status());
    }

    @Test
    void stagesProfileItemsWithTheirOwnWorkshopIdsAndReusesMatchingManifest() throws Exception {
        workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT);

        Path mods = staged.resolve("Contents/mods");
        assertEquals("id=3619862853\n", Files.readString(mods.resolve("ZombieBuddy/workshop.txt")));
        assertTrue(Files.readAllLines(mods.resolve("ZombieBuddy/42/mod.info")).contains("name=Apocalipse [BR] ZombieBuddy"));
        assertEquals("id=3809306528\n", Files.readString(mods.resolve("Viewpoint/workshop.txt")));
        assertEquals("id=3810302175\n", Files.readString(mods.resolve("PZVoxelStudioViewpoint/workshop.txt")));
        assertTrue(Files.isRegularFile(mods.resolve("Viewpoint/42/media/java/client/Viewpoint.jar")));
        Files.writeString(modelMod.resolve("asset.txt"), "updated");

        workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT);
        assertFalse(Files.exists(mods.resolve("PZVoxelStudioViewpoint/asset.txt")));

        writeManifests("4");
        workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT);
        assertEquals("updated", Files.readString(mods.resolve("PZVoxelStudioViewpoint/asset.txt")));
        assertFalse(Files.exists(cache.resolve("apocalipse-viewpoint-previous")));

        createMod("3818189653", "ViewpointVisibleLoot", "42");
        Path rightClick = createMod("3819018807", "ViewpointRightClick", "42");
        Files.writeString(rightClick.resolve("42/mod.info"), "id=ViewpointRightClick\nrequire=Viewpoint\n");
        var extendedWorkshop = new ViewpointWorkshop(cache, (ids, progress) -> {
            assertEquals(List.of("3619862853", "3809306528", "3818189653", "3819018807", "3810302175"), ids);
            return content;
        });

        var approvals = extendedWorkshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT_RIGHT_CLICK);

        assertEquals("id=3819018807\n", Files.readString(mods.resolve("ViewpointRightClick/workshop.txt")));
        assertEquals(Set.of("Viewpoint:3809306528:" + FileHashes.sha256(
                mods.resolve("Viewpoint/42/media/java/client/Viewpoint.jar"))), approvals);
    }

    @Test
    void approvesOnlyHashesFromTheVerifiedWorkshopPackageAndRepairsAModifiedStage() throws Exception {
        String javaMod = "Viewpoint/42/media/java/client/Viewpoint.jar";
        Path source = content.resolve("3809306528/mods").resolve(javaMod);
        Path installed = staged.resolve("Contents/mods").resolve(javaMod);
        String approvedHash = FileHashes.sha256(source);
        var approvals = Set.of("Viewpoint:3809306528:" + approvedHash);

        assertEquals(approvals, workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT));
        Files.writeString(installed, "modified after staging");
        assertFalse(approvedHash.equals(FileHashes.sha256(installed)));

        assertEquals(approvals, workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT));
        assertEquals(approvedHash, FileHashes.sha256(installed));
    }

    @Test
    void preservesThePreviousTemplateWhenAnUpdatedPackageIsIncomplete() throws Exception {
        workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT);
        writeManifests("4");
        Files.delete(modelMod.resolve("42.20.4/mod.info"));

        assertThrows(IOException.class, () -> workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT));
        assertTrue(Files.readString(staged.resolve("launcher-workshop-manifests.txt")).contains("3810302175=3"));
        assertTrue(Files.isRegularFile(staged.resolve("Contents/mods/PZVoxelStudioViewpoint/42.20.4/mod.info")));
    }

    @Test
    void leavesAnExistingUnmanagedStageAlone() throws Exception {
        Files.createDirectories(staged);
        Files.writeString(staged.resolve("workshop.txt"), "user content");

        assertThrows(IOException.class, () -> workshop.synchronize(ignored -> {}, ZomboidLaunchMode.VIEWPOINT));
        assertEquals("user content", Files.readString(staged.resolve("workshop.txt")));
    }

    private Path createMod(String id, String mod, String version) throws IOException {
        Path base = content.resolve(id).resolve("mods").resolve(mod);
        Files.createDirectories(base.resolve(version));
        Files.writeString(base.resolve(version).resolve("mod.info"), "id=" + mod);
        return base;
    }

    private void writeManifests(String modelsManifest) throws IOException {
        Files.writeString(metadata, """
                "AppWorkshop"
                {
                    "WorkshopItemsInstalled"
                    {
                        "3619862853" { "manifest" "1" }
                        "3809306528" { "manifest" "2" }
                        "3810302175" { "manifest" "%s" }
                        "3818189653" { "manifest" "5" }
                        "3819018807" { "manifest" "6" }
                    }
                }
                """.formatted(modelsManifest));
    }
}
