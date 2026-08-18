# Guild Mob Invasions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator-triggered, three-wave mob invasions that damage ordinary structural blocks in a target guild's claims, expose raid-style bossbar progress, and persist guild damage and terminal outcomes.

**Architecture:** A pure `common` invasion engine owns lifecycle, wave accounting, damage, and persistence. Paper adapters resolve guild targets, spawn/tag mobs, enforce guild-scoped destruction, present bossbars, and expose `/territory invasion` commands. PostgreSQL stores a versioned invasion document; active records recover as cancelled rather than respawning after restart.

**Tech Stack:** Java 26, Paper API 26.2, Adventure bossbars/components, PostgreSQL JSONB, Gson, JUnit 5, Mockito, Gradle.

## Global Constraints

- First version is administrator-triggered only; automatic scheduling is excluded.
- Permission node is exactly `territory.admin.invasion`, default `op`.
- Require at least one target-guild claimed plot and one online guild resident.
- Use guild spawn only when it lies inside the guild's claims; otherwise use the safe center of the claimed home chunk.
- Exactly three configuration-defined waves.
- Only persistently tagged invasion mobs may bypass destruction protection, and only inside the matching target guild's claims.
- Only explicit ordinary-structure allowlist materials are destructible; protected categories remain intact.
- Fixed configurable destroyed-block budget maps to guild-wide 0–100% damage.
- Clearing wave three yields `DEFENDED`; 100% damage yields `DEVASTATED`; admin stop/restart yields `CANCELLED`.
- No repair/reset behavior and no reconstruction in this version.
- Existing unrelated dirty-worktree changes must remain untouched and uncommitted by invasion commits.

---

## File Structure

**Create in `common/src/main/java/com/guilds/territory/invasion/`:**

- `InvasionStatus.java` — lifecycle enum.
- `InvasionConfig.java` — validated domain configuration and three wave definitions.
- `InvasionRecord.java` — immutable persisted/runtime invasion snapshot.
- `GuildDamage.java` — accumulated block count and percentage calculation.
- `InvasionState.java` — versioned aggregate persisted document.
- `InvasionStore.java` — persistence boundary for deterministic engine tests.
- `PostgresInvasionStore.java` — JSONB serialization and PostgreSQL persistence.
- `InvasionStartStatus.java`, `InvasionStartResult.java` — explicit start outcomes.
- `InvasionEngine.java` — state transitions, concurrency, wave clearing, damage, cancellation, and recovery.

**Create in `paper/src/main/java/com/guilds/territory/invasion/`:**

- `InvasionConfigLoader.java` — Bukkit configuration parser and exact validation errors.
- `GuildInvasionTargetResolver.java` — guild lookup, claimed-plot eligibility, online-resident eligibility, and center resolution.
- `InvasionMobTags.java` — persistent-data keys and tag read/write helpers.
- `InvasionMobSpawner.java` — bounded safe spawn selection and wave entity creation.
- `InvasionBossBars.java` — audience reconciliation, title, color, and progress.
- `InvasionRuntime.java` — orchestration between engine transitions and Paper effects.
- `InvasionListener.java` — entity death/removal, destruction, player visibility, and stale-tag cleanup events.

**Modify:**

- `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java` — create `invasion_state` JSONB table.
- `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java` — construct, recover, register, expose, tick, and stop invasion runtime.
- `paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java` — `invasion start|stop|status` command and completions.
- `paper/src/main/java/com/guilds/territory/listener/ProtectionListener.java` — preserve general protection; no broad spawn-reason relaxation.
- `paper/src/main/resources/config.yml` — defaults for budget, allowlist, three waves, spawning, bossbar, and inter-wave delay.
- `paper/src/main/resources/plugin.yml` — permission declaration and usage metadata.

**Tests:**

- `common/src/test/java/com/guilds/territory/invasion/InvasionEngineTest.java`
- `common/src/test/java/com/guilds/territory/invasion/PostgresInvasionStoreTest.java`
- `paper/src/test/java/com/guilds/territory/invasion/InvasionConfigLoaderTest.java`
- `paper/src/test/java/com/guilds/territory/invasion/GuildInvasionTargetResolverTest.java`
- `paper/src/test/java/com/guilds/territory/invasion/InvasionMobTagsTest.java`
- `paper/src/test/java/com/guilds/territory/invasion/InvasionListenerTest.java`
- `paper/src/test/java/com/guilds/territory/invasion/InvasionBossBarsTest.java`
- `paper/src/test/java/com/guilds/territory/command/TerritoryCommandInvasionTest.java`

---

