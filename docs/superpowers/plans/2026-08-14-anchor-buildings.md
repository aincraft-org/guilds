# Anchor-Based Territory Buildings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build free-form physical waystones and trading posts whose behavior is attached to one persisted anchor block, never to a prescribed multiblock structure.

**Architecture:** Extend `SettlementFacility` and `FacilityRegistry`; do not create a second building store. Paper owns anchor validation, placement sessions, permission checks, interaction, and teleport effects. Every facility mutation is staged in a candidate registry, persisted transactionally, then published to the live registry so database failure cannot expose uncommitted state.

**Tech Stack:** Java 21, Gradle Kotlin DSL multi-module build, Paper API 26.2, PostgreSQL/MySQL through the existing `Database`/dialect abstraction, Gson, JUnit 5, Mockito.

## Global Constraints

- The registered anchor is the only structural requirement; production code must never inspect neighboring blocks.
- Surrounding blocks, NPCs, signs, holograms, and decoration are presentation only.
- Keep `SettlementFacility` immutable location metadata; do not add inventory, listing, NPC, level, or generic perk fields.
- Reuse `FacilityRegistry` and the existing `facilities` database table; no second building registry or persistence backend.
- `STORAGE` remains backward compatible and receives no new inventory behavior.
- Database commit precedes live-registry mutation for every registration/removal.
- Building mutations run on the Paper main thread and are serialized by one coordinator.
- Player management requires the governing guild plus the existing `set_spawn` guild permission; `azoth.territory.admin` is the administrative override.
- Ungoverned territories are admin-only for management and expose no waystone travel.
- Initial waystone travel is active-waystone to active-waystone for members of the same governing guild only.
- Alliance/public travel, tolls, custom placement items, recipes, auctions, stock, shop UI, building levels, and a generic perk catalog are out of scope.
- Invalid building configuration disables the building subsystem loudly; it does not invent runtime defaults.
- Add observable-contract tests first, but commit production code with its tests in one green atomic commit.

---

## File Structure

### Existing files to modify

- `api/src/main/java/com/azoth/territory/model/FacilityType.java` — add `WAYSTONE`.
- `api/src/main/java/com/azoth/territory/registry/FacilityRegistry.java` — add type/territory queries and candidate-copy support without weakening validation.
- `api/src/test/java/com/azoth/territory/registry/FacilityRegistryTest.java` — protect query and candidate-isolation contracts.
- `common/src/main/java/com/azoth/territory/persist/PostgresFacilityStore.java` — persist a validated facility snapshot rather than requiring the live registry.
- `common/src/main/java/com/azoth/territory/persist/FacilityStore.java` — new narrow persistence interface used by the coordinator.
- `common/src/test/java/com/azoth/territory/persist/PostgresFacilityStoreTest.java` — database round-trip for all facility types.
- `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java` — construct, wire, expose, and stop the subsystem.
- `paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java` — delegate `/territory building ...` parsing and tab completion.
- `paper/src/main/resources/config.yml` — building anchor, placement, warm-up, and cooldown settings.
- `paper/src/main/resources/plugin.yml` — command usage and building-management permission documentation.
- `docs/living-specs/economy.md` — preserve facility-directory boundary and link building behavior to the territory-building surface.
- `docs/living-specs/territory.md` — record shipped anchor-building capability after runtime verification.

### New Paper building package

- `paper/src/main/java/com/azoth/territory/building/BuildingConfig.java` — immutable validated settings.
- `paper/src/main/java/com/azoth/territory/building/BuildingConfigLoader.java` — Bukkit configuration parser.
- `paper/src/main/java/com/azoth/territory/building/AnchorStatus.java` — `ACTIVE`, `WORLD_UNAVAILABLE`, `WRONG_MATERIAL`, `OUTSIDE_TERRITORY`.
- `paper/src/main/java/com/azoth/territory/building/FacilityAnchorValidator.java` — exact-coordinate active-state lookup only.
- `paper/src/main/java/com/azoth/territory/building/FacilityMutationService.java` — staged save-then-publish registration/removal.
- `paper/src/main/java/com/azoth/territory/building/BuildingAuthorization.java` — governing-guild membership and `set_spawn` permission boundary.
- `paper/src/main/java/com/azoth/territory/building/BuildingPlacement.java` — immutable pending placement value.
- `paper/src/main/java/com/azoth/territory/building/BuildingPlacementSessions.java` — per-player timeout/cancel/consume state.
- `paper/src/main/java/com/azoth/territory/building/BuildingCommand.java` — create/cancel/list/info/remove behavior.
- `paper/src/main/java/com/azoth/territory/building/BuildingListener.java` — placement click, anchor interaction, and block-break lifecycle.
- `paper/src/main/java/com/azoth/territory/building/WaystoneAccess.java` — same-governing-guild reachable-set calculation.
- `paper/src/main/java/com/azoth/territory/building/SafeLandingResolver.java` — collision-safe candidate selection around an anchor.
- `paper/src/main/java/com/azoth/territory/building/WaystoneTravelService.java` — warm-up, cancellation, revalidation, teleport, cooldown.
- `paper/src/main/java/com/azoth/territory/building/WaystoneTravelListener.java` — movement, damage, death, and disconnect cancellation.
- `paper/src/main/java/com/azoth/territory/building/TradingPostInteractEvent.java` — one cancellable Bukkit integration seam.

### New focused tests

