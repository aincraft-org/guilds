package dev.mintychochip.territory.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static editor serving without PostgreSQL.
 */
class StaticFileHandlerTest {

    private HttpServer server;
    private int port;
    private final WebConfig config = WebConfig.defaults();

    @BeforeEach
    void setUp() throws IOException {
        port = freePort();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/editor", new StaticFileHandler(
                "/editor",
                "dev/mintychochip/territory/web/static/editor",
                config
        ));
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void relativePathRejectsTraversalAndOutsidePrefix() {
        StaticFileHandler h = new StaticFileHandler("/editor", "dev/mintychochip/territory/web/static/editor", config);
        assertEquals("index.html", h.relativePath("/editor/index.html"));
        assertEquals("js/app.js", h.relativePath("/editor/js/app.js"));
        assertEquals("", h.relativePath("/editor"));
        assertNull(h.relativePath("/editor/../secret"));
        assertNull(h.relativePath("/editor/foo/../../secret"));
        assertNull(h.relativePath("/api/health"));
        assertNull(h.relativePath("/editorx/index.html"));
    }

    @Test
    void servesIndexAndJsFromClasspath() throws Exception {
        String base = "http://127.0.0.1:" + port;
        assertBodyContains(base + "/editor/", "Guilds Map Editor");
        assertBodyContains(base + "/editor/index.html", "leaflet");
        assertBodyContains(base + "/editor/js/app.js", "bootstrap");
        assertBodyContains(base + "/editor/css/editor.css", "--panel");

        HttpURLConnection missing = open(base + "/editor/nope.js");
        assertEquals(404, missing.getResponseCode());
    }

    private static void assertBodyContains(String url, String needle) throws IOException {
        HttpURLConnection conn = open(url);
        assertEquals(200, conn.getResponseCode(), url);
        String body;
        try (InputStream in = conn.getInputStream()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(body.contains(needle), () -> "expected '" + needle + "' in " + url);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