### Task 1: Invasion Domain Configuration And Lifecycle

**Files:**
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionStatus.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionConfig.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionRecord.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/GuildDamage.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionState.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionStore.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionStartStatus.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionStartResult.java`
- Create: `common/src/main/java/com/guilds/territory/invasion/InvasionEngine.java`
- Test: `common/src/test/java/com/guilds/territory/invasion/InvasionEngineTest.java`

**Interfaces:**
- Consumes: no Paper types; caller supplies validated guild/world/center data and timestamps.
- Produces: `InvasionEngine.start(String guildId, String guildName, String worldId, double x, double y, double z, long now)`, `mobSpawned(UUID invasionId, UUID entityId)`, `mobRemoved(UUID invasionId, UUID entityId, long now)`, `recordDestroyedBlock(UUID invasionId, long now)`, `cancel(String guildId, long now)`, `status(String guildId)`, `recover(long now)`, and `activeInvasions()`.

- [ ] **Step 1: Write failing lifecycle tests**

Create tests proving:

```java
assertEquals(InvasionStartStatus.STARTED,
        engine.start("guild-a", "Guild A", "world", 8.5, 70, 8.5, now).status());
assertEquals(InvasionStartStatus.ALREADY_ACTIVE,
        engine.start("guild-a", "Guild A", "world", 8.5, 70, 8.5, now).status());
assertEquals(InvasionStartStatus.STARTED,
        engine.start("guild-b", "Guild B", "world", 40.5, 70, 40.5, now).status());
```

Also prove current-wave entity tracking, progression from waves 1→2→3, `DEFENDED` after the final entity is removed, damage saturation to `DEVASTATED`, explicit `CANCELLED`, cumulative guild damage across later invasion records, and recovery of persisted `ACTIVE` records to `CANCELLED`.

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
./gradlew :common:test --tests '*InvasionEngineTest'
```

Expected: compilation failure because invasion domain types do not exist.

- [ ] **Step 3: Implement immutable domain types and engine**

Use:

```java
public enum InvasionStatus { ACTIVE, DEFENDED, DEVASTATED, CANCELLED }

public record Wave(List<MobEntry> mobs) {}
public record MobEntry(String entityType, int count) {}
public record GuildDamage(long destroyedBlocks, int percent) {}
```

`InvasionConfig` rejects a non-positive block budget, a wave count other than three, blank entity types, and non-positive counts. `InvasionEngine` indexes active records by guild ID and invasion ID, persists every transition synchronously through `InvasionStore`, and rolls back the in-memory mutation if persistence fails. `mobRemoved` ignores unknown or duplicate entity IDs. It returns transition objects identifying `WAVE_CLEARED`, `NEXT_WAVE`, `DEFENDED`, or no change so Paper orchestration never guesses state.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run:

```bash
./gradlew :common:test --tests '*InvasionEngineTest'
```

Expected: all invasion engine tests pass.

- [ ] **Step 5: Commit the domain unit**

```bash
git add common/src/main/java/com/guilds/territory/invasion common/src/test/java/com/guilds/territory/invasion/InvasionEngineTest.java
git commit -m "feat: add guild invasion lifecycle engine"
```

---

### Task 2: PostgreSQL Invasion Persistence

**Files:**
- Create: `common/src/main/java/com/guilds/territory/invasion/PostgresInvasionStore.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java:17-24`
- Test: `common/src/test/java/com/guilds/territory/invasion/PostgresInvasionStoreTest.java`

**Interfaces:**
- Consumes: `InvasionStore`, `InvasionState`, and `PostgresDatabase`.
- Produces: `PostgresInvasionStore.load()` and `save(InvasionState)` against singleton row `invasion_state.id = 1`.

- [ ] **Step 1: Write failing PostgreSQL round-trip tests**

Persist a state containing two guild damage entries and active/terminal invasion records. Reopen through `PostgresInvasionStore` and assert exact identifiers, status, wave, entities, damage, coordinates, and timestamps. Add malformed-version coverage expecting `IOException("unsupported invasion state version")`.

- [ ] **Step 2: Run the focused store test and confirm RED**

```bash
./gradlew :common:test --tests '*PostgresInvasionStoreTest'
```

Expected: compilation failure because `PostgresInvasionStore` and schema are absent.

- [ ] **Step 3: Add schema and versioned JSONB store**

Append exactly:

```java
"CREATE TABLE IF NOT EXISTS invasion_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)"
```

Serialize an object with `version`, `guildDamage`, and `invasions`. Validate root shape, version, status names, UUIDs, non-negative counters, and finite coordinates while loading. Wrap SQL and malformed-document failures in `IOException` with invasion-specific messages.

