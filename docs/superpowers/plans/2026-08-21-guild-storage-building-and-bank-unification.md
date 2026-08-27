# Guild Storage Building & Item Bank Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a complete, settlement-bound Guild Item Bank accessible at physical `FacilityType.STORAGE` anchors in governed territory, implement the SQL item storage aggregate (`OpaqueItemPayload`) for PostgreSQL and MySQL, provide a virtual GUI with role-gated transactional deposit/withdraw, and clean up unanchored/dead legacy paths via strict reference-checked deletion gates.

**Architecture:** Configure `FacilityType.STORAGE` in the territory building pipeline with configurable anchor materials (`BARREL`, `CHEST`). Build a durable SQL item storage aggregate (`guild_storage_banks`, `guild_storage_tabs`, `guild_storage_slots`, `guild_storage_policies`, `guild_storage_audit`) supporting PostgreSQL and MySQL with Paper `ItemStack` $\leftrightarrow$ `OpaqueItemPayload` serialization, optimistic slot versioning, and atomic inventory compensation. Route interactions through `BuildingListener` $\to$ `FacilityAnchorValidator.activeAt()` and `/town storage` / `/guild storage` (which strictly require physical presence at an active anchor), requiring players to be residents of the governing guild in governed territory before opening the `GuildStorageGUI`. Retain the Mint cash bank as an asynchronous currency service for tax settlement and treasury operations, while removing the unanchored scoreboard-tag villager listener and deprecating inert plot metadata.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper 26.2, JUnit 5, Mockito, Adventure Components, PostgreSQL 16+ (default) / MySQL 8.0 (selectable), HikariCP.

## Global Constraints

- `FacilityType.STORAGE` is the canonical physical settlement building for guild item storage; opening storage requires containment in governed territory, active anchor status, and governing guild residency.
- Remote command access to storage is strictly prohibited: `/town storage` (and `/guild storage`) requires the player to be standing at an active `STORAGE` anchor block of their governing guild.
- PostgreSQL is the default SQL backend and MySQL is selectable; no SQLite or JSON file fallback is permitted.
- Territory/storage services in `api` and `common` must store item payloads opaquely (`OpaqueItemPayload`) without inspecting item mechanics; Paper-specific `ItemStack` serialization lives in `paper`.
- Item transfers between player inventory and SQL storage must be strictly atomic: failures or disconnects must roll back or compensate to prevent item duplication or deletion.
- Inactive or damaged anchors must deny access in-game while preserving stored item payloads in SQL.
- Mint cash bank (`MintGuildBankService`, `MintEconomyRail`, `MintGuildTaxSettlement`) remains an independent currency service for tax settlement and coin transactions.
- Zero-reference deletion gates must be verified before removing deprecated or dead components.
- Every task must follow Test-Driven Development (TDD): write failing tests first, verify red, implement minimal code, verify green, and commit atomically.

---

### File Structure & Responsibilities

