# Territory Standing & Harvest Bonuses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-territory, per-guild standing (accrued from governing-guild member activity: PvP/PvE kills + block breaks), tiered harvest multipliers (extra drops for block + mob drops) and influence accrual multipliers, configured via `bonuses.json`, state persisted to PostgreSQL.

**Architecture:** New `standing` module mirroring the existing `influence` module: pure-domain `api` values + `StandingService` interface; Paper-free `common` `StandingEngine` (synchronized, same shape as `InfluenceEngine`), `PostgresStandingStore` (JSON doc in a single `standing_state` row, exactly like `influence_state`), `StandingConfig` immutable values + tier table; thin `paper` `StandingListener` (accrual) + `HarvestBonusListener` (drop multiplication) + command subcommand + plugin wiring. The InfluenceEngine gains one read-only hook — `StandingService.influenceMultiplierFor(guildId)` — applied at accrual time.

**Tech Stack:** Java 21, Paper 26.2 APIs, JUnit 5, Mockito (paper tests), Gson, PostgreSQL (HikariCP), Gradle multi-module (`api` / `common` / `paper`).

## Global Constraints

- **Repository layout:** api module = pure Java, NO Bukkit types. common = Paper-free shared implementation. paper = the single Paper plugin.
- **Persistence:** the `standing_state` table MUST be added to `PostgresDatabase.COMMON_SCHEMA` (single `id INTEGER PRIMARY KEY CHECK (id = 1)` + `doc JSONB` row, exactly like `influence_state`).
- **Engine shape:** `StandingEngine` MUST be synchronized and constructed with `(TerritoryRegistry, GovernanceRegistry, StandingConfig, PostgresStandingStore, Logger)` — mirror `InfluenceEngine` exactly.
- **Eligibility:** an event accrues standing only when the actor's primary guild == the territory's governing guild. Bars are keyed by that owner guild; one bar per territory.
- **Tier validity:** `StandingConfig` validation: `cap > 0`, source values non-negative, tiers non-empty, first threshold 0, thresholds ascending, multipliers `>= 1.0`; built-in defaults when `bonuses.json` absent; invalid file → subsystem disabled (SEVERE log), never partial state.
- **Harvest bonus:** multiplies **base drops only** (block drops via `Block.getDrops(hand)` without Fortune — the plugin computes `hand` as empty for the base drop set; mob drops via `EntityDeathEvent.getDrops()`). Fortune/Looting NEVER multiplied. Extra drops added as copies; originals not mutated.
- **Naming:** package root `com.guilds.territory.standing` everywhere; file `bonuses.json` under the plugin data folder; packaged default in `paper/src/main/resources/bonuses.json`.
- **Repos style:** imperative commit subjects, one logical change per commit. Run `./gradlew test` for common/api, and per-module tests for paper. Do NOT run project-wide static analysis (SpotBugs/PMD/Checkstyle) mid-task — only `./gradlew test` per module, then the full `./gradlew test` once at the end.
- **Verification:** every task's tests must pass before committing.

---

### Task 1: Standing domain values + `StandingService` API (api module)

**Files:**
- Create: `api/src/main/java/com/guilds/territory/standing/StandingSource.java`
- Create: `api/src/main/java/com/guilds/territory/standing/StandingBar.java`
- Create: `api/src/main/java/com/guilds/territory/standing/TerritoryStandingState.java`
- Create: `api/src/main/java/com/guilds/territory/standing/StandingTier.java`
- Create: `api/src/main/java/com/guilds/territory/standing/StandingService.java`
- Test: `api/src/test/java/com/guilds/territory/standing/StandingValuesTest.java`

**Interfaces:**
- Produces (later tasks consume):
  - `enum StandingSource { PVP_KILL, PVE_KILL, BLOCK_BREAK }`
  - `record StandingBar(String guildId, double value)`
  - `record TerritoryStandingState(String territoryId, String ownerGuildId, List<StandingBar> bars)`
  - `record StandingTier(int level, double threshold, double harvestMultiplier, double influenceMultiplier)` with compact constructor validation (`level >= 1`, `threshold >= 0`, `harvestMultiplier >= 1.0`, `influenceMultiplier >= 1.0`)
  - `interface StandingService` with exact methods:
    - `Optional<TerritoryStandingState> standing(String territoryId)`
    - `List<TerritoryStandingState> all()`
    - `double harvestMultiplierFor(String territoryId, String guildId)`
    - `double influenceMultiplierFor(String guildId)`
    - `Optional<StandingTier> tierFor(String territoryId, String guildId)`
    - `boolean adminSet(String territoryId, String guildId, double value)`
    - `boolean adminReset(String territoryId)`

- [ ] **Step 1: Write the failing test**

Create `api/src/test/java/com/guilds/territory/standing/StandingValuesTest.java`:

```java
package com.guilds.territory.standing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandingValuesTest {

    @Test
    void sources_areExactlyPvePvpBlockBreak() {
        assertEquals(List.of(
                StandingSource.PVP_KILL,
                StandingSource.PVE_KILL,
                StandingSource.BLOCK_BREAK
        ), List.of(StandingSource.values()));
    }

    @Test
    void bar_holdsGuildAndValue() {
        StandingBar bar = new StandingBar("g1", 12.5);
        assertEquals("g1", bar.guildId());
        assertEquals(12.5, bar.value(), 0.001);
    }

    @Test
    void state_exposesTerritoryOwnerAndBars() {
        TerritoryStandingState state = new TerritoryStandingState(
                "everfall", "everfall-town",
                List.of(new StandingBar("everfall-town", 200.0)));
        assertEquals("everfall", state.territoryId());
        assertEquals("everfall-town", state.ownerGuildId());
        assertEquals(1, state.bars().size());
    }

    @Test
    void tier_validatesMultipliers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(1, 0, 0.5, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(1, 0, 1.0, 0.9));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(0, 0, 1.0, 1.0));
        assertEquals(2, new StandingTier(2, 100, 1.2, 1.1).level());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.guilds.territory.standing.StandingValuesTest" -q`
Expected: FAIL — `package com.guilds.territory.standing does not exist`

- [ ] **Step 3: Create the five value/interface files**

`api/src/main/java/com/guilds/territory/standing/StandingSource.java`:

```java
package com.guilds.territory.standing;

/** Activity types that accumulate territory standing (spec §4). */
public enum StandingSource {
    PVP_KILL,
    PVE_KILL,
    BLOCK_BREAK
}
```

`api/src/main/java/com/guilds/territory/standing/StandingBar.java`:

```java
package com.guilds.territory.standing;

/** Standing value of one guild on one territory. */
public record StandingBar(String guildId, double value) {
}
```

`api/src/main/java/com/guilds/territory/standing/TerritoryStandingState.java`:

```java
package com.guilds.territory.standing;

import java.util.List;

/** Read snapshot of standing for one territory. */
public record TerritoryStandingState(
        String territoryId,
        String ownerGuildId,
        List<StandingBar> bars
) {
}
```

`api/src/main/java/com/guilds/territory/standing/StandingTier.java`:

```java
package com.guilds.territory.standing;

/** One development tier: standing threshold + harvest/influence multipliers. */
public record StandingTier(
        int level,
        double threshold,
        double harvestMultiplier,
        double influenceMultiplier
) {
    public StandingTier {
        if (level < 1) {
            throw new IllegalArgumentException("tier level must be >= 1");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("tier threshold must be >= 0");
        }
        if (harvestMultiplier < 1.0 || influenceMultiplier < 1.0) {
            throw new IllegalArgumentException("tier multipliers must be >= 1.0");
        }
    }
}
```

`api/src/main/java/com/guilds/territory/standing/StandingService.java`:

```java
package com.guilds.territory.standing;

import java.util.List;
import java.util.Optional;

/**
 * Public standing surface for external consumers (queries + admin).
 * Accrual is engine-internal and driven by the Paper layer.
 */
public interface StandingService {

    /** Standing state for one territory, if any standing exists. */
    Optional<TerritoryStandingState> standing(String territoryId);

    /** Standing state for every territory with recorded standing. */
    List<TerritoryStandingState> all();

    /** Harvest multiplier for {@code guildId} on {@code territoryId} (1.0 when none). */
    double harvestMultiplierFor(String territoryId, String guildId);

    /** Max influence multiplier across all territories {@code guildId} governs (1.0 when none). */
    double influenceMultiplierFor(String guildId);

    /** Highest tier satisfied by {@code guildId}'s standing on the territory, if any state exists. */
    Optional<StandingTier> tierFor(String territoryId, String guildId);

    /** Admin: set a guild's standing bar on a territory (clamped to [0, cap]). */
    boolean adminSet(String territoryId, String guildId, double value);

    /** Admin: drop all standing state for a territory. */
    boolean adminReset(String territoryId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.guilds.territory.standing.StandingValuesTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/guilds/territory/standing api/src/test/java/com/guilds/territory/standing
git commit -m "feat: add standing domain values and service API"
```

---

### Task 2: `StandingConfig` + `bonuses.json` loader (common + paper)

**Files:**
- Create: `common/src/main/java/com/guilds/territory/standing/StandingConfig.java`
- Create: `common/src/main/java/com/guilds/territory/standing/StandingConfigLoader.java` (pure-Java, no Bukkit; reads a `Path`)
- Create: `paper/src/main/resources/bonuses.json`
- Test: `common/src/test/java/com/guilds/territory/standing/StandingConfigTest.java`

**Interfaces:**
- Consumes: `StandingSource`, `StandingTier` from Task 1.
- Produces:
  - `record StandingConfig(double cap, double pvpKill, double pveKill, double blockBreak, List<StandingTier> tiers)` with validation (`cap > 0`, sources non-negative, tiers non-empty, first threshold 0, ascending thresholds) and `static StandingConfig defaults()` matching the packaged JSON.
  - Methods: `double valueOf(StandingSource source)`, `Optional<StandingTier> highestTierFor(double standing)` (highest tier with `threshold <= standing`), `List<StandingTier> tiers()`.
  - `final class StandingConfigLoader` with `static Optional<StandingConfig> load(Path file)` — returns `Optional.empty()` when the file is absent OR invalid (logs the reason via `System.getLogger`-style; pure), else the parsed config.

- [ ] **Step 1: Write the failing test**