- [ ] **Step 4: Run persistence and database tests**

```bash
./gradlew :common:test --tests '*PostgresInvasionStoreTest' --tests '*PostgresDatabase*'
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit persistence**

```bash
git add common/src/main/java/com/guilds/territory/invasion/PostgresInvasionStore.java common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java common/src/test/java/com/guilds/territory/invasion/PostgresInvasionStoreTest.java
git commit -m "feat: persist guild invasion state"
```

---

### Task 3: Paper Configuration And Guild Target Resolution

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionConfigLoader.java`
- Create: `paper/src/main/java/com/guilds/territory/invasion/GuildInvasionTargetResolver.java`
- Modify: `paper/src/main/resources/config.yml:90`
- Test: `paper/src/test/java/com/guilds/territory/invasion/InvasionConfigLoaderTest.java`
- Test: `paper/src/test/java/com/guilds/territory/invasion/GuildInvasionTargetResolverTest.java`

**Interfaces:**
- Consumes: Bukkit `ConfigurationSection`, `GuildService`, `PlotService`, Bukkit worlds/players.
- Produces: validated `InvasionConfig` plus `ResolvedInvasionTarget(guildId, guildName, Location center)` or an explicit rejection status/message.

- [ ] **Step 1: Write failing configuration tests**

Cover defaults and failures for disabled configuration, non-positive budget, missing/extra waves, invalid `EntityType`, non-positive counts, invalid material names, empty allowlist, negative radii, zero spawn attempts, and negative delay.

- [ ] **Step 2: Write failing target-resolution tests**

Prove case-insensitive exact guild-name resolution, unknown guild, no claimed plots, no online resident, valid in-claim spawn, out-of-claim spawn falling back to home chunk, missing home fallback, and unavailable world.

- [ ] **Step 3: Run both focused tests and confirm RED**

```bash
./gradlew :paper:test --tests '*InvasionConfigLoaderTest' --tests '*GuildInvasionTargetResolverTest'
```

Expected: compilation failure because both adapters are absent.

- [ ] **Step 4: Implement parser, defaults, and resolver**

Add `invasions:` defaults with `enabled: true`, `damage.block-budget: 500`, explicit structural materials, `spawn-radius: 24`, `spawn-attempts: 24`, `bossbar.nearby-radius: 96`, `wave-delay-ticks: 100`, and three wave lists. Resolver checks `plotService.getGuildBlocksInGuild`, guild residents against `Bukkit.getPlayer(uuid).isOnline()`, and ownership via `plotService.getGuildBlock(chunkX, chunkZ, world)`.

- [ ] **Step 5: Run focused tests and confirm GREEN**

```bash
./gradlew :paper:test --tests '*InvasionConfigLoaderTest' --tests '*GuildInvasionTargetResolverTest'
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit configuration and resolution**

```bash
git add paper/src/main/java/com/guilds/territory/invasion/InvasionConfigLoader.java paper/src/main/java/com/guilds/territory/invasion/GuildInvasionTargetResolver.java paper/src/main/resources/config.yml paper/src/test/java/com/guilds/territory/invasion/InvasionConfigLoaderTest.java paper/src/test/java/com/guilds/territory/invasion/GuildInvasionTargetResolverTest.java
git commit -m "feat: configure and resolve guild invasion targets"
```

---

### Task 4: Tagged Mob Spawning And Bossbar Runtime

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionMobTags.java`
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionMobSpawner.java`
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionBossBars.java`
- Test: `paper/src/test/java/com/guilds/territory/invasion/InvasionMobTagsTest.java`
- Test: `paper/src/test/java/com/guilds/territory/invasion/InvasionBossBarsTest.java`

**Interfaces:**
- Consumes: `InvasionRecord`, current wave definition, target center, online players, and plugin namespaced keys.
- Produces: persistently tagged mobs and one audience-reconciled Adventure `BossBar` per active invasion.

- [ ] **Step 1: Write failing mob-tag tests**

Assert tags round-trip invasion UUID and target guild ID, malformed/missing tags resolve empty, and a guild tag without an invasion tag never authorizes a mob.

- [ ] **Step 2: Write failing bossbar formatter/audience tests**