- `paper/src/test/java/com/azoth/territory/building/BuildingConfigLoaderTest.java`
- `paper/src/test/java/com/azoth/territory/building/FacilityAnchorValidatorTest.java`
- `paper/src/test/java/com/azoth/territory/building/FacilityMutationServiceTest.java`
- `paper/src/test/java/com/azoth/territory/building/BuildingAuthorizationTest.java`
- `paper/src/test/java/com/azoth/territory/building/BuildingPlacementSessionsTest.java`
- `paper/src/test/java/com/azoth/territory/building/BuildingCommandTest.java`
- `paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java`
- `paper/src/test/java/com/azoth/territory/building/WaystoneAccessTest.java`
- `paper/src/test/java/com/azoth/territory/building/SafeLandingResolverTest.java`
- `paper/src/test/java/com/azoth/territory/building/WaystoneTravelServiceTest.java`
- `paper/src/test/java/com/azoth/territory/building/TradingPostInteractEventTest.java`
- `paper/src/test/java/com/azoth/territory/building/BuildingLifecycleWiringTest.java`

---

### Task 1: Facility snapshots and waystone type

**Files:**
- Modify: `api/src/main/java/com/azoth/territory/model/FacilityType.java`
- Modify: `api/src/main/java/com/azoth/territory/registry/FacilityRegistry.java`
- Modify: `api/src/test/java/com/azoth/territory/registry/FacilityRegistryTest.java`
- Create: `common/src/main/java/com/azoth/territory/persist/FacilityStore.java`
- Modify: `common/src/main/java/com/azoth/territory/persist/PostgresFacilityStore.java`
- Create: `common/src/test/java/com/azoth/territory/persist/PostgresFacilityStoreTest.java`

**Interfaces:**
- Produces: `FacilityType.WAYSTONE`.
- Produces: `List<SettlementFacility> FacilityRegistry.list(String territoryId, FacilityType type)` preserving registration order.
- Produces: `FacilityRegistry FacilityRegistry.copy()` backed by the same `TerritoryRegistry` and populated through `replaceAll` validation.
- Produces: `interface FacilityStore { void save(Collection<SettlementFacility> facilities) throws IOException; }`.
- `PostgresFacilityStore` implements `FacilityStore`; existing `save(FacilityRegistry)` delegates to `save(registry.list())` during clean cutover, then all production callers migrate to the collection method before the overload is removed in Task 2.

- [ ] **Step 1: Add failing registry and enum tests**

Add these contracts to `FacilityRegistryTest`:

```java
@Test
void filtersFacilitiesByTerritoryAndTypeInRegistrationOrder() {
    FacilityRegistry facilities = new FacilityRegistry(
            new TerritoryRegistry(List.of(territory("t1"), territoryAt("t2", 200))));
    SettlementFacility first = facility("first", FacilityType.WAYSTONE, 5, 5);
    SettlementFacility market = facility("market", FacilityType.TRADING_POST, 6, 6);
    SettlementFacility second = facility("second", FacilityType.WAYSTONE, 7, 7);
    facilities.replaceAll(List.of(first, market, second));

    assertEquals(List.of(first, second), facilities.list("t1", FacilityType.WAYSTONE));
}

@Test
void copyCanMutateWithoutChangingLiveRegistry() {
    FacilityRegistry live = new FacilityRegistry(new TerritoryRegistry(List.of(territory("t1"))));
    live.register(facility("market", FacilityType.TRADING_POST, 5, 5));

    FacilityRegistry candidate = live.copy();
    candidate.register(facility("stone", FacilityType.WAYSTONE, 6, 6));

    assertEquals(List.of("market"), live.list().stream().map(SettlementFacility::id).toList());
    assertEquals(List.of("market", "stone"), candidate.list().stream().map(SettlementFacility::id).toList());
}
```

Add a `territoryAt` fixture that creates a non-overlapping territory. Keep every pre-existing registry assertion.

- [ ] **Step 2: Run the API tests and observe the expected compile failure**

Run:

```bash
./gradlew :api:test --tests com.azoth.territory.registry.FacilityRegistryTest
```

Expected: compilation fails because `WAYSTONE`, `copy()`, and filtered `list(...)` do not exist.

- [ ] **Step 3: Implement the minimal API additions**

Add `WAYSTONE` to the enum. Store the constructor's `TerritoryRegistry` as today; implement:

```java
public List<SettlementFacility> list(String territoryId, FacilityType type) {
    Objects.requireNonNull(type, "type");
    if (territoryId == null || territoryId.isBlank()) return List.of();
    String normalized = territoryId.trim();
    return byId.values().stream()
            .filter(f -> f.territoryId().equals(normalized) && f.type() == type)
            .toList();
}

public FacilityRegistry copy() {
    FacilityRegistry copy = new FacilityRegistry(territories);
    copy.replaceAll(byId.values());
    return copy;
}
```

Do not expose the internal map or add neighboring-block/area queries.

- [ ] **Step 4: Add failing SQL round-trip tests**

Create `PostgresFacilityStoreTest` using `com.azoth.territory.PostgresTestDatabase`. Seed one territory, initialize `FacilityRegistry`, save `WAYSTONE`, `TRADING_POST`, and `STORAGE` records through `save(Collection<SettlementFacility>)`, load into a fresh registry, and assert exact record equality and order. Verify rollback by wrapping the test `Database` so its connection throws on the second insert-batch operation, pre-seeding one row, invoking `save`, and asserting the original row remains after the thrown `IOException`.

Core assertion:

```java
store.save(List.of(waystone, market, storage));
FacilityRegistry reloaded = new FacilityRegistry(territories);
store.loadInto(reloaded);
assertEquals(List.of(waystone, market, storage), reloaded.list());
```

- [ ] **Step 5: Run the persistence test and observe the expected compile failure**

Run:

```bash
./gradlew :common:test --tests com.azoth.territory.persist.PostgresFacilityStoreTest
```

