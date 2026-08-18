# Guild-Owned Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared guild-owned virtual item bank with guild-only, facility-bound access and PostgreSQL persistence, while leaving item-instance semantics to the items layer.

**Architecture:** Keep `SettlementFacility` and `FacilityRegistry` as location metadata only. Add a separate concrete PostgreSQL guild-storage store and a Paper-facing service that resolves a storage facility to its governing guild, authorizes the resident/rank, and coordinates opaque item payloads supplied by an items-layer codec. The existing `/town` Brigadier command gains a `storage` subcommand that opens a virtual inventory only at an allowed storage facility.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Paper 1.21.4 API, PostgreSQL, HikariCP, Gson JSONB payloads, JUnit 5, Mockito 5.14.2.

## Global Constraints

- PostgreSQL is mandatory; use the existing shared `PostgresDatabase` pool and never add SQLite, JSON-file, or in-memory durable fallbacks.
- `SettlementFacility` remains location metadata; do not add inventory, payload, or access fields to it.
- Do not create physical chest/trophy storage, housing storage, or block inventories.
- The bank is guild-owned and guild-only: alliance membership, public guild status, and non-resident access do not grant access.
- Default configurable thresholds are resident/member deposit, assistant/officer withdraw, and mayor/leader tab-management.
- Storage is available only at a registered `FacilityType.STORAGE` facility whose territory resolves to the same guild.
- Territory stores item payloads opaquely; the items layer owns item identity, serialization, gear score, perks, sockets, bind state, durability, weight, and stack semantics.
- Failed authorization, codec, audit, or database operations fail closed and must not partially mutate inventory.
- Do not implement PvP, death, weight, durability, Mint charging, or item serialization in this plan.
- Every task ends with focused tests before its atomic commit; run the complete Gradle suite after the final task.

---

## File Map

| Responsibility | Files |
|---|---|
| Shared item/API value types | `api/src/main/java/com/guilds/territory/storage/` |
| Shared PostgreSQL schema | `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java` |
| Guild-storage persistence | `common/src/main/java/com/guilds/territory/persist/PostgresGuildStorageStore.java` |
| Facility lifecycle | `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java` |
| Storage authorization/service | `paper/src/main/java/org/aincraft/guilds/services/GuildStorageService.java`, `paper/src/main/java/org/aincraft/guilds/services/impl/GuildStorageServiceImpl.java` |
| Items-layer Bukkit boundary | `paper/src/main/java/org/aincraft/guilds/storage/GuildStorageItemCodec.java` |
| Virtual bank UI | `paper/src/main/java/org/aincraft/guilds/storage/GuildStorageGui.java` |
| Commands/composition | `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildStorageBrigadierCommand.java`, `GuildBrigadierCommand.java`, `GuildsServices.java`, `BrigadierCommandRegistry.java` |
| Tests | `api/src/test/...`, `common/src/test/...`, `paper/src/test/...` matching each production package |

---

### Task 1: Wire settlement facilities into the PostgreSQL lifecycle

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Test: `paper/src/test/java/com/guilds/territory/PluginFacilityWiringTest.java`
- Existing contracts: `api/src/main/java/com/guilds/territory/registry/FacilityRegistry.java`, `common/src/main/java/com/guilds/territory/persist/PostgresFacilityStore.java`

**Interfaces:**
- `GuildsTerritoryPlugin` produces `FacilityRegistry getFacilityRegistry()` and `PostgresFacilityStore getFacilityStore()`.
- Startup loads facilities after territories and before storage services are registered.
- Shutdown saves facilities before closing `PostgresDatabase`.

- [ ] **Step 1: Write the lifecycle test.** Follow the existing `PluginEconomyWiringTest` registry/facade pattern; assert the facility directory remains a separate location-only registry and exercise a registered `FacilityType.STORAGE` record through the store seam without changing `SettlementFacility`.

