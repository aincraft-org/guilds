# Postgres-Backed Web API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hook the territory REST API (`/api/*` in the embedded web submodule) into a remote PostgreSQL database as its durable store, replacing the `territories.json` file when `database.enabled: true`, with storage-commits-before-memory mutation semantics.

**Architecture:** Keep the embedded `TerritoryWebServer` + `TerritoryApiHandler`. Introduce a `TerritoryRepository` seam (`TerritoryStore` = JSON file, `PostgresTerritoryRepository` = remote Postgres via HikariCP). The plugin picks the implementation from `database.*` config; Postgres failure is loud (no fallback). API mutations stage on a registry copy, persist, then swap the live registry — a failed remote save returns 500 and never diverges memory from the DB.

**Tech Stack:** Java 21, Gradle multi-module (`api`/`common`/`paper`), JDK `HttpServer`, Gson, HikariCP 5.1.0, PostgreSQL JDBC 42.7.13, JUnit 5.

## Global Constraints

- All new production code goes in `common/src/main/java/com/azoth/territory/persist/` (Paper-free) or modifies the existing web classes.
- `TerritoryRepository` methods: `void loadInto(TerritoryRegistry registry) throws IOException`, `void save(TerritoryRegistry registry) throws IOException`, `void close()` (no checked throws). SQL errors are wrapped in `IOException`.
- Config keys are `database.enabled|host|port|name|user|password|ssl|pool-size|jdbc-url`, read from the flattened Bukkit config map (`getValues(true)`), mirroring `WebConfigLoader`.
- Postgres schema is exactly: `CREATE TABLE IF NOT EXISTS territories (id TEXT PRIMARY KEY, doc JSONB NOT NULL)`.
- Driver version pin: `org.postgresql:postgresql:42.7.13`; HikariCP: `com.zaxxer:HikariCP:5.1.0`.
- No JSON fallback when `database.enabled: true` and Postgres is unreachable: store stays null, web submodule does not start, SEVERE logged.
- API mutations with no store at all must fail (500) — `persistOrThrow` throws when `storeSupplier.get()` is null; a null store is never treated as success.
- Working tree has uncommitted WIP (influence endpoints in web classes, `InfluenceWebTest.java`). Do not revert it; edits apply on top. Do not commit the WIP files.
- Commit messages match repo style: imperative, `feat:` prefix (see `git log --oneline`).
- Verification commands: `./gradlew :common:test` and full `./gradlew build`; the whole suite must be green before each commit.

---

### Task 1: Repository Seam

**Files:**
- Create: `common/src/main/java/com/azoth/territory/persist/TerritoryRepository.java`
- Modify: `common/src/main/java/com/azoth/territory/persist/TerritoryStore.java`
- Modify: `common/src/main/java/com/azoth/territory/web/TerritoryWebServer.java`
- Modify: `common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java`

**Interfaces:**
- Produces: `TerritoryRepository` — implemented by `TerritoryStore` (Task 1) and `PostgresTerritoryRepository` (Task 4); consumed by `TerritoryWebServer`/`TerritoryApiHandler` (Task 1, 2) and `AzothTerritoryPlugin` (Task 5).

- [ ] **Step 1: Create the interface**

`common/src/main/java/com/azoth/territory/persist/TerritoryRepository.java`:

```java
package com.azoth.territory.persist;

import com.azoth.territory.registry.TerritoryRegistry;

import java.io.IOException;

/**
 * Durable store for the territory registry.
 * <p>
 * Implementations: {@link TerritoryStore} (JSON file) and
 * {@link PostgresTerritoryRepository} (remote PostgreSQL). Callers — the web
 * API and plugin persistence — depend only on this seam, so the store can be
 * swapped or extracted into a standalone service without touching them.
 */
public interface TerritoryRepository extends AutoCloseable {
    /** Replace the registry contents from durable storage. */
    void loadInto(TerritoryRegistry registry) throws IOException;

    /** Persist the full registry (atomic replace). */
    void save(TerritoryRegistry registry) throws IOException;

    /** Release backing resources; no-op for file-backed stores. */
    @Override
    void close();
}
```

