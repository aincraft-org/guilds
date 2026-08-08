# New World Completeness Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with test-first checkpoints. Keep each logical unit in its own green commit.

**Goal:** Make the existing territory loop correct and complete enough for production use by fixing standing/harvest correctness, adding durable upkeep, exposing influence status to players/maps, finalizing guild upgrades, and cutting the release metadata to `1.1.0`.

**Architecture:** Preserve the current guild-governed territory model in this implementation; the alliance-owned territory overlay is not required for this pass. Keep domain calculations in `common`, Paper event/UI wiring in `paper`, and Guilds upgrade persistence in the existing shared PostgreSQL schema. Reuse `EconomyBridge.chargeExpense` and its idempotency ledger for upkeep rather than adding a second money-transfer path.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Paper 26.2, Adventure Components, squaremap 1.3.15 API, PostgreSQL JSONB, Gson, JUnit 5, Mockito, MockBukkit.

## Global Constraints

- PostgreSQL remains the only durable backend; no JSON/SQLite fallback may be added.
- Domain classes in `api`/`common` remain free of Bukkit and Vault types.
- Every behavior change starts with a failing test and ends with focused tests plus the relevant module suite.
- Existing public APIs remain source-compatible unless the task explicitly adds a new overload.
- Influence state remains guild-keyed for this pass; alliance overlay ownership is not migrated.
- No project-wide formatter, linter, or unrelated refactor is included.
- Commit each coherent behavior unit atomically with its tests.

---

### Task 1: Seed standing configuration and release metadata

**Files:**
- Modify: `build.gradle.kts` — set project version to `1.1.0`.
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java` — seed `bonuses.json` before loading it and wire expense persistence seams.
- Modify: `paper/src/test/java/com/azoth/territory/PluginMetadataTest.java` — assert the packaged standing resource exists.
- Test: `common/src/test/java/com/azoth/territory/standing/StandingConfigTest.java` — preserve parser/default behavior.

**Interfaces:**
- Produces a fresh-install invariant: if `<data>/bonuses.json` is absent, `saveResource("bonuses.json", false)` creates it; existing files are untouched.
- Produces release version `1.1.0`; `plugin.yml` continues to consume `${version}`.

- [ ] **Step 1: Add the failing metadata/resource test.**

Extend `PluginMetadataTest` with a test that opens `bonuses.json` from the test classpath and asserts it is non-null and contains `"version": 1` and `"tiers"`. Run:

```bash
./gradlew :paper:test --tests com.azoth.territory.PluginMetadataTest --no-daemon -q
```

Expected: the new assertion fails only if the resource is not packaged.

- [ ] **Step 2: Set the release version and seed the resource.**

Set the root Gradle `version` property to `1.1.0`. In `onEnable`, immediately after `saveDefaultConfig()` and before `StandingConfigLoader.load(...)`, call:

```java
File bonusesFile = new File(getDataFolder(), "bonuses.json");
if (!bonusesFile.exists()) {
    saveResource("bonuses.json", false);
}
```

Do not overwrite an existing administrator file.

- [ ] **Step 3: Run the focused test and metadata build.**

```bash
./gradlew :paper:test --tests com.azoth.territory.PluginMetadataTest --no-daemon -q
./gradlew :paper:processResources :paper:jar --no-daemon -q
```

Expected: both commands pass and the processed plugin descriptor contains `version: 1.1.0`.

- [ ] **Step 4: Commit.**

```bash
git add build.gradle.kts paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java paper/src/test/java/com/azoth/territory/PluginMetadataTest.java
git commit -m "fix: seed standing config and cut release version"
```

---

### Task 2: Correct harvest event handling

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/standing/HarvestBonusListener.java`.
- Modify: `paper/src/test/java/com/azoth/territory/standing/HarvestBonusListenerTest.java`.
- Create: `paper/src/test/java/com/azoth/territory/standing/HarvestDropCalculatorTest.java` if a pure calculator is extracted.