```java
@Test
void facilityRegistryIsASeparateLocationDirectory() {
    TerritoryRegistry territories = new TerritoryRegistry(List.of(testTerritory("guild-territory")));
    FacilityRegistry facilities = new FacilityRegistry(territories);
    SettlementFacility storage = new SettlementFacility(
            "guild-storage", "Guild Storage", "guild-territory",
            FacilityType.STORAGE, "world", 5, 64, 5);

    facilities.register(storage);

    assertEquals(Optional.of(storage), facilities.resolve("world", 5, 64, 5));
    assertEquals(FacilityType.STORAGE, facilities.get("guild-storage").orElseThrow().type());
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure.**

Run: `./gradlew :paper:test --tests com.guilds.territory.PluginFacilityWiringTest`

Expected: failure until the plugin exposes and loads the facility directory.

- [ ] **Step 3: Add lifecycle fields and wiring.**

Add fields:

```java
private FacilityRegistry facilityRegistry;
private PostgresFacilityStore facilityStore;
```

In `onEnable`, construct `facilityRegistry = new FacilityRegistry(registry)` before loading facilities, construct `facilityStore = new PostgresFacilityStore(database)`, then call `facilityStore.loadInto(facilityRegistry)` after `store.loadInto(registry)`. In `onDisable`, call `facilityStore.save(facilityRegistry)` before `store.save(registry)` and before `database.close()`. Pass the registry to `BukkitEconomyBridge` using its existing two-argument constructor so facility resolution and storage share one directory.

- [ ] **Step 4: Run focused and root tests.**

Run: `./gradlew :paper:test --tests com.guilds.territory.PluginFacilityWiringTest`

Expected: PASS. Then run `./gradlew test`; expected existing and new tests PASS.

- [ ] **Step 5: Commit the lifecycle unit.**

```bash
git add paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java paper/src/test/java/com/guilds/territory/PluginFacilityWiringTest.java
git commit -m "Wire settlement facilities into PostgreSQL lifecycle"
```

---

### Task 2: Define opaque storage payload and domain contracts

**Files:**
- Create: `api/src/main/java/com/guilds/territory/storage/OpaqueItemPayload.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageAddress.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageRank.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageOperation.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageStatus.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageResult.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageOpenResult.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageWithdrawResult.java`
- Create: `api/src/main/java/com/guilds/territory/storage/StorageTab.java`
- Create: `api/src/main/java/com/guilds/territory/storage/GuildStoragePolicy.java`
- Create: `api/src/main/java/com/guilds/territory/storage/GuildStorageSnapshot.java`
- Test: `api/src/test/java/com/guilds/territory/storage/StorageContractTest.java`

**Interfaces:**

```java
public record OpaqueItemPayload(
        String schema,
        String payloadJson,
        String fingerprint
) {}

public record StorageAddress(String guildId, String tabId, int slotIndex) {}

public enum StorageRank { MEMBER, ASSISTANT, MAYOR }
public enum StorageOperation { OPEN, DEPOSIT, WITHDRAW, MANAGE }

public record StorageTab(
        String id,
        String displayName,
        int ordinal,
        int capacitySlots,
        boolean unlocked
) {}

public record GuildStoragePolicy(
        StorageRank depositRank,
        StorageRank withdrawRank,
        StorageRank manageRank
) {
    public static GuildStoragePolicy defaults() {
        return new GuildStoragePolicy(
                StorageRank.MEMBER,
                StorageRank.ASSISTANT,
                StorageRank.MAYOR);
    }
}

public record GuildStorageSnapshot(
        String guildId,
        List<StorageTab> tabs,
        Map<StorageAddress, OpaqueItemPayload> occupiedSlots,
        GuildStoragePolicy policy
) {}

public record StorageOpenResult(
        StorageStatus status,
        String message,
        Optional<GuildStorageSnapshot> snapshot
) {}

public enum StorageStatus {
    SUCCESS, NOT_RESIDENT, WRONG_FACILITY, WRONG_GUILD,
    INSUFFICIENT_RANK, INVALID_ITEM, CONFLICT, STORAGE_ERROR
}

public record StorageResult(StorageStatus status, String message) {}