Expected: compilation fails because the collection-based `save` boundary does not exist.

- [ ] **Step 6: Implement `FacilityStore` and collection persistence**

Create:

```java
public interface FacilityStore {
    void save(Collection<SettlementFacility> facilities) throws IOException;
}
```

Make `PostgresFacilityStore implements FacilityStore`. Copy the incoming collection once with `List.copyOf`, then execute the existing delete-and-batched-upsert transaction against that snapshot. Keep rollback behavior. Retain `loadInto(FacilityRegistry)` as the validated load boundary.

- [ ] **Step 7: Run focused module tests**

Run:

```bash
./gradlew :api:test --tests com.azoth.territory.registry.FacilityRegistryTest \
  :common:test --tests com.azoth.territory.persist.PostgresFacilityStoreTest
```

Expected: both test classes pass.

- [ ] **Step 8: Commit the facility snapshot contract**

```bash
git add api/src/main/java/com/azoth/territory/model/FacilityType.java \
  api/src/main/java/com/azoth/territory/registry/FacilityRegistry.java \
  api/src/test/java/com/azoth/territory/registry/FacilityRegistryTest.java \
  common/src/main/java/com/azoth/territory/persist/FacilityStore.java \
  common/src/main/java/com/azoth/territory/persist/PostgresFacilityStore.java \
  common/src/test/java/com/azoth/territory/persist/PostgresFacilityStoreTest.java
git commit -m "feat: add staged facility snapshot support"
```

---

### Task 2: Save-before-publish facility mutations

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/FacilityMutationService.java`
- Create: `paper/src/test/java/com/azoth/territory/building/FacilityMutationServiceTest.java`
- Modify: `common/src/main/java/com/azoth/territory/persist/PostgresFacilityStore.java` — remove the obsolete `save(FacilityRegistry)` overload after migrating plugin shutdown in Task 11; until then mark the callsite migration in this task by changing plugin shutdown to `save(facilities.list())`.
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`

**Interfaces:**
- Consumes: `FacilityRegistry.copy()`, `FacilityStore.save(Collection<SettlementFacility>)`.
- Produces: `SettlementFacility register(SettlementFacility facility) throws IOException`.
- Produces: `Optional<SettlementFacility> remove(String facilityId) throws IOException`.
- Invariant: candidate validation and database save complete before `live.replaceAll(candidate.list())`.

- [ ] **Step 1: Write mutation failure and success tests**

Create a memory `FacilityStore` with `boolean fail` and a saved snapshot. Cover:

```java
@Test
void registerPublishesOnlyAfterStoreAcceptsCandidate() throws Exception {
    store.fail = true;
    assertThrows(IOException.class, () -> service.register(waystone));
    assertTrue(live.list().isEmpty());

    store.fail = false;
    service.register(waystone);
    assertEquals(List.of(waystone), store.saved);
    assertEquals(List.of(waystone), live.list());
}

@Test
void failedRemovalLeavesLiveFacilityPresent() throws Exception {
    service.register(waystone);
    store.fail = true;
    assertThrows(IOException.class, () -> service.remove(waystone.id()));
    assertEquals(Optional.of(waystone), live.get(waystone.id()));
}
```

Also assert duplicate ID/location exceptions leave both store and live state unchanged, and unknown removal returns `Optional.empty()` without writing.

- [ ] **Step 2: Run the test and observe the expected compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.FacilityMutationServiceTest
```

Expected: compilation fails because `FacilityMutationService` does not exist.

- [ ] **Step 3: Implement the serialized coordinator**

```java
public final class FacilityMutationService {
    private final FacilityRegistry live;
    private final FacilityStore store;

    public synchronized SettlementFacility register(SettlementFacility facility) throws IOException {
        FacilityRegistry candidate = live.copy();
        candidate.register(facility);
        store.save(candidate.list());
        live.replaceAll(candidate.list());
        return facility;
    }

    public synchronized Optional<SettlementFacility> remove(String id) throws IOException {
        SettlementFacility existing = live.get(id).orElse(null);
        if (existing == null) return Optional.empty();
        FacilityRegistry candidate = live.copy();
        candidate.unregister(id);
        store.save(candidate.list());
        live.replaceAll(candidate.list());
        return Optional.of(existing);
    }
}
```

Require non-null dependencies and normalized non-blank removal IDs. Call only from Paper's main thread in production; synchronization protects tests and accidental re-entry.

- [ ] **Step 4: Migrate the existing shutdown call**

Change `facilityStore.save(facilities)` to `facilityStore.save(facilities.list())`. Search all `PostgresFacilityStore.save` callsites and migrate each. Remove the registry overload once no callers remain.

- [ ] **Step 5: Run focused tests**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.FacilityMutationServiceTest \
  :common:test --tests com.azoth.territory.persist.PostgresFacilityStoreTest
```

Expected: both pass, including store-failure/live-state isolation.

- [ ] **Step 6: Commit transaction-safe mutations**

```bash
git add common/src/main/java/com/azoth/territory/persist/PostgresFacilityStore.java \
  paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java \
  paper/src/main/java/com/azoth/territory/building/FacilityMutationService.java \
  paper/src/test/java/com/azoth/territory/building/FacilityMutationServiceTest.java
git commit -m "feat: publish facilities after durable save"
```

---

### Task 3: Building configuration and exact-anchor validation

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingConfig.java`
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingConfigLoader.java`
- Create: `paper/src/main/java/com/azoth/territory/building/AnchorStatus.java`
- Create: `paper/src/main/java/com/azoth/territory/building/FacilityAnchorValidator.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingConfigLoaderTest.java`
- Create: `paper/src/test/java/com/azoth/territory/building/FacilityAnchorValidatorTest.java`
- Modify: `paper/src/main/resources/config.yml`

