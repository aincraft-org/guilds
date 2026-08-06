package com.azoth.territory.web;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.Zone;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.TerritoryStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the real embedded web server and drives HTTP/HTTPS endpoints end-to-end.
 */
class TerritoryWebServerTest {

    @TempDir
    Path tempDir;

    private TerritoryRegistry registry;
    private TerritoryStore store;
    private TerritoryWebServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        registry = new TerritoryRegistry();
        store = new TerritoryStore(tempDir.resolve("territories.json"));
        registry.register(new Territory(
                "everfall",
                "Everfall",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(500, 0),
                        new BlockPos(500, 500),
                        new BlockPos(0, 500)
                )),
                List.of(new Zone(
                        "plot-a",
                        "Plot A",
                        ZoneType.CLAIMABLE,
                        Boundary.ofPolygon(List.of(
                                new BlockPos(100, 100),
                                new BlockPos(200, 100),
                                new BlockPos(200, 200),
                                new BlockPos(100, 200)
                        )),
                        10
                )),
                ZoneType.WILDERNESS
        ));
        port = freePort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void httpApi_listResolveAndStaticUi() throws Exception {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", true, "", true,
                WebConfig.TlsSettings.disabled()
        );
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(), () -> store, () -> java.util.Optional.empty(), Logger.getGlobal());
        server.start();
        assertTrue(server.isRunning());

        // Health
        JsonObject health = getJson("http://127.0.0.1:" + port + "/api/health");
        assertEquals("ok", health.get("status").getAsString());
        assertEquals(1, health.get("territories").getAsInt());
        assertFalse(health.get("tls").getAsBoolean());

        // List territories via real API → shipped registry
        JsonObject list = getJson("http://127.0.0.1:" + port + "/api/territories");
        assertEquals(1, list.getAsJsonArray("territories").size());
        assertEquals("everfall", list.getAsJsonArray("territories").get(0).getAsJsonObject().get("id").getAsString());

        // Resolve claimable interior
        JsonObject resolve = getJson("http://127.0.0.1:" + port + "/api/resolve?world=world&x=150&z=150");
        assertTrue(resolve.get("contained").getAsBoolean());
        assertEquals("everfall", resolve.get("territoryId").getAsString());
        assertEquals("CLAIMABLE", resolve.get("zoneType").getAsString());
        assertEquals("plot-a", resolve.get("zoneId").getAsString());

        // Resolve outside
        JsonObject outside = getJson("http://127.0.0.1:" + port + "/api/resolve?world=world&x=9000&z=9000");
        assertFalse(outside.get("contained").getAsBoolean());

        // Static UI
        String html = getText("http://127.0.0.1:" + port + "/");
        assertTrue(html.contains("Azoth Territory"), "index.html should be served");
        String js = getText("http://127.0.0.1:" + port + "/app.js");
        assertTrue(js.contains("/api/territories"));

        // Reverse-proxy meta
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/meta")
                .toURL().openConnection();
        conn.setRequestProperty("X-Forwarded-Proto", "https");
        conn.setRequestProperty("X-Forwarded-Host", "map.example.com");
        conn.setRequestProperty("X-Forwarded-For", "198.51.100.4");
        String metaBody = readAll(conn);
        assertEquals(200, conn.getResponseCode());
        JsonObject meta = JsonParser.parseString(metaBody).getAsJsonObject();
        assertEquals("https", meta.get("scheme").getAsString());
        assertEquals("map.example.com", meta.get("host").getAsString());
        assertEquals("https://map.example.com", meta.get("publicOrigin").getAsString());
        assertTrue(meta.get("secure").getAsBoolean());
        assertEquals("198.51.100.4", meta.get("clientIp").getAsString());

        System.out.println("WEB_SMOKE resolve territoryId=" + resolve.get("territoryId").getAsString()
                + " zoneType=" + resolve.get("zoneType").getAsString());
        System.out.println("WEB_SMOKE proxy publicOrigin=" + meta.get("publicOrigin").getAsString());
    }

    @Test
    void httpApi_putAndDeleteTerritory_persists() throws Exception {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", false, "", true,
                WebConfig.TlsSettings.disabled()
        );
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(), () -> store, () -> java.util.Optional.empty(), Logger.getGlobal());
        server.start();

        String body = """
                {
                  "id": "brightwood",
                  "name": "Brightwood",
                  "world": "world",
                  "defaultZoneType": "WILDERNESS",
                  "boundary": {
                    "polygon": [
                      {"x": 1000, "z": 1000},
                      {"x": 1200, "z": 1000},
                      {"x": 1200, "z": 1200},
                      {"x": 1000, "z": 1200}
                    ],
                    "chunks": []
                  },
                  "zones": []
                }
                """;
        JsonObject put = putJson("http://127.0.0.1:" + port + "/api/territories/brightwood", body);
        assertEquals("brightwood", put.get("id").getAsString());
        assertEquals(2, registry.size());
        assertTrue(Files.isRegularFile(store.file()));

        int del = delete("http://127.0.0.1:" + port + "/api/territories/brightwood");
        assertEquals(204, del);
        assertFalse(registry.get("brightwood").isPresent());
    }

    @Test
    void apiToken_requiredWhenConfigured() throws Exception {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", false, "secret-token", true,
                WebConfig.TlsSettings.disabled()
        );
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(), () -> store, () -> java.util.Optional.empty(), Logger.getGlobal());
        server.start();

        HttpURLConnection unauthorized = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/territories").toURL().openConnection();
        assertEquals(401, unauthorized.getResponseCode());

        HttpURLConnection ok = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/territories").toURL().openConnection();
        ok.setRequestProperty("X-Api-Token", "secret-token");
        assertEquals(200, ok.getResponseCode());

        // health stays open
        assertEquals(200, ((HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/health").toURL().openConnection()).getResponseCode());
    }

    @Test
    void httpsTls_servesHealthWithKeystore() throws Exception {
        Path ksPath = tempDir.resolve("test.p12");
        String password = "testpass";
        createSelfSignedKeystore(ksPath, password);

        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", false, "", true,
                WebConfig.TlsSettings.of(ksPath, password)
        );
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(), () -> store, () -> java.util.Optional.empty(), Logger.getGlobal());
        server.start();

        trustAllHttps();
        HttpsURLConnection conn = (HttpsURLConnection) URI.create(
                "https://127.0.0.1:" + port + "/api/health").toURL().openConnection();
        conn.setHostnameVerifier((h, s) -> true);
        String body = readAll(conn);
        assertEquals(200, conn.getResponseCode());
        JsonObject health = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("ok", health.get("status").getAsString());
        assertTrue(health.get("tls").getAsBoolean());
        System.out.println("WEB_TLS health status=" + health.get("status").getAsString()
                + " tls=" + health.get("tls").getAsBoolean());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(getText(url)).getAsJsonObject();
    }

    private static String getText(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        int code = conn.getResponseCode();
        String body = readAll(conn);
        if (code >= 400) {
            throw new IOException("GET " + url + " → " + code + " " + body);
        }
        return body;
    }

    private static JsonObject putJson(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String resp = readAll(conn);
        assertEquals(200, conn.getResponseCode(), resp);
        return JsonParser.parseString(resp).getAsJsonObject();
    }

    private static int delete(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("DELETE");
        return conn.getResponseCode();
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

    private static void trustAllHttps() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {
                    }

                    public void checkServerTrusted(X509Certificate[] c, String a) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    /**
     * Creates a PKCS12 keystore with a short-lived self-signed cert for TLS tests
     * via the JDK {@code keytool} utility.
     */
    private static void createSelfSignedKeystore(Path path, String password) throws Exception {
        Path tmpDir = path.getParent();
        ProcessBuilder pb = new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias", "azoth",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-storetype", "PKCS12",
                "-keystore", path.toAbsolutePath().toString(),
                "-storepass", password,
                "-keypass", password,
                "-dname", "CN=localhost,O=Azoth,C=US"
        );
        pb.directory(tmpDir == null ? null : tmpDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0 || !Files.isRegularFile(path)) {
            throw new IllegalStateException("keytool failed (" + code + "): " + out);
        }
    }
}