- `HarvestBonusListener` remains a Bukkit listener.
- Block handling uses the player/tool-aware `Block#getDrops(ItemStack, Entity)` overload.
- Entity handling ignores `Player` victims and mutates the event drop list at a mutating priority rather than spawning at `MONITOR`.
- Mob drops are intentionally scoped to the canonical vanilla `EntityDeathEvent#getDrops()` result. The standing addition does not regenerate loot tables, reroll Looting, or replace the original event drops; because Bukkit exposes only the post-loot event list, this pass does not claim strict pre-Looting base isolation. Document that scope in the listener Javadoc and release notes rather than silently promising a stronger contract.

- [ ] **Step 1: Write failing regression tests.**

Add tests that:

1. invoke `onEntityDeath` with a `PlayerDeathEvent` and verify no bonus item is spawned or appended;
2. invoke block handling with a mocked main-hand tool and verify `block.getDrops(tool, player)` is used;
3. inspect the listener method annotation and require `EventPriority.HIGH` (or another priority below `MONITOR`);
4. invoke an entity death with a base drop and verify the extra stack is added to `event.getDrops()` rather than `World.dropItemNaturally`;
5. invoke an entity death whose event list represents Looting output and verify the original stack is unchanged while the standing extra is appended from that canonical event result; do not assert or imply a pre-Looting baseline.

Run:

```bash
./gradlew :paper:test --tests 'com.azoth.territory.standing.HarvestBonusListenerTest' --no-daemon -q
```

Expected: the new tests fail against the current `MONITOR` handlers, player-victim behavior, context-free block drop call, and world-spawn mutation.

- [ ] **Step 2: Implement the minimal listener correction.**

Change both handlers to a mutating priority (`HIGH`), add:

```java
if (event.getEntity() instanceof Player) {
    return;
}
```

For blocks, obtain the player's main-hand tool and call `block.getDrops(tool, player)`. For entities, clone each eligible stack from the canonical event drop list, calculate the integer bonus, and append the extra stack to `event.getDrops()`. Do not call `dropItemNaturally` from the entity handler. Keep eligibility resolution and multiplier calculation unchanged; do not introduce loot-table regeneration.

- [ ] **Step 3: Run the focused and standing tests.**

```bash
./gradlew :paper:test --tests 'com.azoth.territory.standing.HarvestBonusListenerTest' --tests 'com.azoth.territory.standing.StandingListenerTest' --no-daemon -q
```

Expected: PASS, with no world drop calls for player victims and context-aware block drops verified.

- [ ] **Step 4: Commit.**

```bash
git add paper/src/main/java/com/azoth/territory/standing/HarvestBonusListener.java paper/src/test/java/com/azoth/territory/standing/HarvestBonusListenerTest.java
 git commit -m "fix: make standing harvest drops event-safe"
```

---