**Interfaces:**
- Produces: `BuildingConfig(long placementTimeoutMillis, Map<FacilityType, Set<Material>> anchorMaterials, long waystoneWarmupTicks, long waystoneCooldownMillis)`.
- Produces: `Set<Material> anchorMaterials(FacilityType type)` and `boolean supports(FacilityType type)`.
- Produces: `record AnchorValidation(AnchorStatus status, SettlementFacility facility) { boolean active(); }`.
- Produces: `AnchorValidation validate(SettlementFacility facility)` and `Optional<SettlementFacility> activeAt(String worldId, int x, int y, int z)`.

- [ ] **Step 1: Write configuration contract tests**

Build a `YamlConfiguration` in memory. Assert the approved defaults parse to `LODESTONE`, `BELL`, and `LECTERN`; `STORAGE` is unsupported. Assert rejection of zero timeout, negative warm-up/cooldown, unknown material, `AIR`, and an empty configured material list.

Representative assertion:

```java
BuildingConfig config = BuildingConfigLoader.from(configuration);
assertEquals(Set.of(Material.LODESTONE), config.anchorMaterials(FacilityType.WAYSTONE));
assertEquals(Set.of(Material.BELL, Material.LECTERN),
        config.anchorMaterials(FacilityType.TRADING_POST));
assertFalse(config.supports(FacilityType.STORAGE));
```

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingConfigLoaderTest
```

Expected: missing building configuration types.

- [ ] **Step 3: Implement immutable validated configuration**

Copy all sets/maps defensively. Convert seconds with `Math.multiplyExact`. Use `Material.matchMaterial`, reject `material.isAir()`, and require `material.isBlock()`. Keep exact YAML keys from the spec.

- [ ] **Step 4: Write anchor validator tests**

Mock `Server`, `World`, and `Block`. Cover active exact material, unloaded world, wrong material, outside-territory after registry replacement, and restoration reactivation. Explicitly verify that only `world.getBlockAt(facility.x(), facility.y(), facility.z())` is called and no neighboring coordinate is queried.

- [ ] **Step 5: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.FacilityAnchorValidatorTest
```

Expected: missing validator types.

- [ ] **Step 6: Implement exact-coordinate validation**

`validate` first verifies the current territory resolution, then calls `server.getWorld(worldId)` without creating/loading a world. If unavailable return `WORLD_UNAVAILABLE`; otherwise read exactly one block and compare against the configured set. `activeAt` first uses `FacilityRegistry.resolve` and returns the facility only when `validate(...).active()`.

- [ ] **Step 7: Add approved defaults and run tests**

Add the exact `buildings:` YAML from the design. Run:

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingConfigLoaderTest \
  --tests com.azoth.territory.building.FacilityAnchorValidatorTest
```

Expected: both pass.

- [ ] **Step 8: Commit configuration and anchor state**

```bash
git add paper/src/main/java/com/azoth/territory/building/BuildingConfig.java \
  paper/src/main/java/com/azoth/territory/building/BuildingConfigLoader.java \
  paper/src/main/java/com/azoth/territory/building/AnchorStatus.java \
  paper/src/main/java/com/azoth/territory/building/FacilityAnchorValidator.java \
  paper/src/test/java/com/azoth/territory/building/BuildingConfigLoaderTest.java \
  paper/src/test/java/com/azoth/territory/building/FacilityAnchorValidatorTest.java \
  paper/src/main/resources/config.yml
git commit -m "feat: validate configured facility anchors"
```

---

### Task 4: Building authorization and placement sessions

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingAuthorization.java`
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingPlacement.java`
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingPlacementSessions.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingAuthorizationTest.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingPlacementSessionsTest.java`

**Interfaces:**
- Produces: `boolean canManage(Player player, Territory territory)`.
- Management rule: admin node OR governed guild membership plus `PermissionService.hasPermission(playerId, "set_spawn", "guild", guildName)`.
- Produces: `boolean canUseWaystones(UUID playerId, String guildId)` requiring `GuildService.getGuildById(guildId)` and resident membership.
- Produces: `record BuildingPlacement(FacilityType type, String id, String name, long expiresAtMillis)`.
- Produces: `void begin(UUID playerId, FacilityType type, String id, String name, long nowMillis)`, `Optional<BuildingPlacement> current(UUID playerId, long nowMillis)`, `boolean cancel(UUID playerId)`, and `void complete(UUID playerId)`.

- [ ] **Step 1: Write authorization tests**

Mock `GuildService`, `PermissionService`, `Guild`, and `Player`. Cover admin override, ungoverned denial, missing guild denial, nonresident denial, resident without `set_spawn` denial, permitted resident success, and same-guild waystone membership. Use guild ID for territory binding, resolve its `Guild` via `getGuildById`, then pass `guild.getName()` to `PermissionService`.

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingAuthorizationTest
```

Expected: `BuildingAuthorization` is absent.

- [ ] **Step 3: Implement the authorization boundary**

Keep all guild-name/ID conversion here. Do not inspect rank display strings. `canManage` accepts the admin node but `canUseWaystones` does not.

- [ ] **Step 4: Write deterministic session tests**

Use explicit `nowMillis` values. Cover one session per UUID, replacement by a new `begin`, expiry clearing, cancel, complete, and that querying one player's session cannot consume another's.

```java
sessions.begin(player, FacilityType.WAYSTONE, "north", "North Gate", 1_000L);
assertTrue(sessions.current(player, 1_999L).isPresent());
assertTrue(sessions.current(player, 61_001L).isEmpty());
```

Adjust the exact expiry to the configured timeout passed into the constructor.

- [ ] **Step 5: Implement placement state and rerun tests**

Validate IDs with a stable lowercase pattern `[a-z0-9][a-z0-9_-]{0,63}`; trim names and default blank names to ID. Store sessions in a `HashMap<UUID, BuildingPlacement>` because production access is main-thread-only.

Run:

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingAuthorizationTest \
  --tests com.azoth.territory.building.BuildingPlacementSessionsTest
```