public record StorageWithdrawResult(
        StorageStatus status,
        String message,
        Optional<OpaqueItemPayload> payload
) {}
```

- [ ] **Step 1: Write validation tests.** Reject blank schema/payload/fingerprint values where required, negative slot indexes, non-positive tab capacity, duplicate tab ids, and a policy whose rank values are null. Confirm `defaults()` returns member/assistant/mayor thresholds.

- [ ] **Step 2: Run the focused API test.**

Run: `./gradlew :api:test --tests com.guilds.territory.storage.StorageContractTest`

Expected: compilation or assertion failure before the value types exist.

- [ ] **Step 3: Implement immutable validated records/enums.** Trim textual ids, reject invalid slot/capacity values, copy list/map inputs defensively, and expose only immutable snapshots. Do not add Bukkit or items-module dependencies to `api`.

- [ ] **Step 4: Run focused and root tests.**

Run: `./gradlew :api:test --tests com.guilds.territory.storage.StorageContractTest` and `./gradlew test`.

Expected: PASS.

- [ ] **Step 5: Commit the contract unit.**

```bash
git add api/src/main/java/com/guilds/territory/storage api/src/test/java/com/guilds/territory/storage
git commit -m "Define opaque guild storage contracts"
```

---

### Task 3: Add idempotent PostgreSQL guild-storage schema and store

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java:17-23`
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresGuildStorageStore.java`
- Test: `common/src/test/java/com/guilds/territory/persist/PostgresGuildStorageStoreTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/PostgresDatabaseTest.java`

**Interfaces:**

```java
public final class PostgresGuildStorageStore {
    public PostgresGuildStorageStore(
            PostgresDatabase database,
            int initialCapacitySlots,
            int expansionTabCapacitySlots);
    public GuildStorageSnapshot ensureBank(String guildId) throws IOException;
    public GuildStorageSnapshot load(String guildId) throws IOException;
    public StorageResult setPolicy(String guildId, GuildStoragePolicy policy,
                                   UUID actorUuid, String facilityId) throws IOException;
    public StorageResult put(String guildId, StorageAddress address, OpaqueItemPayload payload,
                             UUID actorUuid, String facilityId) throws IOException;
    public StorageWithdrawResult remove(String guildId, StorageAddress address,
                                        UUID actorUuid, String facilityId) throws IOException;
    public StorageResult unlockTab(String guildId, String tabId, String displayName,
                                  int ordinal, int capacitySlots,
                                  UUID actorUuid, String facilityId) throws IOException;
}
```

- [ ] **Step 1: Add PostgreSQL integration tests guarded by `GUILDS_TEST_JDBC_URL`.** Cover fresh schema creation twice, default bank creation, opaque payload plus fingerprint round trip, policy persistence, tab unlock, audit insertion, rollback on duplicate/invalid slot, and concurrent occupied-slot conflict. Tests use the existing `PostgresTestDatabase`/`assumeTrue` conventions; they must not silently substitute H2 or SQLite.

- [ ] **Step 2: Run the focused store tests before implementation.**

Run: `./gradlew :common:test --tests com.guilds.territory.persist.PostgresGuildStorageStoreTest`

Expected: compile failure because the store/schema do not exist; with no `GUILDS_TEST_JDBC_URL`, integration tests are explicitly skipped rather than falsely passing.

- [ ] **Step 3: Add the exact idempotent schema.** Extend `PostgresDatabase.COMMON_SCHEMA` with `guild_storage_banks`, `guild_storage_tabs`, `guild_storage_slots` (including `item_fingerprint`), `guild_storage_policies`, and `guild_storage_audit` from the design spec. Add foreign keys from tabs/policies to banks and slots to tabs. Keep the `facilities` table unchanged.

- [ ] **Step 4: Implement transactional store operations.** Use `database.connection()`, `SELECT ... FOR UPDATE` for bank/tab/slot mutations, and one transaction for item mutation plus audit row. Store `OpaqueItemPayload.payloadJson` as `JSONB` with its schema/fingerprint metadata preserved for round trip; validate schema/fingerprint before SQL without inspecting item semantics. Return `CONFLICT` from `put` when the locked destination slot is already occupied, return `INVALID_ITEM` for payload/address validation failures, and map committed mutations to `SUCCESS`; pessimistic row locking serializes concurrent attempts, so tests assert the occupied-slot conflict contract. Use `ON CONFLICT` only for explicitly idempotent bank/policy/tab operations. Convert SQL failures to `IOException`; never return a partially updated snapshot.

- [ ] **Step 5: Run focused and root persistence tests.**

Run: `GUILDS_TEST_JDBC_URL=... ./gradlew :common:test --tests com.guilds.territory.persist.PostgresGuildStorageStoreTest` when a disposable PostgreSQL URL is available, then `./gradlew test`.

Expected: PASS, with the no-environment path reporting skipped integration tests and the configured path exercising real JSONB/transaction behavior.

- [ ] **Step 6: Commit the persistence unit.**

```bash
git add common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java common/src/main/java/com/guilds/territory/persist/PostgresGuildStorageStore.java common/src/test/java/com/guilds/territory/persist/PostgresGuildStorageStoreTest.java common/src/test/java/com/guilds/territory/persist/PostgresDatabaseTest.java
git commit -m "Persist guild storage in PostgreSQL"
```

---

### Task 4: Implement facility-bound guild authorization and service facade

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/services/GuildStorageService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildStorageServiceImpl.java`
- Test: `paper/src/test/java/org/aincraft/guilds/services/impl/GuildStorageServiceImplTest.java`

