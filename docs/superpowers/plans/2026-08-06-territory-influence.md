# Territory Influence System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the New World–style territory influence race (accrual → cap → declare → countdown flip → cooldown) per `docs/superpowers/specs/2026-08-06-territory-influence-design.md`, then run the approved Nation→Alliance terminology rename, then rewrite the README.

**Architecture:** Pure-domain `InfluenceEngine` + `InfluenceStore` in `common/` (zero Bukkit types), contracts in `api/` (`InfluenceService` + records), thin Paper wiring (config loader, activity listener, flip tick, `/territory` subcommands). The engine journals flips in `influence.json` (marker with old/new owner + cooldown) so recovery is crash-safe. The rename workstream is a Java identifier + command + idempotent DB migration (v17) with the `/nation` command kept as an alias.

**Tech Stack:** Java 21, Gradle multi-module (api/common/paper), Gson 2.11 (common/paper), JUnit 5 (junit-bom 5.11.4), Mockito 5.14.2 (paper only), Paper API 1.21.4 (compileOnly), SQLite via `org.xerial:sqlite-jdbc` (migrations), Paper Brigadier lifecycle for commands.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-06-territory-influence-design.md` is authoritative. Read it before starting.
- Module rules: `api/` — pure contracts, no Bukkit, no Gson. `common/` — pure domain + JDK HTTP server + Gson, no Bukkit. `paper/` — all Bukkit glue. Nothing in api/common may import `org.bukkit.*`.
- Persistence rules (spec §6): `influence.json` is literal JSON, version 1; bar mutations are batched (dirty flag + `flush()`); declaration and flip transitions persist synchronously + atomically (temp-file + atomic move); flip journal order is marker → ownership → finalize.
- Flip revalidation (spec §5.4, §6): owner/attacker guilds must exist, both have alliances, alliances differ; marker recovery first checks `current owner == pendingFlip.oldOwnerGuildId`.
- Every task ends with its tests green and an atomic commit (repo style: imperative subject, no conventional prefix — see `git log --oneline`).
- Run tests with `./gradlew :common:test --tests '...'` (or `:paper:test`) for focused runs; `./gradlew test` at the end.
- Test style: JUnit 5, plain `org.junit.jupiter.api.Assertions`, `@BeforeEach` setup, private static factory helpers, `FakeGovernanceSource` for governance (common tests), real SQLite `DriverManager` for migration tests, `@TempDir` for file stores.
- Rename-workstream compatibility keys (spec §10.4): the `'town'` context string in the `permissions` table is a persisted storage key — DO NOT rename it. The `'nation'` role string is never persisted (bitmask storage) — safe to rename to `'alliance'`.
- Do not touch `docs/archived-guilds/`.

---

### Task 1: api — influence contracts

**Files:**
- Create: `api/src/main/java/com/guilds/territory/influence/InfluenceSource.java`
- Create: `api/src/main/java/com/guilds/territory/influence/InfluenceBar.java`
- Create: `api/src/main/java/com/guilds/territory/influence/Declaration.java`
- Create: `api/src/main/java/com/guilds/territory/influence/TerritoryInfluenceState.java`
- Create: `api/src/main/java/com/guilds/territory/influence/DeclareStatus.java`
- Create: `api/src/main/java/com/guilds/territory/influence/DeclareResult.java`
- Create: `api/src/main/java/com/guilds/territory/influence/InfluenceService.java`

**Interfaces:**
- Produces: the exact types Tasks 2–8 consume. Signatures below are binding.

- [ ] **Step 1: Write the seven files**

`InfluenceSource.java`:
```java
package com.guilds.territory.influence;

/** Activity event types that feed the influence race (spec §4). */
public enum InfluenceSource {
    PVP_KILL,
    PVE_KILL,
    BLOCK_BREAK,
    BLOCK_PLACE,
    CRAFT
}
```

`InfluenceBar.java`:
```java
package com.guilds.territory.influence;

