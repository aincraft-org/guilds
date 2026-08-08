package com.azoth.territory.web;

import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.persist.PostgresTerritoryStore;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorStaticWebTest {

    private PostgresDatabase database;
    private TerritoryWebServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        TerritoryRegistry registry = new TerritoryRegistry();
        PostgresTerritoryStore store = new PostgresTerritoryStore(database);
        port = freePort();
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", false, "", true,
                WebConfig.TlsSettings.disabled()
        );
        server = new TerritoryWebServer(
                cfg, registry, new TerritoryJson(), store, Optional::empty, Logger.getGlobal());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    void editorIndexAndAssetsAreServed() throws Exception {
        String base = "http://127.0.0.1:" + port;
        assertHtml(base + "/editor/");
        assertHtml(base + "/editor/index.html");
        assertContains(base + "/editor/js/app.js", "bootstrap");
        assertContains(base + "/editor/css/editor.css", "--bg");
        HttpURLConnection bad = (HttpURLConnection) URI.create(base + "/editor/foo/../../secret")
                .toURL().openConnection();
        assertEquals(404, bad.getResponseCode());
    }

    private static void assertHtml(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        assertEquals(200, conn.getResponseCode(), url);
        assertTrue(conn.getContentType().contains("text/html"), conn.getContentType());
        String body = read(conn);
        assertTrue(body.contains("Azoth Map Editor"), body);
    }

    private static void assertContains(String url, String needle) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        assertEquals(200, conn.getResponseCode(), url);
        String body = read(conn);
        assertTrue(body.contains(needle), "expected " + needle + " in " + url);
    }

    private static String read(HttpURLConnection conn) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