**Interfaces:**

```java
public interface GuildStorageService {
    StorageOpenResult open(UUID actor, String world, int blockX, int blockY, int blockZ);
    StorageResult deposit(UUID actor, StorageAddress address, OpaqueItemPayload payload,
                          String facilityId, String world, int blockX, int blockY, int blockZ);
    StorageWithdrawResult withdraw(UUID actor, StorageAddress address,
                                   String facilityId, String world, int blockX, int blockY, int blockZ);
    StorageResult setPolicy(UUID actor, String guildId, GuildStoragePolicy policy,
                            String facilityId, String world, int blockX, int blockY, int blockZ);
    StorageResult unlockTab(UUID actor, String guildId, String tabId, String displayName,
                            int ordinal, int capacitySlots, String facilityId,
                            String world, int blockX, int blockY, int blockZ);
}
```

`StorageOpenResult`, `StorageResult`, and `StorageWithdrawResult` must expose stable statuses (`SUCCESS`, `NOT_RESIDENT`, `WRONG_FACILITY`, `WRONG_GUILD`, `INSUFFICIENT_RANK`, `INVALID_ITEM`, `CONFLICT`, `STORAGE_ERROR`) and user-safe messages; the open/withdraw wrappers carry an optional snapshot/payload only on `SUCCESS`.

- [ ] **Step 1: Write service tests first.** Use Mockito for `GuildService`, `FacilityRegistry`, `GovernanceRegistry`, and `PostgresGuildStorageStore`. Cover:
  - resident at matching `FacilityType.STORAGE` can open/deposit;
  - non-resident and alliance member are denied;
  - a `TRADING_POST` facility is denied;
  - facility territory resolving to another guild is denied;
  - exact world/block mismatch is denied;
  - member cannot withdraw/manage by default;
  - assistant can withdraw but cannot manage;
  - mayor can manage;
  - changed policy thresholds are honored;
  - store failure returns `STORAGE_ERROR` and does not retry or mutate.

- [ ] **Step 2: Run the focused service test and observe the missing contract failure.**

Run: `./gradlew :paper:test --tests org.aincraft.guilds.services.impl.GuildStorageServiceImplTest`

Expected: compile failure until the service/result types exist.