| File Path | Responsibility |
| :--- | :--- |
| `common/src/main/resources/sql/migrations/guilds/V24__guild-storage.sql` | PostgreSQL & MySQL schema migration for banks, tabs, slots, policies, and audit. |
| `common/src/main/resources/sql/migrations/guilds/manifest` | Registers migration `24 guild-storage`. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/OpaqueItemPayload.java` | Paper-free value record for `(schema, fingerprint, payload)`. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/StorageAddress.java` | Target identifier `(guildId, tabId, slotIndex)`. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/GuildStorageBank.java` | Domain record representing bank metadata and schema version. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/StorageTab.java` | Domain record for tab ordinal, display name, and capacity. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/StorageSlot.java` | Domain record for slot index, item payload, fingerprint, and version. |
| `api/src/main/java/org/aincraft/guilds/territory/storage/StoragePolicy.java` | Role thresholds for `DEPOSIT`, `WITHDRAW`, and `MANAGE`. |
| `paper/src/main/java/org/aincraft/guilds/storage/codec/ItemStackStorageCodec.java` | Paper `ItemStack` $\leftrightarrow$ `OpaqueItemPayload` serializer with sha256 fingerprinting. |
| `paper/src/main/java/org/aincraft/guilds/storage/persist/SqlGuildStorageStore.java` | Transactional SQL store for bank creation, slot loading, optimistic updates, and audit logging. |
| `paper/src/main/java/org/aincraft/guilds/storage/service/GuildStorageService.java` | Service interface for storage operations (`open`, `deposit`, `withdraw`, `expand`). |
| `paper/src/main/java/org/aincraft/guilds/storage/service/impl/GuildStorageServiceImpl.java` | Implementation with optimistic locking, membership validation, and audit recording. |
| `paper/src/main/java/org/aincraft/guilds/storage/gui/GuildStorageGUI.java` | Virtual inventory holder rendering tabs, items, and role-gated click handling. |
| `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfigLoader.java` | Loads anchor materials for `WAYSTONE`, `TRADING_POST`, and `STORAGE`. |
| `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingCommand.java` | Handles `/territory building create/list/info/remove` including `storage`. |
| `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java` | Intercepts block interactions on active anchors (`WAYSTONE`, `TRADING_POST`, `STORAGE`). |
| `paper/src/main/java/org/aincraft/guilds/territory/building/StorageFacilityInteractEvent.java` | Event emitted on valid storage anchor interaction. |
| `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java` | Commands including `/town storage` and `/guild storage` requiring physical anchor presence. |
| `paper/src/main/java/org/aincraft/guilds/GuildsServices.java` | Composition root wiring storage service, GUI listeners, commands, and cleaning up old listeners. |

---

### Task 1: Lock Down Current State and Failure Boundaries with Regression Tests

**Files:**
- Create: `paper/src/test/java/org/aincraft/guilds/storage/StorageRegressionTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/services/MintGuildBankServiceTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/territory/building/BuildingConfigLoaderTest.java`

**Interfaces:**
- Consumes: Current `BuildingConfig`, `MintGuildBankService`, `GuildBankVillagerListener`.
- Produces: Regression tests documenting:
  1. `STORAGE` is currently unconfigured in `BuildingConfig`.
  2. `GuildBankVillagerListener` opens Mint account without location checks.
  3. `/guild bank` commands execute without physical facility anchors.
  4. Stable operation keys idempotently report single transfers on Mint retry.

- [ ] **Step 1: Write failing regression tests**

In `StorageRegressionTest.java`:
```java
package org.aincraft.guilds.storage;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.building.BuildingConfig;
import org.aincraft.guilds.territory.building.BuildingConfigLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRegressionTest {
    @Test
    void storageIsSupportedWhenConfigured() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buildings.storage.anchor-materials", java.util.List.of("BARREL", "CHEST"));
        BuildingConfig config = BuildingConfigLoader.from(yaml);
        assertTrue(config.supports(FacilityType.STORAGE), "STORAGE facility must be supported");
    }
}
```

- [ ] **Step 2: Run tests to verify initial failure**

Run:
```bash
./gradlew --no-daemon :paper:test --tests 'org.aincraft.guilds.storage.StorageRegressionTest'
```
Expected: FAIL.

- [ ] **Step 3: Commit initial test harness**

```bash
git add paper/src/test/java/org/aincraft/guilds/storage/StorageRegressionTest.java
git commit -m "test: add storage and bank regression tests"
```

---

### Task 2: Implement SQL Schema Migration (PostgreSQL & MySQL) and Durable Store

**Files:**
- Create: `common/src/main/resources/sql/migrations/guilds/V24__guild-storage.sql`
- Modify: `common/src/main/resources/sql/migrations/guilds/manifest`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/OpaqueItemPayload.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/StorageAddress.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/GuildStorageBank.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/StorageTab.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/StorageSlot.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/storage/StoragePolicy.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/persist/SqlGuildStorageStore.java`
- Create: `paper/src/test/java/org/aincraft/guilds/storage/persist/SqlGuildStorageStoreTest.java`

**Interfaces:**
- Produces: `SqlGuildStorageStore` with `getOrCreateBank(guildId)`, `loadSlots(guildId, tabId)`, `saveSlot(guildId, tabId, slotIndex, OpaqueItemPayload, expectedVersion)`, and `recordAudit(...)`.

- [ ] **Step 1: Write `V24__guild-storage.sql` migration and update manifest**

In `common/src/main/resources/sql/migrations/guilds/manifest`:
Add: `24 guild-storage Add guild storage item bank tables`