Expected: both pass.

- [ ] **Step 6: Commit authorization and sessions**

```bash
git add paper/src/main/java/com/azoth/territory/building/BuildingAuthorization.java \
  paper/src/main/java/com/azoth/territory/building/BuildingPlacement.java \
  paper/src/main/java/com/azoth/territory/building/BuildingPlacementSessions.java \
  paper/src/test/java/com/azoth/territory/building/BuildingAuthorizationTest.java \
  paper/src/test/java/com/azoth/territory/building/BuildingPlacementSessionsTest.java
git commit -m "feat: authorize and track building placement"
```

---

### Task 5: Building management commands

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingCommand.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingCommandTest.java`
- Modify: `paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/resources/plugin.yml` — remove the root `territory` permission gate and declare subcommand permissions.

**Interfaces:**
- Consumes: placement sessions, registry, anchor validator, authorization, and mutation service.
- Produces: `boolean execute(CommandSender sender, String label, String[] buildingArgs)` where args begin after `building`.
- Produces: `List<String> complete(CommandSender sender, String[] buildingArgs)`.
- Command syntax: create/cancel/list/info/remove exactly as approved. The root `/territory` command is reachable by non-op players; each administrative subcommand performs its existing in-handler permission check, while `building` requires `azoth.territory.building.manage` plus domain authorization.

- [ ] **Step 1: Write command behavior tests**

Cover players-only create/cancel, supported type parsing (`waystone`, `trading_post`), ID/name preservation, list defaulting to current territory, explicit list territory, info active status, authorized removal, unknown ID, persistence failure messaging, and no live mutation on failed removal. Add a root-path test using a non-op player with `azoth.territory.building.manage` but without `azoth.territory.admin`; invoke `/territory building create waystone north` through `TerritoryCommand` and assert the placement session starts. Also assert that the same player remains denied from `govern`, reload/save, and other existing admin operations. Capture Adventure `Component` messages using existing command-test conventions.

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingCommandTest
```

Expected: missing `BuildingCommand`.

- [ ] **Step 3: Implement focused command delegation**

Keep command parsing out of the already-large `TerritoryCommand`. Add a nullable `BuildingCommand` constructor dependency or resolve `plugin.getBuildingCommand()` only at delegation. Add:

```java
case "building" -> plugin.getBuildingCommand() != null
        ? plugin.getBuildingCommand().execute(sender, label, Arrays.copyOfRange(args, 1, args.length))
        : buildingUnavailable(sender);
```

Update the root usage and tab completion. `BuildingCommand.remove` rechecks authority against the facility's current territory before calling the mutation service.

- [ ] **Step 4: Update plugin metadata**

Remove `permission: azoth.territory.admin` from the root `territory` command in `plugin.yml`; otherwise Bukkit rejects non-admin building managers before `TerritoryCommand` can authorize the subcommand. Add `building` to command usage and declare:

```yaml
azoth.territory.building.manage:
  description: Register and remove facilities in governed territory
  default: true
```

`BuildingCommand.execute` checks this Bukkit node first, then applies governing-guild domain authorization. Every pre-existing administrative path keeps or gains an explicit `azoth.territory.admin`/existing specialized permission check inside `TerritoryCommand`; the Task 5 tests enumerate every root subcommand to prevent permission broadening. Admin remains `azoth.territory.admin`.

- [ ] **Step 5: Run focused command tests**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingCommandTest \
  --tests com.azoth.territory.command.TerritoryCommandUpkeepTest
```

Expected: building tests pass, the non-op root-path test reaches `building`, every administrative subcommand remains denied, and the existing root-command test remains green.

- [ ] **Step 6: Commit the management command surface**

```bash
git add paper/src/main/java/com/azoth/territory/building/BuildingCommand.java \
  paper/src/test/java/com/azoth/territory/building/BuildingCommandTest.java \
  paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java \
  paper/src/main/resources/plugin.yml
git commit -m "feat: add territory building commands"
```

---

### Task 6: Placement click and protected anchor lifecycle

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/BuildingListener.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java`

**Interfaces:**
- Consumes: placement sessions, configured materials, registry/territory resolution, authorization, mutation service, anchor validator.
- Produces: `@EventHandler onInteract(PlayerInteractEvent)` for pending placement and active anchor interaction delegation.
- Produces: `@EventHandler onBreak(BlockBreakEvent)` for exact registered anchors.
- Initial interaction order: pending placement first; otherwise active facility behavior.

- [ ] **Step 1: Write placement-listener tests**

Cover right-click block only, main-hand only, allowed material, territory resolution, governance/authorization, duplicate errors, persistence error, successful facility construction from the selected block, and successful session completion. Assert a failed material/duplicate attempt keeps the session, while expiry/authorization loss clears it.

- [ ] **Step 2: Write lifecycle tests**

Register an exact anchor and cover unauthorized break cancellation, authorized durable removal followed by uncancelled break, failed persistence cancellation/live preservation, unrelated neighboring block break unchanged, and environmental events supported by the chosen Paper API (`BlockExplodeEvent`, `EntityExplodeEvent`, piston movement) cancelling only when the exact registered coordinate would be changed.