### Task 3: Persist expense journals across restart

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java` — construct `ExpenseLedger` with `PostgresExpenseStore`, load it before economy use, and retain the store for shutdown.
- Modify: `common/src/main/java/com/azoth/territory/economy/ExpenseLedger.java` only if load/snapshot semantics need a narrow correction.
- Create: `common/src/test/java/com/azoth/territory/persist/PostgresExpenseStoreTest.java`.
- Modify: `common/src/test/java/com/azoth/territory/economy/ExpenseLedgerTest.java` if a restart invariant needs a unit assertion.

**Interfaces:**
- `PostgresExpenseStore.save(Collection<ExpenseEntry>)` is the durable snapshot sink.
- `EconomyBridge` receives the loaded `ExpenseLedger` through its existing constructor.

- [ ] **Step 1: Write the failing PostgreSQL round-trip test.**

Create an integration test guarded by `PostgresTestDatabase.open()` that saves one `ExpenseEntry`, loads it into a new `PostgresExpenseStore`, and asserts every field round-trips. Add a second assertion that `EconomyBridge.chargeExpense` returns `ALREADY_APPLIED` after a new bridge loads the same ledger entry.

Run:

```bash
./gradlew :common:test --tests com.azoth.territory.persist.PostgresExpenseStoreTest --no-daemon -q
```

Expected: the store round-trip passes if the existing store is correct; the restart bridge assertion fails until plugin-style ledger loading is covered. If PostgreSQL is unavailable, the test is skipped by the existing assumption.

- [ ] **Step 2: Wire the loaded ledger in the Paper lifecycle.**

Add `PostgresExpenseStore expenseStore` and `ExpenseLedger expenseLedger` fields. Before constructing `EconomyBridge`, load entries from PostgreSQL, construct the ledger with a sink that calls `expenseStore.save`, and call `expenseLedger.load(...)`. On disable, flush the ledger snapshot before closing the database. Preserve reconciliation behavior for failed writes.

- [ ] **Step 3: Run economy and integration tests.**

```bash
./gradlew :common:test --tests 'com.azoth.territory.economy.*' --tests 'com.azoth.territory.persist.PostgresExpenseStoreTest' --no-daemon -q
./gradlew :paper:test --tests 'com.azoth.territory.PluginEconomyWiringTest' --tests 'com.azoth.territory.economy.*' --no-daemon -q
```

Expected: PASS or PostgreSQL-only tests skipped when `AZOTH_TEST_JDBC_URL` is absent.

- [ ] **Step 4: Commit.**

```bash
git add paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java common/src/test/java/com/azoth/territory/persist/PostgresExpenseStoreTest.java common/src/test/java/com/azoth/territory/economy/ExpenseLedgerTest.java
git commit -m "fix: persist treasury expense idempotency across restart"
```

---

### Task 4: Add durable recurring territory upkeep

**Files:**
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepStatus.java`.
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepState.java`.
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepConfig.java`.
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepAssessment.java`.
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepStore.java`.
- Create: `common/src/main/java/com/azoth/territory/upkeep/UpkeepEngine.java`.
- Create: `common/src/main/java/com/azoth/territory/persist/PostgresUpkeepStore.java`.
- Modify: `common/src/main/java/com/azoth/territory/persist/PostgresDatabase.java` — create `upkeep_state` table.
- Modify: `paper/src/main/resources/config.yml` — add `upkeep` settings.
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java` — construct/recover/tick upkeep.
- Modify: `paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java` — add read-only upkeep status.
- Create: `common/src/test/java/com/azoth/territory/upkeep/UpkeepEngineTest.java`.
- Create: `common/src/test/java/com/azoth/territory/persist/PostgresUpkeepStoreTest.java`.
- Create: `paper/src/test/java/com/azoth/territory/command/TerritoryCommandUpkeepTest.java` if command behavior is not covered by an existing fixture.

**Interfaces:**
- `UpkeepStore` exposes `load()` and `save(Collection<UpkeepState>)`.
- `UpkeepEngine` constructor accepts `TerritoryRegistry`, `EconomyBridge`, `FacilityRegistry`, `UpkeepConfig`, `UpkeepStore`, and a development-level function.
- `UpkeepEngine.tick(long nowEpochMs)` returns immutable status transitions and persists every transition before returning it.
- Assessment uses base cost plus territory boundary chunk/polygon footprint, registered facility count, and supplied development level; all coefficients are in `UpkeepConfig`.

- [ ] **Step 1: Write failing domain tests.**

Cover these observable contracts:

- an ungoverned territory has no charge;
- a governed territory due at `now` charges exactly one `UPKEEP` expense with a stable period key;
- retrying the same due period does not charge twice;
- insufficient funds enters `GRACE` and sets a deterministic grace deadline;
- a failed charge after the grace deadline enters `SUSPENDED`;
- a successful later charge returns to `CURRENT` and advances `nextDueEpochMs`.

Use a fake `PaymentRail`/`EconomyBridge` seam only where the existing domain tests use one; assert actual state transitions and amounts.

Run:

```bash
./gradlew :common:test --tests com.azoth.territory.upkeep.UpkeepEngineTest --no-daemon -q
```

Expected: compilation failure because the upkeep package does not exist.

- [ ] **Step 2: Implement immutable state/config and deterministic assessment.**

Use explicit config fields: `baseAmount`, `chunkAmount`, `facilityAmount`, `developmentLevelAmount`, `intervalEpochMs`, and `graceEpochMs`. Validate all amounts non-negative, interval/grace positive, and finite. Define `periodKey(territoryId, dueEpochMs)` as `upkeep:<territoryId>:<dueEpochMs>`.

- [ ] **Step 3: Implement the engine and JSONB store.**

`UpkeepEngine` loads state, drops missing territories, creates initial state for governed territories, calculates due assessments, calls `EconomyBridge.chargeExpense`, and persists state after each transition. Use `PostgresUpkeepStore` with one row (`id = 1`) containing versioned JSONB state, temp-free transactional replacement, and checked `IOException` boundaries matching the standing/influence stores.

- [ ] **Step 4: Add schema/config/plugin wiring and status command.**

Add the `upkeep_state` table to `PostgresDatabase.COMMON_SCHEMA`, default config values, plugin lifecycle recovery/tick scheduling, and `/territory upkeep [territoryId]`. The command reports amount, status, next due, grace deadline, and last outcome; it does not mutate state.

- [ ] **Step 5: Run focused upkeep/persistence tests.**

```bash
./gradlew :common:test --tests 'com.azoth.territory.upkeep.*' --tests 'com.azoth.territory.persist.PostgresUpkeepStoreTest' --no-daemon -q
./gradlew :paper:test --tests 'com.azoth.territory.command.TerritoryCommandUpkeepTest' --no-daemon -q
```

- [ ] **Step 6: Commit.**

```bash
git add common/src/main/java/com/azoth/territory/upkeep common/src/main/java/com/azoth/territory/persist/PostgresUpkeepStore.java common/src/main/java/com/azoth/territory/persist/PostgresDatabase.java common/src/test/java/com/azoth/territory/upkeep common/src/test/java/com/azoth/territory/persist/PostgresUpkeepStoreTest.java paper/src/main/resources/config.yml paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java paper/src/test/java/com/azoth/territory/command/TerritoryCommandUpkeepTest.java
git commit -m "feat: add durable territory upkeep"
```

---

### Task 5: Add influence status HUD and map contest layer

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/influence/InfluenceStatusFormatter.java`.
- Create: `paper/src/main/java/com/azoth/territory/influence/InfluenceStatusTask.java`.
- Create: `paper/src/test/java/com/azoth/territory/influence/InfluenceStatusFormatterTest.java`.
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java` — schedule/cancel HUD task.
- Modify: `paper/src/main/java/com/azoth/territory/squaremap/TerritorySquaremapBridge.java` — accept optional influence supplier and render an influence layer.
- Modify: `paper/src/test/java/com/azoth/territory/squaremap/ChunkOutlinesTest.java` only if style helper coverage belongs there; otherwise create a focused style test.
- Modify: `README.md` — document HUD/map status.

**Interfaces:**
- Formatter consumes `Territory`, `TerritoryInfluenceState`, `InfluenceEngine`, and current time; returns an Adventure `Component` and never performs I/O.
- HUD task runs on the server scheduler, resolves each online player's current territory, sends status only for an active contest/declaration/cooldown, and cancels cleanly on plugin disable.
- Map bridge remains a soft dependency; without squaremap it remains a no-op.

- [ ] **Step 1: Write failing formatter tests.**

Cover uncontained locations, a contested territory with sorted bars, a declarable bar, an active declaration countdown, and cooldown display. Assert the rendered component string contains owner, top attacker, and remaining-time labels without relying on color internals.

Run:

```bash
./gradlew :paper:test --tests com.azoth.territory.influence.InfluenceStatusFormatterTest --no-daemon -q
```

Expected: compilation failure because the formatter does not exist.

- [ ] **Step 2: Implement formatter and HUD task.**

Use a 20-tick repeating task. Resolve player coordinates through `TerritoryRegistry`, query the engine snapshot, and call `player.sendActionBar(formatter.format(...))` only when a state exists. Use `BukkitTask`/task id cancellation during `onDisable`; do not create per-player tasks.

- [ ] **Step 3: Add the map contest layer.**

Register a third `SimpleLayerProvider` named `Azoth Influence`. During refresh, render each territory boundary with a neutral owner stroke when no active race exists, and a contest fill/stroke when bars or a declaration exist. Use the existing escaped tooltip helpers and resolve the leading bar deterministically. Keep all squaremap calls inside the existing soft-dependency guards.

- [ ] **Step 4: Run focused UI/wiring tests and compile.**

```bash
./gradlew :paper:test --tests 'com.azoth.territory.influence.*' --tests 'com.azoth.territory.squaremap.*' --no-daemon -q
./gradlew :paper:compileJava --no-daemon -q
```

- [ ] **Step 5: Commit.**

```bash
git add paper/src/main/java/com/azoth/territory/influence/InfluenceStatusFormatter.java paper/src/main/java/com/azoth/territory/influence/InfluenceStatusTask.java paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java paper/src/main/java/com/azoth/territory/squaremap/TerritorySquaremapBridge.java paper/src/test/java/com/azoth/territory/influence/InfluenceStatusFormatterTest.java README.md
git commit -m "feat: show influence contest status in game and maps"
```

---

### Task 6: Finalize durable guild contributions and upgrades

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/ResourceServiceImpl.java` — implement contribution queries, persistence, totals, statistics, and transactional resource-bank updates.
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildLevelBrigadierCommand.java` — route deposit through `ResourceService.processContribution` and enforce upgrade authority.
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildLevelServiceImpl.java` — lock/recheck upgrade state and consume committed requirements exactly once.
- Modify: `paper/src/main/java/org/aincraft/guilds/services/GuildLevelService.java` only if an explicit authority-aware overload is required; retain existing callers through a delegating overload.
- Modify: `paper/src/test/java/org/aincraft/guilds/services/GuildsServiceTestFixture.java` if the fixture needs resource rows.
- Create: `paper/src/test/java/org/aincraft/guilds/services/ResourceServiceImplContributionTest.java`.
- Create: `paper/src/test/java/org/aincraft/guilds/services/GuildLevelServiceImplUpgradeTest.java`.
- Modify: `paper/src/test/java/org/aincraft/guilds/services/PlotPermissionFormTest.java` only if shared fixture setup changes; no unrelated assertions.