In `common/src/main/resources/sql/migrations/guilds/V24__guild-storage.sql`:
```sql
CREATE TABLE IF NOT EXISTS guild_storage_banks (
    guild_id TEXT PRIMARY KEY,
    schema_version INT NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_tabs (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    ordinal INT NOT NULL,
    capacity_slots INT NOT NULL DEFAULT 54,
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (guild_id, tab_id),
    UNIQUE (guild_id, ordinal),
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_slots (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INT NOT NULL,
    item_schema TEXT NOT NULL,
    item_fingerprint TEXT NOT NULL,
    item_payload TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, tab_id, slot_index),
    FOREIGN KEY (guild_id, tab_id) REFERENCES guild_storage_tabs(guild_id, tab_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_policies (
    guild_id TEXT PRIMARY KEY,
    deposit_role TEXT NOT NULL DEFAULT 'MEMBER',
    withdraw_role TEXT NOT NULL DEFAULT 'ASSISTANT',
    manage_role TEXT NOT NULL DEFAULT 'MAYOR',
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_audit (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    actor_uuid TEXT NOT NULL,
    operation TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INT,
    item_fingerprint TEXT,
    facility_id TEXT NOT NULL,
    created_at TEXT NOT NULL
);
-- +index idx_storage_slots_tab guild_storage_slots (guild_id, tab_id)
-- +index idx_storage_audit_guild guild_storage_audit (guild_id)
```

- [ ] **Step 2: Write failing store tests**

In `SqlGuildStorageStoreTest.java`:
Assert:
- `getOrCreateBank` creates default bank, `general` tab (54 slots), and default policy.
- `saveSlot` with mismatched `expectedVersion` throws optimistic lock exception.
- `loadSlots` round-trips persisted `OpaqueItemPayload` records.

- [ ] **Step 3: Implement domain models and `SqlGuildStorageStore`**

Implement store with parameterized JDBC queries, transactional rollback on error, and optimistic slot concurrency.

- [ ] **Step 4: Run store tests**

Run:
```bash
./gradlew --no-daemon :paper:test --tests 'org.aincraft.guilds.storage.persist.SqlGuildStorageStoreTest'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/resources/sql/migrations/guilds/ \
  api/src/main/java/org/aincraft/guilds/territory/storage/ \
  paper/src/main/java/org/aincraft/guilds/storage/persist/ \
  paper/src/test/java/org/aincraft/guilds/storage/persist/
git commit -m "feat: implement sql guild storage store and migration"
```

---

### Task 3: Implement Paper Item Codec and Transactional Storage Service

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/storage/codec/ItemStackStorageCodec.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/service/GuildStorageService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/service/impl/GuildStorageServiceImpl.java`
- Create: `paper/src/test/java/org/aincraft/guilds/storage/codec/ItemStackStorageCodecTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/storage/service/GuildStorageServiceTest.java`

**Interfaces:**
- Produces:
  - `ItemStackStorageCodec.encode(ItemStack)` $\to$ `OpaqueItemPayload`
  - `ItemStackStorageCodec.decode(OpaqueItemPayload)` $\to$ `ItemStack`
  - `GuildStorageService.deposit(UUID actor, String guildId, String tabId, int slot, OpaqueItemPayload item, String facilityId)`
  - `GuildStorageService.withdraw(UUID actor, String guildId, String tabId, int slot, String facilityId)`

- [ ] **Step 1: Write failing codec and service tests**

In `ItemStackStorageCodecTest.java`:
- Test round-trip serialization of standard items, enchanted tools, custom display names, and NBT metadata.
- Test SHA-256 fingerprint determinism.

In `GuildStorageServiceTest.java`:
- Test deposit by valid member succeeds and records audit.
- Test deposit/withdraw by unauthorized role is rejected with `PERMISSION_DENIED`.
- Test atomic rollback when inventory cannot fit withdrawn item.

- [ ] **Step 2: Implement `ItemStackStorageCodec`**

Use Paper `ItemStack#serializeAsBytes()` / `ItemStack#deserializeBytes()` with Base64 encoding and SHA-256 fingerprinting.

- [ ] **Step 3: Implement `GuildStorageServiceImpl`**

Connect `SqlGuildStorageStore`, `ResidentService`, `GuildService`, and `PermissionService` to enforce role gates (`DEPOSIT`, `WITHDRAW`, `MANAGE`) and atomic slot mutation.

- [ ] **Step 4: Run tests**