- [ ] **Step 2: Make `TerritoryStore` implement it**

In `common/src/main/java/com/azoth/territory/persist/TerritoryStore.java`:

1. Change the class declaration to `public final class TerritoryStore implements TerritoryRepository {`
2. Add before the closing brace:

```java
    @Override
    public void close() {
        // File-backed: nothing to release.
    }
```

- [ ] **Step 3: Widen the web server's store supplier**

In `common/src/main/java/com/azoth/territory/web/TerritoryWebServer.java`:

1. Replace `import com.azoth.territory.persist.TerritoryStore;` with `import com.azoth.territory.persist.TerritoryRepository;`
2. Change the field `private final Supplier<TerritoryStore> storeSupplier;` to `Supplier<TerritoryRepository>`.
3. Change the constructor parameter `Supplier<TerritoryStore> storeSupplier` to `Supplier<TerritoryRepository>`.

- [ ] **Step 4: Widen the API handler's store supplier**

In `common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java`:

1. Replace `import com.azoth.territory.persist.TerritoryStore;` with `import com.azoth.territory.persist.TerritoryRepository;`
2. Field `private final Supplier<TerritoryStore> storeSupplier;` → `Supplier<TerritoryRepository>`.
3. Constructor parameter `Supplier<TerritoryStore> storeSupplier` → `Supplier<TerritoryRepository>`.
4. In `persistQuietly()`: `TerritoryStore store = storeSupplier.get();` → `TerritoryRepository store = storeSupplier.get();`

- [ ] **Step 5: Verify**

Run: `./gradlew :common:test :paper:compileJava`
Expected: BUILD SUCCESSFUL; all common tests pass (web tests still compile because `() -> store` lambdas infer `Supplier<TerritoryRepository>` from a `TerritoryStore`).

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/azoth/territory/persist/TerritoryRepository.java common/src/main/java/com/azoth/territory/persist/TerritoryStore.java common/src/main/java/com/azoth/territory/web/TerritoryWebServer.java common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java
git commit -m "feat: introduce TerritoryRepository persistence seam"
```

---

### Task 2: Commit Storage Before Registry Mutations

**Files:**
- Modify: `common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java`
- Create: `common/src/test/java/com/azoth/territory/web/TerritoryApiPersistenceTest.java`

**Interfaces:**
- Consumes: `TerritoryRepository` (Task 1).
- Produces: behavior contract — `PUT`/`DELETE` mutations persist a *staged* registry copy before swapping the live registry; a failing save (or a missing store) yields HTTP 500 with the live registry untouched.

- [ ] **Step 1: Write the failing test**

`common/src/test/java/com/azoth/territory/web/TerritoryApiPersistenceTest.java`:

```java
package com.azoth.territory.web;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Territory;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.TerritoryRepository;
import com.azoth.territory.registry.TerritoryRegistry;
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
    private FailingStore store;
    private TerritoryWebServer server;
    private int port;

    /** Store whose save always fails — simulates an unreachable remote PostgreSQL. */
    private static final class FailingStore implements TerritoryRepository {
        final AtomicInteger saveAttempts = new AtomicInteger();

        @Override
        public void loadInto(TerritoryRegistry registry) {
        }

        @Override
        public void save(TerritoryRegistry registry) throws IOException {
            saveAttempts.incrementAndGet();
            throw new IOException("connection refused (test)");
        }

        @Override
        public void close() {
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)))));
        store = new FailingStore();
        port = freePort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
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
                registry, new TerritoryJson(), () -> store, () -> java.util.Optional.empty(), Logger.getGlobal());
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests '*TerritoryApiPersistenceTest'`
Expected: FAIL — `failedPersistOnPutReturns500AndLeavesRegistryUntouched`: the current handler mutates the live registry before the (swallowed) save, so `registry.get("north")` is present and the 500 assertion likely fails (persist errors are swallowed → 200).

- [ ] **Step 3: Rewrite the mutation path in the handler**

In `common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java`:

1. Replace `upsertBody`:

```java
    private void upsertBody(HttpExchange exchange) throws IOException {
        String body = HttpResponses.readBody(exchange);
        Territory t = json.fromJsonString(body);
        TerritoryRegistry staged = stagedCopy();
        staged.register(t);
        persistOrThrow(staged);
        registry.replaceAll(staged.list());
        HttpResponses.json(exchange, 200, json.gson().toJson(json.toJson(t)), config);
    }