Assert title `Guild A Invasion — Wave 2/3 — Damage 42%`, progress `living/spawned`, red below 75% damage, purple at/above 75%, resident visibility regardless of location, nearby non-resident visibility in the same world, and removal outside the radius or after terminal state.

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest'
```

Expected: compilation failure because tag and bossbar adapters are absent.

- [ ] **Step 4: Implement bounded spawning and persistent tags**

Use `PersistentDataContainer` string keys `invasion_id` and `invasion_guild_id`. Spawn candidates are sampled within configured radius; require a solid floor, two passable vertical blocks, a loaded world, and a location inside a target-guild claim. Stop after configured attempts per entity. Never use spawn reason alone as authorization.

- [ ] **Step 5: Implement bossbar lifecycle**

Use Adventure `BossBar.bossBar(...)`. Reconcile viewers once per second and immediately after start, wave transition, damage, terminal transition, join, quit, and world change. Clamp progress to `[0,1]`; use `1.0` for an empty not-yet-spawned wave to avoid NaN.

- [ ] **Step 6: Run focused tests and confirm GREEN**

```bash
./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest'
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit spawning and UI**

```bash
git add paper/src/main/java/com/guilds/territory/invasion/InvasionMobTags.java paper/src/main/java/com/guilds/territory/invasion/InvasionMobSpawner.java paper/src/main/java/com/guilds/territory/invasion/InvasionBossBars.java paper/src/test/java/com/guilds/territory/invasion/InvasionMobTagsTest.java paper/src/test/java/com/guilds/territory/invasion/InvasionBossBarsTest.java
git commit -m "feat: spawn tagged invasion waves with bossbars"
```

---

### Task 5: Guild-Scoped Destruction And Runtime Orchestration

**Files:**
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionRuntime.java`
- Create: `paper/src/main/java/com/guilds/territory/invasion/InvasionListener.java`
- Test: `paper/src/test/java/com/guilds/territory/invasion/InvasionListenerTest.java`

**Interfaces:**
- Consumes: engine, resolver, spawner, bossbars, `PlotService`, explicit material allowlist, Paper entity/block/player events.
- Produces: start/stop/status orchestration, wave scheduling, exact claim/material authorization, destruction accounting, cleanup, and startup stale-tag cleanup.

- [ ] **Step 1: Write failing listener authorization tests**

Test the complete authorization matrix:

```text
tagged + active + target claim + allowlisted material => destroy and count
untagged => preserve
stale invasion tag => preserve and remove entity
wrong guild claim => preserve
wilderness => preserve
protected/non-allowlisted material => preserve
terminal invasion => preserve
```

Also test duplicate entity-death events, last-mob wave transition, failed required-wave spawning → `CANCELLED`, and destruction reaching 100% → `DEVASTATED` plus entity/UI cleanup.

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
./gradlew :paper:test --tests '*InvasionListenerTest'
```

Expected: compilation failure because runtime/listener types are absent.

- [ ] **Step 3: Implement orchestration and narrow destruction path**

`InvasionRuntime.start(guildName, now)` resolves eligibility, starts the engine, spawns wave one, registers spawned UUIDs, opens the bar, and cancels if the required wave cannot spawn. `onEntityDeath`/removal forwards exact UUIDs and schedules the next wave after configured delay. Destruction handlers never alter existing `ProtectionListener` allow/deny policy; they implement the invasion-only path after re-resolving plot ownership and allowlist membership.

For explosion events from tagged mobs, filter each block independently. For `EntityChangeBlockEvent`, authorize only the event block. Set `dropItems(false)` or equivalent for invasion destruction so protected containers cannot be indirectly looted and the invasion cannot duplicate structural drops.

- [ ] **Step 4: Implement fail-closed cleanup**

Any persistence exception cancels Paper schedules, removes the invasion's tagged entities, hides its bar, logs at `SEVERE`, and refuses further destructive events for that record. Disable iterates active invasions through the same cancellation operation before database close.

- [ ] **Step 5: Run focused tests and confirm GREEN**

```bash
./gradlew :paper:test --tests '*InvasionListenerTest'
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit runtime and destruction**

```bash
git add paper/src/main/java/com/guilds/territory/invasion/InvasionRuntime.java paper/src/main/java/com/guilds/territory/invasion/InvasionListener.java paper/src/test/java/com/guilds/territory/invasion/InvasionListenerTest.java
git commit -m "feat: enforce guild-scoped invasion destruction"
```

---

### Task 6: Admin Command And Plugin Lifecycle Wiring

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java:91-323,324-381,576-593`
- Modify: `paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java:1-76,515-525`
- Modify: `paper/src/main/resources/plugin.yml:10-21`
- Test: `paper/src/test/java/com/guilds/territory/command/TerritoryCommandInvasionTest.java`
- Test: `paper/src/test/java/com/guilds/territory/PluginMetadataTest.java`

