package com.azoth.territory.web;

import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.persist.PostgresTerritoryStore;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session cookie login against the real embedded web server.
 */
class SessionAuthWebTest {

    private PostgresDatabase database;
    private TerritoryRegistry registry;
    private PostgresTerritoryStore store;
    private int port;
    private TerritoryWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        registry = new TerritoryRegistry();
        store = new PostgresTerritoryStore(database);
        port = freePort();
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
    void sessionCookieAuthorizesMutations() throws Exception {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", false, "secret-token", true,
                WebConfig.TlsSettings.disabled(),
                "http://localhost:8080",
                3600
        );
        server = new TerritoryWebServer(
                cfg, registry, new TerritoryJson(), store, OptionalEmpty.supplier(), Logger.getGlobal());
        server.start();

        String base = "http://127.0.0.1:" + port;

        // No auth → 401
        assertEquals(401, getCode(base + "/api/territories", null));

        // Wrong token login → 401
        HttpURLConnection badLogin = postJson(base + "/api/session", "{\"token\":\"nope\"}", null);
        assertEquals(401, badLogin.getResponseCode());

        // Good login → Set-Cookie
        HttpURLConnection login = postJson(base + "/api/session", "{\"token\":\"secret-token\"}", null);
        assertEquals(200, login.getResponseCode());
        String cookie = sessionCookie(login);
        assertTrue(cookie != null && cookie.startsWith(SessionStore.COOKIE_NAME + "="), cookie);

        // Cookie authorizes GET list
        assertEquals(200, getCode(base + "/api/territories", cookie));

        // Cookie authorizes PUT
        String body = """
                {
                  "id": "session-land",
                  "name": "Session Land",
                  "world": "world",
                  "defaultZoneType": "WILDERNESS",
                  "boundary": {
                    "polygon": [
                      {"x": 0, "z": 0},
                      {"x": 160, "z": 0},
                      {"x": 160, "z": 160},
                      {"x": 0, "z": 160}
                    ],
                    "chunks": []
                  },
                  "zones": []
                }
                """;
        HttpURLConnection put = putJson(base + "/api/territories/session-land", body, cookie);
        assertEquals(200, put.getResponseCode(), readAll(put));
        assertTrue(registry.get("session-land").isPresent());

        // Header token still works without cookie
        HttpURLConnection headerGet = (HttpURLConnection) URI.create(base + "/api/territories").toURL()
                .openConnection();
        headerGet.setRequestProperty("X-Api-Token", "secret-token");
        assertEquals(200, headerGet.getResponseCode());

        // Logout
        HttpURLConnection logout = (HttpURLConnection) URI.create(base + "/api/session").toURL()
                .openConnection();
        logout.setRequestMethod("DELETE");
        logout.setRequestProperty("Cookie", cookie);
        assertEquals(204, logout.getResponseCode());

        // Old cookie no longer valid
        assertEquals(401, getCode(base + "/api/territories", cookie));

        // meta exposes editor fields
        JsonObject meta = JsonParser.parseString(getText(base + "/api/meta", null)).getAsJsonObject();
        assertTrue(meta.get("authRequired").getAsBoolean());
        assertEquals("http://localhost:8080", meta.get("squaremapTileBaseUrl").getAsString());
        assertEquals(3600, meta.get("sessionTtlSeconds").getAsLong());
    }

    private static String sessionCookie(HttpURLConnection conn) {
        List<String> headers = conn.getHeaderFields().get("Set-Cookie");
        if (headers == null) {
            // Some JDKs use lowercase
            headers = conn.getHeaderFields().get("set-cookie");
        }
        if (headers == null) {
            return null;
        }
        for (String h : headers) {
            if (h != null && h.startsWith(SessionStore.COOKIE_NAME + "=")) {
                int semi = h.indexOf(';');
                return semi < 0 ? h : h.substring(0, semi);
            }
        }
        return null;
    }

    private static int getCode(String url, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn.getResponseCode();
    }

    private static String getText(String url, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        int code = conn.getResponseCode();
        String body = readAll(conn);
        if (code >= 400) {
            throw new IOException("GET " + url + " → " + code + " " + body);
        }
        return body;
    }

    private static HttpURLConnection postJson(String url, String body, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        conn.getResponseCode();
        return conn;
    }

    private static HttpURLConnection putJson(String url, String body, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        conn.getResponseCode();
        return conn;
    }

    private static String readAll(HttpURLConnection conn) throws IOException {
        InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            return "";
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /** Avoid raw Optional::empty method ref noise in constructor. */
    private static final class OptionalEmpty {
        static java.util.function.Supplier<java.util.Optional<com.azoth.territory.influence.InfluenceService>>
        supplier() {
            return java.util.Optional::empty;
        }
    }
}