Run:
```bash
./gradlew --no-daemon :paper:test --tests 'org.aincraft.guilds.storage.codec.*' --tests 'org.aincraft.guilds.storage.service.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/storage/codec/ \
  paper/src/main/java/org/aincraft/guilds/storage/service/ \
  paper/src/test/java/org/aincraft/guilds/storage/codec/ \
  paper/src/test/java/org/aincraft/guilds/storage/service/
git commit -m "feat: implement item stack codec and transactional guild storage service"
```

---

### Task 4: Configure `STORAGE` Anchors in Building Infrastructure

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfigLoader.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingCommand.java`
- Modify: `paper/src/main/resources/config.yml`
- Test: `paper/src/test/java/org/aincraft/guilds/territory/building/BuildingConfigLoaderTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/territory/building/BuildingCommandTest.java`

**Interfaces:**
- Consumes: `BuildingConfigLoader`, `BuildingCommand`.
- Produces: `BuildingConfig.supports(FacilityType.STORAGE) == true` with anchor materials `BARREL` and `CHEST`. Tab-completion and `/territory building create storage <id>` enabled.

- [ ] **Step 1: Write failing config loader and command tests**

Assert `BuildingConfig.supports(FacilityType.STORAGE)` is true and `BuildingCommand.complete` suggests `storage`.

- [ ] **Step 2: Implement `STORAGE` in `BuildingConfigLoader` and `BuildingCommand`**

In `BuildingConfigLoader.java`:
```java
materials.put(FacilityType.STORAGE, materials(config,
        "buildings.storage.anchor-materials", List.of("BARREL", "CHEST")));
```

In `BuildingCommand.java`:
Add `storage`, `warehouse`, `bank` cases to `parseType()`.

- [ ] **Step 3: Run building tests**

Run:
```bash
./gradlew --no-daemon :paper:test --tests 'org.aincraft.guilds.territory.building.*'
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/territory/building/ \
  paper/src/main/resources/config.yml \
  paper/src/test/java/org/aincraft/guilds/territory/building/
git commit -m "feat: configure storage facility anchors in building infrastructure"
```

---

### Task 5: Implement Virtual Storage GUI & Wire Spatial Gating in `BuildingListener` and Commands

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/storage/gui/GuildStorageGUI.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/building/StorageFacilityInteractEvent.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Test: `paper/src/test/java/org/aincraft/guilds/storage/gui/GuildStorageGUITest.java`

**Interfaces:**
- Produces:
  - `GuildStorageGUI.open(Player player, String guildId, SettlementFacility facility)`: opens a 54-slot chest GUI backed by `GuildStorageService`.
  - `BuildingListener`: on right-click of active `STORAGE` anchor, validates territory containment and governing guild residency, emits `StorageFacilityInteractEvent`, and opens `GuildStorageGUI`.
  - `/town storage` and `/guild storage`: requires player location to resolve to an active `STORAGE` anchor of their guild; rejects remote execution.
  - Inactive/damaged anchors fail-closed before opening GUI.

- [ ] **Step 1: Write failing GUI and interaction tests**

In `GuildStorageGUITest.java`:
- Assert clicking storage slots triggers transactional deposit/withdraw through `GuildStorageService`.
- Assert player disconnect or inventory close saves dirty state cleanly.
- Assert `/town storage` and `/guild storage` executed in the wilderness or outside an active anchor is rejected with an error.

- [ ] **Step 2: Implement `GuildStorageGUI`**

Implement custom `InventoryHolder` rendering 54 slots for the active tab, tab navigation icons in the bottom bar, and asynchronous slot transaction handling on click.

- [ ] **Step 3: Wire spatial and governance validation in `BuildingListener` and commands**

In `BuildingListener.java`:
```java
if (facility.type() == FacilityType.STORAGE) {
    Optional<Territory> territoryOpt = territories.get(facility.territoryId());
    if (territoryOpt.isEmpty() || territoryOpt.get().governedByGuildId().isEmpty()) {
        player.sendMessage(Component.text("Storage facility is not in governed territory.", NamedTextColor.RED));
        event.setCancelled(true);
        return;
    }
    String governingGuildId = territoryOpt.get().governedByGuildId().get();
    var resident = residentService.getResident(player.getUniqueId());
    if (resident.isEmpty() || !governingGuildId.equals(resident.get().getGuild())) {
        player.sendMessage(Component.text("Only residents of " + governingGuildId + " may access this storage.", NamedTextColor.RED));
        event.setCancelled(true);
        return;
    }
    
    StorageFacilityInteractEvent storageEvent = new StorageFacilityInteractEvent(player, facility, territoryOpt.get(), governingGuildId);
    plugin.getServer().getPluginManager().callEvent(storageEvent);
    if (!storageEvent.isCancelled()) {
        storageGUI.open(player, governingGuildId, facility);
    }
    event.setCancelled(true);
}
```

In `GuildBrigadierCommand.java`:
Wire `/town storage` and `/guild storage` to check `facilityAnchorValidator.activeAt(player.getLocation())` for `STORAGE`, ensuring no remote access.

- [ ] **Step 4: Run full GUI & listener tests**

Run:
```bash
./gradlew --no-daemon :paper:test --tests 'org.aincraft.guilds.storage.*' --tests 'org.aincraft.guilds.territory.building.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/storage/gui/ \
  paper/src/main/java/org/aincraft/guilds/territory/building/ \
  paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java \
  paper/src/main/java/org/aincraft/guilds/GuildsServices.java \
  paper/src/test/java/org/aincraft/guilds/storage/gui/