- [ ] **Step 3: Implement authorization without duplicating governance.** Resolve the facility with `FacilityRegistry.resolve(world, blockX, blockY, blockZ)`, require `FacilityType.STORAGE`, then call `GovernanceRegistry.governingGuildForTerritory(facility.territoryId())` and use the returned `GuildBody.id()`. Find the actor's resident guild by iterating `GuildService.getAllGuilds()` and calling `Guild.isResident(actor)`; compare that guild id with the facility's governing guild id. Map the current `Guild` model to `StorageRank`: mayor UUID → `MAYOR`, assistant UUID → `ASSISTANT`, other residents → `MEMBER`, everyone else denied.

Do not call `BlockProtection` directly for a second access rule; the facility resolver and governance service are the authority for storage access. The service delegates persistence to `PostgresGuildStorageStore` and preserves its status/error contract.

- [ ] **Step 4: Run focused and root Paper tests.**

Run: `./gradlew :paper:test --tests org.aincraft.guilds.services.impl.GuildStorageServiceImplTest` and `./gradlew test`.

Expected: PASS.

- [ ] **Step 5: Commit the authorization unit.**

```bash
git add paper/src/main/java/org/aincraft/guilds/services/GuildStorageService.java paper/src/main/java/org/aincraft/guilds/services/impl/GuildStorageServiceImpl.java paper/src/test/java/org/aincraft/guilds/services/impl/GuildStorageServiceImplTest.java
git commit -m "Enforce guild storage facility access"
```

---

