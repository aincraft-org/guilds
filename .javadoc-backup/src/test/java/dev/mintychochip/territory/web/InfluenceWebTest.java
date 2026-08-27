package dev.mintychochip.territory.web;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.influence.DeclareResult;
import dev.mintychochip.territory.influence.DeclareStatus;
import dev.mintychochip.territory.influence.Declaration;
import dev.mintychochip.territory.persist.TerritoryJson;
import dev.mintychochip.territory.influence.InfluenceBar;
import dev.mintychochip.territory.influence.InfluenceService;
import dev.mintychochip.territory.influence.TerritoryInfluenceState;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.territory.persist.PostgresTerritoryStore;
import dev.mintychochip.territory.persist.PostgresDatabase;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceWebTest {


    private TerritoryRegistry registry;
    private PostgresDatabase database;
    private PostgresTerritoryStore store;
    private int port;
    private TerritoryWebServer server;

    private static InfluenceService service() {
        return new InfluenceService() {
            @Override
            public Optional<TerritoryInfluenceState> influence(String territoryId) {
                if (!"everfall".equals(territoryId)) {
                    return Optional.empty();
                }
                return Optional.of(new TerritoryInfluenceState("everfall", "everfall-town",
                        0L, List.of(new InfluenceBar("rival-guild", 62.5)),
                        new Declaration("rival-guild", 100L, 200L)));
            }

            @Override
            public List<TerritoryInfluenceState> all() {
                return List.of(new TerritoryInfluenceState("everfall", "everfall-town",
                        0L, List.of(new InfluenceBar("rival-guild", 62.5)),
                        new Declaration("rival-guild", 100L, 200L)));
            }

            @Override
            public DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs) {
                return DeclareResult.error(DeclareStatus.RACE_ACTIVE, "read-only in web tests");
            }

            @Override
            public DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs) {
                return DeclareResult.error(DeclareStatus.RACE_ACTIVE, "read-only in web tests");
            }

            @Override
            public boolean isDeclarable(String territoryId, String guildId, long nowEpochMs) {
                return false;
            }

            @Override
            public boolean isCooldownActive(String territoryId, long nowEpochMs) {
                return false;
            }
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        registry = new TerritoryRegistry();
        store = new PostgresTerritoryStore(database);
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS));
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

    private void startServer(Optional<InfluenceService> service) throws Exception {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", port, "", true, "", true,
                WebConfig.TlsSettings.disabled()
        );
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(),
                store, () -> service, Logger.getLogger("influence-web-test"));
        server.start();
    }

    private String get(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + path).toURL().openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        int code = connection.getResponseCode();
        assertEquals(200, code, "GET " + path);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void apiTerritories_includesInfluenceWhenEnginePresent() throws Exception {
        startServer(Optional.of(service()));

        JsonObject body = JsonParser.parseString(get("/api/territories/everfall")).getAsJsonObject();
        JsonObject influence = body.getAsJsonObject("influence");
        assertEquals("everfall-town", influence.get("ownerGuildId").getAsString());
        JsonArray bars = influence.getAsJsonArray("bars");
        assertEquals(1, bars.size());
        assertEquals("rival-guild", bars.get(0).getAsJsonObject().get("guildId").getAsString());
        assertEquals(62.5, bars.get(0).getAsJsonObject().get("value").getAsDouble(), 0.001);
        assertEquals("rival-guild",
                influence.getAsJsonObject("declaration").get("guildId").getAsString());
    }

    @Test
    void apiTerritories_omitsInfluenceWhenEngineAbsent() throws Exception {
        startServer(Optional.empty());

        JsonObject body = JsonParser.parseString(get("/api/territories/everfall")).getAsJsonObject();
        assertTrue(body.get("influence") == null || body.get("influence").isJsonNull());
    }

    @Test
    void apiInfluence_listsAllStates() throws Exception {
        startServer(Optional.of(service()));

        JsonArray body = JsonParser.parseString(get("/api/influence")).getAsJsonArray();
        assertEquals(1, body.size());
        assertEquals("everfall", body.get(0).getAsJsonObject().get("territoryId").getAsString());
    }

    @Test
    void apiInfluence_returns404WhenEngineAbsent() throws Exception {
        startServer(Optional.empty());
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/influence").toURL().openConnection();
        assertEquals(404, connection.getResponseCode());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