/** One attacking guild's influence bar on a territory. */
public record InfluenceBar(String guildId, double value) {
    public InfluenceBar {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
```

`Declaration.java`:
```java
package com.guilds.territory.influence;

/** An active takeover declaration (race is locked while present). */
public record Declaration(String guildId, long declaredAtEpochMs, long flipAtEpochMs) {
    public Declaration {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
```

`TerritoryInfluenceState.java`:
```java
package com.guilds.territory.influence;

import java.util.List;

/** Read-only snapshot of the influence race state for one territory. */
public record TerritoryInfluenceState(
        String territoryId,
        String ownerGuildId,
        long cooldownUntilEpochMs,
        List<InfluenceBar> bars,
        Declaration declaration
) {
    public TerritoryInfluenceState {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId is required");
        }
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
```

`DeclareStatus.java`:
```java
package com.guilds.territory.influence;

/** Outcome of a declare/cancel attempt (spec §7). */
public enum DeclareStatus {
    DECLARED,
    CANCELLED,
    NOT_ELIGIBLE,
    NOT_AT_CAP,
    NOT_AUTHORIZED,
    RACE_ACTIVE,
    TERRITORY_UNKNOWN,
    UNGOVERNABLE,
    STORAGE_ERROR
}
```

`DeclareResult.java`:
```java
package com.guilds.territory.influence;

/** Declare/cancel outcome with a human-readable message. */
public record DeclareResult(DeclareStatus status, String message) {

    public DeclareResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        message = message == null ? "" : message;
    }

    public static DeclareResult ok(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    public static DeclareResult error(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    public boolean isSuccess() {
        return status == DeclareStatus.DECLARED || status == DeclareStatus.CANCELLED;
    }
}
```

`InfluenceService.java`:
```java
package com.guilds.territory.influence;

import java.util.List;
import java.util.Optional;

/**
 * Public influence-race surface for external consumers (queries + declaration
 * lifecycle). Accrual is engine-internal and driven by the Paper layer.
 */
public interface InfluenceService {

    /** Race state for one territory, if any influence state exists. */
    Optional<TerritoryInfluenceState> influence(String territoryId);

    /** Race state for every territory with recorded influence state. */
    List<TerritoryInfluenceState> all();

    /**
     * Declare a takeover on behalf of {@code guildId}; {@code authorityId}
     * must hold a seat in that guild's government. {@code nowEpochMs} is the
     * authoritative clock (injected for testability).
     */
    DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /** Cancel the guild's own active declaration on a territory. */
    DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /** True when the guild may currently declare (eligible + at cap + race open). */
    boolean isDeclarable(String territoryId, String guildId, long nowEpochMs);

    /** True while the post-flip cooldown blocks a new race on the territory. */
    boolean isCooldownActive(String territoryId, long nowEpochMs);
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/guilds/territory/influence
git commit -m "Add influence race contracts to the api module"
```

---

### Task 2: common — InfluenceConfig, influence state model, InfluenceStore

**Files:**
- Create: `common/src/main/java/com/guilds/territory/influence/InfluenceConfig.java`
- Create: `common/src/main/java/com/guilds/territory/influence/InfluenceState.java`
- Create: `common/src/main/java/com/guilds/territory/influence/TerritoryEntry.java`
- Create: `common/src/main/java/com/guilds/territory/influence/PendingFlip.java`
- Create: `common/src/main/java/com/guilds/territory/influence/InfluenceStore.java`
- Create: `common/src/test/java/com/guilds/territory/influence/InfluenceStoreTest.java`

**Interfaces:**
- Consumes: api types from Task 1 (`InfluenceBar`, `Declaration`, `InfluenceSource`).
- Produces: `InfluenceConfig` record (fields below), `InfluenceStore.save(InfluenceState)` / `load()` used by Task 3/4, `InfluenceState`/`TerritoryEntry`/`PendingFlip` package-private types used by the engine.

- [ ] **Step 1: Write the failing store test**

`InfluenceStoreTest.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.influence.InfluenceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceStoreTest {

    @TempDir
    Path tempDir;

    private InfluenceStore store() {
        return new InfluenceStore(tempDir.resolve("influence.json"));
    }

    private static InfluenceState stateWithEverything() {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.cooldownUntilEpochMs = 0L;
        entry.bars.put("rival-guild", 62.5);
        entry.bars.put("other-guild", 100.0);
        entry.declaration = new com.guilds.territory.influence.Declaration(
                "rival-guild", 1780000000000L, 1780086400000L);
        entry.pendingFlip = new PendingFlip(
                "everfall", "everfall-town", "rival-guild",
                1780086400000L, 1780691200000L);
        state.entries.put("everfall", entry);
        return state;
    }

    @Test
    void missingFile_loadsEmptyState() throws IOException {
        InfluenceState loaded = store().load();
        assertTrue(loaded.entries.isEmpty());
    }

    @Test
    void roundTrip_preservesAllFields() throws IOException {
        InfluenceStore s = store();
        s.save(stateWithEverything());

        InfluenceState loaded = s.load();
        TerritoryEntry e = loaded.entries.get("everfall");
        assertEquals("everfall-town", e.ownerGuildId);
        assertEquals(0L, e.cooldownUntilEpochMs);
        assertEquals(62.5, e.bars.get("rival-guild"), 0.001);
        assertEquals(100.0, e.bars.get("other-guild"), 0.001);
        assertEquals("rival-guild", e.declaration.guildId());
        assertEquals(1780086400000L, e.declaration.flipAtEpochMs());
        assertEquals("everfall-town", e.pendingFlip.oldOwnerGuildId());
        assertEquals(1780691200000L, e.pendingFlip.cooldownUntilEpochMs());
    }

    @Test
    void save_createsParentDirectories() throws IOException {
        InfluenceStore s = new InfluenceStore(tempDir.resolve("nested/dir/influence.json"));
        s.save(new InfluenceState());
        assertTrue(Files.isRegularFile(tempDir.resolve("nested/dir/influence.json")));
    }

    @Test
    void save_leavesNoTempFileBehind() throws IOException {
        InfluenceStore s = store();
        s.save(stateWithEverything());
        Path temp = tempDir.resolve("influence.json.tmp");
        assertFalse(Files.exists(temp), "temp file must be moved/removed");
    }

    @Test
    void corruptFile_throwsIOException() throws Exception {
        Path file = tempDir.resolve("influence.json");
        Files.writeString(file, "{ not json");
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void wrongRootType_throwsIOException() throws Exception {
        Files.writeString(tempDir.resolve("influence.json"), "[1,2,3]");
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void wrongVersion_throwsIOException() throws Exception {
        Files.writeString(tempDir.resolve("influence.json"), """
                {"version": 99, "territories": {}}
                """);
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void backupCorrupt_movesFileAsidePreservingContent() throws Exception {
        Path file = tempDir.resolve("influence.json");
        Files.writeString(file, "{ corrupt content");
        Path backup = store().backupCorrupt();

        assertFalse(Files.exists(file), "original must be moved away");
        assertTrue(Files.isRegularFile(backup), "backup must exist: " + backup);
        assertEquals("{ corrupt content", Files.readString(backup));
        assertTrue(backup.getFileName().toString().startsWith("influence.json.corrupt-"));
    }

    @Test
    void backupCorrupt_withoutFile_throwsIOException() {
        assertThrows(IOException.class, () -> store().backupCorrupt());
    }

    @Test
    void missingOptionalFields_loadAsDefaults() throws IOException {
        Files.writeString(tempDir.resolve("influence.json"), """
                {"version": 1, "territories": {"everfall": {"ownerGuildId": "everfall-town"}}}
                """);
        TerritoryEntry e = store().load().entries.get("everfall");
        assertEquals("everfall-town", e.ownerGuildId);
        assertEquals(0L, e.cooldownUntilEpochMs);
        assertTrue(e.bars.isEmpty());
        assertNull(e.declaration);
        assertNull(e.pendingFlip);
    }

    @Test
    void jsonIsStableAndSorted() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry a = new TerritoryEntry();
        a.ownerGuildId = "g1";
        a.bars.put("zeta", 1.0);
        a.bars.put("alpha", 2.0);
        state.entries.put("t2", new TerritoryEntry());
        state.entries.get("t2").ownerGuildId = "g2";
        state.entries.put("t1", a);
        store().save(state);

        String raw = Files.readString(tempDir.resolve("influence.json"));
        assertTrue(raw.contains("\"t1\""), "entries sorted by territory id: " + raw);
        assertTrue(raw.contains("\"t2\""), "entries sorted by territory id: " + raw);
        assertEquals(1, raw.indexOf("\"t1\"") < raw.indexOf("\"t2\"") ? 1 : 0, "t1 before t2");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceStoreTest'`
Expected: FAIL — compile errors, none of the types exist.

- [ ] **Step 3: Write the main types**

`InfluenceConfig.java`:
```java
package com.guilds.territory.influence;

import java.util.Objects;

/** Immutable influence tuning values (spec §4, §12). Pure domain. */
public record InfluenceConfig(
        boolean enabled,
        double cap,
        double pvpKill,
        double pveKill,
        double blockBreak,
        double blockPlace,
        double craft,
        double defenderMultiplier,
        long declareCountdownHours,
        long postFlipCooldownDays,
        long flushSeconds
) {

    public InfluenceConfig {
        if (cap <= 0) {
            throw new IllegalArgumentException("influence cap must be positive");
        }
        if (pvpKill < 0 || pveKill < 0 || blockBreak < 0 || blockPlace < 0 || craft < 0) {
            throw new IllegalArgumentException("influence source values must be non-negative");
        }
        if (defenderMultiplier < 0) {
            throw new IllegalArgumentException("defender multiplier must be non-negative");
        }
        if (declareCountdownHours < 0) {
            throw new IllegalArgumentException("declare countdown hours must be non-negative");
        }
        if (postFlipCooldownDays < 0) {
            throw new IllegalArgumentException("post-flip cooldown days must be non-negative");
        }
        if (flushSeconds <= 0) {
            throw new IllegalArgumentException("flush seconds must be positive");
        }
    }

    public static InfluenceConfig defaults() {
        return new InfluenceConfig(
                true, 100.0, 10.0, 0.5, 0.1, 0.1, 0.2,
                1.0, 24, 7, 60);
    }

    /** Per-source accrual value for an attacker event. */
    public double valueOf(InfluenceSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source) {
            case PVP_KILL -> pvpKill;
            case PVE_KILL -> pveKill;
            case BLOCK_BREAK -> blockBreak;
            case BLOCK_PLACE -> blockPlace;
            case CRAFT -> craft;
        };
    }

    /** Accrual value a defender event removes from every attacker bar. */
    public double defenderValueOf(InfluenceSource source) {
        return valueOf(source) * defenderMultiplier;
    }

    public long declareCountdownEpochMs() {
        return declareCountdownHours * 3_600_000L;
    }

    public long postFlipCooldownEpochMs() {
        return postFlipCooldownDays * 86_400_000L;
    }
}
```

`PendingFlip.java`:
```java
package com.guilds.territory.influence;

/** Journal marker for an in-flight takeover flip (spec §6). */
record PendingFlip(
        String territoryId,
        String oldOwnerGuildId,
        String newOwnerGuildId,
        long flipAtEpochMs,
        long cooldownUntilEpochMs
) {
}
```

`TerritoryEntry.java`:
```java
package com.guilds.territory.influence;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable in-memory race state for one territory (engine-internal). */
final class TerritoryEntry {
    String ownerGuildId;
    long cooldownUntilEpochMs;
    final Map<String, Double> bars = new LinkedHashMap<>();
    Declaration declaration;
    PendingFlip pendingFlip;
}
```

`InfluenceState.java`:
```java
package com.guilds.territory.influence;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory influence state for all territories (engine-internal). */
final class InfluenceState {
    static final int VERSION = 1;
    final Map<String, TerritoryEntry> entries = new LinkedHashMap<>();
}
```

`InfluenceStore.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.influence.Declaration;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * JSON persistence for influence race state (spec §6).
 * <p>
 * File format (literal JSON, version 1):
 * <pre>
 * { "version": 1, "territories": { "&lt;id&gt;": {
 *     "ownerGuildId": "...", "cooldownUntilEpochMs": 0,
 *     "bars": { "guild": 62.5 },
 *     "declaration": { "guildId": "...", "declaredAtEpochMs": 0, "flipAtEpochMs": 0 },
 *     "pendingFlip": { "territoryId": "...", "oldOwnerGuildId": "...",
 *         "newOwnerGuildId": "...", "flipAtEpochMs": 0, "cooldownUntilEpochMs": 0 } } } }
 * </pre>
 * All writes go through a temp file + atomic move.
 */
public final class InfluenceStore {

    private final Path file;

    public InfluenceStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void save(InfluenceState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", InfluenceState.VERSION);
        JsonObject territories = new JsonObject();
        for (Map.Entry<String, TerritoryEntry> e : state.entries.entrySet()) {
            territories.add(e.getKey(), toJson(e.getValue()));
        }
        root.add("territories", territories);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        moveIntoPlace(temp);
    }

    public InfluenceState load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new InfluenceState();
        }
        InfluenceState state = new InfluenceState();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("influence file root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement rawVersion = root.get("version");
            if (rawVersion == null || rawVersion.getAsInt() != InfluenceState.VERSION) {
                throw new IOException("unsupported influence file version: "
                        + (rawVersion == null ? "missing" : rawVersion.getAsInt()));
            }
            JsonElement rawTerritories = root.get("territories");
            if (rawTerritories == null || rawTerritories.isJsonNull()) {
                return state;
            }
            if (!rawTerritories.isJsonObject()) {
                throw new IOException("territories must be an object");
            }
            for (Map.Entry<String, JsonElement> e : rawTerritories.getAsJsonObject().entrySet()) {
                state.entries.put(e.getKey(), fromJson(e.getValue().getAsJsonObject()));
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("invalid influence file: " + file, e);
        }
        return state;
    }

    private static JsonObject toJson(TerritoryEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("ownerGuildId", entry.ownerGuildId);
        object.addProperty("cooldownUntilEpochMs", entry.cooldownUntilEpochMs);
        JsonObject bars = new JsonObject();
        for (Map.Entry<String, Double> b : entry.bars.entrySet()) {
            bars.addProperty(b.getKey(), b.getValue());
        }
        object.add("bars", bars);
        if (entry.declaration != null) {
            JsonObject d = new JsonObject();
            d.addProperty("guildId", entry.declaration.guildId());
            d.addProperty("declaredAtEpochMs", entry.declaration.declaredAtEpochMs());
            d.addProperty("flipAtEpochMs", entry.declaration.flipAtEpochMs());
            object.add("declaration", d);
        }
        if (entry.pendingFlip != null) {
            JsonObject p = new JsonObject();
            p.addProperty("territoryId", entry.pendingFlip.territoryId());
            p.addProperty("oldOwnerGuildId", entry.pendingFlip.oldOwnerGuildId());
            p.addProperty("newOwnerGuildId", entry.pendingFlip.newOwnerGuildId());
            p.addProperty("flipAtEpochMs", entry.pendingFlip.flipAtEpochMs());
            p.addProperty("cooldownUntilEpochMs", entry.pendingFlip.cooldownUntilEpochMs());
            object.add("pendingFlip", p);
        }
        return object;
    }

    private static TerritoryEntry fromJson(JsonObject object) {
        TerritoryEntry entry = new TerritoryEntry();
        JsonElement owner = object.get("ownerGuildId");
        entry.ownerGuildId = owner == null || owner.isJsonNull() ? null : owner.getAsString();
        JsonElement cooldown = object.get("cooldownUntilEpochMs");
        entry.cooldownUntilEpochMs = cooldown == null || cooldown.isJsonNull() ? 0L : cooldown.getAsLong();
        JsonElement rawBars = object.get("bars");
        if (rawBars != null && rawBars.isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : rawBars.getAsJsonObject().entrySet()) {
                entry.bars.put(b.getKey(), b.getValue().getAsDouble());
            }
        }
        JsonElement rawDeclaration = object.get("declaration");
        if (rawDeclaration != null && rawDeclaration.isJsonObject()) {
            JsonObject d = rawDeclaration.getAsJsonObject();
            entry.declaration = new Declaration(
                    d.get("guildId").getAsString(),
                    d.get("declaredAtEpochMs").getAsLong(),
                    d.get("flipAtEpochMs").getAsLong());
        }
        JsonElement rawPending = object.get("pendingFlip");
        if (rawPending != null && rawPending.isJsonObject()) {
            JsonObject p = rawPending.getAsJsonObject();
            entry.pendingFlip = new PendingFlip(
                    p.get("territoryId").getAsString(),
                    p.get("oldOwnerGuildId").getAsString(),
                    p.get("newOwnerGuildId").getAsString(),
                    p.get("flipAtEpochMs").getAsLong(),
                    p.get("cooldownUntilEpochMs").getAsLong());
        }
        return entry;
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Move the corrupt file aside for manual recovery (spec §6). The backup
     * name is unique per millisecond, so no earlier backup is overwritten.
     */
    public Path backupCorrupt() throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-"
                + System.currentTimeMillis());
        Files.move(file, backup);
        return backup;
    }
}
```

Note: `JsonArray` import is unused — drop it from the import list in `InfluenceStore.java`.

Also add `import java.nio.file.Path;` if not already present.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceStoreTest'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/guilds/territory/influence common/src/test/java/com/guilds/territory/influence
git commit -m "Add influence config, state model, and JSON store"
```

---

### Task 3: common — InfluenceEngine accrual & eligibility

**Files:**
- Create: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java`
- Create: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineAccrualTest.java`

**Interfaces:**
- Consumes: api contracts (Task 1), `InfluenceConfig`/`InfluenceState`/`TerritoryEntry`/`PendingFlip`/`InfluenceStore` (Task 2), `GovernanceRegistry` + `FakeGovernanceSource` (common), `TerritoryRegistry`.
- Produces (this task): `InfluenceEngine` constructor `(GovernanceRegistry, InfluenceConfig, InfluenceStore, InfluenceEngine.OwnershipPersister, java.util.logging.Logger)` and `Optional<InfluenceBar> accrue(String territoryId, String guildId, InfluenceSource source, long nowEpochMs, String victimGuildId)`. Task 4 adds declare/cancel/tick/recover/flush/admin + service methods to the same class.

- [ ] **Step 1: Write the failing accrual test**

`InfluenceEngineAccrualTest.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.AllianceBody;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineAccrualTest {

    @TempDir
    Path tempDir;

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private InfluenceConfig config;
    private InfluenceStore store;
    private InfluenceEngine engine;
    private long now;

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    private static GuildBody guild(String id) {
        return new GuildBody(id, id, Government.monarchy("m:" + id), List.of("m:" + id),
                GuildToggles.defaults(), Map.of());
    }

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        config = InfluenceConfig.defaults();
        store = new InfluenceStore(tempDir.resolve("influence.json"));
        engine = new InfluenceEngine(governance, config, store, (t, g) -> { }, Logger.getLogger("test"));
        now = 1_000_000L;
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void setupEverfallContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
    }

    @Test
    void accrue_unknownTerritory_isNoOp() {
        Optional<InfluenceBar> result = engine.accrue("nope", "rival-guild",
                InfluenceSource.PVP_KILL, now, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void accrue_ungovernedTerritory_isNoOp() {
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("freehold", null);
        assertTrue(engine.accrue("freehold", "rival-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_ownerGuildActivity_isNoOp() {
        setupEverfallContest();
        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_unaffiliatedAttacker_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("loner"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "loner",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_unaffiliatedOwner_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_sameAllianceAttacker_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("cousin-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town", "cousin-guild")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "cousin-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_eligibleAttacker_addsSourceValue() {
        setupEverfallContest();
        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now, null);
        assertTrue(bar.isPresent());
        assertEquals("rival-guild", bar.get().guildId());
        assertEquals(10.0, bar.get().value(), 0.001);
    }

    @Test
    void accrue_allSourcesUseTheirValues() {
        setupEverfallContest();
        for (InfluenceSource s : InfluenceSource.values()) {
            engine.accrue("everfall", "rival-guild", s, now, null);
        }
        double expected = config.pvpKill() + config.pveKill() + config.blockBreak()
                + config.blockPlace() + config.craft();
        Optional<InfluenceBar> bar = engine.influence("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("rival-guild")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(expected, bar.get().value(), 0.001);
    }

    @Test
    void accrue_clampsAtCap() {
        setupEverfallContest();
        for (int i = 0; i < 15; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        }
        Optional<InfluenceBar> bar = engine.influence("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("rival-guild")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(config.cap(), bar.get().value(), 0.001);
    }

    @Test
    void accrue_pvpKillSameAllianceVictim_isNoOp() {
        setupEverfallContest();
        source.putGuild(guild("cousin-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild", "cousin-guild")));
        assertTrue(engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL,
                now, "cousin-guild").isEmpty());
    }

    @Test
    void accrue_pvpKillDifferentAllianceVictim_counts() {
        setupEverfallContest();
        assertTrue(engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL,
                now, "some-other-guild").isPresent());
    }

    @Test
    void accrue_defenderSubtractsFromEveryAttackerBar() {
        setupEverfallContest();
        source.putGuild(guild("third-guild"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("third-guild")));
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        engine.accrue("everfall", "third-guild", InfluenceSource.PVP_KILL, now, null);

        // Defender block-break (0.1) pushes both bars down from 10.0.
        engine.accrue("everfall", "everfall-town", InfluenceSource.BLOCK_BREAK, now, null);

        double expected = config.pvpKill() - config.defenderValueOf(InfluenceSource.BLOCK_BREAK);
        var bars = engine.influence("everfall").orElseThrow().bars();
        assertEquals(2, bars.size(), "both attacker bars must survive");
        for (InfluenceBar bar : bars) {
            assertEquals(expected, bar.value(), 0.001, "bar " + bar.guildId());
        }
    }

    @Test
    void accrue_defenderSubtractNeverGoesBelowZero() {
        setupEverfallContest();
        for (int i = 0; i < 3; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.BLOCK_BREAK, now, null);
        }
        for (int i = 0; i < 3; i++) {
            engine.accrue("everfall", "everfall-town", InfluenceSource.BLOCK_BREAK, now, null);
        }
        assertTrue(engine.influence("everfall").orElseThrow().bars().isEmpty(),
                "bar must hit exactly zero and be dropped, never negative");
    }

    @Test
    void recover_finalizesMarkerWhenOwnershipAlreadyApplied() throws IOException {
        // Crash after journal step 2: ownership persisted, finalize not.
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        // Territory already shows the new owner (step 2 done before the crash).
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "rival-guild"));
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertEquals(1, flipped.size(), "the completed takeover must finalize and broadcast");
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty(), "ownership already applied — persister must not run again");
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("rival-guild", recovered.ownerGuildId);
        assertEquals(now + config.postFlipCooldownEpochMs(), recovered.cooldownUntilEpochMs);
        assertNull(recovered.pendingFlip, "marker finalized");
    }

    @Test
    void declare_persistFailure_returnsStorageErrorAndRollsBack() throws Exception {
        setupEverfallContest();
        pushRivalToCap();
        // A directory at the target path makes the atomic move fail.
        java.nio.file.Files.createDirectory(tempDir.resolve("influence.json"));

        DeclareResult result = engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        assertEquals(DeclareStatus.STORAGE_ERROR, result.status());
        assertNull(engine.influence("everfall").orElseThrow().declaration(),
                "declaration must roll back so a retry is safe");
        // Retry succeeds once the blocker is removed.
        java.nio.file.Files.delete(tempDir.resolve("influence.json"));
        assertEquals(DeclareStatus.DECLARED,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void accrue_duringCooldown_isNoOp() throws IOException {
        source.putGuild(guild("everfall-town"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "rival-guild"));

        // Force a cooldown by persisting state directly (engine flip tested in lifecycle tests).
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "rival-guild";
        entry.cooldownUntilEpochMs = now + config.postFlipCooldownEpochMs();
        state.entries.put("everfall", entry);
        store.save(state);
        engine.recover(now);

        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
    }

    @Test
    void accrue_ownerRebindResetsBars() {
        setupEverfallContest();
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);

        // Admin rebinds the territory to a third guild; old race must be discarded.
        source.putGuild(guild("new-owner"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("new-owner")));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isPresent());
        var bars = engine.influence("everfall").orElseThrow().bars();
        assertEquals(1, bars.size(), "old race must be discarded, only the new bar remains");
        assertEquals(10.0, bars.get(0).value(), 0.001);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceEngineAccrualTest'`
Expected: FAIL — `InfluenceEngine` does not exist.

- [ ] **Step 3: Write the engine (accrual part; full class incl. lifecycle methods)**

`InfluenceEngine.java` (complete class — Task 4 adds tests for the lifecycle methods declared here):
```java
package com.guilds.territory.influence;

import com.guilds.territory.model.Territory;
import com.guilds.territory.permission.AllianceBody;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.registry.TerritoryRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure-domain influence race engine (spec §3–§6).
 * <p>
 * Thread-safe: all mutations are {@code synchronized}. Ownership of the
 * territories registry is shared with the plugin; flips register the new
 * owner and ask {@link OwnershipPersister} to persist territories.json.
 */
public final class InfluenceEngine implements InfluenceService {

    /** Persists the territory ownership change (step 2 of the flip journal). */
    public interface OwnershipPersister {
        void persist(String territoryId, String newOwnerGuildId) throws IOException;
    }

    /** A completed takeover, for server broadcasts. */
    public record TerritoryFlip(String territoryId, String oldOwnerGuildId, String newOwnerGuildId) {
    }

    private final GovernanceRegistry governance;
    private final InfluenceConfig config;
    private final InfluenceStore store;
    private final OwnershipPersister persister;
    private final Logger log;

    private final InfluenceState state = new InfluenceState();
    private boolean dirty;
    /** True when even the corrupt-file backup failed — subsystem fails closed. */
    private boolean loadFailed;

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            InfluenceStore store,
            OwnershipPersister persister,
            Logger log
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.persister = Objects.requireNonNull(persister, "persister");
        this.log = Objects.requireNonNull(log, "log");
    }

    private boolean unusable() {
        return loadFailed;
    }

    private TerritoryRegistry territories() {
        return governance.territories();
    }

    // ── Accrual ───────────────────────────────────────────────────────────

    /**
     * Record one activity event. Attacker events (guild != owner) add the
     * source value to the guild's bar; owner-guild events subtract the
     * defender value from every attacker bar. Returns the actor's updated
     * bar, or empty when the event was a no-op (ineligible, defender, locked).
     *
     * @param victimGuildId primary guild of a PvP victim (only PVP_KILL);
     *                      same-alliance victims accrue nothing
     */
    public synchronized Optional<InfluenceBar> accrue(
            String territoryId,
            String guildId,
            InfluenceSource source,
        long nowEpochMs,
        String victimGuildId
    ) {
        if (unusable()) {
            return Optional.empty();
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
        if (entry == null) {
            return Optional.empty();
        }
        if (isCooldownActive(entry, nowEpochMs) || entry.declaration != null) {
            return Optional.empty();
        }
        if (source == InfluenceSource.PVP_KILL && victimGuildId != null && !victimGuildId.isBlank()
                && sameAlliance(guildId, victimGuildId.trim())) {
            return Optional.empty();
        }
        if (guildId != null && guildId.equals(entry.ownerGuildId)) {
            defend(entry, source);
            return Optional.empty();
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return Optional.empty();
        }
        double value = round2(entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source));
        entry.bars.put(guildId, Math.min(config.cap(), value));
        dirty = true;
        return Optional.of(new InfluenceBar(guildId, entry.bars.get(guildId)));
    }

    private void defend(TerritoryEntry entry, InfluenceSource source) {
        double defenderValue = config.defenderValueOf(source);
        if (defenderValue <= 0) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            double next = round2(bar.getValue() - defenderValue);
            if (next <= 0) {
                toRemove.add(bar.getKey());
            } else {
                bar.setValue(next);
            }
        }
        for (String guildId : toRemove) {
            entry.bars.remove(guildId);
        }
        if (!toRemove.isEmpty() || defenderValue > 0) {
            dirty = true;
        }
    }

    // ── Eligibility ───────────────────────────────────────────────────────

    private boolean canContest(String ownerGuildId, String attackerGuildId) {
        if (ownerGuildId == null || attackerGuildId == null || ownerGuildId.isBlank()
                || attackerGuildId.isBlank() || ownerGuildId.equals(attackerGuildId)) {
            return false;
        }
        Optional<GuildBody> owner = governance.source().guild(ownerGuildId);
        Optional<GuildBody> attacker = governance.source().guild(attackerGuildId);
        if (owner.isEmpty() || attacker.isEmpty()) {
            return false;
        }
        Optional<AllianceBody> ownerAlliance = governance.source().allianceContainingGuild(ownerGuildId);
        Optional<AllianceBody> attackerAlliance = governance.source().allianceContainingGuild(attackerGuildId);
        if (ownerAlliance.isEmpty() || attackerAlliance.isEmpty()) {
            return false;
        }
        return !ownerAlliance.get().id().equals(attackerAlliance.get().id());
    }

    private boolean sameAlliance(String guildA, String guildB) {
        if (guildA == null || guildB == null || guildA.isBlank() || guildB.isBlank()) {
            return false;
        }
        return governance.source().allianceContainingGuild(guildA)
                .flatMap(a -> governance.source().allianceContainingGuild(guildB)
                        .map(b -> a.id().equals(b.id())))
                .orElse(false);
    }

    /**
     * Territory entry synced to the current owner; null when the territory is
     * unknown or ungoverned. On external rebind, bars + declaration reset
     * (cooldown kept, spec §6 rule 2).
     */
    private TerritoryEntry syncedEntry(String territoryId, long nowEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            return null;
        }
        Optional<Territory> t = territories().get(territoryId.trim());
        if (t.isEmpty()) {
            return null;
        }
        String owner = t.get().governedByGuildId().orElse(null);
        if (owner == null) {
            return null;
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            entry = new TerritoryEntry();
            entry.ownerGuildId = owner;
            state.entries.put(territoryId.trim(), entry);
            return entry;
        }
        if (!owner.equals(entry.ownerGuildId)) {
            entry.bars.clear();
            entry.declaration = null;
            entry.ownerGuildId = owner;
            dirty = true;
            log.info("Influence state reset for " + territoryId.trim() + ": owner changed to " + owner);
        }
        return entry;
    }

    private static boolean isCooldownActive(TerritoryEntry entry, long nowEpochMs) {
        return entry.cooldownUntilEpochMs > nowEpochMs;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Declaration lifecycle (implemented in Task 4) ─────────────────────

    @Override
    public synchronized DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized Optional<TerritoryInfluenceState> influence(String territoryId) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized List<TerritoryInfluenceState> all() {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized boolean isDeclarable(String territoryId, String guildId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized boolean isCooldownActive(String territoryId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized List<TerritoryFlip> tickFlips(long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized List<TerritoryFlip> recover(long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized void flush() throws IOException {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized boolean adminSet(String territoryId, String guildId, double value, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized boolean adminReset(String territoryId) {
        throw new UnsupportedOperationException("declared in Task 4");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceEngineAccrualTest'`
Expected: PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java common/src/test/java/com/guilds/territory/influence/InfluenceEngineAccrualTest.java
git commit -m "Add influence accrual with alliance eligibility gate"
```

---

### Task 4: common — InfluenceEngine declare/flip/journal/recovery

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java` (replace the Task 3 stubs)
- Create: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineLifecycleTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–3. Uses the same test doubles.
- Produces: complete `InfluenceEngine` with `declare`, `cancelDeclaration`, `influence`, `all`, `isDeclarable`, `isCooldownActive`, `tickFlips`, `recover`, `flush`, `adminSet`, `adminReset`.

- [ ] **Step 1: Write the failing lifecycle test**

`InfluenceEngineLifecycleTest.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.AllianceBody;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineLifecycleTest {

    @TempDir
    Path tempDir;

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private InfluenceConfig config;
    private InfluenceStore store;
    private InfluenceEngine engine;
    private List<InfluenceEngine.TerritoryFlip> flips;
    private List<String> persistedOwnership;
    private long now;

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    private static GuildBody guild(String id) {
        return new GuildBody(id, id, Government.monarchy("m:" + id), List.of("m:" + id),
                GuildToggles.defaults(), Map.of());
    }

    private void freshEngine() {
        engine = new InfluenceEngine(governance, config, store, (t, g) -> persistedOwnership.add(t + "->" + g),
                Logger.getLogger("lifecycle-test"));
    }

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        config = InfluenceConfig.defaults();
        store = new InfluenceStore(tempDir.resolve("influence.json"));
        flips = new ArrayList<>();
        persistedOwnership = new ArrayList<>();
        freshEngine();
        now = 1_000_000L;
    }

    private void setupEverfallContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));
    }

    private void pushRivalToCap() {
        for (int i = 0; i < 10; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        }
    }

    // ── declare ───────────────────────────────────────────────────────────

    @Test
    void declare_unknownTerritory() {
        assertEquals(DeclareStatus.TERRITORY_UNKNOWN,
                engine.declare("nope", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_ungovernedTerritory() {
        territories.register(new Territory("freehold", "Freehold", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), null));
        assertEquals(DeclareStatus.UNGOVERNABLE,
                engine.declare("freehold", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresEligibility() {
        setupEverfallContest();
        pushRivalToCap();
        // Rival joins the owner's alliance (and leaves its own) — same-alliance
        // guilds may not contest.
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town", "rival-guild")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));
        assertEquals(DeclareStatus.NOT_ELIGIBLE,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresCap() {
        setupEverfallContest();
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        assertEquals(DeclareStatus.NOT_AT_CAP,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresAuthority() {
        setupEverfallContest();
        pushRivalToCap();
        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.declare("everfall", "rival-guild", "some-rando", now).status());
        assertEquals(DeclareStatus.DECLARED,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_succeedsAndLocksRace() {
        setupEverfallContest();
        pushRivalToCap();
        DeclareResult result = engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        assertEquals(DeclareStatus.DECLARED, result.status());
        assertTrue(result.isSuccess());

        // Race locked: no accrual for anyone.
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
        // No second declaration.
        assertEquals(DeclareStatus.RACE_ACTIVE,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now + 1).status());
    }

    @Test
    void declare_persistsSynchronously() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        InfluenceState loaded = store.load();
        TerritoryEntry entry = loaded.entries.get("everfall");
        assertEquals("rival-guild", entry.declaration.guildId());
        assertEquals(now + config.declareCountdownEpochMs(), entry.declaration.flipAtEpochMs());
    }

    @Test
    void cancelDeclaration_requiresOwnershipAndAuthority() {
        setupEverfallContest();
        pushRivalToCap();
        source.putGuild(guild("third-guild"));
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.cancelDeclaration("everfall", "rival-guild", "some-rando", now).status());
        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.cancelDeclaration("everfall", "third-guild", "m:third-guild", now).status());
    }

    @Test
    void cancelDeclaration_resumesRace() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        DeclareResult cancelled = engine.cancelDeclaration("everfall", "rival-guild", "m:rival-guild", now);
        assertEquals(DeclareStatus.CANCELLED, cancelled.status());
        assertTrue(engine.influence("everfall").orElseThrow().declaration() == null);
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isPresent());
    }

    @Test
    void cancelDeclaration_noActiveDeclaration() {
        setupEverfallContest();
        assertEquals(DeclareStatus.RACE_ACTIVE,
                engine.cancelDeclaration("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    // ── tickFlips ─────────────────────────────────────────────────────────

    @Test
    void tickFlips_notDue_doesNothing() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        List<InfluenceEngine.TerritoryFlip> flips = engine.tickFlips(now + 1);
        assertTrue(flips.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
    }

    @Test
    void tickFlips_appliesDueFlip() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        List<InfluenceEngine.TerritoryFlip> flipped = engine.tickFlips(flipTime);

        assertEquals(1, flipped.size());
        assertEquals("everfall-town", flipped.get(0).oldOwnerGuildId());
        assertEquals("rival-guild", flipped.get(0).newOwnerGuildId());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        // Owner rebound in the registry.
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        // Bars reset, cooldown set (starts at flip completion), declaration gone.
        TerritoryInfluenceState state = engine.influence("everfall").orElseThrow();
        assertTrue(state.bars().isEmpty());
        assertNull(state.declaration());
        assertEquals(flipTime + config.postFlipCooldownEpochMs(), state.cooldownUntilEpochMs());
        // Journal final state persisted.
        TerritoryEntry persisted = store.load().entries.get("everfall");
        assertEquals("rival-guild", persisted.ownerGuildId);
        assertNull(persisted.pendingFlip);
        assertNull(persisted.declaration);
        assertEquals(flipTime + config.postFlipCooldownEpochMs(), persisted.cooldownUntilEpochMs);
    }

    @Test
    void tickFlips_invalidAtFlipTime_cancelsDeclaration() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        // Attacker leaves its alliance before the flip — the takeover is void.
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));

        List<InfluenceEngine.TerritoryFlip> flipped = engine.tickFlips(now + config.declareCountdownEpochMs());

        assertTrue(flipped.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        assertNull(engine.influence("everfall").orElseThrow().declaration());
    }

    @Test
    void tickFlips_secondRaceAfterCooldown() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        engine.tickFlips(now + config.declareCountdownEpochMs());

        // After the cooldown, the new owner is contestable again by a rival
        // from a different alliance (rival-guild's own alliance-mates cannot).
        long later = now + config.declareCountdownEpochMs() + config.postFlipCooldownEpochMs() + 1;
        source.putGuild(guild("other-rival"));
        source.putAlliance(new AllianceBody("eastern-pact", "Eastern Pact",
                Government.anarchy(), List.of("other-rival")));

        assertTrue(engine.accrue("everfall", "other-rival", InfluenceSource.PVP_KILL, later, null).isPresent());
    }

    // ── recover ───────────────────────────────────────────────────────────

    @Test
    void recover_appliesOverdueDeclaration() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        // Simulate restart: fresh engine recovers from disk with the flip due.
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(flipTime);

        assertEquals(1, flipped.size());
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        assertTrue(engine.influence("everfall").orElseThrow().cooldownUntilEpochMs() > now);
    }

    @Test
    void recover_invalidOverdueDeclaration_cancelsWithoutFlip() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        // Attacker leaves its alliance while the server was down.
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(flipTime);

        assertTrue(flipped.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        assertNull(store.load().entries.get("everfall").declaration());
    }

    @Test
    void recover_voidsStaleMarkerWhenOwnerMovedOn() throws IOException {
        // Simulate: marker written, then owner externally rebound, then restart.
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        // Territory owner changed externally to a third guild while down.
        source.putGuild(guild("new-owner"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("new-owner")));
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertTrue(flipped.isEmpty());
        assertEquals("new-owner", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        engine.flush(); // the void is batched, not sync
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertNull(recovered.pendingFlip, "stale marker must be voided");
        assertEquals(0L, recovered.cooldownUntilEpochMs, "no cooldown for a voided flip");
    }

    @Test
    void recover_appliesValidMarker() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertEquals(1, flipped.size());
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("rival-guild", recovered.ownerGuildId);
        assertEquals(now + config.postFlipCooldownEpochMs(), recovered.cooldownUntilEpochMs);
        assertNull(recovered.pendingFlip);
    }

    @Test
    void recover_ownerMismatchResetsBarsKeepsCooldown() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "old-owner";
        entry.cooldownUntilEpochMs = now + 5;
        entry.bars.put("attacker", 42.0);
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        freshEngine();
        engine.recover(now);
        engine.flush(); // mismatch/drop resets are batched, not sync

        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("everfall-town", recovered.ownerGuildId);
        assertTrue(recovered.bars.isEmpty(), "bars reset on owner mismatch");
        assertEquals(now + 5, recovered.cooldownUntilEpochMs, "cooldown kept");
    }

    @Test
    void recover_dropsEntryForMissingTerritory() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "some-guild";
        entry.bars.put("attacker", 5.0);
        state.entries.put("ghost", entry);
        store.save(state);

        freshEngine();
        engine.recover(now);
        engine.flush(); // the drop is batched, not sync

        assertFalse(store.load().entries.containsKey("ghost"));
    }

    @Test
    void recover_corruptFile_preservesBackupAndStartsEmpty() throws Exception {
        java.nio.file.Files.writeString(tempDir.resolve("influence.json"), "{ corrupt content");
        setupEverfallContest();
        freshEngine();

        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now);

        assertTrue(flipped.isEmpty());
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("influence.json")),
                "corrupt file must be moved aside");
        boolean backupExists = java.nio.file.Files.list(tempDir).anyMatch(
                p -> p.getFileName().toString().startsWith("influence.json.corrupt-"));
        assertTrue(backupExists, "corrupt backup must exist");
        // The subsystem is usable again with fresh state.
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now, null).isPresent());
    }

    // ── queries, admin, flush ─────────────────────────────────────────────

    @Test
    void influence_reportsStateSorted() {
        setupEverfallContest();
        source.putGuild(guild("zeta-guild"));
        source.putGuild(guild("alpha-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild", "zeta-guild", "alpha-guild")));
        engine.accrue("everfall", "zeta-guild", InfluenceSource.PVP_KILL, now, null);
        engine.accrue("everfall", "alpha-guild", InfluenceSource.PVP_KILL, now, null);

        TerritoryInfluenceState state = engine.influence("everfall").orElseThrow();
        assertEquals(List.of("alpha-guild", "zeta-guild"),
                state.bars().stream().map(InfluenceBar::guildId).toList());
        assertEquals("everfall-town", state.ownerGuildId());
    }

    @Test
    void influence_unknownTerritory_isEmpty() {
        assertTrue(engine.influence("nope").isEmpty());
    }

    @Test
    void isDeclarable_requiresCapEligibilityAndOpenRace() {
        setupEverfallContest();
        assertFalse(engine.isDeclarable("everfall", "rival-guild", now));
        pushRivalToCap();
        assertTrue(engine.isDeclarable("everfall", "rival-guild", now));
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        assertFalse(engine.isDeclarable("everfall", "rival-guild", now + 1));
    }

    @Test
    void isCooldownActive_afterFlip() {
        setupEverfallContest();
        assertFalse(engine.isCooldownActive("everfall", now));
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        engine.tickFlips(now + config.declareCountdownEpochMs());
        assertTrue(engine.isCooldownActive("everfall", now + config.declareCountdownEpochMs() + 1));
        assertFalse(engine.isCooldownActive("everfall",
                now + config.declareCountdownEpochMs() + config.postFlipCooldownEpochMs() + 1));
    }

    @Test
    void adminSet_writesBarAndAdminResetDropsEntry() throws IOException {
        setupEverfallContest();
        assertTrue(engine.adminSet("everfall", "rival-guild", 55.0, now));
        assertEquals(55.0, engine.influence("everfall").orElseThrow().bars().get(0).value(), 0.001);
        assertTrue(engine.adminReset("everfall"));
        assertTrue(engine.influence("everfall").isEmpty());

        // Reset must survive a restart: flush and reload from disk.
        engine.flush();
        assertTrue(store.load().entries.isEmpty(), "admin reset must persist");
    }

    @Test
    void flush_persistsOnlyWhenDirty() throws IOException {
        setupEverfallContest();
        engine.flush();
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("influence.json")));

        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        engine.flush();
        assertEquals(10.0, store.load().entries.get("everfall").bars.get("rival-guild"), 0.001);

        engine.flush();
        assertEquals(10.0, store.load().entries.get("everfall").bars.get("rival-guild"), 0.001);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceEngineLifecycleTest'`
Expected: FAIL — `UnsupportedOperationException` from the Task 3 stubs.

- [ ] **Step 3: Replace the stubs with the full implementations**

Replace every `throw new UnsupportedOperationException("declared in Task 4");` body in `InfluenceEngine.java`:

```java
    // ── Declaration lifecycle ─────────────────────────────────────────────

    @Override
    public synchronized DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "territory is required");
        }
        if (unusable()) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "influence subsystem unavailable (corrupt state file)");
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
        if (entry == null) {
            Optional<Territory> t = territories().get(territoryId.trim());
            if (t.isEmpty()) {
                return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "unknown territory: " + territoryId);
            }
            return DeclareResult.error(DeclareStatus.UNGOVERNABLE,
                    "territory is not governed by a guild");
        }
        if (isCooldownActive(entry, nowEpochMs)) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "post-takeover cooldown is still active");
        }
        if (entry.declaration != null) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE,
                    "a declaration is already active on this territory");
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "your guild may not contest this territory (alliance gate)");
        }
        Optional<GuildBody> attacker = governance.source().guild(guildId);
        if (attacker.isEmpty() || !attacker.get().government().holderIds().contains(authorityId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "you need a seat in your guild's government to declare");
        }
        Double bar = entry.bars.get(guildId);
        if (bar == null || bar < config.cap()) {
            return DeclareResult.error(DeclareStatus.NOT_AT_CAP,
                    "your guild has not reached 100% influence");
        }
        long flipAt = nowEpochMs + config.declareCountdownEpochMs();
        entry.declaration = new Declaration(guildId, nowEpochMs, flipAt);
        dirty = true;
        if (!persistSync()) {
            entry.declaration = null; // roll back so a retry is safe
            dirty = true;
            return DeclareResult.error(DeclareStatus.STORAGE_ERROR,
                    "could not persist the declaration — please retry");
        }
        log.info("Territory " + territoryId + ": declaration by guild " + guildId
                + ", flip at " + flipAt);
        return DeclareResult.ok(DeclareStatus.DECLARED, "declaration filed; territory flips in "
                + config.declareCountdownHours() + "h");
    }

    @Override
    public synchronized DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "territory is required");
        }
        if (unusable()) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE,
                    "influence subsystem unavailable (corrupt state file)");
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null || entry.declaration == null) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE, "no active declaration on this territory");
        }
        if (!entry.declaration.guildId().equals(guildId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "only the declaring guild may cancel");
        }
        Optional<GuildBody> attacker = governance.source().guild(guildId);
        if (attacker.isEmpty() || !attacker.get().government().holderIds().contains(authorityId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "you need a seat in your guild's government to cancel");
        }
        Declaration previous = entry.declaration;
        entry.declaration = null;
        dirty = true;
        if (!persistSync()) {
            entry.declaration = previous; // roll back so the race stays locked
            dirty = true;
            return DeclareResult.error(DeclareStatus.STORAGE_ERROR,
                    "could not persist the cancellation — please retry");
        }
        return DeclareResult.ok(DeclareStatus.CANCELLED, "declaration cancelled; the race continues");
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<TerritoryInfluenceState> influence(String territoryId) {
        if (unusable()) {
            return Optional.empty();
        }
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(territoryId.trim(), entry));
    }

    @Override
    public synchronized List<TerritoryInfluenceState> all() {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryInfluenceState> out = new ArrayList<>();
        for (Map.Entry<String, TerritoryEntry> e : state.entries.entrySet()) {
            out.add(toSnapshot(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }

    private static TerritoryInfluenceState toSnapshot(String territoryId, TerritoryEntry entry) {
        List<InfluenceBar> bars = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.add(new InfluenceBar(bar.getKey(), bar.getValue()));
        }
        bars.sort((a, b) -> a.guildId().compareTo(b.guildId()));
        return new TerritoryInfluenceState(territoryId, entry.ownerGuildId,
                entry.cooldownUntilEpochMs, bars, entry.declaration);
    }

    @Override
    public synchronized boolean isDeclarable(String territoryId, String guildId, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || guildId == null || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return false;
        }
        if (isCooldownActive(entry, nowEpochMs) || entry.declaration != null) {
            return false;
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return false;
        }
        Double bar = entry.bars.get(guildId);
        return bar != null && bar >= config.cap();
    }

    @Override
    public synchronized boolean isCooldownActive(String territoryId, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || territoryId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        return entry != null && isCooldownActive(entry, nowEpochMs);
    }

    // ── Flips, recovery, persistence ──────────────────────────────────────

    /** Apply due flips; returns the completed takeovers for broadcasting. */
    public synchronized List<TerritoryFlip> tickFlips(long nowEpochMs) {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryFlip> flipped = new ArrayList<>();
        for (TerritoryEntry entry : new ArrayList<>(state.entries.values())) {
            if (entry.pendingFlip != null && entry.pendingFlip.flipAtEpochMs() <= nowEpochMs) {
                applyJournal(entry, nowEpochMs, flipped);
            } else if (entry.declaration != null && entry.declaration.flipAtEpochMs() <= nowEpochMs) {
                flipFromDeclaration(entry, nowEpochMs, flipped);
            }
        }
        return List.copyOf(flipped);
    }

    /** Load-time recovery (spec §6): journal, overdue declarations, owner mismatches. */
    public synchronized List<TerritoryFlip> recover(long nowEpochMs) {
        InfluenceState loaded;
        try {
            loaded = store.load();
        } catch (IOException e) {
            state.entries.clear();
            dirty = false;
            try {
                Path backup = store.backupCorrupt();
                log.log(Level.SEVERE, "Failed to load influence state — corrupt file preserved at " + backup
                        + "; starting with an empty race state (restore the backup and restart to recover)", e);
            } catch (IOException backupError) {
                log.log(Level.SEVERE, "Failed to load influence state AND could not preserve the corrupt file at "
                        + store.file() + " — influence subsystem disabled until the file is removed or fixed", e);
                loadFailed = true;
            }
            return List.of();
        }
        state.entries.clear();
        state.entries.putAll(loaded.entries);

        List<TerritoryFlip> flipped = new ArrayList<>();
        for (Map.Entry<String, TerritoryEntry> e : new ArrayList<>(state.entries.entrySet())) {
            String territoryId = e.getKey();
            TerritoryEntry entry = e.getValue();
            Optional<Territory> t = territories().get(territoryId);
            if (t.isEmpty()) {
                state.entries.remove(territoryId);
                dirty = true;
                log.warning("Dropped influence state for missing territory " + territoryId);
                continue;
            }
            if (entry.pendingFlip != null) {
                applyJournal(entry, nowEpochMs, flipped);
                continue;
            }
            if (entry.declaration != null && entry.declaration.flipAtEpochMs() <= nowEpochMs) {
                flipFromDeclaration(entry, nowEpochMs, flipped);
                continue;
            }
            String currentOwner = t.get().governedByGuildId().orElse(null);
            if (!java.util.Objects.equals(currentOwner, entry.ownerGuildId)) {
                entry.bars.clear();
                entry.declaration = null;
                entry.ownerGuildId = currentOwner;
                dirty = true;
                log.info("Influence state reset for " + territoryId + ": owner changed to " + currentOwner);
            }
        }
        return List.copyOf(flipped);
    }

    /** Declaration reached flip time: journal the flip, then apply it (spec §6). */
    private void flipFromDeclaration(TerritoryEntry entry, long nowEpochMs, List<TerritoryFlip> flipped) {
        String territoryId = findTerritoryId(entry);
        Territory t = territories().get(territoryId).orElse(null);
        String currentOwner = t == null ? null : t.governedByGuildId().orElse(null);
        if (t == null || !Objects.equals(currentOwner, entry.ownerGuildId)
                || !canContest(entry.ownerGuildId, entry.declaration.guildId())) {
            log.warning("Declaration on " + territoryId + " invalidated — cancelling without flip");
            entry.declaration = null;
            dirty = true;
            persistSync();
            return;
        }
        // Step 1 of the journal: write the marker BEFORE mutating the race state,
        // so a crash can never lose the takeover.
        PendingFlip marker = new PendingFlip(territoryId, entry.ownerGuildId,
                entry.declaration.guildId(), entry.declaration.flipAtEpochMs(),
                nowEpochMs + config.postFlipCooldownEpochMs());
        entry.pendingFlip = marker;
        dirty = true;
        if (!persistSync()) {
            entry.pendingFlip = null; // roll back — declaration + bars untouched
            dirty = true;
            return;
        }
        entry.declaration = null;
        entry.bars.clear();
        dirty = true;
        applyJournal(entry, nowEpochMs, flipped);
    }

    /** Journal marker recovery/apply (spec §6): owner pin check, revalidation, apply or void. */
    private void applyJournal(TerritoryEntry entry, long nowEpochMs, List<TerritoryFlip> flipped) {
        PendingFlip marker = entry.pendingFlip;
        if (marker == null) {
            return;
        }
        String territoryId = marker.territoryId();
        Territory t = territories().get(territoryId).orElse(null);
        String currentOwner = t == null ? null : t.governedByGuildId().orElse(null);
        if (t == null) {
            log.warning("Pending flip for missing territory " + territoryId + " — dropping marker");
            entry.pendingFlip = null;
            dirty = true;
            return;
        }
        boolean oldOwnerStillOwns = Objects.equals(currentOwner, marker.oldOwnerGuildId());
        boolean newOwnerAlreadyOwns = Objects.equals(currentOwner, marker.newOwnerGuildId());
        if (!oldOwnerStillOwns && !newOwnerAlreadyOwns) {
            // External rebind during the crash window: neither pre- nor post-flip
            // owner — the flip is void and must never overwrite the new owner.
            log.warning("Pending flip for " + territoryId + " voided: owner changed to " + currentOwner
                    + " before recovery");
            entry.pendingFlip = null;
            entry.cooldownUntilEpochMs = 0L;
            dirty = true;
            return;
        }
        if (marker.flipAtEpochMs() > nowEpochMs) {
            return; // not due yet; tickFlips will apply it
        }
        if (oldOwnerStillOwns && !canContest(marker.oldOwnerGuildId(), marker.newOwnerGuildId())) {
            log.warning("Pending flip for " + territoryId + " voided: takeover no longer eligible");
            entry.pendingFlip = null;
            entry.cooldownUntilEpochMs = 0L;
            dirty = true;
            return;
        }
        // oldOwnerStillOwns → step 2 (ownership) is still pending and
        // applyFlipCore performs it; newOwnerAlreadyOwns → step 2 already
        // succeeded and the takeover is committed — only finalization runs
        // (no eligibility re-check: the new owner is already in place).
        applyFlipCore(entry, marker, flipped);
    }

    /** Steps 2–3 of the flip journal: register new owner, persist, finalize state. */
    private void applyFlipCore(TerritoryEntry entry, PendingFlip marker, List<TerritoryFlip> flipped) {
        String territoryId = marker.territoryId();
        Territory t = territories().get(territoryId).orElse(null);
        if (t == null) {
            log.warning("Pending flip for missing territory " + territoryId + " — dropping marker");
            entry.pendingFlip = null;
            dirty = true;
            return;
        }
        String currentOwner = t.governedByGuildId().orElse(null);
        if (!Objects.equals(currentOwner, marker.newOwnerGuildId())) {
            try {
                territories().register(t.withGoverningGuild(marker.newOwnerGuildId()));
                persister.persist(territoryId, marker.newOwnerGuildId());
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to persist ownership change for " + territoryId
                        + " — retrying next tick (marker kept)", e);
                return;
            }
        }
        entry.ownerGuildId = marker.newOwnerGuildId();
        entry.cooldownUntilEpochMs = marker.cooldownUntilEpochMs();
        entry.declaration = null;
        entry.bars.clear();
        entry.pendingFlip = null;
        dirty = true;
        if (!persistSync()) {
            entry.pendingFlip = marker; // finalize failed — keep marker for retry
            dirty = true;
            return; // no broadcast until the finalize actually persisted
        }
        flipped.add(new TerritoryFlip(territoryId, marker.oldOwnerGuildId(), marker.newOwnerGuildId()));
    }

    private String findTerritoryId(TerritoryEntry entry) {
        for (Map.Entry<String, TerritoryEntry> e : state.entries.entrySet()) {
            if (e.getValue() == entry) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("entry not registered");
    }

    /** Synchronous atomic transition write; false when the write failed. */
    private boolean persistSync() {
        try {
            store.save(state);
            dirty = false;
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to persist influence state transition", e);
            return false;
        }
    }

    /** Batched flush of dirty bar mutations (spec §6). */
    public synchronized void flush() throws IOException {
        if (unusable() || !dirty) {
            return;
        }
        store.save(state);
        dirty = false;
    }

    // ── Admin overrides ───────────────────────────────────────────────────

    /** Admin: set a guild's bar on a territory (clamped to [0, cap]). */
    public synchronized boolean adminSet(String territoryId, String guildId, double value, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || guildId == null || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
        if (entry == null) {
            return false;
        }
        double clamped = Math.max(0.0, Math.min(config.cap(), value));
        if (clamped <= 0) {
            entry.bars.remove(guildId);
        } else {
            entry.bars.put(guildId, round2(clamped));
        }
        dirty = true;
        return true;
    }

    /** Admin: drop all influence state for a territory (persisted on next flush). */
    public synchronized boolean adminReset(String territoryId) {
        if (unusable() || territoryId == null) {
            return false;
        }
        boolean removed = state.entries.remove(territoryId) != null;
        if (removed) {
            dirty = true;
        }
        return removed;
    }
```

- [ ] **Step 4: Run the lifecycle tests to verify they pass**

Run: `./gradlew :common:test --tests 'com.guilds.territory.influence.InfluenceEngineLifecycleTest'`
Expected: PASS (29 tests).

Note: `InfluenceEngine.java` needs `import java.nio.file.Path;` for the `backupCorrupt()` result type in `recover()`.

- [ ] **Step 5: Run the whole common suite to verify no regressions**

Run: `./gradlew :common:test`
Expected: BUILD SUCCESSFUL, all tests pass (engine accrual + lifecycle + existing).

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java common/src/test/java/com/guilds/territory/influence/InfluenceEngineLifecycleTest.java
git commit -m "Add influence declaration, flip journal, and crash recovery"
```

---

### Task 5: paper — InfluenceConfigLoader + config.yml block

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/influence/InfluenceConfigLoader.java`
- Modify: `paper/src/main/resources/config.yml` (append the `influence:` block)
- Create: `paper/src/test/java/com/guilds/territory/influence/InfluenceConfigLoaderTest.java`

**Interfaces:**
- Consumes: `com.guilds.territory.influence.InfluenceConfig` (common).
- Produces: `InfluenceConfigLoader.fromBukkit(FileConfiguration)` used by Task 7 wiring.

- [ ] **Step 1: Write the failing test**

`InfluenceConfigLoaderTest.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.influence.InfluenceConfig;
import com.guilds.territory.influence.InfluenceConfigLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceConfigLoaderTest {

    private static InfluenceConfig fromYaml(String yaml) {
        return InfluenceConfigLoader.fromBukkit(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
    }

    @Test
    void defaults_whenBlockMissing() {
        InfluenceConfig cfg = fromYaml("");
        InfluenceConfig defaults = InfluenceConfig.defaults();
        assertEquals(defaults, cfg);
    }

    @Test
    void readsExplicitValues() {
        InfluenceConfig cfg = fromYaml("""
                influence:
                  enabled: false
                  cap: 50.0
                  values:
                    pvp-kill: 5.0
                    pve-kill: 0.25
                    block-break: 0.05
                    block-place: 0.05
                    craft: 0.1
                  defender-multiplier: 2.0
                  declare-countdown-hours: 12
                  post-flip-cooldown-days: 3
                  flush-seconds: 30
                """);
        assertFalse(cfg.enabled());
        assertEquals(50.0, cfg.cap(), 0.001);
        assertEquals(5.0, cfg.pvpKill(), 0.001);
        assertEquals(0.25, cfg.pveKill(), 0.001);
        assertEquals(0.05, cfg.blockBreak(), 0.001);
        assertEquals(0.05, cfg.blockPlace(), 0.001);
        assertEquals(0.1, cfg.craft(), 0.001);
        assertEquals(2.0, cfg.defenderMultiplier(), 0.001);
        assertEquals(12, cfg.declareCountdownHours());
        assertEquals(3, cfg.postFlipCooldownDays());
        assertEquals(30, cfg.flushSeconds());
    }

    @Test
    void partialBlock_usesDefaultsForMissingKeys() {
        InfluenceConfig cfg = fromYaml("influence:\n  cap: 75.0\n");
        assertEquals(75.0, cfg.cap(), 0.001);
        assertEquals(InfluenceConfig.defaults().pvpKill(), cfg.pvpKill(), 0.001);
        assertTrue(cfg.enabled());
    }

    @Test
    void invalidValues_fallBackToDefaults() {
        InfluenceConfig cfg = fromYaml("influence:\n  cap: -5.0\n");
        assertEquals(InfluenceConfig.defaults(), cfg);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests 'com.guilds.territory.influence.InfluenceConfigLoaderTest'`
Expected: FAIL — `InfluenceConfigLoader` missing.

- [ ] **Step 3: Write the loader**

`InfluenceConfigLoader.java`:
```java
package com.guilds.territory.influence;

import org.bukkit.configuration.file.FileConfiguration;

/** Loads the {@code influence:} block from config.yml (spec §12). */
public final class InfluenceConfigLoader {

    private InfluenceConfigLoader() {
    }

    public static InfluenceConfig fromBukkit(FileConfiguration cfg) {
        InfluenceConfig defaults = InfluenceConfig.defaults();
        if (cfg == null) {
            return defaults;
        }
        try {
            return new InfluenceConfig(
                    cfg.getBoolean("influence.enabled", defaults.enabled()),
                    cfg.getDouble("influence.cap", defaults.cap()),
                    cfg.getDouble("influence.values.pvp-kill", defaults.pvpKill()),
                    cfg.getDouble("influence.values.pve-kill", defaults.pveKill()),
                    cfg.getDouble("influence.values.block-break", defaults.blockBreak()),
                    cfg.getDouble("influence.values.block-place", defaults.blockPlace()),
                    cfg.getDouble("influence.values.craft", defaults.craft()),
                    cfg.getDouble("influence.defender-multiplier", defaults.defenderMultiplier()),
                    cfg.getLong("influence.declare-countdown-hours", defaults.declareCountdownHours()),
                    cfg.getLong("influence.post-flip-cooldown-days", defaults.postFlipCooldownDays()),
                    cfg.getLong("influence.flush-seconds", defaults.flushSeconds())
            );
        } catch (IllegalArgumentException e) {
            return defaults;
        }
    }
}
```

- [ ] **Step 4: Append the config block**

Append to `paper/src/main/resources/config.yml`:
```yaml

# Territory influence race (New World style): rival guilds accrue influence in
# enemy-alliance territories; at 100% they may declare and take over after a
# countdown. Both sides must belong to alliances.
influence:
  enabled: true
  cap: 100.0
  values:
    pvp-kill: 10.0
    pve-kill: 0.5
    block-break: 0.1
    block-place: 0.1
    craft: 0.2
  # Defender activity pushes every attacker bar down by source value * this.
  defender-multiplier: 1.0
  # Hours between declare and the takeover flip.
  declare-countdown-hours: 24
  # Days after a flip before a new race may start on that territory.
  post-flip-cooldown-days: 7
  # Seconds between batched influence.json flushes (bar changes only).
  flush-seconds: 60
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :paper:test --tests 'com.guilds.territory.influence.InfluenceConfigLoaderTest'`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/influence/InfluenceConfigLoader.java paper/src/main/resources/config.yml paper/src/test/java/com/guilds/territory/influence/InfluenceConfigLoaderTest.java
git commit -m "Add influence config loading from config.yml"
```

---

### Task 6: paper — InfluenceListener

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/influence/InfluenceListener.java`
- Create: `paper/src/test/java/com/guilds/territory/influence/InfluenceListenerTest.java`

**Interfaces:**
- Consumes: `InfluenceEngine.accrue(...)` (Task 4), `GovernanceRegistry`, api `InfluenceSource`.
- Produces: `InfluenceListener(GovernanceRegistry, InfluenceEngine)` with accessor `engine()`; registered by Task 7.

- [ ] **Step 1: Write the failing test**

`InfluenceListenerTest.java` (structural, mirroring `InteractionProtectionListenerTest`):
```java
package com.guilds.territory.influence;

import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceListenerTest {

    @Test
    void influenceListener_isListenerAndHoldsEngine() {
        TerritoryRegistry territories = new TerritoryRegistry();
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        InfluenceEngine engine = new InfluenceEngine(governance, InfluenceConfig.defaults(),
                new InfluenceStore(java.nio.file.Path.of("influence-test.json")),
                (t, g) -> { }, java.util.logging.Logger.getLogger("test"));
        InfluenceListener listener = new InfluenceListener(governance, engine);

        assertTrue(listener instanceof Listener);
        assertEquals(engine, listener.engine());
    }

    @Test
    void influenceListener_declaresHandlersForActivityVectors() {
        Set<Class<?>> handled = Arrays.stream(InfluenceListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(m -> m.getParameterTypes()[0])
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(handled.contains(PlayerDeathEvent.class), "missing PlayerDeathEvent handler: " + handled);
        assertTrue(handled.contains(EntityDeathEvent.class), "missing EntityDeathEvent handler: " + handled);
        assertTrue(handled.contains(BlockBreakEvent.class), "missing BlockBreakEvent handler: " + handled);
        assertTrue(handled.contains(BlockPlaceEvent.class), "missing BlockPlaceEvent handler: " + handled);
        assertTrue(handled.contains(CraftItemEvent.class), "missing CraftItemEvent handler: " + handled);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests 'com.guilds.territory.influence.InfluenceListenerTest'`
Expected: FAIL — `InfluenceListener` missing.

- [ ] **Step 3: Write the listener**

`InfluenceListener.java`:
```java
package com.guilds.territory.influence;

import com.guilds.territory.model.LookupResult;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;

import java.util.Optional;

/**
 * Maps player activity events onto {@link InfluenceEngine#accrue} (spec §4).
 * PvP kills carry the victim's primary guild so same-alliance kills accrue
 * nothing (the engine enforces the gate).
 */
public final class InfluenceListener implements Listener {

    private final GovernanceRegistry governance;
    private final InfluenceEngine engine;

    public InfluenceListener(GovernanceRegistry governance, InfluenceEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    public InfluenceEngine engine() {
        return engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        String victimGuild = primaryGuild(victim.getUniqueId().toString()).orElse(null);
        accrueAt(victim.getLocation(), killer.getUniqueId().toString(),
                InfluenceSource.PVP_KILL, victimGuild);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // handled by onPlayerDeath
        }
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), killer.getUniqueId().toString(),
                InfluenceSource.PVE_KILL, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                InfluenceSource.BLOCK_BREAK, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                InfluenceSource.BLOCK_PLACE, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        accrueAt(player.getLocation(), player.getUniqueId().toString(), InfluenceSource.CRAFT, null);
    }

    private void accrueAt(Location location, String holderId, InfluenceSource source, String victimGuildId) {
        if (location.getWorld() == null) {
            return;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained()) {
            return;
        }
        Optional<String> guildId = primaryGuild(holderId);
        if (guildId.isEmpty()) {
            return;
        }
        engine.accrue(result.territoryId().orElseThrow(), guildId.get(), source,
                System.currentTimeMillis(), victimGuildId);
    }

    private Optional<String> primaryGuild(String holderId) {
        return governance.primaryGuildForMember(holderId).map(GuildBody::id);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :paper:test --tests 'com.guilds.territory.influence.InfluenceListenerTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/influence/InfluenceListener.java paper/src/test/java/com/guilds/territory/influence/InfluenceListenerTest.java
git commit -m "Add influence activity listener for pvp pve build and craft events"
```

---

### Task 7: paper — commands + plugin wiring

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Modify: `paper/src/test/java/com/guilds/territory/PluginMetadataTest.java` (assert config defaults shipped)

**Interfaces:**
- Consumes: `InfluenceConfigLoader` (Task 5), `InfluenceListener` (Task 6), `InfluenceEngine` (Task 4), api records.
- Produces: plugin accessor `getInfluenceEngine()` (nullable when disabled) used by the command; `/territory influence|declare` subcommands.

- [ ] **Step 1: Wire the plugin (GuildsTerritoryPlugin.java)**

Add fields (next to the existing `blockProtection` field):
```java
    private InfluenceEngine influenceEngine;
    private InfluenceStore influenceStore;
```

In `onEnable()`, immediately after the `blockProtection` listener registration block and before `TerritoryCommand` construction, insert:
```java
        // Territory influence race (accrual → declare → countdown flip).
        InfluenceConfig influenceConfig = InfluenceConfigLoader.fromBukkit(getConfig());
        if (influenceConfig.enabled()) {
            try {
                this.influenceStore = new InfluenceStore(
                        getDataFolder().toPath().resolve("influence.json"));
                this.influenceEngine = new InfluenceEngine(
                        governance, influenceConfig, influenceStore,
                        (territoryId, newOwnerGuildId) -> saveTerritories(),
                        getLogger());
                List<InfluenceEngine.TerritoryFlip> recovered =
                        influenceEngine.recover(System.currentTimeMillis());
                broadcastFlips(recovered);
                getServer().getPluginManager().registerEvents(
                        new InfluenceListener(governance, influenceEngine), this);
                long flushTicks = Math.max(1, influenceConfig.flushSeconds() * 20L);
                getServer().getScheduler().runTaskTimer(this, () -> {
                    try {
                        broadcastFlips(influenceEngine.tickFlips(System.currentTimeMillis()));
                        influenceEngine.flush();
                    } catch (IOException e) {
                        getLogger().log(Level.SEVERE, "Failed to flush influence state", e);
                    }
                }, flushTicks, flushTicks);
                getLogger().info("Territory influence race enabled (cap " + influenceConfig.cap() + ")");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start influence system — disabled", e);
                this.influenceEngine = null;
                this.influenceStore = null;
            }
        } else {
            getLogger().info("Territory influence race disabled (influence.enabled=false)");
        }
```

Add the broadcast helper and accessor (near the other getters):
```java
    private void broadcastFlips(List<InfluenceEngine.TerritoryFlip> flips) {
        for (InfluenceEngine.TerritoryFlip flip : flips) {
            String oldName = resolveGuildName(flip.oldOwnerGuildId());
            String newName = resolveGuildName(flip.newOwnerGuildId());
            getServer().broadcast(Component.text(
                    "The territory '" + flip.territoryId() + "' has been taken over by "
                            + newName + " (formerly " + oldName + ")!", NamedTextColor.GOLD));
            getLogger().info("Territory " + flip.territoryId() + " flipped "
                    + oldName + " -> " + newName);
        }
    }

    private String resolveGuildName(String guildId) {
        if (governance != null && guildId != null) {
            return governance.source().guild(guildId).map(GuildBody::name).orElse(guildId);
        }
        return guildId;
    }

    public InfluenceEngine getInfluenceEngine() {
        return influenceEngine;
    }
```

Imports to add to `GuildsTerritoryPlugin.java`:
```java
import com.guilds.territory.influence.InfluenceConfig;
import com.guilds.territory.influence.InfluenceConfigLoader;
import com.guilds.territory.influence.InfluenceEngine;
import com.guilds.territory.influence.InfluenceListener;
import com.guilds.territory.influence.InfluenceStore;
import com.guilds.territory.permission.GuildBody;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
```

In `onDisable()`, after the reconciliation save and before the territories save, add:
```java
        if (influenceEngine != null) {
            try {
                influenceEngine.flush();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush influence state on disable", e);
            }
        }
```

- [ ] **Step 2: Extend TerritoryCommand**

In the `onCommand` switch, add cases (keep the default usage line updated):
```java
            case "influence" -> influence(sender, args);
            case "declare" -> declare(sender, args);
```
and update the usage string in the default branch:
```java
                sender.sendMessage(Component.text(
                        "Usage: /" + label + " [lookup|list|reload|save|web|govern|influence|declare]",
                        NamedTextColor.RED));
```

Add the new methods (before `onTabComplete`):
```java
    private InfluenceEngine engine() {
        InfluenceEngine engine = plugin.getInfluenceEngine();
        if (engine == null) {
            return null;
        }
        return engine;
    }

    /**
     * /territory influence [territoryId] — show the influence race state.
     */
    private boolean influence(CommandSender sender, String[] args) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        String territoryId = args.length > 1 ? args[1] : territoryAt(sender);
        if (territoryId == null) {
            sender.sendMessage(Component.text(
                    "Usage: /territory influence <territoryId>", NamedTextColor.RED));
            return true;
        }
        Optional<TerritoryInfluenceState> state = engine.influence(territoryId);
        if (state.isEmpty()) {
            sender.sendMessage(Component.text(
                    "No influence recorded for " + territoryId + ".", NamedTextColor.GRAY));
            return true;
        }
        TerritoryInfluenceState s = state.get();
        sender.sendMessage(Component.text("Influence — ", NamedTextColor.GOLD)
                .append(Component.text(s.territoryId(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Owner: ", NamedTextColor.GOLD)
                .append(Component.text(plugin.resolveGuildNameFor(s.ownerGuildId()), NamedTextColor.WHITE)));
        long now = System.currentTimeMillis();
        if (s.cooldownUntilEpochMs() > now) {
            long hours = (s.cooldownUntilEpochMs() - now) / 3_600_000L;
            sender.sendMessage(Component.text("Cooldown: ", NamedTextColor.RED)
                    .append(Component.text(hours + "h until a new race may start", NamedTextColor.WHITE)));
        }
        for (InfluenceBar bar : s.bars()) {
            boolean declarable = engine.isDeclarable(s.territoryId(), bar.guildId(), now);
            sender.sendMessage(Component.text(" • ", NamedTextColor.YELLOW)
                    .append(Component.text(plugin.resolveGuildNameFor(bar.guildId()), NamedTextColor.WHITE))
                    .append(Component.text(" " + bar.value() + "/" + engine.cap(), NamedTextColor.GRAY))
                    .append(declarable
                            ? Component.text(" [DECLARABLE]", NamedTextColor.GOLD)
                            : Component.empty()));
        }
        if (s.declaration() != null) {
            long remaining = Math.max(0, s.declaration().flipAtEpochMs() - now);
            sender.sendMessage(Component.text("Declaration by ", NamedTextColor.GOLD)
                    .append(Component.text(plugin.resolveGuildNameFor(s.declaration().guildId()), NamedTextColor.WHITE))
                    .append(Component.text(" — flips in " + (remaining / 3_600_000L) + "h", NamedTextColor.GRAY)));
        }
        return true;
    }

    /**
     * /territory declare <territoryId> [confirm] | cancel <territoryId>
     */
    private boolean declare(CommandSender sender, String[] args) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /territory declare <territoryId> [confirm] | cancel <territoryId>",
                    NamedTextColor.RED));
            return true;
        }
        if ("cancel".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(Component.text(
                        "Usage: /territory declare cancel <territoryId>", NamedTextColor.RED));
                return true;
            }
            DeclareResult result = engine.cancelDeclaration(args[2],
                    player.getUniqueId().toString(), player.getUniqueId().toString(),
                    System.currentTimeMillis());
            sender.sendMessage(Component.text(result.message(),
                    result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
        }
        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(Component.text(
                    "Declaring a takeover is permanent. Confirm with: /territory declare "
                            + args[1] + " confirm", NamedTextColor.YELLOW));
            return true;
        }
        Optional<String> guildId = plugin.getGovernance().primaryGuildForMember(
                player.getUniqueId().toString()).map(GuildBody::id);
        if (guildId.isEmpty()) {
            sender.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
            return true;
        }
        DeclareResult result = engine.declare(args[1], guildId.get(),
                player.getUniqueId().toString(), System.currentTimeMillis());
        sender.sendMessage(Component.text(result.message(),
                result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    /** /territory influence set <territoryId> <guildId> <value> | reset <territoryId> */
    private boolean influenceAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("You need 'guilds.territory.admin'.", NamedTextColor.RED));
            return true;
        }
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /territory influence reset <territoryId>", NamedTextColor.RED));
                return true;
            }
            boolean removed = engine.adminReset(args[2]);
            sender.sendMessage(Component.text(removed
                    ? "Influence state dropped for " + args[2] + "."
                    : "No influence state for " + args[2] + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args.length < 5 || !"set".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text(
                    "Usage: /territory influence set <territoryId> <guildId> <value> | reset <territoryId>",
                    NamedTextColor.RED));
            return true;
        }
        try {
            double value = Double.parseDouble(args[4]);
            boolean ok = engine.adminSet(args[2], args[3], value, System.currentTimeMillis());
            sender.sendMessage(Component.text(ok
                    ? "Set influence of " + args[3] + " on " + args[2] + " to " + value + "."
                    : "Unknown territory or guild.", NamedTextColor.GREEN));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Value must be a number.", NamedTextColor.RED));
            return true;
        }
    }

    private String territoryAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return null;
        }
        LookupResult result = plugin.getRegistry().resolve(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
        return result.isContained() ? result.territoryId().orElse(null) : null;
    }
```

Add the switch case in `onCommand` for the admin subcommand:
```java
            case "influence" -> args.length > 1 && ("set".equalsIgnoreCase(args[1])
                    || "reset".equalsIgnoreCase(args[1]))
                    ? influenceAdmin(sender, args)
                    : influence(sender, args);
```

Imports to add to `TerritoryCommand.java`:
```java
import com.guilds.territory.influence.DeclareResult;
import com.guilds.territory.influence.InfluenceBar;
import com.guilds.territory.influence.InfluenceEngine;
import com.guilds.territory.influence.TerritoryInfluenceState;
```

Add to `GuildsTerritoryPlugin` the helper used by the command (the command needs a public guild-name resolver — `resolveGuildName` is private; add a public wrapper):
```java
    public String resolveGuildNameFor(String guildId) {
        return resolveGuildName(guildId);
    }
```

Also add `public double influenceCap()` to the plugin or expose the engine's cap via a public accessor on the engine. Simplest: add to `InfluenceEngine`:
```java
    /** Configured influence cap (public for display). */
    public double cap() {
        return config.cap();
    }
```
and keep the command's `engine.cap()` call.

- [ ] **Step 3: Update PluginMetadataTest**

Read `paper/src/test/java/com/guilds/territory/PluginMetadataTest.java` first. Add one assertion (mirroring its existing style) that the shipped `config.yml` contains `influence:` with `enabled: true`:
```java
        String yml = new String(
                getClass().getResourceAsStream("/config.yml").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(yml.contains("influence:"), "config.yml must ship the influence block");
        assertTrue(yml.contains("post-flip-cooldown-days: 7"));
```

- [ ] **Step 4: Compile and run paper tests**

Run: `./gradlew :paper:compileJava :paper:test`
Expected: BUILD SUCCESSFUL, all paper tests pass.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java paper/src/test/java/com/guilds/territory/PluginMetadataTest.java common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java
git commit -m "Wire influence engine into plugin enable and territory commands"
```

---

### Task 8: common web — influence in REST payloads

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java` (constructor)
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java` (`/api/influence` + enrichment)
- Modify: `common/src/test/java/com/guilds/territory/web/TerritoryWebServerTest.java` (constructor call sites)
- Create: `common/src/test/java/com/guilds/territory/web/InfluenceWebTest.java`

**Interfaces:**
- Consumes: api `InfluenceService` (Task 1).
- Produces: `TerritoryWebServer(WebConfig, TerritoryRegistry, TerritoryJson, Supplier<TerritoryStore>, Supplier<Optional<InfluenceService>>, Logger)` — paper plugin passes `() -> Optional.ofNullable(influenceEngine)`.

- [ ] **Step 1: Write the failing web test**

`InfluenceWebTest.java`:
```java
package com.guilds.territory.web;

import com.guilds.territory.influence.Declaration;
import com.guilds.territory.influence.InfluenceBar;
import com.guilds.territory.influence.InfluenceService;
import com.guilds.territory.influence.TerritoryInfluenceState;
import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.persist.TerritoryJson;
import com.guilds.territory.persist.TerritoryStore;
import com.guilds.territory.registry.TerritoryRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceWebTest {

    @TempDir
    Path tempDir;

    private TerritoryRegistry registry;
    private TerritoryStore store;
    private TerritoryWebServer server;
    private int port;

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
            public com.guilds.territory.influence.DeclareResult declare(
                    String territoryId, String guildId, String authorityId, long nowEpochMs) {
                return com.guilds.territory.influence.DeclareResult.error(
                        com.guilds.territory.influence.DeclareStatus.RACE_ACTIVE, "read-only in web tests");
            }

            @Override
            public com.guilds.territory.influence.DeclareResult cancelDeclaration(
                    String territoryId, String guildId, String authorityId, long nowEpochMs) {
                return com.guilds.territory.influence.DeclareResult.error(
                        com.guilds.territory.influence.DeclareStatus.RACE_ACTIVE, "read-only in web tests");
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
        registry = new TerritoryRegistry();
        store = new TerritoryStore(tempDir.resolve("territories.json"));
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
    }

    private void startServer(Optional<InfluenceService> service) throws Exception {
        WebConfig cfg = new WebConfig(true, InetAddress.getByName("127.0.0.1"), port,
                "", false, "", true, "", null);
        server = new TerritoryWebServer(cfg, registry, new TerritoryJson(),
                () -> store, () -> service, Logger.getLogger("influence-web-test"));
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
```

Note: check the real `WebConfig` constructor arity in `common/src/main/java/com/guilds/territory/web/WebConfig.java` and adjust the `new WebConfig(...)` call in `startServer` to match (the test file must compile against the real record; use the same argument list as `TerritoryWebServerTest`).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests 'com.guilds.territory.web.InfluenceWebTest'`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Extend the web server + API handler**

`TerritoryWebServer.java` — add a field + constructor parameter:
```java
    private final Supplier<Optional<InfluenceService>> influenceSupplier;
```
and extend the constructor (keep all existing params, add `Supplier<Optional<InfluenceService>> influenceSupplier` before `Logger log`), assigning the field. In `start()`, where the `/api` context is created (the line constructing `TerritoryApiHandler`), pass the supplier through.

`TerritoryApiHandler.java` — add the same field + constructor param, then:

Add a route in `handle(...)` (after the `/resolve` block):
```java
            if ("/influence".equals(path) && "GET".equals(method)) {
                influenceList(exchange);
                return;
            }
```

Add the handlers:
```java
    private void influenceList(HttpExchange exchange) throws IOException {
        Optional<InfluenceService> service = influenceSupplier.get();
        if (service.isEmpty()) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        JsonArray out = new JsonArray();
        for (TerritoryInfluenceState s : service.get().all()) {
            out.add(toInfluenceJson(s));
        }
        HttpResponses.json(exchange, 200, json.gson().toJson(out), config);
    }

    private void getOne(HttpExchange exchange, String id) throws IOException {
        Optional<Territory> t = registry.get(id);
        if (t.isEmpty()) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        JsonObject body = json.toJson(t.get());
        influenceSupplier.get().flatMap(s -> s.influence(id))
                .ifPresent(state -> body.add("influence", toInfluenceJson(state)));
        HttpResponses.json(exchange, 200, json.gson().toJson(body), config);
    }

    private static JsonObject toInfluenceJson(TerritoryInfluenceState state) {
        JsonObject out = new JsonObject();
        out.addProperty("ownerGuildId", state.ownerGuildId());
        out.addProperty("cooldownUntilEpochMs", state.cooldownUntilEpochMs());
        JsonArray bars = new JsonArray();
        for (InfluenceBar bar : state.bars()) {
            JsonObject b = new JsonObject();
            b.addProperty("guildId", bar.guildId());
            b.addProperty("value", bar.value());
            bars.add(b);
        }
        out.add("bars", bars);
        if (state.declaration() != null) {
            JsonObject d = new JsonObject();
            d.addProperty("guildId", state.declaration().guildId());
            d.addProperty("declaredAtEpochMs", state.declaration().declaredAtEpochMs());
            d.addProperty("flipAtEpochMs", state.declaration().flipAtEpochMs());
            out.add("declaration", d);
        }
        return out;
    }
```
(Replace the existing `getOne` body with the enriched version.)

Imports to add to `TerritoryApiHandler.java`:
```java
import com.guilds.territory.influence.InfluenceBar;
import com.guilds.territory.influence.InfluenceService;
import com.guilds.territory.influence.TerritoryInfluenceState;
import com.google.gson.JsonArray;
```
(`JsonArray` may already be imported; check.)

Update every existing `TerritoryWebServer` construction in `TerritoryWebServerTest.java` (and any other call sites — grep `new TerritoryWebServer(`) to pass `() -> Optional.empty()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :common:test --tests 'com.guilds.territory.web.*'`
Expected: PASS (all web tests incl. new InfluenceWebTest).

- [ ] **Step 5: Wire the plugin's web server**

In `GuildsTerritoryPlugin.startWebIfEnabled()`, pass the supplier:
```java
            this.webServer = new TerritoryWebServer(
                    webConfig,
                    registry,
                    store.json(),
                    () -> store,
                    () -> Optional.ofNullable(influenceEngine),
                    getLogger()
            );
```
(import `java.util.Optional` — check existing imports).

- [ ] **Step 6: Compile + run all common & paper tests**

Run: `./gradlew :common:test :paper:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/guilds/territory/web common/src/test/java/com/guilds/territory/web paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java
git commit -m "Expose influence race state through the web API"
```

---

### Task 9: paper — DB migration v17 (nation tables → alliance tables)

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/database/migration/AddAllianceRenameMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/AddNationMigration.java` (fresh-install table names)
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/SchemaInitializer.java` (register v17)
- Create: `paper/src/test/java/org/aincraft/guilds/database/migration/AllianceRenameMigrationTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/database/migration/GuildRenameMigrationTest.java` (post-v16 assertions now post-v17)

**Interfaces:**
- Consumes: `DatabaseMigration` interface, `SchemaInitializer` registration list, `AddGuildRenameMigration` helper pattern (renameTable/renameColumn/recreateIndex/tableExists/indexExists/columnExists — copy those private helpers verbatim).
- Produces: v17 migration making `alliances`, `alliance_members`, `alliance_ministers`, `alliance_relations` the canonical tables.

- [ ] **Step 1: Write the failing migration test**

`AllianceRenameMigrationTest.java`:
```java
package org.aincraft.guilds.database.migration;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the v17 nation→alliance schema rename through the
 * real {@link SchemaInitializer} flow:
 * <ul>
 *   <li>fresh install: v11 now creates alliance* names directly, v17 no-ops;</li>
 *   <li>legacy install: nations/nation_* tables and rows renamed in place;</li>
 *   <li>both paths are idempotent.</li>
 * </ul>
 */
class AllianceRenameMigrationTest {

    private Path dbFile;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("guilds-alliance-rename-test", ".db");
        Files.delete(dbFile);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        Files.deleteIfExists(dbFile);
    }

    private static JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guilds-alliance-rename-test"));
        return plugin;
    }

    private void createLegacyNationSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE nations (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, "
                    + "king_uuid TEXT NOT NULL, capital_guild_id TEXT NOT NULL, "
                    + "tax_rate REAL DEFAULT 0.0, is_open INTEGER DEFAULT 0, created_at TEXT NOT NULL)");
            stmt.execute("CREATE TABLE nation_members (nation_id TEXT NOT NULL, guild_id TEXT NOT NULL, "
                    + "PRIMARY KEY (nation_id, guild_id))");
            stmt.execute("CREATE TABLE nation_ministers (nation_id TEXT NOT NULL, player_uuid TEXT NOT NULL, "
                    + "PRIMARY KEY (nation_id, player_uuid))");
            stmt.execute("CREATE TABLE nation_relations (nation_id TEXT NOT NULL, other_nation TEXT NOT NULL, "
                    + "relation_type TEXT NOT NULL, PRIMARY KEY (nation_id, other_nation))");
            stmt.execute("CREATE INDEX idx_nations_capital ON nations(capital_guild_id)");
            stmt.execute("CREATE INDEX idx_nation_members_nation ON nation_members(nation_id)");
            stmt.execute("INSERT INTO nations VALUES ('n1', 'Northern Pact', 'king-1', 'g1', 0.0, 1, '2026-01-01')");
            stmt.execute("INSERT INTO nation_members VALUES ('n1', 'g1')");
            stmt.execute("INSERT INTO nation_ministers VALUES ('n1', 'king-1')");
            stmt.execute("INSERT INTO nation_relations VALUES ('n1', 'n2', 'ENEMY')");
        }
    }

    @Test
    void freshInstall_createsAllianceNames_v17NoOps() throws Exception {
        new SchemaInitializer(plugin()).initialize(connection);

        for (String table : new String[]{
                "alliances", "alliance_members", "alliance_ministers", "alliance_relations"}) {
            assertTrue(tableExists(table), "expected table " + table);
        }
        for (String legacy : new String[]{
                "nations", "nation_members", "nation_ministers", "nation_relations"}) {
            assertFalse(tableExists(legacy), "legacy table must not exist: " + legacy);
        }
        assertTrue(columnExists("alliances", "capital_guild_id"));
        assertTrue(columnExists("alliance_members", "alliance_id"));
        assertTrue(columnExists("alliance_relations", "other_alliance"));
        assertTrue(indexExists("idx_alliance_members_guild"));

        new SchemaInitializer(plugin()).initialize(connection);
        assertTrue(tableExists("alliances"));
    }

    @Test
    void legacySchema_renamesNationTablesPreservingRows() throws Exception {
        createLegacyNationSchema();

        new SchemaInitializer(plugin()).initialize(connection);

        assertTrue(tableExists("alliances"));
        assertFalse(tableExists("nations"));
        assertTrue(tableExists("alliance_members"));
        assertTrue(tableExists("alliance_ministers"));
        assertTrue(tableExists("alliance_relations"));
        assertFalse(tableExists("nation_members"));

        assertTrue(columnExists("alliance_members", "alliance_id"));
        assertFalse(columnExists("alliance_members", "nation_id"));
        assertTrue(columnExists("alliance_relations", "other_alliance"));
        assertFalse(columnExists("alliance_relations", "other_nation"));

        assertTrue(indexExists("idx_alliances_capital"));
        assertFalse(indexExists("idx_nations_capital"));
        assertTrue(indexExists("idx_alliance_members_guild"));
        assertFalse(indexExists("idx_nation_members_guild"));

        assertEquals(1, scalar("SELECT COUNT(*) FROM alliances"));
        assertEquals("Northern Pact", scalarString("SELECT name FROM alliances WHERE id = 'n1'"));
        assertEquals("g1", scalarString("SELECT guild_id FROM alliance_members WHERE alliance_id = 'n1'"));
        assertEquals("king-1", scalarString("SELECT player_uuid FROM alliance_ministers WHERE alliance_id = 'n1'"));
        assertEquals("ENEMY", scalarString("SELECT relation_type FROM alliance_relations WHERE alliance_id = 'n1'"));

        // Idempotent second run.
        new SchemaInitializer(plugin()).initialize(connection);
        assertEquals(1, scalar("SELECT COUNT(*) FROM alliances"));
    }

    @Test
    void legacySchema_withoutNationTables_stillMigrates() throws Exception {
        // A pre-nation DB: schema_migrations only, no nation tables.
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, "
                    + "description TEXT, applied_at TEXT)");
            stmt.execute("INSERT INTO schema_migrations VALUES (10, 'up to economy', '2026-01-01')");
        }
        new SchemaInitializer(plugin()).initialize(connection);
        assertTrue(tableExists("alliances"));
    }

    private boolean tableExists(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ? AND name NOT LIKE 'sqlite_autoindex%'")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int scalar(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String scalarString(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests 'org.aincraft.guilds.database.migration.AllianceRenameMigrationTest'`
Expected: FAIL — alliance tables missing.

- [ ] **Step 3: Rework AddNationMigration for fresh installs**

In `AddNationMigration.migrate(...)`, replace every table/column/index name:
- `nations` → `alliances`
- `nation_members` → `alliance_members`
- `nation_ministers` → `alliance_ministers`
- `nation_relations` → `alliance_relations`
- `nation_id` → `alliance_id` (in all three child tables)
- `other_nation` → `other_alliance`
- `idx_nations_capital` → `idx_alliances_capital`
- `idx_nations_king` → `idx_alliances_king`
- `idx_nation_members_nation` → `idx_alliance_members_nation`
- `idx_nation_members_guild` → `idx_alliance_members_guild`
- `idx_nation_ministers_nation` → `idx_alliance_ministers_nation`
- `idx_nation_relations_nation` → `idx_alliance_relations_nation`
- `idx_nation_relations_type` → `idx_alliance_relations_type`

Update the class Javadoc: "Migration to add alliance system tables. Version 11 — after AddEconomyMigration (v10)." Keep `VERSION = 11` and the `isApplied`/`markAsApplied` methods unchanged.

- [ ] **Step 4: Write the v17 migration**

`AddAllianceRenameMigration.java` — copy the full helper set from `AddGuildRenameMigration` (renameTable, renameColumn, recreateIndex, tableExists, indexExists, columnExists, and the migrate transaction wrapper), with this body:
```java
public class AddAllianceRenameMigration implements DatabaseMigration {

    private static final int VERSION = 17;
    private static final String DESCRIPTION = "Rename legacy nation schema objects to alliance naming";

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean wasAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            renameTable(connection, "nations", "alliances");
            renameTable(connection, "nation_members", "alliance_members");
            renameTable(connection, "nation_ministers", "alliance_ministers");
            renameTable(connection, "nation_relations", "alliance_relations");

            renameColumn(connection, "alliance_members", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_ministers", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_relations", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_relations", "other_nation", "other_alliance");

            recreateIndex(connection, "idx_nations_capital", "idx_alliances_capital", "alliances", "capital_guild_id");
            recreateIndex(connection, "idx_nations_king", "idx_alliances_king", "alliances", "king_uuid");
            recreateIndex(connection, "idx_nation_members_nation", "idx_alliance_members_nation", "alliance_members", "alliance_id");
            recreateIndex(connection, "idx_nation_members_guild", "idx_alliance_members_guild", "alliance_members", "guild_id");
            recreateIndex(connection, "idx_nation_ministers_nation", "idx_alliance_ministers_nation", "alliance_ministers", "alliance_id");
            recreateIndex(connection, "idx_nation_relations_nation", "idx_alliance_relations_nation", "alliance_relations", "alliance_id");
            recreateIndex(connection, "idx_nation_relations_type", "idx_alliance_relations_type", "alliance_relations", "relation_type");

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(wasAutoCommit);
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, VERSION);
            ps.setString(2, getDescription());
            ps.setString(3, java.time.LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }
}
```
(Copy the private static helper methods from `AddGuildRenameMigration` verbatim; add the `PreparedStatement` import.)

- [ ] **Step 5: Register in SchemaInitializer**

In `SchemaInitializer.registerMigrations(...)`, after `migrations.add(new AddGuildRenameMigration());` add:
```java
        // Rename legacy nation* schema objects to alliance* naming
        migrations.add(new AddAllianceRenameMigration());
```

- [ ] **Step 6: Update GuildRenameMigrationTest expectations**

In `GuildRenameMigrationTest`, the assertions that reference `nations`/`nation_members` columns and `idx_nation_*` indexes now run AFTER v17. Change:
- `assertTrue(columnExists("nations", "capital_guild_id"))` → `assertTrue(columnExists("alliances", "capital_guild_id"))`
- any `nation_members` column checks → `alliance_members` with `alliance_id`
- `assertTrue(indexExists("idx_nation_members_guild"))` → `assertTrue(indexExists("idx_alliance_members_guild"))`
- `assertFalse(indexExists("idx_nations_capital"))`/similar legacy-name negatives → check the `nation*` legacy names are gone (`assertFalse(indexExists("idx_nations_capital"))`, `assertFalse(tableExists("nations"))`).

- [ ] **Step 7: Run migration tests**

Run: `./gradlew :paper:test --tests 'org.aincraft.guilds.database.migration.*'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/database/migration paper/src/test/java/org/aincraft/guilds/database/migration
git commit -m "Rename nation database schema to alliance naming"
```

---

### Task 10: paper — Java rename Nation → Alliance

**Files:** (renames + edits in `paper/src/main/java/org/aincraft/guilds/`)

| From | To |
|------|----|
| `models/Nation.java` | `models/Alliance.java` |
| `services/NationService.java` | `services/AllianceService.java` |
| `services/impl/NationServiceImpl.java` | `services/impl/AllianceServiceImpl.java` |
| `commands/brigadier/NationBrigadierCommand.java` | `commands/brigadier/AllianceBrigadierCommand.java` |
| `listeners/NationListener.java` | `listeners/AllianceListener.java` |

Plus edits: `GuildsGovernanceSource.java`, `GuildsServices.java`, `commands/BrigadierCommandRegistry.java`, `commands/arguments/RoleArgumentType.java`, `commands/brigadier/PlotBrigadierCommand.java`, `models/Permission.java`, `resources/plugin.yml`, `src/test/.../GuildsIntegrationTest.java`, and Javadoc-only touches in `api/src/main/java/com/guilds/territory/permission/{GovernanceSource,AllianceBody}.java` + `api/.../model/Government.java` (lines mentioning "nation").

**Rename map (apply mechanically):**

| Old identifier | New identifier |
|----------------|----------------|
| `Nation` (class) | `Alliance` |
| `NationRelation` (inner enum) | `AllianceRelation` |
| `NationService` | `AllianceService` |
| `NationServiceImpl` | `AllianceServiceImpl` |
| `NationBrigadierCommand` | `AllianceBrigadierCommand` |
| `NationListener` | `AllianceListener` |
| `nationService` (field/param/local) | `allianceService` |
| `nationCommand` | `allianceCommand` |
| `nationListener` | `allianceListener` |
| `getNationService()` | `getAllianceService()` |
| `getNationListener()` | `getAllianceListener()` |
| `setNationForm(...)` | `setAllianceForm(...)` |
| `nationAuthorityIds(...)` | `allianceAuthorityIds(...)` |
| `getNation(name)` / `getNationById(id)` | `getAlliance(name)` / `getAllianceById(id)` |
| `getAllNations()` | `getAllAlliances()` |
| `createNation(...)` / `deleteNation(name)` | `createAlliance(...)` / `deleteAlliance(name)` |
| `loadAllNations()` | `loadAllAlliances()` |
| `loadNationFromResult(...)` | `loadAllianceFromResult(...)` |
| `nationsById` / `nationsByName` | `alliancesById` / `alliancesByName` |
| `getPlayerNation(...)` | `getPlayerAlliance(...)` |
| `victimNation` / `attackerNation` | `victimAlliance` / `attackerAlliance` |
| `"nation"` command literal + all `/nation ...` strings in `AllianceBrigadierCommand` | `"alliance"` + `/alliance ...` |
| `"guilds.commands.nation"` permission string | `"guilds.commands.alliance"` |
| SQL tables `nations`, `nation_members`, `nation_ministers`, `nation_relations` in `AllianceServiceImpl` | `alliances`, `alliance_members`, `alliance_ministers`, `alliance_relations` |
| SQL columns `nation_id` → `alliance_id`, `other_nation` → `other_alliance` (same file) | — |

**Interfaces / behavioral notes (NOT in the rename map — keep as-is):**
- The `Nation` model methods `addGuild/removeGuild/hasGuild/addMinister/isKing/isAlly/isEnemy/getRelation/addAlly/removeAlly/addEnemy/removeEnemy/setTaxRate/setOpen/updateNation...` keep their names (king/minister are guilds roles, not nation vocabulary). In `AllianceServiceImpl` the service method names follow the map above; role methods like `setKing` keep their names.
- Command literals: `AllianceBrigadierCommand.buildCommand()` uses `Commands.literal("alliance")` with `requires(...hasPermission("guilds.commands.alliance"))`. Subcommand structure unchanged.
- In `BrigadierCommandRegistry.registerCommands()`, replace:
```java
        // Register nation command with alias
        commands.register(nationCommand.buildCommand());
        commands.register(Commands.literal("n")
                .redirect(nationCommand.buildCommand())
                .build());
```
with:
```java
        // Register alliance command with aliases (legacy /nation kept for compatibility)
        commands.register(allianceCommand.buildCommand());
        commands.register(Commands.literal("n")
                .redirect(allianceCommand.buildCommand())
                .build());
        commands.register(Commands.literal("nation")
                .redirect(allianceCommand.buildCommand())
                .build());
```
- `GuildsGovernanceSource`: rename field/ctor param/method per map; `toAllianceBody` keeps its name; the `setAllianceForm` SQL table string changes `"nations"` → `"alliances"`; the `readForm("nations", ...)` call in `toAllianceBody` → `readForm("alliances", ...)`.
- `RoleArgumentType.ROLE_TYPES`: `"nation"` → `"alliance"`; `isPlotRole` case `"nation"` → `"alliance"`. (`getExamples` unchanged.)
- `PlotBrigadierCommand`: help text line `"§7Available roles: resident, ally, outsider, nation"` → `"... outsider, alliance"`; the roles array `{"resident", "ally", "outsider", "nation"}` → `{"resident", "ally", "outsider", "alliance"}`; `getRoleShift` `case "nation": return 12;` → `case "alliance": return 12;`.
- `Permission.Target.NATION`: `public static final String NATION = "nation";` → `public static final String ALLIANCE = "alliance";` (deprecated; never persisted).
- `GuildsServices`: rename wiring per map (constructor line `new NationServiceImpl(...)` → `new AllianceServiceImpl(...)`, listener + command construction, getters).
- `plugin.yml`:
  - `guilds.commands.nation` → rename to `guilds.commands.alliance` (description "Permission to use alliance commands", default true), and add a legacy alias node:
```yaml
  guilds.commands.nation:
    description: Legacy alias of guilds.commands.alliance
    default: true
```
  - `guilds.nation.*` → `guilds.alliance.*` with children `guilds.alliance.create/invite/join/leave/kick/ally/enemy/set`; add legacy `guilds.nation.*` with the same children (children keys become `guilds.nation.<sub>`).
  - `guilds.admin.nation` → `guilds.admin.alliance`; add legacy `guilds.admin.nation: true` child too.
  - In `guilds.*` children: replace `guilds.nation.*: true` with `guilds.alliance.*: true` and `guilds.nation.*: true` (both).
- `GuildsIntegrationTest`: add assertions that `guilds.alliance.*`, `guilds.commands.alliance`, and the legacy `guilds.nation.*` node exist in the shipped plugin.yml (mirror its existing assertion style).
- `api` Javadocs: in `GovernanceSource.java` change "nations as alliances" → "alliances", "First alliance (nation)" → "First alliance"; in `AllianceBody.java` change "(nation)" parentheticals to "(alliance)"; in `Government.java` the `fromRoles` Javadoc "alliance (nation) governments" → "alliance governments".

- [ ] **Step 1: Use git mv for the five renames, then apply the rename map**

```bash
git mv paper/src/main/java/org/aincraft/guilds/models/Nation.java paper/src/main/java/org/aincraft/guilds/models/Alliance.java
git mv paper/src/main/java/org/aincraft/guilds/services/NationService.java paper/src/main/java/org/aincraft/guilds/services/AllianceService.java
git mv paper/src/main/java/org/aincraft/guilds/services/impl/NationServiceImpl.java paper/src/main/java/org/aincraft/guilds/services/impl/AllianceServiceImpl.java
git mv paper/src/main/java/org/aincraft/guilds/commands/brigadier/NationBrigadierCommand.java paper/src/main/java/org/aincraft/guilds/commands/brigadier/AllianceBrigadierCommand.java
git mv paper/src/main/java/org/aincraft/guilds/listeners/NationListener.java paper/src/main/java/org/aincraft/guilds/listeners/AllianceListener.java
```
Then apply the rename map in the moved files and the wiring files (`GuildsGovernanceSource.java`, `GuildsServices.java`, `BrigadierCommandRegistry.java`). Use `grep` to find every remaining `Nation`/`nationService`/`getNationService`/`"nation"` reference and fix each per the map. Every class/interface declaration, constructor, method signature, and string literal listed above must be renamed.

- [ ] **Step 2: Compile to verify the rename**

Run: `./gradlew :paper:compileJava`
Expected: BUILD SUCCESSFUL with no `Nation` identifiers remaining in `org.aincraft.guilds` main sources (grep `\bNation\w*` over `paper/src/main/java/org/aincraft/guilds` → only Javadoc references like "legacy" allowed if present; the goal is zero).

- [ ] **Step 3: Update plugin.yml + RoleArgumentType + PlotBrigadierCommand + Permission + tests**

Apply the plugin.yml, role-key, plot-command, and test edits listed above.

- [ ] **Step 4: Run all paper tests**

Run: `./gradlew :paper:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main paper/src/test api/src/main
git commit -m "Rename nation identifiers to alliance throughout guilds subsystem"
```

---

### Task 11: README rewrite + influence documentation

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the final behavior of Tasks 1–10.

- [ ] **Step 1: Rewrite the governance/protection sections and add the influence section**

Apply these edits to `README.md`:

1. In the "Government / sovereignty" section, replace the town/nation vocabulary:
   - "**towns are the local governments** and **nations are the alliance entities**" → "**guilds are the local governments** and **alliances are the alliance entities**"
   - "- Territory → the bound town's **nation** if the town is a nation member, else the **town** itself, else the territory-local government attachment." → "- Territory → the bound guild's **alliance** if the guild is an alliance member, else the **guild** itself, else the territory-local government attachment."
   - "- Holder → the first guild (town) listing them as a resident." → "- Holder → the first guild listing them as a member."
   - "Each guild (town) and alliance (nation) picks a **governance form** (`/town government <form>`, `/nation government <form>`, mayor/king only)." → "Each guild and alliance picks a **governance form** (`/town government <form>`, `/alliance government <form>` — the `/town` and `/alliance` commands manage guilds and alliances; `/nation` remains as an alias of `/alliance`)."
   - Table header "| Form | Town (guild) seats | Nation (alliance) seats |" → "| Form | Guild seats | Alliance seats |"; row "every resident → `REPRESENTATIVE` | every member-town mayor → `REPRESENTATIVE`" → "every member → `REPRESENTATIVE` | every member-guild mayor → `REPRESENTATIVE`"
   - "(town/nation form + roles)" → "(guild/alliance form + roles)"
   - "// Town picks MONARCHY → the mayor is the sovereign and may decree; // /territory govern everfall everfall-town binds the territory." → "// Guild picks MONARCHY → the mayor is the sovereign and may decree; // /territory govern everfall everfall-town binds the territory." (keep the example id)
2. In "Guilds, alliances, and permissions":
   - "members (residents of the governing town; for nations, any member-town resident)" → "members (members of the governing guild; for alliances, any member-guild member)"
   - "outsiders are denied unless the town is **public**" → "outsiders are denied unless the guild is **public**"
   - "(mirroring guilds town-owned plot defaults)" → "(mirroring guilds guild-owned plot defaults)"
   - "**Environmental flags follow the governing town's toggles**" → "**Environmental flags follow the governing guild's toggles**"
   - "PvP follows the town's `pvp` toggle" → "PvP follows the guild's `pvp` toggle"
   - "GovernanceSource source = guilds.getGovernanceSource(); // towns + nations" → "// guilds + alliances"
   - "blocks.canBreak(\"world\", x, z, \"resident-uuid\"); // true for town members" → "// true for guild members"
   - "// false in a closed town" → "// false in a closed guild"
3. Add a new section after "Guilds, alliances, and permissions" (before "## Data format"):

```markdown
### Territory influence race

New World–style influence contests (config `influence:` block):

- Only **alliance members** participate: both the territory's governing guild
  and the challenger must belong to alliances, and the alliances must differ.
  Unaffiliated guilds cannot accrue influence or be challenged.
- Activity inside a territory accrues influence for the actor's guild:
  PvP kills (10), PvE kills (0.5), block break/place (0.1 each), crafting
  (0.2) — values configurable. Same-alliance PvP kills accrue nothing.
- The governing guild's own activity **defends**: every attacker bar loses
  the same source value (`defender-multiplier`, default 1.0).
- At 100% influence a guild may `/territory declare <id> confirm` (requires a
  seat in the guild's government). The declaration locks the race and the
  territory flips after the countdown (default 24h); the takeover is
  announced server-wide.
- After a flip the new owner is protected by a cooldown (default 7 days)
  during which no new race may start; all influence resets.
- `/territory influence [id]` shows bars, declarations, and cooldowns;
  `/territory influence set|reset` are admin operations.
- State persists in `plugins/GuildsTerritory/influence.json` (bars flushed
  batched; declarations and flips written atomically). Restart recovery
  revalidates owner/attacker alliances before applying an overdue flip.
```

4. In "Data format (sketch)" keep the JSON as-is (territories.json is unchanged).

- [ ] **Step 2: Verify no stray town/nation vocabulary remains in the live docs**

Run: `grep` for `\btown(s)?\b` and `\bnation(s)?\b` (case-insensitive) in `README.md` — the only remaining hits must be: the `/town` command references (factual command names), "town-owned"→removed, the legacy `/nation` alias note, and the archived docs are untouched by design.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Document influence race and guild alliance vocabulary in README"
```

---

### Task 12: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — every module's tests pass.

- [ ] **Step 2: Build the deliverable jar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; artifact at `paper/build/libs/guilds-1.0.0-SNAPSHOT.jar`.

- [ ] **Step 3: Final rename audit**

Run: `grep -riE '\bNation\w*' paper/src/main/java/org/aincraft/guilds` — expect no Java-identifier hits (comments that mention the historical "nation" term are allowed only in migration-history Javadoc). Run `git status --short` — expect a clean tree.

- [ ] **Step 4: Report**

Summarize: influence system behavior, config keys, commands, web endpoints, migration v17, README changes, test counts.

## Self-review notes (filled at plan time)

- Spec coverage: accrual §3–§4 → Tasks 3–6; lifecycle §5 → Task 4; persistence + journal §6 → Tasks 2–4; API §7 → Tasks 1, 8; Paper wiring §8 → Tasks 5–7; web §9 → Task 8; rename §10 → Tasks 9–11; testing §11 → each task's tests; config §12 → Task 5. All spec sections mapped.
- Type consistency: `InfluenceEngine.accrue` 5-arg signature used by listener + tests; `adminSet` 4-arg used by command; `cap()` accessor added in Task 7 for the command; `resolveGuildNameFor` added in Task 7 for the command; `TerritoryWebServer` new ctor param order documented in Task 8; `InfluenceConfig` record field order matches `InfluenceConfigLoader` construction order (enabled, cap, pvpKill, pveKill, blockBreak, blockPlace, craft, defenderMultiplier, declareCountdownHours, postFlipCooldownDays, flushSeconds).