- [ ] **Step 3: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingListenerTest
```

Expected: listener missing.

- [ ] **Step 4: Implement placement and break paths**

Use the clicked block's exact `world.getName()/x/y/z`. Construct `SettlementFacility` with the territory resolved at that coordinate. For break, set cancelled before persistence, call `remove`, then clear cancellation only on success. Never manually break/set the block.

Add environmental handlers only for cancellable events where exact affected blocks can be determined. Do not add a periodic scanner or inspect decoration.

- [ ] **Step 5: Run listener tests**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingListenerTest
```

Expected: all placement and lifecycle contracts pass, including neighboring-block independence.

- [ ] **Step 6: Commit anchor placement and protection**

```bash
git add paper/src/main/java/com/azoth/territory/building/BuildingListener.java \
  paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java
git commit -m "feat: register and protect facility anchors"
```

---

### Task 7: Pure waystone reachability

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/WaystoneAccess.java`
- Create: `paper/src/test/java/com/azoth/territory/building/WaystoneAccessTest.java`

**Interfaces:**
- Produces: `List<SettlementFacility> reachable(UUID playerId, SettlementFacility origin)`.
- Consumes: facility registry, territory registry, anchor validator, building authorization.
- Result excludes origin, inactive anchors, non-waystones, ungoverned territories, and different governing guilds; sorts by display name then stable ID.

- [ ] **Step 1: Write the reachability matrix**

Create territories owned by guild A, guild A again, guild B, and ungoverned. Register active/inactive waystones plus a trading post. Assert only active same-guild destinations are returned for a guild-A resident; nonmember gets an empty list.

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.WaystoneAccessTest
```

Expected: `WaystoneAccess` missing.

- [ ] **Step 3: Implement deterministic filtering**

Resolve the origin territory and governing guild once. Require origin type and active status. Require `canUseWaystones`. Filter the registry list with exact type, active status, same governing guild ID, and non-origin ID. Sort case-insensitively by name and then ID.

- [ ] **Step 4: Run and commit**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.WaystoneAccessTest
git add paper/src/main/java/com/azoth/territory/building/WaystoneAccess.java \
  paper/src/test/java/com/azoth/territory/building/WaystoneAccessTest.java
git commit -m "feat: resolve reachable guild waystones"
```

---

### Task 8: Safe landing and waystone travel state machine

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/SafeLandingResolver.java`
- Create: `paper/src/main/java/com/azoth/territory/building/WaystoneTravelService.java`
- Create: `paper/src/main/java/com/azoth/territory/building/WaystoneTravelListener.java`
- Create: `paper/src/test/java/com/azoth/territory/building/SafeLandingResolverTest.java`
- Create: `paper/src/test/java/com/azoth/territory/building/WaystoneTravelServiceTest.java`

**Interfaces:**
- Produces: `Optional<Location> find(SettlementFacility destination)`.
- Landing candidates: top of anchor first, then north/east/south/west at anchor Y; candidate requires solid support, passable feet and head blocks, and no liquid.
- Produces: `StartResult start(Player player, SettlementFacility origin, String destinationId, long nowMillis)` with explicit statuses (`STARTED`, `COOLDOWN`, `INVALID_ORIGIN`, `INACCESSIBLE_DESTINATION`, `NO_SAFE_LANDING`, `PROTECTED_DESTINATION`).
- Produces: `void cancel(UUID playerId, CancelReason reason)`, `void tick(UUID playerId, long nowMillis)`, `long remainingCooldownMillis(UUID playerId, long nowMillis)`, `void stop()`.

- [ ] **Step 1: Write safe-landing tests**

Mock block passability/liquid/support for each deterministic candidate. Prove top-of-anchor preference, fallback order, rejection of solid feet/head, liquid, missing support, unavailable world, and no world mutation.

- [ ] **Step 2: Implement safe landing and verify**

Use block centers (`x + 0.5`, `z + 0.5`) and preserve a neutral yaw/pitch. Never call `setType`, `breakNaturally`, or place temporary support.

```bash
./gradlew :paper:test --tests com.azoth.territory.building.SafeLandingResolverTest
```

Expected: pass.

- [ ] **Step 3: Write travel-state tests**

Use a fake scheduler/clock boundary rather than sleeping. Cover every `StartResult`, warm-up pending state, movement to another block, damage, death, quit, authorization loss, origin/destination invalidation, protection recheck, teleport failure, success, cooldown only after success, and `stop()` cancellation.

Critical assertions:

```java
assertEquals(StartResult.STARTED, service.start(player, origin, "south", 1_000L));
service.tick(player.getUniqueId(), 6_000L);
verify(player).teleport(safeDestination);
assertTrue(service.remainingCooldownMillis(player.getUniqueId(), 6_000L) > 0L);
```

For failed teleport, verify cooldown remains zero.

- [ ] **Step 4: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.WaystoneTravelServiceTest
```

Expected: travel types missing.

- [ ] **Step 5: Implement the state machine**

Store one pending travel and cooldown expiry per UUID. `start` validates origin, reachable destination, safe landing, and `BlockProtection.canTeleportInto`. Schedule one delayed Paper task or expose `tick` behind a small scheduler adapter; retain IDs, not mutable Bukkit entities, in pending state. Immediately before teleport, resolve player, origin, destination, access, active state, safe landing, and protection again. Set cooldown only when `player.teleport(...)` returns true.

- [ ] **Step 6: Implement cancellation listener**

Cancel on `PlayerMoveEvent` only when block coordinates/world change; on player damage, death, and quit always cancel. Do not cancel for head rotation. Listener delegates only; state logic stays in the service.

- [ ] **Step 7: Run focused tests and commit**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.SafeLandingResolverTest \
  --tests com.azoth.territory.building.WaystoneTravelServiceTest
```