```

2. Replace `upsert`:

```java
    private void upsert(HttpExchange exchange, String id) throws IOException {
        String body = HttpResponses.readBody(exchange);
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        if (!o.has("id")) {
            o.addProperty("id", id);
        } else if (!id.equals(o.get("id").getAsString())) {
            throw new IllegalArgumentException("path id does not match body id");
        }
        Territory t = json.fromJson(o);
        TerritoryRegistry staged = stagedCopy();
        staged.register(t);
        persistOrThrow(staged);
        registry.replaceAll(staged.list());
        HttpResponses.json(exchange, 200, json.gson().toJson(json.toJson(t)), config);
    }
```

3. Replace `delete`:

```java
    private void delete(HttpExchange exchange, String id) throws IOException {
        TerritoryRegistry staged = stagedCopy();
        if (!staged.unregister(id)) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        persistOrThrow(staged);
        registry.replaceAll(staged.list());
        HttpResponses.noContent(exchange, config);
    }
```

4. Replace `persistQuietly` with:

```java
    /**
     * Copy of the live registry for a staged mutation. The copy is persisted
     * first; only a successful save swaps it into {@link #registry}, so a
     * failed remote save can never leave memory ahead of the store.
     */
    private TerritoryRegistry stagedCopy() {
        TerritoryRegistry next = new TerritoryRegistry();
        next.replaceAll(registry.list());
        return next;
    }

    private void persistOrThrow(TerritoryRegistry staged) throws IOException {
        TerritoryRepository store = storeSupplier.get();
        if (store == null) {
            throw new IllegalStateException("no territory store configured — mutations disabled");
        }
        store.save(staged);
    }