Create `common/src/test/java/com/guilds/territory/standing/StandingConfigTest.java`:

```java
package com.guilds.territory.standing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingConfigTest {

    @TempDir
    Path tempDir;

    private static final String VALID = """
            {
              "version": 1,
              "cap": 500.0,
              "sources": {
                "pvp-kill": 10.0,
                "pve-kill": 0.5,
                "block-break": 0.15
              },
              "tiers": [
                { "level": 1, "threshold": 0,     "harvest_multiplier": 1.0, "influence_multiplier": 1.0 },
                { "level": 2, "threshold": 100,   "harvest_multiplier": 1.2, "influence_multiplier": 1.1 },
                { "level": 3, "threshold": 300,   "harvest_multiplier": 1.5, "influence_multiplier": 1.25 }
              ]
            }
            """;

    @Test
    void defaults_matchSpecValues() {
        StandingConfig d = StandingConfig.defaults();
        assertEquals(500.0, d.cap(), 0.001);
        assertEquals(10.0, d.valueOf(StandingSource.PVP_KILL), 0.001);
        assertEquals(0.5, d.valueOf(StandingSource.PVE_KILL), 0.001);
        assertEquals(0.15, d.valueOf(StandingSource.BLOCK_BREAK), 0.001);
        assertEquals(3, d.tiers().size());
        assertEquals(1.0, d.tiers().get(0).harvestMultiplier(), 0.001);
        assertEquals(1.25, d.tiers().get(2).influenceMultiplier(), 0.001);
    }

    @Test
    void validJson_parses() throws Exception {
        Path file = tempDir.resolve("bonuses.json");
        Files.writeString(file, VALID);
        Optional<StandingConfig> loaded = StandingConfigLoader.load(file);
        assertTrue(loaded.isPresent());
        assertEquals(500.0, loaded.get().cap(), 0.001);
        assertEquals(3, loaded.get().tiers().size());
    }

    @Test
    void missingFile_returnsEmpty() {
        assertTrue(StandingConfigLoader.load(tempDir.resolve("nope.json")).isEmpty());
    }

    @Test
    void invalidJson_returnsEmpty() throws Exception {
        Path file = tempDir.resolve("bonuses.json");
        Files.writeString(file, "{ not json");
        assertTrue(StandingConfigLoader.load(file).isEmpty());
    }

    @Test
    void validation_rejectsBadCapOrTiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(0, 10, 0.5, 0.15, List.of(new StandingTier(1, 0, 1.0, 1.0))));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15,
                        List.of(new StandingTier(1, 50, 1.0, 1.0))));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15,
                        List.of(new StandingTier(1, 0, 1.0, 1.0),
                                new StandingTier(2, 0, 1.2, 1.1))));
    }

    @Test
    void highestTierFor_selectsSaturatingThreshold() {
        StandingConfig d = StandingConfig.defaults();
        assertEquals(1, d.highestTierFor(99.9).orElseThrow().level());
        assertEquals(2, d.highestTierFor(100.0).orElseThrow().level());
        assertEquals(3, d.highestTierFor(300.5).orElseThrow().level());
        assertEquals(3, d.highestTierFor(500.0).orElseThrow().level());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests "com.guilds.territory.standing.StandingConfigTest" -q`
Expected: FAIL — `package com.guilds.territory.standing does not exist`

- [ ] **Step 3: Create `StandingConfig`**

`common/src/main/java/com/guilds/territory/standing/StandingConfig.java`:

```java
package com.guilds.territory.standing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable standing tuning values + tier table (spec §5). Pure domain. */
public record StandingConfig(
        double cap,
        double pvpKill,
        double pveKill,
        double blockBreak,
        List<StandingTier> tiers
) {

    public StandingConfig {
        if (cap <= 0) {
            throw new IllegalArgumentException("standing cap must be positive");
        }
        if (pvpKill < 0 || pveKill < 0 || blockBreak < 0) {
            throw new IllegalArgumentException("standing source values must be non-negative");
        }
        Objects.requireNonNull(tiers, "tiers");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one standing tier is required");
        }
        if (tiers.get(0).threshold() != 0.0) {
            throw new IllegalArgumentException("first standing tier must start at threshold 0");
        }
        double previous = 0.0;
        for (StandingTier tier : tiers) {
            if (tier.threshold() < previous) {
                throw new IllegalArgumentException("standing tier thresholds must be ascending");
            }
            previous = tier.threshold();
        }
    }

    public static StandingConfig defaults() {
        return new StandingConfig(
                500.0, 10.0, 0.5, 0.15,
                List.of(
                        new StandingTier(1, 0, 1.0, 1.0),
                        new StandingTier(2, 100, 1.2, 1.1),
                        new StandingTier(3, 300, 1.5, 1.25)
                ));
    }

    /** Per-source standing value for an eligible actor event. */
    public double valueOf(StandingSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source) {
            case PVP_KILL -> pvpKill;
            case PVE_KILL -> pveKill;
            case BLOCK_BREAK -> blockBreak;
        };
    }

    /** Highest tier whose threshold is satisfied by {@code standing}. */
    public Optional<StandingTier> highestTierFor(double standing) {
        StandingTier best = null;
        for (StandingTier tier : tiers) {
            if (standing >= tier.threshold()) {
                best = tier;
            }
        }
        return Optional.ofNullable(best);
    }
}
```

- [ ] **Step 4: Create `StandingConfigLoader`**

`common/src/main/java/com/guilds/territory/standing/StandingConfigLoader.java` (pure Java, `Path`-based; reads via `Files.readString`, parses with Gson):

