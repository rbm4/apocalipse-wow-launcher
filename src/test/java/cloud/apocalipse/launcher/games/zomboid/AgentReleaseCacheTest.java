package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.config.ZomboidLaunchMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache.Agent.APOCBR;
import static cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache.Agent.ZOMBIE_BUDDY;
import static cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache.Status.CHECKING;
import static cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache.Status.VERIFIED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReleaseCacheTest {
    private static final String APOCBR_PREMAIN = "cloud.apocalipse.pz.agent.ApocBrAgent";
    private static final String ZOMBIE_BUDDY_PREMAIN = "me.zed_0xff.zombie_buddy.Agent";
    private static final String BUDDY_DOWNLOAD = "/buddy/download/v1/ZombieBuddy.jar";

    @TempDir
    Path directory;
    private final AtomicInteger buddyChecks = new AtomicInteger();
    private final AtomicInteger buddyDownloads = new AtomicInteger();
    private HttpServer server;
    private AgentReleaseCache apocbr;
    private AgentReleaseCache buddy;
    private volatile int buddyReleaseStatus = 200;
    private volatile byte[] buddyJar;
    private volatile byte[] buddyDownload;

    @BeforeEach
    void startFixture() throws Exception {
        byte[] apocbrJar = agentJar(APOCBR_PREMAIN);
        buddyJar = agentJar(ZOMBIE_BUDDY_PREMAIN);
        buddyDownload = buddyJar;
        String apocbrDownload = "/apocbr/download/v1/apocbr-agent.jar";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/apocbr/latest", exchange -> respond(exchange, 200,
                releaseMetadata("apocbr-agent.jar", base.resolve(apocbrDownload), apocbrJar)));
        server.createContext(apocbrDownload, exchange -> respond(exchange, 200, apocbrJar));
        server.createContext("/buddy/latest", exchange -> {
            buddyChecks.incrementAndGet();
            respond(exchange, buddyReleaseStatus, releaseMetadata("ZombieBuddy.jar", base.resolve(BUDDY_DOWNLOAD), buddyJar));
        });
        server.createContext(BUDDY_DOWNLOAD, exchange -> {
            buddyDownloads.incrementAndGet();
            respond(exchange, 200, buddyDownload);
        });
        server.start();
        apocbr = new AgentReleaseCache(directory.resolve("apocbr"), APOCBR,
                base.resolve("/apocbr/latest"), base.resolve("/apocbr/download/"));
        buddy = new AgentReleaseCache(directory.resolve("buddy"), ZOMBIE_BUDDY,
                base.resolve("/buddy/latest"), base.resolve("/buddy/download/"));
    }

    @AfterEach
    void stopFixture() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void comparesTheLatestReleaseOnEachLaunchAndReusesItsVerifiedJar() throws Exception {
        var progress = new ArrayList<AgentReleaseCache.Progress>();
        Path cached = buddy.resolve(progress::add);

        assertArrayEquals(buddyJar, Files.readAllBytes(cached));
        progress.clear();
        assertEquals(cached, buddy.resolve(progress::add));
        assertEquals(2, buddyChecks.get());
        assertEquals(1, buddyDownloads.get());
        assertEquals(List.of(new AgentReleaseCache.Progress(ZOMBIE_BUDDY, CHECKING),
                new AgentReleaseCache.Progress(ZOMBIE_BUDDY, VERIFIED)), progress);

        Files.writeString(cached, "corrupt");
        assertEquals(cached, buddy.resolve(progress::add));
        assertEquals(2, buddyDownloads.get());
        assertArrayEquals(buddyJar, Files.readAllBytes(cached));
    }

    @Test
    void downloadsTheNewAssetWhenLatestReleaseDigestChanges() throws Exception {
        Path previous = buddy.resolve(ignored -> {});
        buddyJar = agentJar(ZOMBIE_BUDDY_PREMAIN, "new release");
        buddyDownload = buddyJar;

        Path latest = buddy.resolve(ignored -> {});

        assertFalse(previous.equals(latest));
        assertArrayEquals(buddyJar, Files.readAllBytes(latest));
        assertEquals(2, buddyDownloads.get());
    }

    @Test
    void rejectsWrongDigestAndWrongJavaAgentEntryPoint() throws Exception {
        buddyDownload = buddyJar.clone();
        buddyDownload[0] ^= 1;
        IOException badDigest = assertThrows(IOException.class, () -> buddy.resolve(ignored -> {}));
        assertTrue(badDigest.getMessage().contains("SHA-256"));

        buddyJar = agentJar(APOCBR_PREMAIN);
        buddyDownload = buddyJar;
        IOException badAgent = assertThrows(IOException.class, () -> buddy.resolve(ignored -> {}));
        assertTrue(badAgent.getMessage().contains("ZombieBuddy Java agent entry point"));
        try (var files = Files.list(directory.resolve("buddy"))) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void viewpointChecksBothReleasesWithoutOptimizationsAndStopsWhenZombieBuddyIsUnpublished() throws Exception {
        buddyReleaseStatus = 404;
        Path installation = Files.createDirectory(directory.resolve("game"));
        Files.createFile(installation.resolve("ProjectZomboid64.exe"));
        Files.createFile(installation.resolve("projectzomboid.jar"));
        Path javaBin = Files.createDirectories(installation.resolve("jre64/bin"));
        Files.createFile(javaBin.resolve("javaw.exe"));
        var workshop = new ViewpointWorkshop(directory, (ids, event) -> {
            throw new AssertionError("Workshop checked before the agent release was verified");
        });
        var launcher = new ProjectZomboidLauncher(directory, apocbr, buddy, workshop, () -> true);
        var progress = new ArrayList<AgentReleaseCache.Progress>();

        IOException missing = assertThrows(IOException.class,
                () -> launcher.launch(installation, ZomboidLaunchMode.VIEWPOINT, Set.of(), progress::add, ignored -> {}));

        assertTrue(missing.getMessage().contains("ZombieBuddy latest GitHub release request failed with HTTP 404"));
        assertEquals(List.of(APOCBR, ZOMBIE_BUDDY), progress.stream()
                .filter(update -> update.status() == CHECKING).map(AgentReleaseCache.Progress::agent).toList());
        assertEquals(0, buddyDownloads.get());
        assertFalse(Files.exists(directory.resolve("project-zomboid-console.log")));

        progress.clear();
        assertThrows(IOException.class, () -> launcher.launch(installation, ZomboidLaunchMode.STANDARD,
                Set.of(), progress::add, ignored -> {}));
        assertTrue(progress.stream().anyMatch(update -> update.agent() == APOCBR && update.status() == VERIFIED));
        assertEquals(2, buddyChecks.get());
    }

    @Test
    void selectedProfileRunsWorkshopSynchronizationAfterVerifyingBothAgents() throws Exception {
        Path installation = Files.createDirectory(directory.resolve("game"));
        Files.createFile(installation.resolve("ProjectZomboid64.exe"));
        Files.createFile(installation.resolve("projectzomboid.jar"));
        Files.createFile(Files.createDirectories(installation.resolve("jre64/bin")).resolve("javaw.exe"));
        var progress = new ArrayList<AgentReleaseCache.Progress>();
        var requestedItems = new ArrayList<List<String>>();
        var workshop = new ViewpointWorkshop(directory.resolve("user-game"), (ids, event) -> {
            requestedItems.add(List.copyOf(ids));
            throw new IOException("Workshop unavailable");
        });
        var launcher = new ProjectZomboidLauncher(directory, apocbr, buddy, workshop, () -> true);

        for (ZomboidLaunchMode mode : ZomboidLaunchMode.values()) {
            progress.clear();
            IOException failure = assertThrows(IOException.class,
                    () -> launcher.launch(installation, mode, Set.of(), progress::add,
                            workshopEvent -> assertEquals(List.of(APOCBR, ZOMBIE_BUDDY), progress.stream()
                                    .filter(event -> event.status() == VERIFIED)
                                    .map(AgentReleaseCache.Progress::agent).toList())));
            assertEquals("Workshop unavailable", failure.getMessage());
        }

        assertEquals(List.of(List.of("3619862853"),
                List.of("3619862853", "3809306528", "3810302175"),
                List.of("3619862853", "3809306528", "3818189653", "3810302175"),
                List.of("3619862853", "3809306528", "3818189653", "3819018807", "3810302175")), requestedItems);
        assertFalse(Files.exists(directory.resolve("project-zomboid-console.log")));
    }

    private static byte[] agentJar(String premain) throws IOException {
        return agentJar(premain, "original");
    }

    private static byte[] agentJar(String premain, String version) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", premain);
        manifest.getMainAttributes().putValue("Implementation-Version", version);
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes, manifest)) {
            jar.putNextEntry(new JarEntry(premain.replace('.', '/') + ".class"));
            jar.closeEntry();
            if (ZOMBIE_BUDDY_PREMAIN.equals(premain)) {
                jar.putNextEntry(new JarEntry("me/zed_0xff/zombie_buddy/LauncherModTemplate.class"));
                jar.closeEntry();
                jar.putNextEntry(new JarEntry("me/zed_0xff/zombie_buddy/LauncherModTrust$Approval.class"));
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] releaseMetadata(String name, URI download, byte[] bytes) {
        return """
                {"tag_name": "v1", "assets": [{"name": "%s", "state": "uploaded", "size": %d,
                    "digest": "sha256:%s", "browser_download_url": "%s"}]}
                """.formatted(name, bytes.length, sha256(bytes), download).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