Expected: pass.

```bash
git add paper/src/main/java/com/azoth/territory/building/SafeLandingResolver.java \
  paper/src/main/java/com/azoth/territory/building/WaystoneTravelService.java \
  paper/src/main/java/com/azoth/territory/building/WaystoneTravelListener.java \
  paper/src/test/java/com/azoth/territory/building/SafeLandingResolverTest.java \
  paper/src/test/java/com/azoth/territory/building/WaystoneTravelServiceTest.java
git commit -m "feat: add safe waystone travel"
```

---

### Task 9: Waystone selection interaction

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/building/BuildingCommand.java`
- Modify: `paper/src/main/java/com/azoth/territory/building/BuildingListener.java`
- Modify: `paper/src/test/java/com/azoth/territory/building/BuildingCommandTest.java`
- Modify: `paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java`
- Create: `paper/src/main/java/com/azoth/territory/building/WaystoneSelections.java`
- Create: `paper/src/test/java/com/azoth/territory/building/WaystoneSelectionsTest.java`

**Interfaces:**
- Adds command: `/territory building travel <destinationId>` valid only after interacting with an origin waystone.
- Placement-session state remains separate from travel-origin state.
- `BuildingListener` records the active origin and sends deterministic clickable Adventure components for reachable destinations.

- [ ] **Step 1: Add failing interaction tests**

Assert active waystone right-click cancels normal interaction, lists only `WaystoneAccess.reachable`, and emits clickable `/territory building travel <id>` components. Assert inactive/wrong-material anchor does not open travel. Assert the travel command rejects no origin, stale/inactive origin, unreachable destination, and reports each `StartResult`.

- [ ] **Step 2: Run and observe failures**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingCommandTest \
  --tests com.azoth.territory.building.BuildingListenerTest \
  --tests com.azoth.territory.building.WaystoneSelectionsTest
```

Expected: travel interaction assertions fail.

- [ ] **Step 3: Implement selection without a new GUI framework**

Create `paper/src/main/java/com/azoth/territory/building/WaystoneSelections.java` with `select(UUID playerId, String originFacilityId, long nowMillis)`, `Optional<String> origin(UUID playerId, long nowMillis)`, and `clear(UUID playerId)`. Give it the same configured expiry duration as placement sessions. Send name/territory plus clickable components. The command resolves the origin by ID and delegates to `WaystoneTravelService.start`.

- [ ] **Step 4: Run and commit**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingCommandTest \
  --tests com.azoth.territory.building.BuildingListenerTest \
  --tests com.azoth.territory.building.WaystoneSelectionsTest \
  --tests com.azoth.territory.building.WaystoneTravelServiceTest
```

Expected: pass.

```bash
git add paper/src/main/java/com/azoth/territory/building/BuildingCommand.java \
  paper/src/main/java/com/azoth/territory/building/BuildingListener.java \
  paper/src/main/java/com/azoth/territory/building/WaystoneSelections.java \
  paper/src/test/java/com/azoth/territory/building/BuildingCommandTest.java \
  paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java \
  paper/src/test/java/com/azoth/territory/building/WaystoneSelectionsTest.java
git commit -m "feat: select destinations at waystone anchors"
```

---

### Task 10: Trading-post integration event

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/building/TradingPostInteractEvent.java`
- Modify: `paper/src/main/java/com/azoth/territory/building/BuildingListener.java`
- Create: `paper/src/test/java/com/azoth/territory/building/TradingPostInteractEventTest.java`
- Modify: `paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java`

**Interfaces:**
- Produces cancellable Bukkit event with `Player player()`, `SettlementFacility facility()`, `Territory territory()`, `Optional<String> governingGuildId()`, static `getHandlerList()`, and instance `getHandlers()`.
- Event fires only for exact, active `TRADING_POST` anchors after ordinary territory interaction permission succeeds.

- [ ] **Step 1: Write event-shape and dispatch tests**

Assert constructor null checks, getters, cancellation contract, handler list, one event per right-click, no event for inactive anchors/non-trading types/neighboring blocks, and resolved current governing guild after territory ownership replacement.

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.TradingPostInteractEventTest \
  --tests com.azoth.territory.building.BuildingListenerTest
```

Expected: event type missing.

- [ ] **Step 3: Implement one Bukkit event seam**

Extend `Event` and implement `Cancellable`; do not add a callback registry. Listener calls `pluginManager.callEvent(event)`. If cancelled, cancel the player interaction and display the integration's cancellation reason only if the event contract includes a nonblank reason; otherwise give a generic unavailable message. If not cancelled, display trading-post name and territory.

- [ ] **Step 4: Run and commit**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.TradingPostInteractEventTest \
  --tests com.azoth.territory.building.BuildingListenerTest
```

Expected: pass.

```bash
git add paper/src/main/java/com/azoth/territory/building/TradingPostInteractEvent.java \
  paper/src/main/java/com/azoth/territory/building/BuildingListener.java \
  paper/src/test/java/com/azoth/territory/building/TradingPostInteractEventTest.java \
  paper/src/test/java/com/azoth/territory/building/BuildingListenerTest.java
git commit -m "feat: expose trading post interaction event"
```

---

### Task 11: Plugin lifecycle wiring

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Create: `paper/src/test/java/com/azoth/territory/building/BuildingLifecycleWiringTest.java`
- Modify: `paper/src/test/java/com/azoth/territory/PluginEconomyWiringTest.java`

**Interfaces:**
- Produces getters for `FacilityRegistry`, `FacilityMutationService`, `BuildingCommand`, and `WaystoneTravelService` required by command/tests/integrators.
- Building startup occurs after territories, facilities, guilds, governance, and block protection are ready, but before root command registration.
- `onDisable` calls `waystoneTravelService.stop()` before database close.