```

Note: the `handle()` catch-all already converts a thrown `Exception` (both `IOException` and `IllegalStateException`) into `HttpResponses.serverError` (HTTP 500) and logs it — no new error handling needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests '*TerritoryApiPersistenceTest'`
Expected: PASS (both tests). Then run `./gradlew :common:test` — full common suite green.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java common/src/test/java/com/azoth/territory/web/TerritoryApiPersistenceTest.java
git commit -m "feat: commit storage before registry mutations in web API"
```

---

### Task 3: Database Settings Config

**Files:**
- Create: `common/src/main/java/com/azoth/territory/persist/DatabaseSettings.java`
- Create: `common/src/main/java/com/azoth/territory/persist/DatabaseSettingsLoader.java`
- Create: `common/src/test/java/com/azoth/territory/persist/DatabaseSettingsLoaderTest.java`

**Interfaces:**
- Produces: `DatabaseSettings` (accessors `enabled()`, `host()`, `port()`, `name()`, `user()`, `password()`, `ssl()`, `poolSize()`, `jdbcUrl()`, static `disabled()`) and `DatabaseSettingsLoader.fromValues(Map<String,Object>)`. Consumed by `PostgresTerritoryRepository` (Task 4) and `AzothTerritoryPlugin` (Task 5).

- [ ] **Step 1: Write the failing test**

`common/src/test/java/com/azoth/territory/persist/DatabaseSettingsLoaderTest.java`:

```java
package com.azoth.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsLoaderTest {

    @Test
    void defaultsToDisabledWithDerivedUrl() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(new HashMap<>());
        assertFalse(s.enabled());
        assertEquals("jdbc:postgresql://127.0.0.1:5432/azoth_territory", s.jdbcUrl());
        assertEquals(10, s.poolSize());
    }

    @Test
    void readsEveryKey() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.enabled", true);
        cfg.put("database.host", "db.example.com");
        cfg.put("database.port", 5433);
        cfg.put("database.name", "azoth");
        cfg.put("database.user", "map");
        cfg.put("database.password", "hunter2");
        cfg.put("database.ssl", true);
        cfg.put("database.pool-size", 4);
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertTrue(s.enabled());
        assertEquals("db.example.com", s.host());
        assertEquals(5433, s.port());
        assertEquals("azoth", s.name());
        assertEquals("map", s.user());
        assertEquals("hunter2", s.password());
        assertTrue(s.ssl());
        assertEquals(4, s.poolSize());
        assertEquals("jdbc:postgresql://db.example.com:5433/azoth?sslmode=require", s.jdbcUrl());
    }

    @Test
    void jdbcUrlOverrideWins() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.enabled", true);
        cfg.put("database.jdbc-url",
                "jdbc:postgresql://cloud.example.com:6543/azoth?sslmode=verify-full");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("jdbc:postgresql://cloud.example.com:6543/azoth?sslmode=verify-full", s.jdbcUrl());
    }

    @Test
    void disabledInstanceIsDisabled() {
        assertFalse(DatabaseSettings.disabled().enabled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests '*DatabaseSettingsLoaderTest'`
Expected: FAIL — `DatabaseSettings`/`DatabaseSettingsLoader` do not exist (compile error).

- [ ] **Step 3: Implement settings + loader**

`common/src/main/java/com/azoth/territory/persist/DatabaseSettings.java`:

```java
package com.azoth.territory.persist;

import java.util.Objects;

/**
 * Connection settings for the remote PostgreSQL territory store.
 * <p>
 * Bukkit-free; loaded from {@code database.*} config keys by
 * {@link DatabaseSettingsLoader}.
 */
public final class DatabaseSettings {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String name;
    private final String user;
    private final String password;
    private final boolean ssl;
    private final int poolSize;
    private final String jdbcUrlOverride;

    public DatabaseSettings(
            boolean enabled,
            String host,
            int port,
            String name,
            String user,
            String password,
            boolean ssl,
            int poolSize,
            String jdbcUrlOverride
    ) {
        this.enabled = enabled;
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.name = Objects.requireNonNull(name, "name");
        this.user = Objects.requireNonNull(user, "user");
        this.password = password == null ? "" : password;
        this.ssl = ssl;
        this.poolSize = poolSize;
        this.jdbcUrlOverride = jdbcUrlOverride == null ? "" : jdbcUrlOverride;
    }

    public static DatabaseSettings disabled() {
        return new DatabaseSettings(false, "127.0.0.1", 5432, "azoth_territory", "azoth",
                "", false, 10, "");
    }

    public boolean enabled() {
        return enabled;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String name() {
        return name;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public boolean ssl() {
        return ssl;
    }

    public int poolSize() {
        return poolSize;
    }

    /**
     * Effective JDBC URL: an explicit {@code database.jdbc-url} wins;
     * otherwise derived from host/port/name, with {@code sslmode=require}
     * appended when {@code database.ssl} is set.
     */
    public String jdbcUrl() {
        if (!jdbcUrlOverride.isBlank()) {
            return jdbcUrlOverride;
        }
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        return ssl ? url + "?sslmode=require" : url;
    }
}
```

`common/src/main/java/com/azoth/territory/persist/DatabaseSettingsLoader.java`:

```java
package com.azoth.territory.persist;

import java.util.Map;

/**
 * Loads {@link DatabaseSettings} from plugin configuration values.
 * Bukkit-free: callers pass the flattened config map
 * (e.g. {@code FileConfiguration.getValues(true)}).
 */
public final class DatabaseSettingsLoader {
    private DatabaseSettingsLoader() {
    }

    public static DatabaseSettings fromValues(Map<String, Object> cfg) {
        boolean enabled = bool(cfg, "database.enabled", false);
        String host = str(cfg, "database.host", "127.0.0.1");
        int port = intOf(cfg, "database.port", 5432);
        String name = str(cfg, "database.name", "azoth_territory");
        String user = str(cfg, "database.user", "azoth");
        String password = str(cfg, "database.password", "");
        boolean ssl = bool(cfg, "database.ssl", false);
        int poolSize = intOf(cfg, "database.pool-size", 10);
        String jdbcUrl = str(cfg, "database.jdbc-url", "");
        return new DatabaseSettings(enabled, host, port, name, user, password, ssl, poolSize, jdbcUrl);
    }

    private static boolean bool(Map<String, Object> cfg, String key, boolean def) {
        Object value = cfg.get(key);
        return value instanceof Boolean b ? b : def;
    }

    private static String str(Map<String, Object> cfg, String key, String def) {
        Object value = cfg.get(key);
        return value != null ? value.toString() : def;
    }

    private static int intOf(Map<String, Object> cfg, String key, int def) {
        Object value = cfg.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests '*DatabaseSettingsLoaderTest'`
Expected: PASS (all four tests).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/azoth/territory/persist/DatabaseSettings.java common/src/main/java/com/azoth/territory/persist/DatabaseSettingsLoader.java common/src/test/java/com/azoth/territory/persist/DatabaseSettingsLoaderTest.java
git commit -m "feat: add database settings config for territory store"
```

---

### Task 4: Postgres-Backed Repository

**Files:**
- Modify: `common/build.gradle.kts`
- Modify: `paper/build.gradle.kts`
- Create: `common/src/main/java/com/azoth/territory/persist/PostgresTerritoryRepository.java`
- Create: `common/src/test/java/com/azoth/territory/persist/PostgresTerritoryRepositoryTest.java`

**Interfaces:**
- Consumes: `TerritoryRepository` (Task 1), `DatabaseSettings` (Task 3).
- Produces: `PostgresTerritoryRepository(DatabaseSettings)` — constructor throws `IOException` when the DB is unreachable or schema init fails; implements `loadInto`/`save`/`close`.

- [ ] **Step 1: Add dependencies**

In `common/build.gradle.kts`, inside `dependencies { … }` (after the Gson line):

```kotlin
    // Remote PostgreSQL store for territory persistence. HikariCP is declared
    // as `api` because the paper module's guilds subsystem compiles against it.
    api("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
```

In `paper/build.gradle.kts`, remove the line `implementation("com.zaxxer:HikariCP:5.1.0")` (now inherited from `:common` via `api`).

- [ ] **Step 2: Write the failing integration test**

`common/src/test/java/com/azoth/territory/persist/PostgresTerritoryRepositoryTest.java`:

```java
package com.azoth.territory.persist;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test against a real PostgreSQL server.
 * <p>
 * Skipped unless {@code AZOTH_TEST_JDBC_URL} is set, e.g.:
 * <pre>
 * AZOTH_TEST_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/azoth_test?user=azoth&password=azoth" \
 *   ./gradlew :common:test --tests '*PostgresTerritoryRepositoryTest'
 * </pre>
 * The database must exist and the role must be able to create tables.
 */
class PostgresTerritoryRepositoryTest {

    private static final String TEST_URL = System.getenv("AZOTH_TEST_JDBC_URL");
    private static PostgresTerritoryRepository repo;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(TEST_URL != null && !TEST_URL.isBlank(),
                "AZOTH_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        repo = new PostgresTerritoryRepository(new DatabaseSettings(
                true, "ignored", 5432, "ignored", "ignored", "", false, 5, TEST_URL));
    }

    @AfterAll
    static void disconnect() {
        if (repo != null) {
            repo.close();
        }
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)))));
        repo.save(registry);

        TerritoryRegistry reloaded = new TerritoryRegistry();
        repo.loadInto(reloaded);
        assertEquals(1, reloaded.size());
        Optional<Territory> t = reloaded.get("everfall");
        assertTrue(t.isPresent());
        assertEquals("Everfall", t.get().name());
        assertEquals("world", t.get().world());
    }

    @Test
    void saveReplacesEntireRegistry() throws Exception {
        TerritoryRegistry first = new TerritoryRegistry();
        first.register(new Territory("alpha", "Alpha", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10)))));
        first.register(new Territory("beta", "Beta", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(100, 100), new BlockPos(110, 100),
                        new BlockPos(110, 110), new BlockPos(100, 110)))));
        repo.save(first);

        TerritoryRegistry second = new TerritoryRegistry();
        second.register(new Territory("gamma", "Gamma", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(200, 200), new BlockPos(210, 200),
                        new BlockPos(210, 210), new BlockPos(200, 210)))));
        repo.save(second);

        TerritoryRegistry reloaded = new TerritoryRegistry();
        repo.loadInto(reloaded);
        assertEquals(List.of("gamma"), reloaded.list().stream().map(Territory::id).toList());
    }

    @Test
    void schemaInitIsIdempotent() throws Exception {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)))));
        repo.save(registry);
        // Reconnecting (repo constructor re-runs CREATE TABLE IF NOT EXISTS) must not fail.
        PostgresTerritoryRepository second = new PostgresTerritoryRepository(new DatabaseSettings(
                true, "ignored", 5432, "ignored", "ignored", "", false, 5, TEST_URL));
        try {
            TerritoryRegistry reloaded = new TerritoryRegistry();
            second.loadInto(reloaded);
            assertEquals(1, reloaded.size());
        } finally {
            second.close();
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :common:test --tests '*PostgresTerritoryRepositoryTest'`
Expected: FAIL — `PostgresTerritoryRepository` does not exist (compile error).

- [ ] **Step 4: Implement the repository**

`common/src/main/java/com/azoth/territory/persist/PostgresTerritoryRepository.java`:

```java
package com.azoth.territory.persist;

import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote PostgreSQL store for the territory registry.
 * <p>
 * The database is assumed remote: connections come from a pooled, validated
 * HikariCP data source and every save is a single transaction, so a failed
 * write leaves the previous state fully intact.
 */
public final class PostgresTerritoryRepository implements TerritoryRepository {
    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS territories (
                id  TEXT PRIMARY KEY,
                doc JSONB NOT NULL
            )
            """;

    private final HikariDataSource dataSource;
    private final TerritoryJson json = new TerritoryJson();

    /**
     * @throws IOException if the database is unreachable or schema init fails
     */
    public PostgresTerritoryRepository(DatabaseSettings settings) throws IOException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("azoth-territory-pg");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(2);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTimeout(30_000);
        config.setConnectionTestQuery("SELECT 1");

        HikariDataSource ds = null;
        try {
            ds = new HikariDataSource(config);
            try (Connection c = ds.getConnection();
                 Statement s = c.createStatement()) {
                s.execute(SCHEMA);
            }
        } catch (SQLException | RuntimeException e) {
            if (ds != null) {
                ds.close();
            }
            throw new IOException("PostgreSQL unavailable at " + settings.jdbcUrl()
                    + " — " + e.getMessage(), e);
        }
        this.dataSource = ds;
    }

    @Override
    public void loadInto(TerritoryRegistry registry) throws IOException {
        List<Territory> territories = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT doc FROM territories")) {
            while (rs.next()) {
                String doc = rs.getString("doc");
                territories.add(json.fromJson(JsonParser.parseString(doc).getAsJsonObject()));
            }
        } catch (SQLException e) {
            throw new IOException("Failed to load territories from PostgreSQL", e);
        }
        registry.replaceAll(territories);
    }

    @Override
    public void save(TerritoryRegistry registry) throws IOException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (Statement clear = c.createStatement()) {
                    clear.execute("DELETE FROM territories");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO territories (id, doc) VALUES (?, ?::jsonb)")) {
                    for (Territory t : registry.list()) {
                        ps.setString(1, t.id());
                        ps.setString(2, json.toJson(t).toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to save territories to PostgreSQL", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
```

- [ ] **Step 5: Run test + full common suite**

Run: `./gradlew :common:test`
Expected: PASS — integration tests SKIPPED (no `AZOTH_TEST_JDBC_URL`), everything else green. Then `./gradlew :paper:compileJava` — paper still compiles with HikariCP inherited from `:common`.

- [ ] **Step 6: Commit**

```bash
git add common/build.gradle.kts paper/build.gradle.kts common/src/main/java/com/azoth/territory/persist/PostgresTerritoryRepository.java common/src/test/java/com/azoth/territory/persist/PostgresTerritoryRepositoryTest.java
git commit -m "feat: add Postgres-backed territory repository"
```

---

### Task 5: Plugin Wiring + Config

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Modify: `paper/src/main/resources/config.yml`

**Interfaces:**
- Consumes: `DatabaseSettings`, `DatabaseSettingsLoader`, `PostgresTerritoryRepository`, `TerritoryRepository`, `TerritoryJson` (all from `com.azoth.territory.persist`).
- Produces: plugin picks the store at enable; `store == null` when Postgres is configured but unreachable; web submodule gated on `store != null`; `getStore()` returns `TerritoryRepository`.

- [ ] **Step 1: Update imports and field**

In `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`:

1. Add imports:

```java
import com.azoth.territory.persist.DatabaseSettings;
import com.azoth.territory.persist.DatabaseSettingsLoader;
import com.azoth.territory.persist.PostgresTerritoryRepository;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.TerritoryRepository;
```

2. Change the field `private TerritoryStore store;` → `private TerritoryRepository store;` (keep the `TerritoryStore` import — still used for the JSON path).

- [ ] **Step 2: Rework enable-time store creation**

Replace the current block in `onEnable()`:

```java
        this.store = new TerritoryStore(dataFile);
        try {
            store.loadInto(registry);
            getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + dataFile.getFileName());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load territories from " + dataFile, e);
        }
```

with:

```java
        try {
            this.store = createStore(dataFile);
        } catch (IOException e) {
            this.store = null;
            getLogger().log(Level.SEVERE,
                    "Territory persistence unavailable (database.enabled=true) — "
                            + "territory data and web submodule disabled", e);
        }
        if (store != null) {
            try {
                store.loadInto(registry);
                getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + describeStore(dataFile));
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to load territories from " + describeStore(dataFile), e);
            }
        }
```

- [ ] **Step 3: Add store factory + descriptor methods**

Add to `AzothTerritoryPlugin` (near `startWebIfEnabled`):

```java
    /**
     * Pick the territory store: {@link TerritoryStore} (JSON file) unless
     * {@code database.enabled: true}, which requires a reachable remote
     * PostgreSQL — no silent fallback.
     *
     * @throws IOException when Postgres is configured but unreachable
     */
    private TerritoryRepository createStore(Path dataFile) throws IOException {
        DatabaseSettings db = DatabaseSettingsLoader.fromValues(getConfig().getValues(true));
        if (!db.enabled()) {
            return new TerritoryStore(dataFile);
        }
        PostgresTerritoryRepository repo = new PostgresTerritoryRepository(db);
        getLogger().info("Territory persistence: remote PostgreSQL at " + db.jdbcUrl());
        return repo;
    }

    private String describeStore(Path dataFile) {
        return store instanceof PostgresTerritoryRepository
                ? "PostgreSQL"
                : dataFile.getFileName().toString();
    }
```

- [ ] **Step 4: Gate the web submodule and swap the json argument**

In `startWebIfEnabled()`, after the `webConfig.enabled()` check, add:

```java
            if (store == null) {
                getLogger().warning("Territory web submodule not started: no territory store (see previous errors)");
                return;
            }
```

and change the `new TerritoryWebServer(…` call's argument `store.json(),` → `new TerritoryJson(),`.

- [ ] **Step 5: Close the store on disable**

In `onDisable()`, after the existing save block:

```java
        if (store != null && registry != null) {
            try {
                store.save(registry);
                getLogger().info("Saved " + registry.size() + " territor(y/ies)");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save territories", e);
            }
        }
        if (store != null) {
            store.close();
        }
```

(Add only the `if (store != null) { store.close(); }` block; the save block already exists.)

- [ ] **Step 6: Widen the getter**

Change:

```java
    public TerritoryStore getStore() {
        return store;
    }
```

to:

```java
    public TerritoryRepository getStore() {
        return store;
    }
```

- [ ] **Step 7: Document the config**

In `paper/src/main/resources/config.yml`, append after the `web:` section (check the existing top-level sections first — if `web:` is not the last section, insert before the next one instead):

```yaml
# Territory persistence backend. Default: local territories.json.
# Set enabled: true to store territories in a remote PostgreSQL database
# instead — the map UI and REST API then serve Postgres-backed data.
# The database must exist and the configured role must be able to create
# tables. If Postgres is unreachable at startup the plugin logs SEVERE and
# the web submodule does not start (no fallback to the JSON file).
database:
  enabled: false
  host: 127.0.0.1
  port: 5432
  name: azoth_territory
  user: azoth
  password: ""
  ssl: false
  pool-size: 10
  # Optional full JDBC URL — wins over host/port/name/ssl. Use for cloud
  # Postgres with extra parameters, e.g.
  # jdbc-url: "jdbc:postgresql://db.example.com:5432/azoth?sslmode=verify-full"
  jdbc-url: ""
```

- [ ] **Step 8: Verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — full multi-module build with tests green (including the new persistence tests; Postgres integration tests skip without `AZOTH_TEST_JDBC_URL`).

- [ ] **Step 9: Commit**

```bash
git add paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java paper/src/main/resources/config.yml
git commit -m "feat: wire territory store to remote PostgreSQL"
```

---

### Task 6: README Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the persistence description**

In `README.md`:

1. In the Features list, replace the line `- JSON **save/load** (\`plugins/AzothTerritory/territories.json\`)` with:

```markdown
- **Persistence**: JSON save/load (`plugins/AzothTerritory/territories.json`) or
  **remote PostgreSQL** (`database.enabled: true`) — the map UI and REST API
  serve Postgres-backed data when configured
```

2. In the Web submodule endpoint table, change the PUT row description
   `Create/update (persists to disk)` to `Create/update (persists to the configured store — PostgreSQL or JSON)`.

- [ ] **Step 2: Add a persistence section**

Insert a `## Persistence` section after the Web submodule section (before `## Spatial rules`):

```markdown
## Persistence

Territories default to `plugins/AzothTerritory/territories.json`. To point the
web thing at a **remote PostgreSQL** database instead, set `database.enabled:
true` in `config.yml`:

```yaml
database:
  enabled: true
  host: db.example.com
  port: 5432
  name: azoth_territory
  user: azoth
  password: "…"
  ssl: true
  pool-size: 10
```

The database must exist and the role must be able to create tables (the
`territories` table is created automatically). A `database.jdbc-url` override
accepts any valid PostgreSQL JDBC URL and wins over `host`/`port`/`name`/`ssl`.

Failure is loud: if Postgres is unreachable at startup the plugin logs SEVERE
and the web submodule does not start — it never silently serves the JSON file
when PostgreSQL was requested. API mutations (`PUT`/`DELETE`) commit to the
database *before* updating the in-memory registry, so a failed remote save
returns HTTP 500 and leaves the served data unchanged.
```

- [ ] **Step 3: Verify + commit**

Run: `git diff README.md` — review the rendered changes.
Then:

```bash
git add README.md
git commit -m "docs: document PostgreSQL territory store"
```