### Task 5: Add the items-layer codec boundary and virtual bank UI

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/storage/GuildStorageItemCodec.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/GuildStorageGui.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/GuildStorageListener.java`
- Test: `paper/src/test/java/org/aincraft/guilds/storage/GuildStorageGuiTest.java`

**Interfaces:**

```java
public interface GuildStorageItemCodec {
    String schemaId();
    OpaqueItemPayload encode(org.bukkit.inventory.ItemStack item);
    org.bukkit.inventory.ItemStack decode(OpaqueItemPayload payload);
}
```

Register the codec through Bukkit's services manager or inject it through the composition root. If no compatible codec is registered, the GUI open path returns a `StorageOpenResult` with `STORAGE_ERROR` and a visible unavailable message; it must not invoke item mutations or use a vanilla `ItemStack` serialization fallback.

- [ ] **Step 1: Write GUI tests with a fake codec.** Cover opening one general tab with empty slots, rendering an opaque payload through the codec, rejecting a click from a non-owner inventory, and closing without mutation. Test that an absent codec produces a visible unavailable message rather than a fake or lossy inventory.

- [ ] **Step 2: Run the focused GUI test before implementation.**

Run: `./gradlew :paper:test --tests org.aincraft.guilds.storage.GuildStorageGuiTest`

Expected: compile failure until the codec/GUI/listener classes exist.

- [ ] **Step 3: Implement the virtual GUI.** Use a Bukkit virtual inventory only; never place or inspect a physical chest. Render `GuildStorageSnapshot` slots through `GuildStorageItemCodec.decode`, route deposits through `encode`, and route every click/close mutation through `GuildStorageService`. Keep the currently opened `StorageAddress` and facility coordinates in a private holder/session object; reject stale sessions and clicks from other inventories.

- [ ] **Step 4: Add item-transfer safety.** Cancel unsupported shift-click/drag paths until they can be represented as explicit deposit/withdraw transactions. On any failed transaction, restore the pre-click inventory state and send the stable service message. Do not clear the player inventory or bank slot on codec failure.

- [ ] **Step 5: Run focused and root Paper tests.**

Run: `./gradlew :paper:test --tests org.aincraft.guilds.storage.GuildStorageGuiTest` and `./gradlew test`.

Expected: PASS.

- [ ] **Step 6: Commit the UI/codec unit.**

```bash
git add paper/src/main/java/org/aincraft/guilds/storage paper/src/test/java/org/aincraft/guilds/storage
git commit -m "Add virtual guild storage UI boundary"
```

---

### Task 6: Wire `/town storage`, policy commands, and lifecycle registration

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildStorageBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Test: `paper/src/test/java/com/guilds/territory/GuildStorageWiringTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/commands/GuildStorageCommandTest.java`
- Modify: `paper/src/main/resources/guilds-config.yml` only for storage defaults and permission messages

**Interfaces:**

```java
public void registerGuildStorage(
        FacilityRegistry facilities,
        PostgresGuildStorageStore store,
        GovernanceRegistry governance,
        GuildStorageItemCodec codec
);
```

`GuildsServices.registerGuildStorage` is idempotent and must be called by
`GuildsTerritoryPlugin` after the facility registry, PostgreSQL stores, guilds
services, and governance registry all exist, but before `GuildsServices.enable()`
registers Brigadier commands and listeners. This follows the existing deferred
`registerHearthstone(BlockProtection)` lifecycle pattern.

- [ ] **Step 1: Write wiring/command tests.** Verify the command tree contains `/town storage` and the existing `t` alias, storage registration is idempotent, the guild storage listener is registered once, and command execution denies a player outside a facility without touching the store.

- [ ] **Step 2: Run focused wiring tests before implementation.**

Run: `./gradlew :paper:test --tests com.guilds.territory.GuildStorageWiringTest --tests org.aincraft.guilds.commands.GuildStorageCommandTest`

Expected: compile failure until the command and registration seam exists.

- [ ] **Step 3: Add the command node.** Add a `storage` child to the existing literal `town` command (registered by `GuildBrigadierCommand` and reachable through alias `t`). The command obtains the executing `Player`, converts the current block location to the service location, and opens the GUI. Add a `policy` child with `deposit`, `withdraw`, and `manage` rank arguments; require the service to authorize management before updating the policy.

- [ ] **Step 4: Wire deferred composition.** Add nullable/lazy storage fields to `GuildsServices`, construct the command with a supplier or late-bound service reference, and register `GuildStorageListener` in `registerListeners()` only after `registerGuildStorage` has run. In `GuildsTerritoryPlugin.onEnable`, read the configured initial and expansion capacities, construct `PostgresGuildStorageStore(database, initialCapacity, expansionTabCapacity)`, resolve an optional `GuildStorageItemCodec` from the Bukkit services manager, and call `guilds.registerGuildStorage(facilityRegistry, storageStore, governance, codec)` after `BlockProtection`/governance setup and before `enableGuildsSubsystem()`. A missing codec is passed as `null` and fails closed.

- [ ] **Step 5: Add defaults and user-facing messages.** Add `guild-storage.initial-capacity: 54`, `guild-storage.expansion-tab-capacity: 54`, and stable denial/error messages to `guilds-config.yml`. Keep rank thresholds in PostgreSQL policy rows; config values are defaults for newly created banks, not a second source of truth.

- [ ] **Step 6: Run focused and root tests.**

Run: `./gradlew :paper:test --tests com.guilds.territory.GuildStorageWiringTest --tests org.aincraft.guilds.commands.GuildStorageCommandTest`, then `./gradlew test`.

Expected: PASS.

- [ ] **Step 7: Commit the wiring unit.**

```bash
git add paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildStorageBrigadierCommand.java paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java paper/src/main/java/org/aincraft/guilds/GuildsServices.java paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java paper/src/main/resources/guilds-config.yml paper/src/test/java/com/guilds/territory/GuildStorageWiringTest.java paper/src/test/java/org/aincraft/guilds/commands/GuildStorageCommandTest.java
git commit -m "Wire guild storage command and lifecycle"
```

---

## Final Verification

- [ ] Run `./gradlew test` with no PostgreSQL URL; deterministic unit tests pass and integration tests explicitly skip.
- [ ] Run `GUILDS_TEST_JDBC_URL=<disposable-postgres> ./gradlew test`; schema/bootstrap, round-trip, rollback, and restart tests pass against PostgreSQL.
- [ ] Run `./gradlew build`; compilation, tests, quality gates, and shadow artifact complete.
- [ ] Confirm `git diff --check` is clean and `git status --short` contains no unintended files.
- [ ] Confirm no changes were made to `SettlementFacility` inventory semantics, physical chest/trophy behavior, `guilds` combat/death logic, or `items` item rules.