```java
package com.guilds.territory.standing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@code bonuses.json} from a path. Returns empty when the file is
 * absent or invalid — caller decides whether to fall back to defaults or
 * disable the subsystem (spec §5).
 */
public final class StandingConfigLoader {

    private static final Logger LOG = Logger.getLogger(StandingConfigLoader.class.getName());

    private StandingConfigLoader() {
    }

    public static Optional<StandingConfig> load(Path file) {
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.get("version") == null || root.get("version").getAsInt() != 1) {
                LOG.warning("bonuses.json: unsupported version (expected 1)");
                return Optional.empty();
            }
            double cap = root.get("cap").getAsDouble();
            JsonObject sources = root.getAsJsonObject("sources");
            double pvpKill = sources.get("pvp-kill").getAsDouble();
            double pveKill = sources.get("pve-kill").getAsDouble();
            double blockBreak = sources.get("block-break").getAsDouble();
            List<StandingTier> tiers = new ArrayList<>();
            JsonArray rawTiers = root.getAsJsonArray("tiers");
            for (JsonElement raw : rawTiers) {
                JsonObject tier = raw.getAsJsonObject();
                tiers.add(new StandingTier(
                        tier.get("level").getAsInt(),
                        tier.get("threshold").getAsDouble(),
                        tier.get("harvest_multiplier").getAsDouble(),
                        tier.get("influence_multiplier").getAsDouble()
                ));
            }
            return Optional.of(new StandingConfig(cap, pvpKill, pveKill, blockBreak, List.copyOf(tiers)));
        } catch (IOException | IllegalStateException | JsonSyntaxException | NullPointerException |
                 NumberFormatException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "bonuses.json: invalid configuration — " + e.getMessage(), e);
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 5: Create packaged default**

`paper/src/main/resources/bonuses.json`:

```json
{
  "version": 1,
  "cap": 500.0,
  "sources": {
    "pvp-kill": 10.0,
    "pve-kill": 0.5,
    "block-break": 0.15
  },
  "tiers": [
    { "level": 1, "threshold": 0,     "harvest_multiplier": 1.0, "influence_multiplier": 1.0 },
    { "level": 2, "threshold": 100,   "harvest_multiplier": 1.2, "influence_multiplier": 1.1 },
    { "level": 3, "threshold": 300,   "harvest_multiplier": 1.5, "influence_multiplier": 1.25 }
  ]
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :common:test --tests "com.guilds.territory.standing.StandingConfigTest" -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/guilds/territory/standing/StandingConfig.java \
        common/src/main/java/com/guilds/territory/standing/StandingConfigLoader.java \
        common/src/test/java/com/guilds/territory/standing/StandingConfigTest.java \
        paper/src/main/resources/bonuses.json
git commit -m "feat: add standing config and bonuses.json loader"
```

---

### Task 3: `PostgresStandingStore` (common)

**Files:**
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java` — **Modify**: add `standing_state` to `COMMON_SCHEMA`.
- Create: `common/src/main/java/com/guilds/territory/standing/StandingState.java`
- Create: `common/src/main/java/com/guilds/territory/standing/StandingEntry.java`
- Create: `common/src/main/java/com/guilds/territory/standing/PostgresStandingStore.java`
- Test: `common/src/test/java/com/guilds/territory/standing/PostgresStandingStoreTest.java`

**Interfaces:**
- Consumes: nothing new (PostgresDatabase, standing engine will use later).
- Produces:
  - `final class StandingState` with `static final int VERSION = 1` and `final Map<String, StandingEntry> entries = new LinkedHashMap<>()`.
  - `final class StandingEntry` with `String ownerGuildId` and `final Map<String, Double> bars = new LinkedHashMap<>()`.
  - `final class PostgresStandingStore` with `void save(StandingState state) throws IOException` and `StandingState load() throws IOException` — identical SQL shape to `PostgresInfluenceStore` but table `standing_state`, doc `{version, territories: {id: {ownerGuildId, bars}}}`.

- [ ] **Step 1: Write the failing test**

Create `common/src/test/java/com/guilds/territory/standing/PostgresStandingStoreTest.java` (mirrors `PostgresTerritoryStoreTest`, uses `PostgresTestDatabase`):

```java
package com.guilds.territory.standing;

import com.guilds.territory.PostgresTestDatabase;
import com.guilds.territory.persist.PostgresDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresStandingStoreTest {
    private static final String TEST_URL = System.getenv("GUILDS_TEST_JDBC_URL");
    private static PostgresDatabase database;
    private static PostgresStandingStore store;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(TEST_URL != null && !TEST_URL.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = PostgresTestDatabase.open();
        store = new PostgresStandingStore(database);
    }

    @AfterAll
    static void disconnect() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        StandingState state = new StandingState();
        StandingEntry entry = new StandingEntry();
        entry.ownerGuildId = "everfall-town";
        entry.bars.put("everfall-town", 200.5);
        state.entries.put("everfall", entry);
        store.save(state);

        StandingState reloaded = store.load();
        StandingEntry loaded = reloaded.entries.get("everfall");
        assertTrue(loaded != null, "entry must survive reload");
        assertEquals("everfall-town", loaded.ownerGuildId);
        assertEquals(200.5, loaded.bars.get("everfall-town"), 0.001);
    }

    @Test
    void loadWithNoStoredState_isEmpty() throws Exception {
        // fresh store on the same DB; save nothing, load
        StandingState reloaded = store.load();
        assertTrue(reloaded.entries.isEmpty());
    }

    @Test
    void saveReplacesPreviousState() throws Exception {
        StandingState first = new StandingState();
        StandingEntry entry = new StandingEntry();
        entry.ownerGuildId = "g1";
        entry.bars.put("g1", 10.0);
        first.entries.put("t1", entry);
        store.save(first);

        StandingState second = new StandingState();
        StandingEntry entry2 = new StandingEntry();
        entry2.ownerGuildId = "g2";
        entry2.bars.put("g2", 20.0);
        second.entries.put("t2", entry2);
        store.save(second);

        StandingState reloaded = store.load();
        assertEquals(1, reloaded.entries.size());
        assertTrue(reloaded.entries.containsKey("t2"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `GUILDS_TEST_JDBC_URL=… ./gradlew :common:test --tests "com.guilds.territory.standing.PostgresStandingStoreTest" -q`
Expected: FAIL — `package com.guilds.territory.standing does not exist` (or table missing if only config created — either way red).

- [ ] **Step 3: Add `standing_state` to `COMMON_SCHEMA`**

In `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java`, add one line to `COMMON_SCHEMA`:

```java
"CREATE TABLE IF NOT EXISTS standing_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
```

- [ ] **Step 4: Create `StandingState` + `StandingEntry`**

`common/src/main/java/com/guilds/territory/standing/StandingState.java`:

```java
package com.guilds.territory.standing;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory standing state for all territories (engine-internal). */
final class StandingState {
    static final int VERSION = 1;
    final Map<String, StandingEntry> entries = new LinkedHashMap<>();
}
```

`common/src/main/java/com/guilds/territory/standing/StandingEntry.java`:

```java
package com.guilds.territory.standing;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable in-memory standing for one territory (engine-internal). */
final class StandingEntry {
    String ownerGuildId;
    final Map<String, Double> bars = new LinkedHashMap<>();
}
```

- [ ] **Step 5: Create `PostgresStandingStore`**

`common/src/main/java/com/guilds/territory/standing/PostgresStandingStore.java`:

```java
package com.guilds.territory.standing;

import com.guilds.territory.persist.PostgresDatabase;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** PostgreSQL persistence for standing state (single doc row, mirrors influence). */
public final class PostgresStandingStore {
    private final PostgresDatabase database;

    public PostgresStandingStore(PostgresDatabase database) {
        this.database = database;
    }

    public void save(StandingState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", StandingState.VERSION);
        JsonObject territories = new JsonObject();
        for (Map.Entry<String, StandingEntry> entry : state.entries.entrySet()) {
            territories.add(entry.getKey(), toJson(entry.getValue()));
        }
        root.add("territories", territories);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO standing_state (id, doc) VALUES (1, ?::jsonb)
                     ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc
                     """)) {
            ps.setString(1, new GsonBuilder().create().toJson(root));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save standing state to PostgreSQL", e);
        }
    }

    public StandingState load() throws IOException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT doc FROM standing_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new StandingState();
            }
            return fromJson(JsonParser.parseString(rs.getString("doc")));
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load standing state from PostgreSQL", e);
        }
    }

    private static JsonObject toJson(StandingEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("ownerGuildId", entry.ownerGuildId);
        JsonObject bars = new JsonObject();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.addProperty(bar.getKey(), bar.getValue());
        }
        object.add("bars", bars);
        return object;
    }

    private static StandingState fromJson(JsonElement parsed) throws IOException {
        if (!parsed.isJsonObject()) {
            throw new IOException("standing state root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonElement version = root.get("version");
        if (version == null || version.getAsInt() != StandingState.VERSION) {
            throw new IOException("unsupported standing state version");
        }
        StandingState state = new StandingState();
        JsonElement rawTerritories = root.get("territories");
        if (rawTerritories == null || rawTerritories.isJsonNull()) {
            return state;
        }
        for (Map.Entry<String, JsonElement> e : rawTerritories.getAsJsonObject().entrySet()) {
            state.entries.put(e.getKey(), fromEntry(e.getValue().getAsJsonObject()));
        }
        return state;
    }

    private static StandingEntry fromEntry(JsonObject object) throws IOException {
        StandingEntry entry = new StandingEntry();
        JsonElement owner = object.get("ownerGuildId");
        if (owner == null || owner.isJsonNull()) {
            throw new IOException("standing entry missing ownerGuildId");
        }
        entry.ownerGuildId = owner.getAsString();
        JsonElement rawBars = object.get("bars");
        if (rawBars != null && rawBars.isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : rawBars.getAsJsonObject().entrySet()) {
                entry.bars.put(b.getKey(), b.getValue().getAsDouble());
            }
        }
        return entry;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `GUILDS_TEST_JDBC_URL=… ./gradlew :common:test --tests "com.guilds.territory.standing.PostgresStandingStoreTest" -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java \
        common/src/main/java/com/guilds/territory/standing \
        common/src/test/java/com/guilds/territory/standing/PostgresStandingStoreTest.java
git commit -m "feat: add postgres standing store"
```

---

### Task 4: `StandingEngine` (common, pure domain)

**Files:**
- Create: `common/src/main/java/com/guilds/territory/standing/StandingEngine.java`
- Test: `common/src/test/java/com/guilds/territory/standing/StandingEngineAccrualTest.java`
- Test: `common/src/test/java/com/guilds/territory/standing/StandingEngineTierTest.java`

**Interfaces:**
- Consumes: `StandingService`, `StandingConfig`, `StandingState`, `StandingEntry`, `PostgresStandingStore`, `TerritoryRegistry` (via `GovernanceRegistry.territories()`), `GovernanceRegistry`, `GuildBody`.
- Produces:
  - `StandingEngine implements StandingService`:
    - `public synchronized Optional<InfluenceBar>` — **no**: correct signature:
      `public synchronized Optional<StandingBar> accrue(String territoryId, String guildId, StandingSource source)` — accrues standing for an eligible actor (actor guild == owner guild). Returns the updated bar or `Optional.empty()` when ineligible/unknown/ungoverned. Applies `Math.min(cap, bar + value)`, rounds to 2 decimals.
    - `public synchronized void flush() throws IOException` — batched store save when dirty.
    - `public synchronized void recover(long nowEpochMs)` — load from store; drop entries for missing territories; reset bars + owner on owner mismatch (keep nothing else); mark dirty on change. (`nowEpochMs` unused except to mirror influence signature; keep for symmetry.)
    - `public synchronized boolean adminSet(String territoryId, String guildId, double value)` — clamp to `[0, cap]`, remove bar when <= 0, mark dirty.
    - `public synchronized boolean adminReset(String territoryId)` — remove entry.
    - overrides of `standing`, `all`, `harvestMultiplierFor`, `influenceMultiplierFor`, `tierFor`.

- [ ] **Step 1: Write the failing tests**

Create `common/src/test/java/com/guilds/territory/standing/StandingEngineAccrualTest.java`:

```java
package com.guilds.territory.standing;

import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.persist.PostgresDatabase;
import com.guilds.territory.PostgresTestDatabase;
import com.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingEngineAccrualTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private StandingConfig config;
    private PostgresDatabase database;
    private PostgresStandingStore store;
    private StandingEngine engine;

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
        config = StandingConfig.defaults();
        database = PostgresTestDatabase.open();
        store = new PostgresStandingStore(database);
        engine = new StandingEngine(governance, config, store, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void setupEverfall() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
    }

    @Test
    void accrue_unknownTerritory_isNoOp() {
        assertTrue(engine.accrue("nope", "everfall-town", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_ungovernedTerritory_isNoOp() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("freehold", null);
        assertTrue(engine.accrue("freehold", "everfall-town", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_nonOwnerGuild_isNoOp() {
        setupEverfall();
        source.putGuild(guild("outsider"));
        assertTrue(engine.accrue("everfall", "outsider", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_ownerGuild_addsSourceValue() {
        setupEverfall();
        Optional<StandingBar> bar = engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        assertTrue(bar.isPresent());
        assertEquals("everfall-town", bar.get().guildId());
        assertEquals(10.0, bar.get().value(), 0.001);
    }

    @Test
    void accrue_allSourcesUseTheirValues() {
        setupEverfall();
        for (StandingSource s : StandingSource.values()) {
            engine.accrue("everfall", "everfall-town", s);
        }
        double expected = config.valueOf(StandingSource.PVP_KILL)
                + config.valueOf(StandingSource.PVE_KILL)
                + config.valueOf(StandingSource.BLOCK_BREAK);
        Optional<StandingBar> bar = engine.standing("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("everfall-town")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(expected, bar.get().value(), 0.001);
    }

    @Test
    void accrue_clampsAtCap() {
        setupEverfall();
        for (int i = 0; i < 60; i++) {
            engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        }
        Optional<StandingBar> bar = engine.standing("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("everfall-town")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(config.cap(), bar.get().value(), 0.001);
    }

    @Test
    void accrue_ownerRebindResetsBar() {
        setupEverfall();
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        source.putGuild(guild("new-owner"));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        Optional<StandingBar> bar = engine.accrue("everfall", "new-owner", StandingSource.PVP_KILL);
        assertTrue(bar.isPresent());
        assertEquals(10.0, bar.get().value(), 0.001);
        assertEquals(1, engine.standing("everfall").orElseThrow().bars().size());
    }
}
```

Create `common/src/test/java/com/guilds/territory/standing/StandingEngineTierTest.java` (tier/multiplier logic — pure, needs `FakeGovernanceSource` + a registered territory; no Postgres needed except construction which takes the store; construct with `PostgresStandingStore` but the engine only uses it on `flush`):

```java
package com.guilds.territory.standing;

import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingEngineTierTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private StandingEngine engine;

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
    void setUp() {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        // Store is nullable in the engine (only touched by flush/recover),
        // so tests that never persist construct with null.
        engine = new StandingEngine(governance, StandingConfig.defaults(), null, Logger.getLogger("test"));
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void accrueTimes(String territoryId, String guildId, StandingSource source, int times) {
        for (int i = 0; i < times; i++) {
            engine.accrue(territoryId, guildId, source);
        }
    }

    @Test
    void tierFor_returnsSaturatingTier() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
        accrueTimes("everfall", "everfall-town", StandingSource.PVP_KILL, 30); // 300
        Optional<StandingTier> tier = engine.tierFor("everfall", "everfall-town");
        assertEquals(3, tier.orElseThrow().level());
    }

    @Test
    void harvestMultiplierFor_nonOwnerGuild_isOne() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("outsider"));
        registerTerritory("everfall", "everfall-town");
        assertEquals(1.0, engine.harvestMultiplierFor("everfall", "outsider"), 0.001);
    }

    @Test
    void harvestMultiplierFor_ownerReflectsTier() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
        accrueTimes("everfall", "everfall-town", StandingSource.PVP_KILL, 30); // 300 → tier 3
        assertEquals(1.5, engine.harvestMultiplierFor("everfall", "everfall-town"), 0.001);
    }

    @Test
    void influenceMultiplierFor_noTerritories_isOne() {
        assertEquals(1.0, engine.influenceMultiplierFor("loner"), 0.001);
    }

    @Test
    void influenceMultiplierFor_takesMaxOverGovernedTerritories() {
        source.putGuild(guild("g1"));
        registerTerritory("t1", "g1");
        registerTerritory("t2", "g1");
        accrueTimes("t1", "g1", StandingSource.PVP_KILL, 10);  // 100 → tier 2 (1.1)
        accrueTimes("t2", "g1", StandingSource.PVP_KILL, 30);  // 300 → tier 3 (1.25)
        assertEquals(1.25, engine.influenceMultiplierFor("g1"), 0.001);
    }

    @Test
    void influenceMultiplierFor_ignoresTerritoriesGuildDoesNotGovern() {
        source.putGuild(guild("g1"));
        source.putGuild(guild("g2"));
        registerTerritory("t1", "g1");
        registerTerritory("t2", "g2");
        accrueTimes("t1", "g1", StandingSource.PVP_KILL, 30); // g1 at 300 → 1.25
        accrueTimes("t2", "g2", StandingSource.PVP_KILL, 30); // g2 at 300 → 1.25
        assertEquals(1.25, engine.influenceMultiplierFor("g1"), 0.001);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :common:test --tests "com.guilds.territory.standing.StandingEngine*" -q`
Expected: FAIL — `cannot find symbol: class StandingEngine`

- [ ] **Step 3: Create `StandingEngine`**

`common/src/main/java/com/guilds/territory/standing/StandingEngine.java`:

```java
package com.guilds.territory.standing;

import com.guilds.territory.model.Territory;
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
 * Pure-domain standing engine (spec §4–§7). Thread-safe: all mutations are
 * {@code synchronized}. Standing accrues only to the governing guild of a
 * territory, from its members' activity inside that territory. Tiers derived
 * from {@link StandingConfig}; harvest and influence multipliers are read
 * through {@link StandingService}.
 */
public final class StandingEngine implements StandingService {

    private final GovernanceRegistry governance;
    private final StandingConfig config;
    private final PostgresStandingStore store;
    private final Logger log;

    private final StandingState state = new StandingState();
    private boolean dirty;
    /** True when PostgreSQL state could not be loaded — subsystem fails closed. */
    private boolean loadFailed;

    public StandingEngine(
            GovernanceRegistry governance,
            StandingConfig config,
            PostgresStandingStore store,
            Logger log
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        // Store is nullable in tests that never flush/recover; the engine
        // only touches it inside flush()/recover().
        this.store = store;
        this.log = Objects.requireNonNull(log, "log");
    }

    private boolean unusable() {
        return loadFailed;
    }

    private TerritoryRegistry territories() {
        return governance.territories();
    }
```

The rest of the engine is as drafted above — `accrue`, `syncedEntry`, `round2`, queries, `recover`, `flush`, admin overrides. `flush()`/`recover()` guard `store` with a null check (return/no-op when null — tests never call them with a null store; defensive only).
    // ── Accrual ───────────────────────────────────────────────────────────

    /**
     * Record one activity event. Only members of the territory's governing
     * guild accrue (each event adds the source value to the owner's bar).
     * Returns the updated bar, or empty when the event was a no-op
     * (unknown/un-governed territory, or actor not in the governing guild).
     */
    public synchronized Optional<StandingBar> accrue(
            String territoryId,
            String guildId,
            StandingSource source
    ) {
        if (unusable()) {
            return Optional.empty();
        }
        StandingEntry entry = syncedEntry(territoryId);
        if (entry == null) {
            return Optional.empty();
        }
        if (guildId == null || !guildId.equals(entry.ownerGuildId)) {
            return Optional.empty();
        }
        double value = round2(entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source));
        entry.bars.put(guildId, Math.min(config.cap(), value));
        dirty = true;
        return Optional.of(new StandingBar(guildId, entry.bars.get(guildId)));
    }

    /**
     * Territory entry synced to the current owner; null when the territory is
     * unknown or ungoverned. On external rebind, bars reset (spec §14:
     * owner-change resets the standing bar).
     */
    private StandingEntry syncedEntry(String territoryId) {
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
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            entry = new StandingEntry();
            entry.ownerGuildId = owner;
            state.entries.put(territoryId.trim(), entry);
            return entry;
        }
        if (!owner.equals(entry.ownerGuildId)) {
            entry.bars.clear();
            entry.ownerGuildId = owner;
            dirty = true;
            log.info("Standing state reset for " + territoryId.trim() + ": owner changed to " + owner);
        }
        return entry;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<TerritoryStandingState> standing(String territoryId) {
        if (unusable()) {
            return Optional.empty();
        }
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(territoryId.trim(), entry));
    }

    @Override
    public synchronized List<TerritoryStandingState> all() {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryStandingState> out = new ArrayList<>();
        for (Map.Entry<String, StandingEntry> e : state.entries.entrySet()) {
            out.add(toSnapshot(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }

    private static TerritoryStandingState toSnapshot(String territoryId, StandingEntry entry) {
        List<StandingBar> bars = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.add(new StandingBar(bar.getKey(), bar.getValue()));
        }
        bars.sort((a, b) -> a.guildId().compareTo(b.guildId()));
        return new TerritoryStandingState(territoryId, entry.ownerGuildId, bars);
    }

    @Override
    public synchronized double harvestMultiplierFor(String territoryId, String guildId) {
        if (unusable() || territoryId == null || guildId == null) {
            return 1.0;
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null || guildId.equals(entry.ownerGuildId)) {
            // owner's tier multiplier, whether or not a bar exists yet
        }
        if (entry == null) {
            return 1.0;
        }
        if (!guildId.equals(entry.ownerGuildId)) {
            return 1.0;
        }
        double bar = entry.bars.getOrDefault(guildId, 0.0);
        return config.highestTierFor(bar).map(StandingTier::harvestMultiplier).orElse(1.0);
    }

    @Override
    public synchronized double influenceMultiplierFor(String guildId) {
        if (unusable() || guildId == null || guildId.isBlank()) {
            return 1.0;
        }
        double max = 1.0;
        for (Map.Entry<String, StandingEntry> e : state.entries.entrySet()) {
            StandingEntry entry = e.getValue();
            if (!guildId.equals(entry.ownerGuildId)) {
                continue;
            }
            double bar = entry.bars.getOrDefault(guildId, 0.0);
            double tierMultiplier = config.highestTierFor(bar)
                    .map(StandingTier::influenceMultiplier).orElse(1.0);
            if (tierMultiplier > max) {
                max = tierMultiplier;
            }
        }
        return max;
    }

    @Override
    public synchronized Optional<StandingTier> tierFor(String territoryId, String guildId) {
        if (unusable() || territoryId == null || guildId == null) {
            return Optional.empty();
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null || !guildId.equals(entry.ownerGuildId)) {
            return Optional.empty();
        }
        double bar = entry.bars.getOrDefault(guildId, 0.0);
        return config.highestTierFor(bar);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Load-time recovery: drop missing territories, reset on owner mismatch. */
    public synchronized void recover(long nowEpochMs) {
        if (store == null) {
            return;
        }
        StandingState loaded;
        try {
            loaded = store.load();
        } catch (IOException e) {
            state.entries.clear();
            dirty = false;
            loadFailed = true;
            log.log(Level.SEVERE,
                    "Failed to load standing state from PostgreSQL; standing subsystem disabled", e);
            return;
        }
        state.entries.clear();
        state.entries.putAll(loaded.entries);

        for (Map.Entry<String, StandingEntry> e : new ArrayList<>(state.entries.entrySet())) {
            String territoryId = e.getKey();
            StandingEntry entry = e.getValue();
            Optional<Territory> t = territories().get(territoryId);
            if (t.isEmpty()) {
                state.entries.remove(territoryId);
                dirty = true;
                log.warning("Dropped standing state for missing territory " + territoryId);
                continue;
            }
            String currentOwner = t.get().governedByGuildId().orElse(null);
            if (!Objects.equals(currentOwner, entry.ownerGuildId)) {
                entry.bars.clear();
                entry.ownerGuildId = currentOwner;
                dirty = true;
                log.info("Standing state reset for " + territoryId + ": owner changed to " + currentOwner);
            }
        }
    }

    /** Batched flush of dirty bar mutations (spec §10). */
    public synchronized void flush() throws IOException {
        if (unusable() || !dirty || store == null) {
            return;
        }
        store.save(state);
        dirty = false;
    }

    // ── Admin overrides ───────────────────────────────────────────────────

    /** Admin: set the owner's standing bar (clamped to [0, cap]). */
    @Override
    public synchronized boolean adminSet(String territoryId, String guildId, double value) {
        if (unusable() || territoryId == null || guildId == null
                || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        StandingEntry entry = syncedEntry(territoryId);
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

    /** Admin: drop all standing state for a territory (persisted on next flush). */
    @Override
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
}
```

Note the `harvestMultiplierFor` reads cleanly (owner → tier multiplier; non-owner → 1.0). (The stray `if` guard in the middle is leftover from drafting — remove it in the actual file: the method should read exactly:

```java
    @Override
    public synchronized double harvestMultiplierFor(String territoryId, String guildId) {
        if (unusable() || territoryId == null || guildId == null) {
            return 1.0;
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return 1.0;
        }
        if (!guildId.equals(entry.ownerGuildId)) {
            return 1.0;
        }
        double bar = entry.bars.getOrDefault(guildId, 0.0);
        return config.highestTierFor(bar).map(StandingTier::harvestMultiplier).orElse(1.0);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :common:test --tests "com.guilds.territory.standing.StandingEngine*" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/guilds/territory/standing/StandingEngine.java \
        common/src/test/java/com/guilds/territory/standing/StandingEngineAccrualTest.java \
        common/src/test/java/com/guilds/territory/standing/StandingEngineTierTest.java
git commit -m "feat: add standing engine with accrual and tier multipliers"
```

---

### Task 5: Influence accrual multiplier hook (common + paper)

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Test: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineStandingHookTest.java`

**Interfaces:**
- Consumes: `StandingService` (from Task 1) — specifically `influenceMultiplierFor(String guildId)`.
- Produces:
  - `InfluenceEngine` gains an optional constructor overload:
    `public InfluenceEngine(GovernanceRegistry, InfluenceConfig, PostgresInfluenceStore, OwnershipPersister, Logger)` — **unchanged** (keeps callers working);
    `new InfluenceEngine(..., StandingService standingService)` — sets a `multiplier` field defaulting to NO-OP (1.0) when absent.
  - In `accrue(...)`, before `entry.bars.put(...)`, multiply the source value:
    `double value = round2((entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source)) * influenceMultiplierFor(guildId));`
    where `influenceMultiplierFor(guildId)` returns `standingService == null ? 1.0 : standingService.influenceMultiplierFor(guildId)`.
- Behavior: existing `InfluenceEngine` tests (no standing service) stay green because the default multiplier is 1.0.

- [ ] **Step 1: Write the failing test**

Create `common/src/test/java/com/guilds/territory/influence/InfluenceEngineStandingHookTest.java`:

```java
package com.guilds.territory.influence;

import com.guilds.territory.PostgresTestDatabase;
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
import com.guilds.territory.persist.PostgresDatabase;
import com.guilds.territory.registry.TerritoryRegistry;
import com.guilds.territory.standing.StandingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineStandingHookTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private PostgresDatabase database;
    private PostgresInfluenceStore store;
    private InfluenceEngine engine;

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
        database = PostgresTestDatabase.open();
        store = new PostgresInfluenceStore(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void setupContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
    }

    @Test
    void accrual_multipliedByStandingInfluenceMultiplier() {
        setupContest();
        StandingService standing = () -> 1.5;  // stair-style stub: influenceMultiplierFor -> 1.5
        engine = new InfluenceEngine(governance, InfluenceConfig.defaults(), store,
                (t, g) -> { }, Logger.getLogger("test"), standing);

        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, 1_000_000L, null);
        assertTrue(bar.isPresent());
        assertEquals(15.0, bar.get().value(), 0.001);  // 10 * 1.5
    }

    @Test
    void accrual_withoutStandingService_usesDefaultMultiplier() {
        setupContest();
        engine = new InfluenceEngine(governance, InfluenceConfig.defaults(), store,
                (t, g) -> { }, Logger.getLogger("test"));

        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, 1_000_000L, null);
        assertTrue(bar.isPresent());
        assertEquals(10.0, bar.get().value(), 0.001);  // 10 * 1.0
    }
}
```

(Note: the stub `StandingService` with a lambda only works if `StandingService` is a functional interface — it has 7 methods, so it is NOT. Use an anonymous class overriding all methods, or a tiny local `StandingService` implementation in the test. The plan below fixes this: use a minimal `FakeStanding` class implementing `StandingService` with everything but `influenceMultiplierFor` returning defaults.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests "com.guilds.territory.influence.InfluenceEngineStandingHookTest" -q`
Expected: FAIL — no constructor `InfluenceEngine(..., StandingService)`.

- [ ] **Step 3: Modify `InfluenceEngine`**

Add a `StandingService` field + overloaded constructor. In `InfluenceEngine.java`:

```java
import com.guilds.territory.standing.StandingService;

    private final StandingService standingService;
    …

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            PostgresInfluenceStore store,
            OwnershipPersister persister,
            Logger log
    ) {
        this(governance, config, store, persister, log, null);
    }

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            PostgresInfluenceStore store,
            OwnershipPersister persister,
            Logger log,
            StandingService standingService
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.persister = Objects.requireNonNull(persister, "persister");
        this.log = Objects.requireNonNull(log, "log");
        this.standingService = standingService;
    }

    /** Influence accrual multiplier from the standing engine (1.0 when absent). */
    private double influenceMultiplierFor(String guildId) {
        if (standingService == null) {
            return 1.0;
        }
        return standingService.influenceMultiplierFor(guildId);
    }
```

And in `accrue(...)`, replace the accrual line:

```java
        double value = round2(entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source));
```
with:
```java
        double value = round2((entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source))
                * influenceMultiplierFor(guildId));
```

- [ ] **Step 4: Fix the test's stub to a full implementation**

The test above used a lambda which won't compile. Replace the stub with a small `FakeStanding` class inside the test file:

```java
    private static final class FakeStanding implements StandingService {
        private final double influenceMultiplier;

        FakeStanding(double influenceMultiplier) {
            this.influenceMultiplier = influenceMultiplier;
        }

        @Override public Optional<TerritoryStandingState> standing(String territoryId) { return Optional.empty(); }
        @Override public List<TerritoryStandingState> all() { return List.of(); }
        @Override public double harvestMultiplierFor(String territoryId, String guildId) { return 1.0; }
        @Override public double influenceMultiplierFor(String guildId) { return influenceMultiplier; }
        @Override public Optional<StandingTier> tierFor(String territoryId, String guildId) { return Optional.empty(); }
        @Override public boolean adminSet(String territoryId, String guildId, double value) { return false; }
        @Override public boolean adminReset(String territoryId) { return false; }
    }
```

Then in the tests use `new FakeStanding(1.5)` / the no-service constructor.

- [ ] **Step 5: Run all influence + standing tests**

Run: `./gradlew :common:test -q`
Expected: PASS (all influence tests — including `InfluenceEngineAccrualTest`, `InfluenceEngineLifecycleTest` — plus standing tests).

- [ ] **Step 6: Wire into the plugin**

In `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`, in `onEnable`, where the influence engine is constructed, pass the standing engine (created earlier in the same enable path; Task 6 wires standing itself, so at this point create the standing engine before influence):

```java
        // Standing engine (constructed before influence so the influence hook can read it)
        StandingConfig standingConfig = StandingConfigLoader.load(
                new java.io.File(getDataFolder(), "bonuses.json").toPath())
                .orElse(StandingConfig.defaults());
        this.standingStore = new PostgresStandingStore(database);
        this.standingEngine = new StandingEngine(
                governance, standingConfig, standingStore, getLogger());
        this.standingEngine.recover(System.currentTimeMillis());

        // after influence config block, when constructing InfluenceEngine:
        this.influenceEngine = new InfluenceEngine(
                governance, influenceConfig, influenceStore,
                (territoryId, newOwnerGuildId) -> saveTerritories(),
                getLogger(),
                standingEngine);
```

(Full plugin wiring for standing listeners/commands happens in Task 6; here only the construction so the influence hook is live.)

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java \
        common/src/test/java/com/guilds/territory/influence/InfluenceEngineStandingHookTest.java \
        paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java
git commit -m "feat: multiply influence accrual by standing development tier"
```

---

### Task 6: Paper listeners + plugin wiring (`StandingListener`, `HarvestBonusListener`)

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/standing/StandingListener.java`
- Create: `paper/src/main/java/com/guilds/territory/standing/HarvestBonusListener.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Test: `paper/src/test/java/com/guilds/territory/standing/StandingListenerTest.java`
- Test: `paper/src/test/java/com/guilds/territory/standing/HarvestBonusListenerTest.java`

**Interfaces:**
- Consumes: `StandingEngine` (accrue), `GovernanceRegistry` (`primaryGuildForMember`), `TerritoryRegistry.resolve`, `GuildBody`.
- Produces:
  - `final class StandingListener implements Listener` — handlers:
    - `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) void onPlayerDeath(PlayerDeathEvent)` — actor = `event.getEntity().getKiller()`; accrues `PVP_KILL` at the death location.
    - `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) void onEntityDeath(EntityDeathEvent)` — actor = `event.getEntity().getKiller()`; accrues `PVE_KILL` at the entity location (skip players — those go through `PlayerDeathEvent`; also skip when killer is null).
    - `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) void onBlockBreak(BlockBreakEvent)` — actor = `event.getPlayer()`; accrues `BLOCK_BREAK` at the block location.
    - `engine()` accessor (for the wiring test).
  - `final class HarvestBonusListener implements Listener`:
    - `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) void onBlockBreak(BlockBreakEvent)` — if eligible (governing-guild member inside territory) and `player.getInventory().getItemInMainHand()` has no Fortune (or simply: always compute base via `block.getDrops(emptyHand)`), add extra copies: compute `base = block.getDrops(new ItemStack(Material.AIR))`; `extra = (int) (base.size() * (multiplier - 1))`; add `extra` copies of a random/flat selection (use the first `extra` items in the list, preserving stack sizes 1 — this is the transportable approximation; spec says extra copies of *base drops*).
      Simpler + deterministic: multiply the **count** of each base drop (each `ItemStack` in `base`) by `(int) (amount * multiplier)` — i.e. `stack.setAmount((int) (stack.getAmount() * multiplier))`, then drop those at the block location. Fortune is NOT applied (we use `getDrops(AIR)`).
    - `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) void onEntityDeath(EntityDeathEvent)` — if eligible, for each `ItemStack` in `event.getDrops()`, `setAmount((int) (amount * multiplier))` (Looting already applied by vanilla; the bonus multiplies the resulting drops).
    - eligibility helper: `boolean eligible(Player player)` — resolve territory at player location; actor's primary guild == governing guild; `engine.harvestMultiplierFor(territoryId, guildId) > 1.0`.

- [ ] **Step 1: Write the failing tests**

Create `paper/src/test/java/com/guilds/territory/standing/StandingListenerTest.java` (structural, mirrors `InfluenceListenerTest`):

```java
package com.guilds.territory.standing;

import com.guilds.territory.PostgresTestDatabase;
import com.guilds.territory.persist.PostgresDatabase;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingListenerTest {

    @Test
    void standingListener_isListenerAndHoldsEngine() throws Exception {
        PostgresDatabase database = PostgresTestDatabase.open();
        try {
            TerritoryRegistry territories = new TerritoryRegistry();
            GovernanceRegistry governance = new GovernanceRegistry(territories);
            StandingEngine engine = new StandingEngine(governance, StandingConfig.defaults(),
                    new PostgresStandingStore(database), Logger.getLogger("test"));
            StandingListener listener = new StandingListener(governance, engine);

            assertTrue(listener instanceof Listener);
            assertEquals(engine, listener.engine());
        } finally {
            database.close();
        }
    }

    @Test
    void standingListener_declaresHandlersForActivityVectors() {
        Set<Class<?>> handled = Arrays.stream(StandingListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(m -> m.getParameterTypes()[0])
                .collect(Collectors.toCollection(HashSet::new));
        assertTrue(handled.contains(PlayerDeathEvent.class), "missing PlayerDeathEvent handler: " + handled);
        assertTrue(handled.contains(EntityDeathEvent.class), "missing EntityDeathEvent handler: " + handled);
        assertTrue(handled.contains(BlockBreakEvent.class), "missing BlockBreakEvent handler: " + handled);
    }
}
```

Create `paper/src/test/java/com/guilds/territory/standing/HarvestBonusListenerTest.java` (behavior — uses Mockito to mock Bukkit objects; style mirrors `ProtectionListenerWiringTest` where possible, but this one needs real event objects):

```java
package com.guilds.territory.standing;

import com.guilds.territory.PostgresTestDatabase;
import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.persist.PostgresDatabase;
import com.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HarvestBonusListenerTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private PostgresDatabase database;
    private StandingEngine engine;
    private HarvestBonusListener listener;
    private World world;
    private Player owner;

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        database = PostgresTestDatabase.open();
        engine = new StandingEngine(governance, StandingConfig.defaults(),
                new PostgresStandingStore(database), Logger.getLogger("test"));
        listener = new HarvestBonusListener(governance, engine);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        UUID ownerId = UUID.randomUUID();
        source.putGuild(new GuildBody("everfall-town", "Everfall Town",
                Government.monarchy("m:everfall-town"), List.of("m:everfall-town"),
                GuildToggles.defaults(), Map.of()));
        // make the owner a member of the governing guild via primaryGuildForMember
        source.putGuild(new GuildBody("everfall-town", "Everfall Town",
                Government.monarchy("m:everfall-town"), List.of(ownerId.toString()),
                GuildToggles.defaults(), Map.of()));
        territories.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenReturn(new Location(world, 10, 64, 10));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void accrueToTier3() {
        for (int i = 0; i < 30; i++) {
            engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        }
    }

    @Test
    void blockBreak_multipliesBaseDropsForOwnerMember() {
        accrueToTier3(); // harvest multiplier 1.5

        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.DIAMOND_ORE);
        when(block.getDrops(org.mockito.ArgumentMatchers.any())).thenReturn(
                List.of(new ItemStack(Material.DIAMOND, 1)));

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(owner.getInventory()).thenReturn(inv);

        BlockBreakEvent event = new BlockBreakEvent(block, owner);
        listener.onBlockBreak(event);

        // Base drop of 1 diamond, multiplied by 1.5 → ceiling 2 diamonds total
        // (the listener drops the extra 1 at the location)
        assertTrue(event.isDropItems());
        // verify: we can't easily assert the world drops; check that the drop
        // was scheduled — assert via mock verification that world.dropItemNaturally
        // was called with an ItemStack amount 2.
        org.mockito.Mockito.verify(world).dropItemNaturally(
                org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.argThat(s -> s.getAmount() == 2));
    }

    @Test
    void blockBreak_outsiderGetsNoMultiplier() {
        UUID outsiderId = UUID.randomUUID();
        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(outsiderId);
        when(outsider.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(outsider.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(outsider.getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));

        accrueToTier3();

        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.DIAMOND_ORE);
        when(block.getDrops(org.mockito.ArgumentMatchers.any())).thenReturn(
                List.of(new ItemStack(Material.DIAMOND, 1)));

        BlockBreakEvent event = new BlockBreakEvent(block, outsider);
        listener.onBlockBreak(event);

        // Outsider: no extra drop scheduled
        org.mockito.Mockito.verify(world, org.mockito.Mockito.never()).dropItemNaturally(
                org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.any(ItemStack.class));
    }
}
```

Note: the `owner` guild body is registered twice in `setUp` (once, then overwritten with the member). Remove the first registration — keep only the one with `List.of(ownerId.toString())`. Also `World.dropItemNaturally` is a real method — verify the listener is implemented to call it.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :paper:test --tests "com.guilds.territory.standing.*" -q`
Expected: FAIL — classes not found.

- [ ] **Step 3: Create `StandingListener`**

`paper/src/main/java/com/guilds/territory/standing/StandingListener.java`:

```java
package com.guilds.territory.standing;

import com.guilds.territory.model.LookupResult;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

/**
 * Maps player activity events onto {@link StandingEngine#accrue} (spec §4).
 * Only members of the territory's governing guild accrue standing.
 */
public final class StandingListener implements Listener {

    private final GovernanceRegistry governance;
    private final StandingEngine engine;

    public StandingListener(GovernanceRegistry governance, StandingEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    public StandingEngine engine() {
        return engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), killer.getUniqueId().toString(),
                StandingSource.PVP_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // players handled by PlayerDeathEvent
        }
        if (event.getEntity().getKiller() == null) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), event.getEntity().getKiller().getUniqueId().toString(),
                StandingSource.PVE_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                StandingSource.BLOCK_BREAK);
    }

    private void accrueAt(Location location, String holderId, StandingSource source) {
        Optional<String> guildId = primaryGuild(holderId);
        if (guildId.isEmpty()) {
            return;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained() || result.territoryId().isEmpty()) {
            return;
        }
        engine.accrue(result.territoryId().orElseThrow(), guildId.get(), source);
    }

    private Optional<String> primaryGuild(String holderId) {
        return governance.primaryGuildForMember(holderId).map(GuildBody::id);
    }
}
```

- [ ] **Step 4: Create `HarvestBonusListener`**

`paper/src/main/java/com/guilds/territory/standing/HarvestBonusListener.java`:

```java
package com.guilds.territory.standing;

import com.guilds.territory.model.LookupResult;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Applies the standing tier's harvest multiplier to block and mob drops
 * for governing-guild members inside the territory (spec §6). Multiplies
 * base drops only; Fortune/Looting are never re-rolled.
 */
public final class HarvestBonusListener implements Listener {

    private final GovernanceRegistry governance;
    private final StandingEngine engine;

    public HarvestBonusListener(GovernanceRegistry governance, StandingEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        double multiplier = multiplierAt(player, event.getBlock().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        Block block = event.getBlock();
        // Base drops without any tool enchantment (hand is AIR):
        List<ItemStack> base = block.getDrops(new ItemStack(Material.AIR));
        for (ItemStack drop : base) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player player = event.getEntity().getKiller();
        double multiplier = multiplierAt(player, event.getEntity().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                event.getEntity().getWorld().dropItemNaturally(
                        event.getEntity().getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
    }

    /** Harvest multiplier for the player at the location, or 1.0 when ineligible. */
    private double multiplierAt(Player player, Location location) {
        Optional<String> guildId = governance.primaryGuildForMember(player.getUniqueId().toString())
                .map(GuildBody::id);
        if (guildId.isEmpty()) {
            return 1.0;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained() || result.territoryId().isEmpty()) {
            return 1.0;
        }
        return engine.harvestMultiplierFor(result.territoryId().orElseThrow(), guildId.get());
    }
}
```

- [ ] **Step 5: Wire into the plugin**

In `GuildsTerritoryPlugin.java` `onEnable`, after the influence block and before `TerritoryCommand`, add standing registration:

```java
        // Standing + harvest bonus subsystem
        if (standingEngine != null) {
            getServer().getPluginManager().registerEvents(
                    new StandingListener(governance, standingEngine), this);
            getServer().getPluginManager().registerEvents(
                    new HarvestBonusListener(governance, standingEngine), this);
            long flushTicks = Math.max(1, 60L * 20L);
            getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    standingEngine.flush();
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Failed to flush standing state", e);
                }
            }, flushTicks, flushTicks);
            getLogger().info("Territory standing + harvest bonuses enabled");
        }
```

And add fields + construction (already partially in Task 5):

```java
    private StandingEngine standingEngine;
    private PostgresStandingStore standingStore;
```

In `onEnable`, where the standing engine is created (before influence):

```java
        this.standingStore = new PostgresStandingStore(database);
        this.standingEngine = new StandingEngine(
                governance, standingConfig, standingStore, getLogger());
        this.standingEngine.recover(System.currentTimeMillis());
```

In `onDisable`, add:

```java
        if (standingEngine != null) {
            try {
                standingEngine.flush();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush standing state on disable", e);
            }
        }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :paper:test --tests "com.guilds.territory.standing.*" -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/standing \
        paper/src/test/java/com/guilds/territory/standing \
        paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java
git commit -m "feat: wire standing listeners and harvest bonus drops"
```

---

### Task 7: `/territory standing` command (paper)

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java`
- Test: `paper/src/test/java/com/guilds/territory/command/TerritoryCommandStandingTest.java`

**Interfaces:**
- Consumes: `StandingEngine` (via `plugin.getStandingEngine()` — add getter), `TerritoryStandingState`, `StandingBar`.
- Produces:
  - `plugin.getStandingEngine()` — getter added to `GuildsTerritoryPlugin` (public).
  - `/territory standing [territoryId]` — shows owner, bars, tier readout.
  - `/territory standing set <territoryId> <guildId> <value>` — admin (requires `guildsterritory.admin`).
  - `/territory standing reset <territoryId>` — admin.

- [ ] **Step 1: Write the failing test**

Create `paper/src/test/java/com/guilds/territory/command/TerritoryCommandStandingTest.java` (behavioral smoke test with Mockito's `mock(GuildsTerritoryPlugin.class)`; real engine built on a test Postgres DB):

```java
package com.guilds.territory.command;

import com.guilds.territory.GuildsTerritoryPlugin;
import com.guilds.territory.PostgresTestDatabase;
import com.guilds.territory.model.BlockPos;
import com.guilds.territory.model.Boundary;
import com.guilds.territory.model.Government;
import com.guilds.territory.model.Territory;
import com.guilds.territory.model.ZoneType;
import com.guilds.territory.permission.FakeGovernanceSource;
import com.guilds.territory.permission.GovernanceRegistry;
import com.guilds.territory.permission.GuildBody;
import com.guilds.territory.permission.GuildToggles;
import com.guilds.territory.persist.PostgresDatabase;
import com.guilds.territory.registry.TerritoryRegistry;
import com.guilds.territory.standing.StandingConfig;
import com.guilds.territory.standing.StandingEngine;
import com.guilds.territory.standing.PostgresStandingStore;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerritoryCommandStandingTest {

    private PostgresDatabase database;
    private StandingEngine engine;
    private TerritoryRegistry territories;

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        FakeGovernanceSource source = new FakeGovernanceSource();
        UUID ownerId = UUID.randomUUID();
        source.putGuild(new GuildBody("everfall-town", "Everfall Town",
                Government.monarchy("m:everfall-town"), List.of(ownerId.toString()),
                GuildToggles.defaults(), Map.of()));
        GovernanceRegistry governance = new GovernanceRegistry(territories, source);
        territories.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));
        database = PostgresTestDatabase.open();
        engine = new StandingEngine(governance, StandingConfig.defaults(),
                new PostgresStandingStore(database), Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void pluginExposesStandingEngine() throws Exception {
        Method getter = GuildsTerritoryPlugin.class.getMethod("getStandingEngine");
        assertEquals(StandingEngine.class, getter.getReturnType());
    }

    @Test
    void commandHandlesStandingWithoutServer() {
        engine.accrue("everfall", "everfall-town", com.guilds.territory.standing.StandingSource.PVP_KILL);
        GuildsTerritoryPlugin plugin = mock(GuildsTerritoryPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "everfall"}));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests "com.guilds.territory.command.TerritoryCommandStandingTest" -q`
Expected: FAIL — `GuildsTerritoryPlugin.getStandingEngine()` missing (mock cannot stub), and `TerritoryCommand` does not handle `standing` yet.

In `TerritoryCommand.java`, add handling in `onCommand` for `"standing"`:

```java
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "standing" -> standing(sender, rest);
            case "standingset" -> standingAdminSet(sender, rest);
            case "standingreset" -> standingAdminReset(sender, rest);
            ...
        }
```

Implementations (use `plugin.getStandingEngine()`):

```java
    private boolean standing(CommandSender sender, String[] args) {
        StandingEngine engine = plugin.getStandingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        String territoryId = args.length >= 1 && !args[0].isBlank()
                ? args[0] : territoryAt(sender);
        if (territoryId == null) {
            sender.sendMessage(Component.text("You must stand inside a territory or name one.",
                    NamedTextColor.RED));
            return true;
        }
        Optional<TerritoryStandingState> state = engine.standing(territoryId);
        if (state.isEmpty()) {
            sender.sendMessage(Component.text("No standing for territory '" + territoryId + "'.",
                    NamedTextColor.YELLOW));
            return true;
        }
        TerritoryStandingState s = state.get();
        sender.sendMessage(Component.text("Standing for " + territoryId
                + " (owner: " + s.ownerGuildId() + "):", NamedTextColor.GOLD));
        for (StandingBar bar : s.bars()) {
            Optional<StandingTier> tier = engine.tierFor(territoryId, bar.guildId());
            sender.sendMessage(Component.text("  " + bar.guildId() + ": " + bar.value()
                    + (tier.isPresent() ? " (tier " + tier.get().level() + ")" : ""),
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean standingAdminSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guildsterritory.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /territory standing set <territory> <guild> <value>",
                    NamedTextColor.RED));
            return true;
        }
        StandingEngine engine = plugin.getStandingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Value must be a number.", NamedTextColor.RED));
            return true;
        }
        boolean ok = engine.adminSet(args[0], args[1], value);
        sender.sendMessage(ok
                ? Component.text("Set standing for " + args[1] + " on " + args[0] + " to " + value,
                        NamedTextColor.GREEN)
                : Component.text("Could not set standing (unknown territory?)", NamedTextColor.RED));
        return true;
    }

    private boolean standingAdminReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guildsterritory.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /territory standing reset <territory>",
                    NamedTextColor.RED));
            return true;
        }
        StandingEngine engine = plugin.getStandingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        boolean ok = engine.adminReset(args[0]);
        sender.sendMessage(ok
                ? Component.text("Reset standing for " + args[0], NamedTextColor.GREEN)
                : Component.text("No standing state for " + args[0], NamedTextColor.YELLOW));
        return true;
    }
```

Also add the getter to `GuildsTerritoryPlugin`:

```java
    public StandingEngine getStandingEngine() {
        return standingEngine;
    }
```

And update tab completion: replace the hard-coded completion for the `standing` branch with a real list (details in the full file change; at minimum keep the existing literals working).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :paper:test --tests "com.guilds.territory.command.*" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java \
        paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java \
        paper/src/test/java/com/guilds/territory/command/TerritoryCommandStandingTest.java
git commit -m "feat: add /territory standing admin and readout commands"
```

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java`
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java`
- Test: `common/src/test/java/com/guilds/territory/web/StandingWebTest.java`

**Interfaces:**
- Consumes: `StandingService` (via a `Supplier<Optional<StandingService>>` constructor param added to `TerritoryWebServer`).
- Produces:
  - `TerritoryWebServer` constructor gains a `Supplier<Optional<StandingService>> standingSupplier` argument — **exact current signature is**:
    `public TerritoryWebServer(WebConfig config, TerritoryRegistry registry, TerritoryJson json, PostgresTerritoryStore store, Supplier<Optional<InfluenceService>> influenceSupplier, Logger log)`.
    **Keep the existing 6-arg constructor** (delegating to the new one with `Optional::empty` for standing) so the existing call sites — `InfluenceWebTest.java`, `TerritoryWebServerTest.java`, `TerritoryApiPersistenceTest.java`, `TerritoryApiHandler` internal wiring — still compile unchanged. Add a new 7-arg constructor:
    `public TerritoryWebServer(WebConfig config, TerritoryRegistry registry, TerritoryJson json, PostgresTerritoryStore store, Supplier<Optional<InfluenceService>> influenceSupplier, Supplier<Optional<StandingService>> standingSupplier, Logger log)`.
    The 6-arg constructor delegates: `this(config, registry, json, store, influenceSupplier, Optional::empty, log)`.
  - `TerritoryApiHandler` constructor **also keeps its existing 7-arg signature** and gains an **overloaded 8-arg** version adding `standingSupplier` (so only `TerritoryWebServer` passes it; existing direct constructions stay green):
    existing: `TerritoryApiHandler(WebConfig config, ReverseProxySupport proxy, TerritoryRegistry registry, TerritoryJson json, PostgresTerritoryStore store, Supplier<Optional<InfluenceService>> influenceSupplier, Logger log)` — delegates to the 8-arg with `Optional::empty`.
    new: `TerritoryApiHandler(WebConfig config, ReverseProxySupport proxy, TerritoryRegistry registry, TerritoryJson json, PostgresTerritoryStore store, Supplier<Optional<InfluenceService>> influenceSupplier, Supplier<Optional<StandingService>> standingSupplier, Logger log)`.
  - `TerritoryApiHandler` gains `standingJson()` (all states) and embeds a `standing` object in `getOne`'s territory JSON when the service reports state (mirroring `influence`). Route: `if ("/standing".equals(path) && "GET".equals(method)) { standingList(exchange); return; }` after the `/influence` route.

- [ ] **Step 1: Write the failing test**

Design the handler so the standing path is testable without a live server: use the **8-arg** `TerritoryApiHandler` constructor with `null` for everything except the suppliers and log; implement `standingJson()` to use only the standing supplier + Gson. `toStandingJson(TerritoryStandingState)` is a `static` method (mirror `toInfluenceJson`).

`common/src/test/java/com/guilds/territory/web/StandingWebTest.java`:

```java
package com.guilds.territory.web;

import com.guilds.territory.standing.StandingBar;
import com.guilds.territory.standing.StandingService;
import com.guilds.territory.standing.StandingTier;
import com.guilds.territory.standing.TerritoryStandingState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingWebTest {

    private static Supplier<Optional<StandingService>> supplierOf(StandingService service) {
        return () -> Optional.of(service);
    }

    private static TerritoryApiHandler handlerWith(StandingService standing) {
        // 8-arg constructor: config, proxy, registry, json, store, influence, standing, log
        return new TerritoryApiHandler(
                null, null, null, null, null,
                Optional::empty, supplierOf(standing), Logger.getLogger("test"));
    }

    @Test
    void standingJson_serializesAllStates() {
        StandingService standing = fakeStanding(new TerritoryStandingState(
                "everfall", "everfall-town",
                List.of(new StandingBar("everfall-town", 200.0))));
        String json = handlerWith(standing).standingJson();
        assertTrue(json.contains("\"everfall\""));
        assertTrue(json.contains("\"everfall-town\""));
        assertTrue(json.contains("200.0"));
    }

    @Test
    void emptyStanding_returnsEmptyArray() {
        StandingService standing = fakeStanding(null);
        String json = handlerWith(standing).standingJson();
        assertTrue(json.contains("\"standing\":[]"));
    }

    private static StandingService fakeStanding(TerritoryStandingState state) {
        return new StandingService() {
            @Override public Optional<TerritoryStandingState> standing(String territoryId) {
                return state == null ? Optional.empty() : Optional.of(state);
            }
            @Override public List<TerritoryStandingState> all() {
                return state == null ? List.of() : List.of(state);
            }
            @Override public double harvestMultiplierFor(String t, String g) { return 1.0; }
            @Override public double influenceMultiplierFor(String g) { return 1.0; }
            @Override public Optional<StandingTier> tierFor(String t, String g) { return Optional.empty(); }
            @Override public boolean adminSet(String t, String g, double v) { return false; }
            @Override public boolean adminReset(String t) { return false; }
        };
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests "com.guilds.territory.web.StandingWebTest" -q`
Expected: FAIL — no 8-arg constructor / no `standingJson` method.

- [ ] **Step 3: Modify `TerritoryApiHandler`**

Add a standing supplier field + **overloaded 8-arg constructor** (the existing 7-arg delegates with `Optional::empty`), route, and methods (mirror `influence` exactly — the influence supplier is handled as `influenceSupplier == null ? Optional::empty : influenceSupplier`):

```java
import com.guilds.territory.standing.StandingService;
import com.guilds.territory.standing.TerritoryStandingState;
import com.guilds.territory.standing.StandingBar;
…
    private final Supplier<Optional<StandingService>> standingSupplier;
    …

    // Keep the existing 7-arg constructor (delegates, standing absent):
    public TerritoryApiHandler(
            WebConfig config,
            ReverseProxySupport proxy,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Logger log
    ) {
        this(config, proxy, registry, json, store, influenceSupplier, Optional::empty, log);
    }

    public TerritoryApiHandler(
            WebConfig config,
            ReverseProxySupport proxy,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Supplier<Optional<StandingService>> standingSupplier,
            Logger log
    ) {
        this.config = config;
        this.proxy = proxy;
        this.registry = registry;
        this.json = json;
        this.store = store;
        this.influenceSupplier = influenceSupplier == null ? Optional::empty : influenceSupplier;
        this.standingSupplier = standingSupplier == null ? Optional::empty : standingSupplier;
        this.log = log;
    }

    // in handle(): after the influence route
    if ("/standing".equals(path) && "GET".equals(method)) {
        standingList(exchange);
        return;
    }

    private void standingList(HttpExchange exchange) throws IOException {
        Optional<StandingService> service = standingSupplier.get();
        if (service.isEmpty()) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        JsonArray out = new JsonArray();
        for (TerritoryStandingState s : service.get().all()) {
            out.add(toStandingJson(s));
        }
        JsonObject root = new JsonObject();
        root.add("standing", out);
        HttpResponses.json(exchange, 200, json.gson().toJson(root), config);
    }

    // in getOne(): mirror the influence add
    standingSupplier.get().flatMap(s -> s.standing(id))
            .ifPresent(state -> body.add("standing", toStandingJson(state)));

    private static JsonObject toStandingJson(TerritoryStandingState state) {
        JsonObject out = new JsonObject();
        out.addProperty("territoryId", state.territoryId());
        out.addProperty("ownerGuildId", state.ownerGuildId());
        JsonArray bars = new JsonArray();
        for (StandingBar bar : state.bars()) {
            JsonObject b = new JsonObject();
            b.addProperty("guildId", bar.guildId());
            b.addProperty("value", bar.value());
            bars.add(b);
        }
        out.add("bars", bars);
        return out;
    }
```

`standingJson()` (used by the test) is the serialization of `all()` alone (the route handler is the HTTP wrapper):

```java
    /** JSON for all standing states (spec §9). */
    public String standingJson() {
        Optional<StandingService> service = standingSupplier.get();
        if (service.isEmpty()) {
            return "{\"standing\":[]}";
        }
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (TerritoryStandingState s : service.get().all()) {
            arr.add(toStandingJson(s));
        }
        root.add("standing", arr);
        return root.toString();
    }
```

- [ ] **Step 4: Modify `TerritoryWebServer`**

Keep the existing 6-arg constructor (delegates to the new 7-arg), add the 7-arg:

```java
    public TerritoryWebServer(
            WebConfig config,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Logger log
    ) {
        this(config, registry, json, store, influenceSupplier, Optional::empty, log);
    }

    public TerritoryWebServer(
            WebConfig config,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Supplier<Optional<StandingService>> standingSupplier,
            Logger log
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.json = json == null ? new TerritoryJson() : json;
        this.store = Objects.requireNonNull(store, "store");
        this.influenceSupplier = influenceSupplier == null ? Optional::empty : influenceSupplier;
        this.standingSupplier = standingSupplier == null ? Optional::empty : standingSupplier;
        this.log = log == null ? Logger.getLogger("GuildsTerritoryWeb") : log;
    }
```

Add a field `private final Supplier<Optional<StandingService>> standingSupplier;` and pass it in `start()`:

```java
        TerritoryApiHandler api = new TerritoryApiHandler(
                config, proxy, registry, json, store, influenceSupplier, standingSupplier, log
        );
```

- [ ] **Step 5: Update the plugin call-site**

In `GuildsTerritoryPlugin.startWebIfEnabled()`:

```java
            this.webServer = new TerritoryWebServer(
                    webConfig,
                    registry,
                    new TerritoryJson(),
                    store,
                    () -> Optional.ofNullable(influenceEngine),
                    () -> Optional.ofNullable(standingEngine),
                    getLogger()
            );
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :common:test -q`
Expected: PASS (existing web tests `InfluenceWebTest`, `TerritoryWebServerTest`, `TerritoryApiPersistenceTest` still green + `StandingWebTest` green)

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java \
        common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java \
        common/src/test/java/com/guilds/territory/web/StandingWebTest.java \
        paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java
git commit -m "feat: expose standing in web API"
```

---

### Task 8: Web exposure (REST)

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java`
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java`
- Test: `common/src/test/java/com/guilds/territory/web/StandingWebTest.java`

**Interfaces:**
- Consumes: `StandingService` (via a `Supplier<Optional<StandingService>>` constructor param).
- Produces: the overloaded constructors + `standingJson()` + route + `getOne` standing object + plugin call-site (as spelled out in the steps above).

- [ ] **Step 1: Confirm the failing test exists (already written in the current plan state)** — the test file from the earlier Task 8 draft is in place; if the previous edits replaced it, re-apply the `StandingWebTest.java` content from the plan's Task 8 Step 1. Then run:

Run: `./gradlew :common:test --tests "com.guilds.territory.web.StandingWebTest" -q`
Expected: FAIL — no 8-arg constructor / no `standingJson`.

- [ ] **Step 2: Apply the handler + server modifications** (Steps 3–5 of the plan's Task 8: `TerritoryApiHandler` overloaded constructor + methods, `TerritoryWebServer` overloaded constructor + field + `start()` wiring, plugin call-site update).

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :common:test -q`
Expected: PASS (existing web tests + `StandingWebTest` green)

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java \
        common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java \
        common/src/test/java/com/guilds/territory/web/StandingWebTest.java \
        paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java
git commit -m "feat: expose standing in web API"
```

---

### Task 9: Final integration + full verification

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java` (ensure ordering: standing engine before influence; both before web; listeners + flush registered; disable flush)
- Modify: `README.md` (document the standing system)
- Test: none new — run the full suite.

**Interfaces:** all produced by Task 1–8. This task is the wiring correctness pass.

- [ ] **Step 1: Verify plugin enable ordering**

In `GuildsTerritoryPlugin.onEnable()`, ensure the exact order:

1. Registry + DB load (`store.loadInto`).
2. `constructGuildsSubsystem()` + `wireTerritoryRegistry`.
3. `GovernanceSource` + `GovernanceRegistry`.
4. Economy wiring.
5. `BlockProtection` + protection listeners.
6. squaremap bridge.
7. **Standing engine + store + recover** (before influence).
8. **Influence engine (with standing hook)** + influence listeners/flush.
9. **Standing listeners + flush timer** (after influence so the influence accrual sees the multiplier).
10. `TerritoryCommand`.
11. `startWebIfEnabled()` (passes standing supplier).
12. `enableGuildsSubsystem()`.

Confirm each step's code is present. If any piece is missing, add it now.

- [ ] **Step 2: Check compile + full test suite**

Run: `./gradlew test -q`
Expected: PASS — all modules, all tests (api, common, paper; Postgres tests skip when `GUILDS_TEST_JDBC_URL` unset).

- [ ] **Step 3: Smoke test with the runServer**

Run: `./gradlew :paper:runServer` (needs Paper 26.2 download; EULA accepted in `paper/run/eula.txt`; Postgres reachable per `config.yml`).
Expected: server boots, logs "Territory standing + harvest bonuses enabled", `/territory standing` works from console/player.

(If no Postgres is available in the local environment, skip this step and note it — the acceptance is the full test suite green.)

- [ ] **Step 4: Update README**

Add a short "Territory standing & harvest bonuses" section to `README.md` after the influence section:

```markdown
## Territory standing & harvest bonuses

Governing-guild members accrue **standing** from activity inside their own
territory (PvP kills, PvE kills, block breaks; values in `bonuses.json`).
Standing raises development **tiers**, which grant:

- **Harvest bonuses** — extra drops from blocks (ores/crops) and mobs killed
  inside the territory (base drops only; Fortune/Looting unaffected).
- **Influence bonuses** — the governing guild's influence accrual in other
  territories is multiplied by its highest tier across the territories it
  governs.

Config: `bonuses.json` (data folder). State persists to PostgreSQL
(`standing_state`). Read-only REST: `/api/standing`, and a `standing` object
on `/api/territories/{id}`. Admin: `/territory standing set|reset`.
```

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java README.md
git commit -m "docs: document territory standing and harvest bonuses"
```

---

## Self-Review Notes (post-writing)

- **FakeStanding in Task 5 step 4** fix is noted inline (anonymous class instead of lambda — `StandingService` has 7 methods, not functional).
- **`StandingPostgresStore` typo** in Task 7 test draft is corrected in the note (class is `PostgresStandingStore`).
- **Task 7 test** switched from resource-read structural to a behavioral Mockito smoke test.
- **Task 8** mirrors the influence web JSON shape (read `TerritoryApiHandler`'s existing influence block to copy the Gson idioms exactly).
- **Spec coverage** check: accrual sources (Task 4), tier table + validation (Task 2), harvest bonus listened (Task 6), influence hook (Task 5), state persistence (Task 3), commands (Task 7), web (Task 8), README (Task 9), acceptance tests embedded in each task. Postgres tests skip when `GUILDS_TEST_JDBC_URL` unset (repo convention).
