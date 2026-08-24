package org.aincraft.guilds.territory.web;

import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.persist.PostgresDatabase;
import org.aincraft.guilds.territory.persist.PostgresTerritoryStore;
import org.aincraft.guilds.territory.persist.TerritoryJson;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: web API mutations must not diverge from the store — a failed
 * remote save (e.g. PostgreSQL unreachable) returns 500 and leaves the live
 * registry untouched.
 */
class TerritoryApiPersistenceTest {

    private TerritoryRegistry registry;
    private PostgresDatabase database;
    private TerritoryWebServer server;
    private int port;
    private FailingStore store;

    /** Store whose save always fails — simulates an unreachable remote PostgreSQL. */
    private static final class FailingStore extends PostgresTerritoryStore {
        final AtomicInteger saveAttempts = new AtomicInteger();

        private FailingStore(PostgresDatabase database) {
            super(database);
        }

        @Override
        public void save(TerritoryRegistry registry) throws IOException {
            saveAttempts.incrementAndGet();
            throw new IOException("connection refused (test)");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)))));
        database = PostgresTestDatabase.open();
        store = new FailingStore(database);
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
    void failedPersistOnPutReturns500AndLeavesRegistryUntouched() throws Exception {
        server = startServer();
        int code = send("PUT", "/api/territories/north",
                "{\"id\":\"north\",\"name\":\"North\",\"world\":\"world\","
                        + "\"boundary\":{\"polygon\":[{\"x\":200,\"z\":200},{\"x\":300,\"z\":200},"
                        + "{\"x\":300,\"z\":300},{\"x\":200,\"z\":300}],\"chunks\":[]}}");
        assertEquals(500, code);
        assertTrue(registry.get("north").isEmpty(), "failed save must not mutate the live registry");
        assertEquals(1, registry.size());
        assertEquals(1, store.saveAttempts.get());
    }

    @Test
    void failedPersistOnDeleteReturns500AndLeavesRegistryUntouched() throws Exception {
        server = startServer();
        int code = send("DELETE", "/api/territories/everfall", null);
        assertEquals(500, code);
        assertTrue(registry.get("everfall").isPresent(), "failed save must not mutate the live registry");
        assertEquals(1, registry.size());
        assertEquals(1, store.saveAttempts.get());
    }

    private TerritoryWebServer startServer() throws IOException {
        TerritoryWebServer s = new TerritoryWebServer(
                new WebConfig(true, "127.0.0.1", port, "", true, "", true, WebConfig.TlsSettings.disabled()),
                registry, new TerritoryJson(), store, () -> java.util.Optional.empty(), Logger.getGlobal());
        s.start();
        return s;
    }

    private int send(String method, String path, String body) throws IOException {
        HttpURLConnection con = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        if (body != null) {
            con.setDoOutput(true);
            con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        if (in != null) {
            in.close();
        }
        con.disconnect();
        return code;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