**Interfaces:**
- `ResourceService.processContribution(Guild, UUID, String, int)` remains the canonical deposit operation.
- `GuildLevelService.performGuildUpgrade(Guild)` remains source-compatible; the implementation must authorize through the command before invocation and recheck database state inside the mutation.
- Contribution rows use the existing `resource_contributions` schema; resource-bank rows use `guild_resources`.

- [ ] **Step 1: Write failing persistence tests.**

Add tests for:

- `recordResourceContribution` inserts and `getResourceContribution` reconstructs a row;
- guild/player/resource aggregate queries return real totals and recent rows;
- invalid resource types and non-positive amounts do not insert rows;
- `processContribution` refunds inventory when bank insertion or contribution recording fails;
- two upgrade attempts cannot both consume the same requirements;
- level benefits are recorded once per guild/level/benefit type.

Run the focused classes. Expected: the stub methods return empty values and the new assertions fail.

- [ ] **Step 2: Implement ResourceService persistence.**

Use prepared statements and explicit `LocalDateTime` serialization matching existing schema conventions. In `processContribution`, validate the guild resident and inventory, remove items, execute bank credit plus contribution insert through `DatabaseManager.executeTransaction`, update the guild progress only after the SQL transaction succeeds, and refund the inventory when any later step fails. Restrict supported resources to `ResourceType` values accepted by the upgrade system instead of arbitrary Bukkit items.