- [ ] **Step 1: Write lifecycle wiring tests**

Use reflection/wiring conventions from `PluginEconomyWiringTest`. Assert construction order dependencies, listener registration, command availability, getter types, invalid config fail-closed behavior, and stop-before-database-close ordering through mocks or extracted `startBuildings`/`stopBuildings` methods.

- [ ] **Step 2: Run and observe failure**

```bash
./gradlew :paper:test --tests com.azoth.territory.building.BuildingLifecycleWiringTest \
  --tests com.azoth.territory.PluginEconomyWiringTest
```

Expected: missing building lifecycle/getters.

- [ ] **Step 3: Wire composition root**

After `blockProtection` exists, parse config and construct validator, authorization from `guilds.getGuildService()/getPermissionService()`, mutation service, placement sessions, reachability, safe landing, travel, command, and listeners. Register both listeners. On configuration error log `SEVERE`, leave building fields null, and keep the rest of the plugin enabled.

Expose `getFacilities()` rather than leaking the store. Keep shutdown's snapshot save as a final safety flush; normal mutations are already durable.

- [ ] **Step 4: Run focused wiring and all building tests**

```bash
./gradlew :paper:test --tests 'com.azoth.territory.building.*' \
  --tests com.azoth.territory.PluginEconomyWiringTest
```

Expected: pass.

- [ ] **Step 5: Commit lifecycle wiring**

```bash
git add paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java \
  paper/src/test/java/com/azoth/territory/building/BuildingLifecycleWiringTest.java \
  paper/src/test/java/com/azoth/territory/PluginEconomyWiringTest.java
git commit -m "feat: wire territory building lifecycle"
```

---

### Task 12: Documentation and end-to-end verification

**Files:**
- Modify: `docs/living-specs/economy.md`
- Modify: `docs/living-specs/territory.md`
- Modify: `README.md` only if this repository's player-command documentation belongs there; otherwise update the existing operator command document discovered during execution.

**Interfaces:**
- Documents exact commands, configured anchor materials, RP freedom, active/inactive restoration, same-guild waystone limits, and `TradingPostInteractEvent` integration boundary.

- [ ] **Step 1: Run the complete automated suite before documentation claims**

```bash
./gradlew test
```

Expected: all modules and tests pass. Fix regressions in the atomic commit that introduced them; do not hide failures in this documentation task.

- [ ] **Step 2: Launch the actual Paper development server**

Use the repository's existing run-server task discovered from `build.gradle.kts` (expected `./gradlew :paper:runServer` or the configured equivalent) through the harness process manager. Connect it to the configured development database. Wait for `AzothTerritory` enable and building-subsystem-ready log output.

- [ ] **Step 3: Exercise the free-form building scenario**

On the server:

```text
/territory building create waystone north North Waystone
# right-click a lodestone inside a governed territory
/territory building create waystone south South Waystone
# right-click a second lodestone in territory governed by the same guild
/territory building list
```

Build unrelated blocks around both anchors, break and replace neighboring blocks, then run list/info and right-click again. Expected: both remain active; only changes to the exact lodestone matter.

Right-click the north waystone, select south, move during warm-up, and verify cancellation. Retry without movement and verify teleport plus cooldown. Replace the south lodestone with a disallowed block and verify inactive; restore lodestone and verify active without re-registration.

Register a `TRADING_POST` on a configured bell/lectern inside a bank or market shell. Interact and verify the displayed facility identity and emitted event in the smoke listener/log probe.

- [ ] **Step 4: Verify persistence and failure semantics**

Restart the server and confirm both facility records reload. Run the dedicated failure test again:

```bash
./gradlew :paper:test --tests com.azoth.territory.building.FacilityMutationServiceTest
```

Expected: pass, proving failed save leaves live state unchanged. Runtime database sabotage is not required because the integration test deterministically covers this boundary.

- [ ] **Step 5: Update authoritative living specs and command docs**

In `territory.md`, add checked Current capabilities for anchor registration/lifecycle, waystone travel, and trading-post interaction. State explicitly that neighboring structures are unconstrained presentation. In `economy.md`, retain facilities as metadata and document that `TradingPostInteractEvent` is the behavior/integration seam, not a marketplace. Document all `/territory building` commands and exact default config.

- [ ] **Step 6: Run final verification after documentation changes**

```bash
./gradlew test
```

Expected: all tests pass. Also verify the server smoke log contains no building exceptions and stop the server cleanly.

- [ ] **Step 7: Commit verified documentation**

```bash
git add docs/living-specs/economy.md docs/living-specs/territory.md README.md
git commit -m "docs: document territory anchor buildings"
```

If command documentation lives elsewhere, stage that exact file instead of `README.md`. Do not stage unrelated user changes.

---

## Completion Gate

Before claiming the building system complete, verify all of the following against current repository/runtime evidence:

- `./gradlew test` passes after all changes.
- Facility mutation failure tests prove live-state isolation.
- Paper startup loads existing `STORAGE`/`TRADING_POST` records plus `WAYSTONE` records.
- Command-then-click creates and durably removes anchors.
- Neighboring construction never affects active state.
- Missing/wrong anchor is inactive and restoration reactivates it.
- Same-guild waystone travel enforces access, safe landing, warm-up cancellation, revalidation, protection, and post-success cooldown.
- Trading-post right-click emits exactly one cancellable event and does not invent marketplace behavior.
- Living specs and command/config documentation match the behavior actually exercised.
- Every implementation commit contains one logical behavior plus its tests; unrelated pre-existing worktree files remain untouched.
