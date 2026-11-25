package cloud.apocalipse.launcher.infrastructure.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherReleasesTest {
    private static final String ASSET = "launcher-v0.1.10/ApocalipseWoW-0.1.10.msi";
    private static final byte[] PACKAGE = "test installer".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path cache;
    private final AtomicInteger downloads = new AtomicInteger();
    private volatile String metadata;
    private volatile byte[] installer = PACKAGE;
    private HttpServer server;
    private URI downloadBase;
    private LauncherReleases releases;

    @BeforeEach
    void startFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        downloadBase = base.resolve("/download/");
        metadata = metadata(downloadBase.resolve(ASSET).toString());
        server.createContext("/latest", exchange -> respond(exchange, metadata.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/download/" + ASSET, exchange -> {
            downloads.incrementAndGet();
            respond(exchange, installer);
        });
        server.start();
        releases = new LauncherReleases(cache, HttpClient.newHttpClient(), base.resolve("/latest"), downloadBase);
    }

    @AfterEach
    void stopFixture() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void comparesVersionFieldsNumericallyAndSkipsSameOrOlderReleases() throws Exception {
        var update = releases.check("0.1.9").orElseThrow();
        assertEquals("0.1.10", update.version());
        assertEquals("https://github.com/rbm4/apocalipse-wow-launcher/releases/tag/launcher-v0.1.10",
                update.page().toString());
        assertTrue(releases.check("0.1.10").isEmpty());
        assertTrue(releases.check("0.2.0").isEmpty());
        assertTrue(releases.check("0.1.7-SNAPSHOT").isEmpty());
    }

    @Test
    void ignoresPrerelease() throws Exception {
        metadata = metadata.replace("\"prerelease\": false", "\"prerelease\": true");
        assertTrue(releases.check("0.1.9").isEmpty());
    }

    @Test
    void rejectsUnexpectedDownloadUrl() {
        metadata = metadata.replace(downloadBase.toString(), "https://example.invalid/");
        assertThrows(IOException.class, () -> releases.check("0.1.9"));
        assertEquals(0, downloads.get());
    }

    @Test
    void cachesVerifiedDownloadAndReplacesCorruptedCopy() throws Exception {
        var update = releases.check("0.1.9").orElseThrow();
        Path saved = releases.download(update);
        assertArrayEquals(PACKAGE, Files.readAllBytes(saved));
        assertEquals(saved, releases.download(update));
        assertEquals(1, downloads.get());
        Files.write(saved, "bad".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(PACKAGE, Files.readAllBytes(releases.download(update)));
        assertEquals(2, downloads.get());
    }

    @Test
    void rejectsWrongSizeOrDigestWithoutKeepingPartialFiles() throws Exception {
        var update = releases.check("0.1.9").orElseThrow();
        installer = "unexpected".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> releases.download(update));
        installer = PACKAGE.clone();
        installer[0] ^= 1;
        assertThrows(IOException.class, () -> releases.download(update));
        try (var files = Files.list(cache)) {
            assertEquals(0L, files.count());
        }
    }

    private static String metadata(String url) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PACKAGE));
        return """
                {"tag_name": "launcher-v0.1.10", "prerelease": false, "assets": [
                   {"name": "ApocalipseWoW-0.1.10.msi", "state": "uploaded", "size": %d,
                    "digest": "sha256:%s", "browser_download_url": "%s"}
                ]}
                """.formatted(PACKAGE.length, digest, url);
    }

    private static void respond(HttpExchange exchange, byte[] bytes) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