- [ ] **Step 3: Route commands through services and enforce authority.**

Replace the direct inventory/progress mutation in `handleDeposit` with `resourceService.processContribution`. In `handleUpgrade`, require the guild mayor or an existing explicit guild-admin upgrade permission before calling the service. Show the service result and next-level progress; remove unused direct inventory helper calls.

- [ ] **Step 4: Make upgrade mutation concurrency-safe.**

In `performGuildUpgrade`, use a transaction that selects the guild level/progress row `FOR UPDATE`, rechecks next-level requirements, records consumed resource amounts, updates level/tech points/progress, and inserts missing benefit rows with `ON CONFLICT DO NOTHING`. Return failure without mutating the guild object when requirements are stale.

- [ ] **Step 5: Run guild focused tests and integration wiring.**

```bash
./gradlew :paper:test --tests 'org.aincraft.guilds.services.ResourceServiceImplContributionTest' --tests 'org.aincraft.guilds.services.GuildLevelServiceImplUpgradeTest' --tests 'com.azoth.territory.GuildsIntegrationTest' --no-daemon -q
```

- [ ] **Step 6: Commit.**

```bash
git add paper/src/main/java/org/aincraft/guilds/services/impl/ResourceServiceImpl.java paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildLevelBrigadierCommand.java paper/src/main/java/org/aincraft/guilds/services/impl/GuildLevelServiceImpl.java paper/src/main/java/org/aincraft/guilds/services/GuildLevelService.java paper/src/test/java/org/aincraft/guilds/services/ResourceServiceImplContributionTest.java paper/src/test/java/org/aincraft/guilds/services/GuildLevelServiceImplUpgradeTest.java
git commit -m "fix: finalize durable guild upgrade contributions"
```