git commit -m "feat: implement virtual guild storage gui and wire spatial anchor listener and command"
```

---

### Task 6: Execute Deletion Gates & Clean Up Dead Code Paths

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/MintGuildBankService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/models/PlotTypes.java`
- Remove: `paper/src/main/java/org/aincraft/guilds/listeners/GuildBankVillagerListener.java`

**Interfaces:**
- Deletion Gate: Perform repository grep verification ensuring zero remaining references before deletion.

- [ ] **Step 1: Verify zero remaining references to `GuildBankVillagerListener`**

Execute:
```bash
git grep "GuildBankVillagerListener"
```
Verify only `GuildsServices.java` and `GuildBankVillagerListener.java` appear.

- [ ] **Step 2: Delete `GuildBankVillagerListener` and unwire from `GuildsServices`**

- Remove registration in `GuildsServices.java`.
- Delete `paper/src/main/java/org/aincraft/guilds/listeners/GuildBankVillagerListener.java`.

- [ ] **Step 3: Remove dead `MintGuildBankService.creditTax` overload**

Remove `creditTax(String guildId, BigDecimal amount, String key)` which lacked the required payer UUID.

- [ ] **Step 4: Deprecate `PlotTypes.BANK`**

Mark `PlotTypes.BANK` as `@Deprecated` with Javadoc pointing to `FacilityType.STORAGE`.

- [ ] **Step 5: Run full project verification**

Run:
```bash
./gradlew --no-daemon check
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/GuildsServices.java \
  paper/src/main/java/org/aincraft/guilds/services/MintGuildBankService.java \
  paper/src/main/java/org/aincraft/guilds/models/PlotTypes.java
git rm paper/src/main/java/org/aincraft/guilds/listeners/GuildBankVillagerListener.java
git commit -m "refactor: remove unanchored villager listener and clean up dead bank paths"
```

---

### Task 7: Update Living Specs and Verification Documentation

**Files:**
- Modify: `docs/living-specs/guild-storage.md`
- Modify: `docs/living-specs/territory.md`
- Modify: `docs/living-specs/economy.md`
- Modify: `README.md`

**Interfaces:**
- Updates living spec checkboxes, decisions log, and operator documentation.

- [ ] **Step 1: Update `docs/living-specs/guild-storage.md`**

Flip completed checkboxes:
```markdown
### Capability (shipped)
- [x] FacilityType.STORAGE and facility directory exist (economy)
- [x] Storage building anchor placement and validation (/territory building create storage <id>)
- [x] SQL item aggregate (PostgreSQL/MySQL), versioned slot store, and schema migrations (V24__guild-storage.sql)
- [x] Virtual 54-slot guild warehouse UI (GuildStorageGUI)
- [x] Role-gated transactional deposit / withdraw services (GuildStorageService)
- [x] Fail-closed spatial resolution and governing guild access gating (/town storage at anchor only)
```

- [ ] **Step 2: Update `README.md`**

Document `/territory building create storage <id>` and `/town storage` at the settlement item warehouse.

- [ ] **Step 3: Run full check**

Run:
```bash
./gradlew --no-daemon check
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add docs/living-specs/ README.md
git commit -m "docs: update living specs and readme for complete guild storage building"
```