**Interfaces:**
- Consumes: `InvasionRuntime.start`, `stop`, `status`, guild names for completion, and plugin enable/disable lifecycle.
- Produces: the approved `/territory invasion start|stop|status <guild>` operator surface and `GuildsTerritoryPlugin.getInvasionRuntime()`.

- [ ] **Step 1: Write failing command tests**

Cover missing `territory.admin.invasion`, console/op authorization, usage errors, unknown guild, no claims, no online resident, already active, successful start, successful/invalid stop, active and historical status formatting, and case-insensitive subcommands.

- [ ] **Step 2: Extend metadata tests**

Assert `plugin.yml` declares `territory.admin.invasion` with `default: op` and `/territory` usage includes `invasion`.

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
./gradlew :paper:test --tests '*TerritoryCommandInvasionTest' --tests '*PluginMetadataTest'
```

Expected: command assertions fail because invasion wiring and permission metadata are absent.

- [ ] **Step 4: Wire startup and shutdown**

After guild subsystem construction and service availability, load config, create `PostgresInvasionStore`, create/recover engine, construct runtime, register listeners, and schedule one-second bossbar reconciliation. On invalid config or initialization failure, log the exact error and leave only invasion functionality disabled. On disable, cancel runtime before disabling guild services and closing PostgreSQL.

- [ ] **Step 5: Add command routing and completions**

Add `case "invasion" -> invasion(sender, args);`, permission check, exact action/argument validation, Adventure messages, and completions for `start|stop|status` and guild names. Do not grant access through the broader `guilds.territory.admin` check unless the sender also has the exact invasion node or is op/console.

- [ ] **Step 6: Run focused tests and confirm GREEN**

```bash
./gradlew :paper:test --tests '*TerritoryCommandInvasionTest' --tests '*PluginMetadataTest'
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit command and lifecycle wiring**

```bash
git add paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java paper/src/main/java/com/guilds/territory/command/TerritoryCommand.java paper/src/main/resources/plugin.yml paper/src/test/java/com/guilds/territory/command/TerritoryCommandInvasionTest.java paper/src/test/java/com/guilds/territory/PluginMetadataTest.java
git commit -m "feat: expose admin guild invasion commands"
```

---

### Task 7: End-To-End Verification And Documentation Alignment

**Files:**
- Modify only if behavior requires correction: invasion files from Tasks 1–6.
- Modify: `README.md:3` — remove the now-false blanket claim that siege systems are out of scope and describe admin-triggered guild mob invasions precisely.
- Modify: `docs/living-specs/territory.md` — replace only the obsolete war/siege exclusion with a link to the invasion design and current admin-triggered scope.

**Interfaces:**
- Consumes: assembled plugin and a PostgreSQL-backed Paper test server.
- Produces: verified runtime behavior and accurate project documentation.

- [ ] **Step 1: Run all invasion-focused tests together**

```bash
./gradlew :common:test --tests '*invasion*' :paper:test --tests '*invasion*' --tests '*Invasion*'
```

Expected: all invasion tests pass.

- [ ] **Step 2: Run module regression tests and build**

```bash
./gradlew :common:test :paper:test :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL` with no failing tests.

- [ ] **Step 3: Launch the actual Paper server**

Run `./gradlew :paper:runServer` through the harness process manager. Wait for the server-ready log. Use a PostgreSQL test database and a guild fixture with claimed plots and an online resident.

- [ ] **Step 4: Exercise the admin-triggered defense path**

Run `/territory invasion start <guild>`, observe the bossbar, clear all mobs in all three waves, then run `/territory invasion status <guild>`. Expected: `DEFENDED`, no tagged entities, no bossbar, damage below 100%, and protected/outside blocks unchanged.

- [ ] **Step 5: Exercise devastation and cancellation paths**

Start another invasion, allow tagged mobs to destroy allowlisted blocks until 100%, and confirm immediate `DEVASTATED` cleanup. Start a third invasion, execute `stop`, and confirm `CANCELLED` with retained damage. Restart during an active invasion and confirm recovery records `CANCELLED`, retains damage, and does not respawn mobs.

- [ ] **Step 6: Align documentation with verified behavior**

Update only the obsolete scope lines. State that current invasions are admin-triggered, guild-plot scoped, destructive, and not scheduled automatically.

- [ ] **Step 7: Commit documentation separately**

```bash
git add README.md docs/living-specs/territory.md
git commit -m "docs: document guild mob invasions"
```

- [ ] **Step 8: Inspect final worktree partition**

Run:

```bash
git status --short
git log -8 --oneline
```

Expected: invasion work is committed atomically; only pre-existing unrelated changes remain dirty.