---

### Task 7: Full verification and release smoke path

**Files:**
- Modify: `README.md` — document `1.1.0`, upkeep, influence HUD/map, and upgrade behavior.
- Modify: `paper/src/main/resources/config.yml` — ensure all new settings are documented.
- Modify: `paper/src/main/resources/plugin.yml` only if new commands/permissions are added.

- [ ] **Step 1: Run all focused suites.**

```bash
./gradlew :common:test --no-daemon -q
./gradlew :paper:test --no-daemon -q
```

Expected: all tests pass; PostgreSQL integration tests may be skipped only when `AZOTH_TEST_JDBC_URL` is unset.

- [ ] **Step 2: Run the application smoke path.**

With PostgreSQL configured and no existing plugin data folder:

```bash
./gradlew :paper:runServer
```

Verify from the server log and live commands:

1. plugin metadata reports `1.1.0`;
2. `plugins/AzothTerritory/bonuses.json` is created and remains unchanged on restart;
3. `/territory influence <id>` and the HUD report the same contest state;
4. a due upkeep period charges once and retrying the scheduler does not double-charge;
5. a failed charge enters grace/suspended state visibly;
6. `/townlevel deposit` persists contribution history across restart;
7. authorized upgrade advances once and grants benefits once;
8. shutdown flushes standing, influence, expenses, and upkeep state.

- [ ] **Step 3: Review and commit release documentation.**

```bash
git add README.md paper/src/main/resources/config.yml paper/src/main/resources/plugin.yml
git commit -m "docs: document territory completeness release"
```

- [ ] **Step 4: Final verification.**

```bash
git status --short
./gradlew build --no-daemon -q
```

Expected: clean worktree after commits and a successful full build. Record any PostgreSQL or external-plugin smoke limitation explicitly rather than claiming it was exercised.
