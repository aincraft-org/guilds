# Fast Travel Network Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved guild-owned fast-travel network with local and allied crystals, terminals, boats, airships, finite player-bound travel currency, durable reservations, and no command/item spawn bypass.

**Architecture:** Extend the existing `FacilityType`/`Territory`/`FacilityRegistry` model and existing facility SQL/document codecs. Keep facility ownership derived from current territory governance. Generalize the existing waystone warmup/cancellation/safe-landing flow into one mode-aware Paper service. Add pure policy, cost, water-mask, and cache components around that service. Store wallets, idempotent awards, and expiring reservations in the existing shared SQL pool. Keep Bukkit/Paper world access on the main thread; perform bounded route analysis and database work off-thread; marshal all player-facing callbacks back to the main thread.

**Tech Stack:** Java 26, Gradle 9.6.1, Paper API, Adventure, Brigadier, HikariCP, PostgreSQL/MySQL-compatible SQL migrations, JUnit 5, Mockito conventions already used by `guilds-paper` tests.

**Spec:** `docs/superpowers/specs/2026-08-28-fast-travel-network-design.md` (approved design).

## Global Constraints

- Preserve existing `WAYSTONE`, `TRADING_POST`, `STORAGE`, and `BANK` records and behavior. The only existing-waystone behavior change is the personal travel-currency charge.
- Use no second membership authority, registry, persistence backend, alliance-link table, guild resource type, or guild-money balance for travel currency.
- Keep `SettlementFacility` records free of a second ownership field. Resolve effective ownership from the current governing guild of the facility’s territory at authorization and construction time.
- Persist inactive facility records; derive activity by current governance, anchor/spawn reconciliation, and endpoint checks. Inactive records count for cardinality and quota checks.
- A failed candidate facility save leaves both the durable store and live registry unchanged. A failed territory-policy save leaves the live territory unchanged.
- Use classpath SQL resources for Paper DML and the shared migration stream for schema. SQL must remain portable between the configured PostgreSQL default and MySQL selection.
- Do not access Bukkit worlds, blocks, entities, or `Player` state from worker threads. Capture UUIDs and immutable facility snapshots before dispatching work.
- Use LSP `rename_file`/symbol references for the Waystone-to-fast-travel cutover; do not perform cross-file text renames that can miss usages.
- Each task runs only its focused verification. Run formatting, lint, and the full quality gate once in the final task.

## Requirement Mapping

These IDs map the approved design to implementation tasks:

- **FR-001:** Facility types and exact compatibility modes.
- **FR-002:** `fast_travel`, `remote_crystal`, `boat_travel`, and `airship_travel` capability gates.
- **FR-003:** Governance-derived ownership, crystal/terminal cardinality, inactive reconciliation, and facility quotas.
- **FR-004:** Immutable territory quota and cross-territory policy with local-terminal exception.
- **FR-005:** Crystal spawn matching, terminal placement, bounded boat shoreline validation, and airship platform/sky validation.
- **FR-006:** Same-guild/alliance authorization, mode-specific endpoints, warmup/cancellation, safe landing, and return/continue flow.
- **FR-007:** Player wallet, starter/cap rules, canonical distance cost, reservation/commit/release, and depletion.
- **FR-008:** Quest, exploration, and guild-activity rewards with actor attribution and idempotency.
- **FR-009:** SQL persistence, atomic transactions, expiry, orphan cleanup, and restart recovery.
- **FR-010:** Removal of `/g spawn` and hearthstone-item spawn teleport while retaining `/g setspawn` data for the crystal.
- **NFR-001:** Bounded, responsive Paper-side boat connectivity with cache invalidation.
- **NFR-002:** Backward-compatible documents and portable SQL.

---

## Task 1: Add API facility modes, policy, and registry helpers

**Requirements:** FR-001, FR-003, FR-004, NFR-002.

**Files:**

- `guilds-api/src/main/java/org/aincraft/guilds/territory/model/FacilityType.java`
- `guilds-api/src/main/java/org/aincraft/guilds/territory/model/FastTravelMode.java` (new)
- `guilds-api/src/main/java/org/aincraft/guilds/territory/model/FastTravelPolicy.java` (new)
- `guilds-api/src/main/java/org/aincraft/guilds/territory/model/Territory.java`
- `guilds-api/src/main/java/org/aincraft/guilds/territory/registry/FacilityRegistry.java`
- `guilds-api/src/test/java/org/aincraft/guilds/territory/model/FastTravelPolicyTest.java` (new)
- `guilds-api/src/test/java/org/aincraft/guilds/territory/model/TerritoryFastTravelPolicyTest.java` (new)
- `guilds-api/src/test/java/org/aincraft/guilds/territory/registry/FacilityRegistryTest.java`

**Changes:**

- Add `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP` to `FacilityType`. Do not rename or reorder existing values used by document serialization.
- Add `FastTravelMode` with exactly `WAYSTONE`, `CRYSTAL`, `LOCAL_TERMINAL`, `BOAT`, and `AIRSHIP`. Provide one total mapping from facility type to mode: `WAYSTONE -> WAYSTONE`, `GUILD_CRYSTAL -> CRYSTAL`, `TELEPORT_TERMINAL -> LOCAL_TERMINAL`, `BOAT -> BOAT`, and `AIRSHIP -> AIRSHIP`; non-transport facility types return no travel mode.
- Add immutable `FastTravelPolicy`:

  ```java
  public record FastTravelPolicy(
      Map<FacilityType, Integer> facilityQuotas,
      Set<FastTravelMode> crossTerritoryModes) {
      public static final int UNLIMITED_QUOTA = Integer.MAX_VALUE;

      public static FastTravelPolicy defaults();
      public int quotaFor(FacilityType type);
      public boolean allowsCrossTerritory(FastTravelMode mode);
  }
  ```

  Copy both collections defensively. Reject null keys/values, negative quotas, non-transport quota keys, `LOCAL_TERMINAL` in `crossTerritoryModes`, and null modes. `defaults()` uses unlimited quotas and permits `CRYSTAL`, `BOAT`, and `AIRSHIP`; absent policy in old documents must not restrict existing facilities.
- Add a `fastTravelPolicy` field to `Territory`, preserve every existing constructor by delegating to `FastTravelPolicy.defaults()`, and add `fastTravelPolicy()` plus `withFastTravelPolicy(FastTravelPolicy)`. Ensure `withGoverningGuild`, `withoutGoverningGuild`, and `withPolicies` preserve the policy through `copyWith`.
- Add `FacilityRegistry.count(String territoryId, FacilityType type)` and a candidate-registry helper that counts all persisted records, including records that will be inactive at runtime. Keep registry validation for territory/world/location uniqueness unchanged.

**Tests:** Verify defensive immutability, invalid policy rejection, independent quota versus boundary settings, default compatibility for old constructor paths, policy preservation through territory copies, and inactive-record counting. Run:

```text
./gradlew :guilds-api:test --tests 'org.aincraft.guilds.territory.model.FastTravelPolicyTest' --tests 'org.aincraft.guilds.territory.model.TerritoryFastTravelPolicyTest' --tests 'org.aincraft.guilds.territory.registry.FacilityRegistryTest'
```

---

## Task 2: Persist policies, new facility types, wallets, awards, and reservations

**Requirements:** FR-001, FR-004, FR-007, FR-009, NFR-002.

**Files:**

- `guilds-common/src/main/java/org/aincraft/guilds/territory/persist/TerritoryJson.java`
- `guilds-common/src/main/java/org/aincraft/guilds/territory/persist/PostgresFacilityStore.java`
- `guilds-common/src/main/resources/sql/migrations/guilds/manifest`
- `guilds-common/src/main/resources/sql/migrations/guilds/V31__fast-travel-currency.sql` (new)
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/TerritoryBindingJsonTest.java`
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/TerritoryJsonFastTravelTest.java` (new)
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/PostgresFacilityStoreTest.java`
- `guilds-common/src/test/java/org/aincraft/guilds/database/migration/SqlMigrationCatalogTest.java`
- `guilds-paper/src/main/resources/sql/travel/select-wallet.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/insert-wallet.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/update-wallet-balance.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/insert-award.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/insert-reservation.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/select-reservation.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/commit-reservation.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/release-reservation.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/select-expired-reservations.sql` (new)
- `guilds-paper/src/main/resources/sql/travel/recover-reservation.sql` (new)

**Changes:**

- Extend `TerritoryJson` with an optional object shaped as follows:

  ```json
  "fastTravelPolicy": {
    "facilityQuotas": {"BOAT": 2, "AIRSHIP": 1},
    "crossTerritoryModes": ["CRYSTAL", "BOAT"]
  }
  ```

  Serialize deterministic enum names and sorted map/set entries. On missing or malformed policy data, use the validated default only for an absent legacy field; reject malformed present policy data rather than silently widening it. Round-trip all existing territory fields unchanged.
- Extend `PostgresFacilityStore`’s existing JSON codec so all four new enum values round-trip while old facility documents remain readable. Do not add an `active` or owner-guild column to the facility document.
- Add manifest version `31 fast-travel-currency` and `V31__fast-travel-currency.sql` with portable tables:

  ```sql
  CREATE TABLE player_travel_wallets (
      player_uuid TEXT PRIMARY KEY,
      balance BIGINT NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
  );

  CREATE TABLE travel_currency_awards (
      source TEXT NOT NULL,
      event_id TEXT NOT NULL,
      player_uuid TEXT NOT NULL,
      amount BIGINT NOT NULL,
      awarded_at TEXT NOT NULL,
      PRIMARY KEY (source, event_id)
  );

  CREATE TABLE travel_currency_reservations (
      reservation_id TEXT PRIMARY KEY,
      trip_id TEXT NOT NULL UNIQUE,
      player_uuid TEXT NOT NULL,
      amount BIGINT NOT NULL,
      status TEXT NOT NULL,
      expires_at BIGINT NOT NULL,
      created_at BIGINT NOT NULL,
      completed_at BIGINT,
      released_at BIGINT
  );
  ```

  Do not add a foreign key to `residents`; exploration and other valid actors can receive a wallet before joining a guild. Add an index on `(status, expires_at)` for recovery.
- Put all Paper DML in the `travel/` resource directory. The reserve transaction must conditionally decrement a wallet only when `balance >= amount`, then insert a unique `trip_id` reservation in the same transaction. Award insertion and capped balance update must be one transaction. Commit and release updates must be conditional on `status = 'RESERVED'` so retries are idempotent.
- Update `SqlMigrationCatalogTest` from the stale expected latest version `27` to `31`, assert the new manifest slug and migration resource, and retain ordering/duplicate-version assertions.

**Tests:** Round-trip new and legacy facility documents, round-trip policies, assert V31 catalog ordering, and verify SQL resource names are loadable. Run:

```text
./gradlew :guilds-common:test --tests 'org.aincraft.guilds.territory.persist.TerritoryBindingJsonTest' --tests 'org.aincraft.guilds.territory.persist.TerritoryJsonFastTravelTest' --tests 'org.aincraft.guilds.territory.persist.PostgresFacilityStoreTest' --tests 'org.aincraft.guilds.database.migration.SqlMigrationCatalogTest'
```

---

## Task 3: Implement personal travel currency and canonical cost calculation

**Requirements:** FR-007, FR-009.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/config/TravelCurrencyConfig.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/config/TravelCurrencyConfigLoader.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelCostCalculator.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/TravelCurrencyRewardSource.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/WalletSnapshot.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/TravelCurrencyService.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/TravelCurrencyServiceImpl.java` (new)
- `guilds-paper/src/main/resources/guilds-config.yml`
- `guilds-paper/src/test/java/org/aincraft/guilds/config/TravelCurrencyConfigLoaderTest.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelCostCalculatorTest.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/services/TravelCurrencyServiceImplTest.java` (new)

**Changes:**

- Add this global `guilds-config.yml` section with explicit defaults: starter `10`, maximum `1000`, base cost `1`, distance divisor `100.0`, mode multipliers `WAYSTONE: 1.0`, `CRYSTAL: 1.0`, `BOAT: 1.25`, `AIRSHIP: 1.5`, reservation duration `30_000` milliseconds, and reward amounts `QUEST_COMPLETION: 20`, `EXPLORATION_MILESTONE: 10`, `GUILD_ACTIVITY: 5`. Validate non-negative starter/base/rewards, maximum at least starter, positive finite divisor/multipliers, and positive reservation duration.
- Define `TravelCurrencyRewardSource` with exactly `QUEST_COMPLETION`, `EXPLORATION_MILESTONE`, and `GUILD_ACTIVITY`.
- Implement the pure calculator with this contract:

  ```java
  public long calculate(FastTravelMode mode, double distance) {
      requireFiniteNonNegative(distance);
      double raw = config.baseCost()
          + config.modeMultiplier(mode) * distance / config.distanceDivisor();
      return Math.max(0L, checkedCeilToLong(raw));
  }
  ```

  Use straight-line endpoint distance for `WAYSTONE`, `CRYSTAL`, and `AIRSHIP`; pass the scalar navigable distance from the boat route result for `BOAT`. Reject invalid numeric inputs instead of producing a free or overflowing trip.
- Define the service API around durable state:

  ```java
  public interface TravelCurrencyService {
      CompletionStage<WalletSnapshot> wallet(UUID playerId);
      CompletionStage<ReserveResult> reserve(UUID playerId, String tripId,
          long amount, long nowMillis);
      CompletionStage<ReservationResult> commit(String reservationId, long nowMillis);
      CompletionStage<ReservationResult> release(String reservationId, long nowMillis);
      CompletionStage<RewardResult> award(UUID playerId,
          TravelCurrencyRewardSource source, String eventId, long amount, long nowMillis);
      CompletionStage<Integer> recoverExpired(long nowMillis);
  }
  ```

  Define `ReserveStatus` as `RESERVED`, `INSUFFICIENT`, `DUPLICATE_TRIP`, `INVALID_AMOUNT`, or `FAILED`; `ReserveResult` carries the status, reservation ID when reserved, and post-reservation balance. Define `ReservationStatus` as `COMMITTED`, `RELEASED`, `ALREADY_COMMITTED`, `ALREADY_RELEASED`, `EXPIRED`, or `NOT_FOUND`; `ReservationResult` carries that status. Define `RewardStatus` as `AWARDED`, `DUPLICATE`, `INVALID_AMOUNT`, or `FAILED`; `RewardResult` carries that status and the resulting wallet snapshot.
- Create the starter wallet atomically on first access/reservation. Reserve with a conditional balance decrement and unique trip identity. Award only when `(source,eventId)` is newly inserted, clamp to maximum, and return a duplicate result without changing balance. Release restores the reserved amount with the same maximum cap. Commit is idempotent and consumes the already-reserved amount. Recovery releases every expired `RESERVED` row and clears orphaned in-memory travel attempts without granting a trip.
- Ensure all transaction callbacks use `DatabaseManager`’s existing shared-pool transaction helpers and the named classpath SQL resources. Never mutate wallet state in memory before durable success.

**Tests:** Cover first-wallet starter grant, cap clamping, zero/negative rejection, exact ceil boundary, each mode multiplier, concurrent reserve overspend protection, duplicate award idempotency, commit/release idempotency, expiry recovery, and wallet unchanged after rejected/canceled reservations. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.config.TravelCurrencyConfigLoaderTest' --tests 'org.aincraft.guilds.territory.building.FastTravelCostCalculatorTest' --tests 'org.aincraft.guilds.services.TravelCurrencyServiceImplTest'
```

---

## Task 4: Extend building configuration and tech capabilities

**Requirements:** FR-002, FR-005, FR-007.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfig.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfigLoader.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/TechTreeService.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/TechTreeServiceImpl.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/config/TechTreeConfigLoader.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`
- `guilds-paper/src/main/resources/config.yml`
- `guilds-paper/src/main/resources/techtree.yml`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingConfigLoaderTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/services/TechTreeServiceCapabilityTest.java` (new)

**Changes:**

- Extend `BuildingConfig` with a typed transport-geometry configuration containing boat entry radius/width, clear boat-space height, bounded route chunk radius/budget, airship platform radius, and airship vertical-clearance height. Keep existing placement timeout and waystone warmup/cooldown fields intact.
- Add root `config.yml` anchor materials for `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP`, plus the transport geometry values. Preserve current material defaults and loader behavior for existing types.
- Update `fast_travel`’s description to mention local crystal/terminal travel while retaining `teleport_cooldown_reduction: 0.5`. Add nodes with these concrete defaults: `remote_crystal` cost `3`, `boat_travel` cost `3`, and `airship_travel` cost `4`; each has `fast_travel` as parent/prerequisite and an explicit infrastructure GUI position. Keep `fast_travel`’s existing cost/prerequisite unchanged. Update the inline fallback YAML in `TechTreeConfigLoader` byte-for-byte in meaning with the packaged resource.
- Add capability queries to `TechTreeService`/implementation for a guild/node and a numeric effect lookup. The generalized travel service must use the `fast_travel` node only for the existing WAYSTONE cooldown reduction and must not apply that effect to crystal, boat, or airship cooldowns.
- Add non-overlapping graph coordinates for the three new nodes and update layout tests. Do not create a second unlock store or special-case new capabilities outside the existing tech rows.

**Tests:** Verify old config defaults, every new anchor/geometry field, fallback/package parity, node prerequisites and descriptions, node layout uniqueness, and `fast_travel` effect scope. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BuildingConfigLoaderTest' --tests 'org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest' --tests 'org.aincraft.guilds.services.TechTreeServiceCapabilityTest'
```

---

## Task 5: Add construction validation, quota atomicity, and endpoint reconciliation

**Requirements:** FR-003, FR-004, FR-005, FR-009.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/AnchorStatus.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FacilityAnchorValidator.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FacilityMutationService.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelFacilityValidator.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelFacilityState.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FacilityAnchorValidatorTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelFacilityValidatorTest.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FacilityMutationServiceTest.java`

**Changes:**

- Add explicit anchor outcomes for missing/invalid transport geometry, spawn mismatch, and unavailable world/chunk while retaining existing statuses. Every result must identify the failed category used by command/listener messages.
- Implement `FastTravelFacilityValidator` with these main-thread-only operations:

  ```java
  ValidationResult validateCandidate(
      SettlementFacility candidate, FacilityRegistry candidateRegistry);
  ValidationResult validateBoatAnchor(Location anchor);
  ValidationResult validateAirshipAnchor(Location anchor);
  ValidationResult validateCrystalSpawn(SettlementFacility candidate);
  boolean isActive(SettlementFacility facility);
  ```

  Candidate validation resolves a governed territory, checks the effective owner’s required node, counts all candidate records by `(territory,type,effectiveOwner)`, enforces `FastTravelPolicy.quotaFor`, and enforces one persisted crystal/terminal per effective guild. Crystal validation requires exact world/block equality with `GuildService.getGuildSpawn` and containment in a territory governed by that guild. Boat validation checks only the configured local shoreline/open-water window and clear boat space. Airship validation checks its anchor, launch platform, and configured clear vertical space; it never searches a 3D corridor.
- Treat an ungoverned territory as inactive immediately. On rebind, `isActive` requires governance, owner capability, quota/cardinality validity, exact anchor validity, and crystal spawn match before access resumes. Do not add `active` or `ownerGuildId` to `SettlementFacility`.
- Add candidate validation to the synchronized mutation boundary. For register, replacement, explicit reactivation, and removal, build the candidate registry, run validation/counts against that candidate, save the candidate through the existing durable store, and publish it only after success. A failed save leaves the current registry untouched. Removal must use the same durable-then-live ordering.
- Keep lowering a quota from invalidating new construction/reactivation but not deleting or blocking an already-active facility at travel time. Global crystal/terminal cardinality continues to count inactive persisted records that still resolve to the effective guild.

**Tests:** Cover exact crystal spawn/world matching, moved-spawn inactivity, governance loss/rebind, one-per-guild inactive counting, per-type quota independence, lowered-quota existing-facility behavior, failed-save rollback, boat shoreline windows, airship clearance, and no world-scale construction search. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FacilityAnchorValidatorTest' --tests 'org.aincraft.guilds.territory.building.FastTravelFacilityValidatorTest' --tests 'org.aincraft.guilds.territory.building.FacilityMutationServiceTest'
```

---

## Task 6: Implement bounded boat connectivity and invalidation

**Requirements:** FR-005, FR-006, NFR-001.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterMask.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterSnapshot.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteResult.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteCalculator.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteCache.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteService.java` (new)
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterChangeListener.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteCalculatorTest.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteCacheTest.java` (new)
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteServiceTest.java` (new)

**Changes:**

- Define water snapshots as immutable chunk masks containing navigable surface cells and endpoint clear-space data. `BoatRouteCalculator` receives snapshots, endpoint cells, and a hard chunk/node budget; return `CONNECTED(distance)`, `DISCONNECTED`, `PENDING`, or `UNAVAILABLE`. Use bidirectional A* or an equivalent bounded search and retain only scalar distance, never a path.
- Implement `BoatRouteCache` keys as `(world UUID, normalized endpoint pair, water revision)`. Store geometry results only. `invalidateChunk` increments the world revision and removes routes touching the changed chunk or any of its eight neighbors; no authorization, alliance, upgrade, facility activity, quota, or territory-policy result may be cached.
- Implement `BoatRouteService` as a main-thread snapshot producer and worker-thread analyzer. Capture only a bounded number of loaded chunks per scheduler batch, stop at configured radius/budget, and return `PENDING`/`UNAVAILABLE` when the budget or loaded-world prerequisites cannot be met. Never perform an unbounded block DFS on the Paper thread.
- Register block-change listeners for place/break, fluid movement, and piston changes that can alter water masks. Filter to water-affecting blocks before invalidating affected and neighboring chunks. Stop the route executor and clear pending work during plugin shutdown.

**Tests:** Verify connected/disconnected masks, scalar distance, hard budget behavior, cache reuse, endpoint-order normalization, neighbor invalidation, stale-revision rejection, and that route checks do not execute arbitrary world access on the worker. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BoatRouteCalculatorTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteCacheTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest'
```

---

## Task 7: Generalize waystone travel into the mode-aware fast-travel service

**Requirements:** FR-001, FR-002, FR-004, FR-006, FR-007, FR-009, NFR-001.

**Files:**

- Rename with LSP and migrate all references:
  - `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/WaystoneAccess.java` -> `FastTravelAccess.java`
  - `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/WaystoneSelections.java` -> `FastTravelSelections.java`
  - `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/WaystoneTravelService.java` -> `FastTravelService.java`
  - `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/WaystoneTravelListener.java` -> `FastTravelListener.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelAccess.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelService.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelListener.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingCommand.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/WaystoneAccessTest.java` -> `FastTravelAccessTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/WaystoneSelectionsTest.java` -> `FastTravelSelectionsTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/WaystoneTravelServiceTest.java` -> `FastTravelServiceTest.java`

**Changes:**

- Before editing, use LSP references for every exported Waystone symbol, then use `rename_file`; remove the obsolete Waystone names after all callers and tests are migrated. Do not leave aliases or deprecated wrappers.
- Preserve the existing WAYSTONE destination filtering and cooldown behavior in `FastTravelAccess`. Add exact mode checks for crystal, terminal-local, boat, and airship endpoints:
  - Crystal destinations are active `GUILD_CRYSTAL` records. Local terminal-to-own-crystal travel is allowed in one territory. Remote crystal travel requires the traveler’s `remote_crystal`, the destination guild’s `fast_travel`, current alliance membership, different territory IDs, and both territories allowing `CRYSTAL`.
  - Boat and airship require matching active endpoint types, traveler capability, endpoint-owner capability, same guild or current alliance, different territory IDs, both boundary policies, and same-world endpoints. Boat additionally requires a connected route result; airship has no terrain route search.
  - Existing waystone travel continues to use same-governing-guild destinations and does not inherit crystal/boat/airship boundary policy.
- Define mode-aware service entry points:

  ```java
  CompletionStage<StartResult> start(
      Player player, SettlementFacility origin,
      String destinationId, long nowMillis);
  void cancel(UUID playerId, CancelReason reason);
  long remainingCooldownMillis(UUID playerId,
      FastTravelMode mode, long nowMillis);
  boolean isPending(UUID playerId);
  void recover(long nowMillis);
  void stop();
  ```

  Add distinct results for inactive origin/destination, type mismatch, missing capability, non-allied destination, same-territory remote attempt, policy denial, route pending/unavailable/disconnected, unsafe/protected landing, insufficient currency, and reservation failure.
- Enforce the approved check order: active origin, active destination, exact compatibility, traveler identity/membership, traveler and owner capabilities, guild/alliance relationship, territory IDs and both policies, boat route, landing/protection, distance/cost, then durable reservation. Do not consult quota at travel time for an existing active facility.
- Keep the existing warmup, movement/damage/death/quit cancellation, safe-landing, and per-player pending-trip protections. Store UUIDs, immutable IDs, mode, cost, reservation ID, expiry, and route scalar in pending state. Reserve only after all pre-reservation checks. Re-run governance, alliance, endpoint, policy, route, and landing checks before completion; release on cancellation, disconnect, expiry, invalidation, or failure. Commit only after final arrival checks succeed. A rejected/canceled/failed request leaves the wallet unchanged after release.
- Apply `TechTreeService`’s `teleport_cooldown_reduction` only to WAYSTONE cooldown calculation. Keep separate mode cooldowns for crystal, boat, and airship. Build physical interactions in `BuildingListener` around the generalized selection/service path: terminal starts local crystal flow, crystal opens eligible return/continue destinations, and boat/airship use same-mode endpoints.
- Extend `BuildingCommand` suggestions and parsing with all new facility types and generalized travel selection while retaining old waystone command syntax where it does not recreate `/g spawn`. Do not add a public reactivation subcommand in this slice; `FacilityMutationService.reactivate(String facilityId)` is the explicit internal lifecycle operation used by startup/governance reconciliation and runs the same candidate validator and mutation boundary.

**Tests:** Cover every access matrix, local exception, remote denial, alliance success, return from allied crystal, endpoint mismatch, capability ownership, exact check ordering, warmup cancellation, safe-landing failure, route pending/disconnected, concurrent pending trips, currency failure, reservation release, WAYSTONE-only cooldown reduction, and no quota check on active travel. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.FastTravelSelectionsTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingListenerTest'
```

---

## Task 8: Add actor-attributed, idempotent currency rewards

**Requirements:** FR-008, FR-007, FR-009.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/services/QuestService.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/QuestServiceImpl.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/QuestBrigadierCommand.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/TechTreeBrigadierCommand.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/listeners/PlayerMovementListener.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/services/QuestServiceImplTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/commands/brigadier/TechTreeBrigadierCommandTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/listeners/PlayerMovementListenerTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/services/TravelCurrencyServiceImplTest.java`

**Changes:**

- Change quest progress mutation to carry the contributor UUID:

  ```java
  void incrementProgress(
      String guildId, String questId, int amount, UUID contributorUuid);
  ```

  On the first incomplete-to-complete transition only, award the configured `QUEST_COMPLETION` amount to the contributor. Use event ID `quest:<guildId>:<questId>` and let the wallet service’s `(source,eventId)` uniqueness prevent retries. A null actor is a valid progress mutation for non-rewarding internal use but never awards personal currency.
- In `PlayerMovementListener`, detect a player’s transition into a territory in `updateTerritoryTitle`. Award `EXPLORATION_MILESTONE` to the entering player with event ID `territory:<territoryId>:<playerUuid>`, once per player/territory through the durable award key. Do not award repeated movement events or actorless transitions.
- In `TechTreeBrigadierCommand`, capture the active project node ID before `completeActiveProject(guild)`. After successful completion, award `GUILD_ACTIVITY` to the command sender with event ID `project:<guildId>:<nodeId>`. Do not award when completion fails or the sender is not a player.
- Read reward amounts from `TravelCurrencyConfig`; all providers call `TravelCurrencyService.award` rather than touching wallet tables or guild resources. Keep event IDs stable across retries and restart.

**Tests:** Verify quest threshold crossing awards exactly once to the contributor, actorless progress awards nothing, territory entry awards once for each player entering the same territory, project completion attributes to the initiating player, duplicate event delivery is harmless, and configured rewards clamp at the wallet cap. Run:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.services.QuestServiceImplTest' --tests 'org.aincraft.guilds.commands.brigadier.TechTreeBrigadierCommandTest' --tests 'org.aincraft.guilds.listeners.PlayerMovementListenerTest' --tests 'org.aincraft.guilds.services.TravelCurrencyServiceImplTest'
```

---

## Task 9: Remove spawn bypasses and wire the complete lifecycle

**Requirements:** FR-002, FR-003, FR-005, FR-006, FR-007, FR-009, FR-010.

**Files:**

- `guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/GuildService.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/GuildServiceImpl.java`
- `guilds-paper/src/main/java/org/aincraft/guilds/services/GuildHearthstoneService.java` (delete)
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/GuildHearthstoneServiceImpl.java` (delete)
- `guilds-paper/src/main/java/org/aincraft/guilds/listeners/GuildHearthstoneListener.java` (delete)
- `guilds-paper/src/test/java/org/aincraft/guilds/services/GuildHearthstoneServiceImplTest.java` (delete with removed feature)
- `guilds-paper/src/main/resources/guilds-config.yml`
- `guilds-paper/src/main/resources/plugin.yml`
- `guilds-paper/src/main/resources/paper-plugin.yml`
- `guilds-paper/src/test/java/org/aincraft/guilds/GuildsServicesWiringTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingLifecycleWiringTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommandTest.java`
- `guilds-paper/src/test/java/org/aincraft/guilds/services/GuildServiceImplTest.java`

**Changes:**

- Before deleting exported spawn/hearthstone symbols, run LSP references for `canTeleportToSpawn`, `GuildHearthstoneService`, `registerHearthstone`, and the old Waystone service names. Keep `GuildService.getGuildSpawn` and `/g setspawn`; the crystal reconciler depends on persisted spawn data.
- Remove the `/guild spawn` literal, aliases/help text, `handleOwnSpawn`, and `handleGuildSpawn` handlers. Remove `guilds.guild.spawn` permission from both plugin descriptors. No replacement command may teleport directly to a spawn; the terminal is the only local spawn travel entry point.
- Remove hearthstone config, service fields, listener registration, item listener, shutdown path, and implementation/test files. Remove `GuildService.canTeleportToSpawn` only after references show no remaining caller.
- In `GuildsServices`, construct `TravelCurrencyService` before quest/movement/tech command consumers, expose it to the composition root, register `FastTravelListener` and `BoatWaterChangeListener`, and run expired/orphan reservation recovery on startup and normal shutdown. Preserve the one `DatabaseManager`/Hikari pool.
- In `GuildsPlugin.startBuildings`, load the extended `BuildingConfig`, construct the facility validator, route service, currency/cost service, generalized access/service/listener, and wire them into existing building commands/interactions. Reconcile persisted crystal records against `GuildService.getGuildSpawn` during startup without deleting mismatches. Stop route/travel services and release pending reservations during disable.
- Update listener ordering so all Bukkit mutations and player messages execute on the main thread. Ensure startup recovery completes before transport interactions are enabled, while a failed recovery leaves reservations available for expiry cleanup rather than granting travel.
- Update `BuildingCommandTypeTest`, `GuildsIntegrationTest`, `GuildsServicesWiringTest`, and lifecycle tests for new service names, new facility types, removed spawn/hearthstone paths, and startup/shutdown recovery.

**Tests:** Run the focused wiring and command suite after the cutover:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.GuildsServicesWiringTest' --tests 'org.aincraft.guilds.territory.building.BuildingLifecycleWiringTest' --tests 'org.aincraft.guilds.commands.brigadier.GuildBrigadierCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTypeTest' --tests 'org.aincraft.guilds.GuildsIntegrationTest'
```

---

## Task 10: Update living specifications and perform full verification

**Requirements:** FR-001 through FR-010, NFR-001, NFR-002.

**Files:**

- `docs/living-specs/territory.md`
- `docs/living-specs/guilds.md`
- `docs/living-specs/persistence.md`
- `docs/living-specs/README.md` is unchanged because the existing index already covers the living-specification files

**Changes:**

- Update territory behavior with `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP`; governance-derived ownership; inactive-record semantics; `FastTravelPolicy` quotas; local-terminal exception; cross-territory requirements; and bounded boat geometry/cache rules.
- Update guild behavior with the four tech capabilities, crystal/terminal cardinality, alliance authorization, `/g spawn` and hearthstone removal, player-bound currency, reward actor attribution, and finite travel depletion. Explicitly keep guild resources and guild money separate.
- Update persistence behavior with the V31 wallet/award/reservation tables, transactional reserve/commit/release, idempotent awards, expiry/orphan recovery, policy JSON backward defaults, and portable SQL requirements. Mark the relevant current implementation checkboxes/decisions complete only after the preceding tests pass.
- Do not add web UI work or a new economy specification; the browser-facing territory JSON automatically carries the optional policy document through the existing codec.

**Verification:**

1. Run the full Java/Paper quality gate:

   ```text
   ./gradlew --no-daemon check
   ```

2. If the configured test database is available, run the focused database-backed currency, facility mutation, and recovery tests with `GUILDS_TEST_JDBC_URL` set through the repository’s existing test fixture conventions. If it is unavailable, retain the deterministic unit/transaction-mock evidence and report the missing runtime prerequisite rather than claiming database coverage.
3. Run the real server smoke path with the existing test harness and a configured SQL database:

   ```text
   ./gradlew :guilds-test:runServer
   ```

   Exercise two governed territories, local terminal-to-crystal travel, allied crystal return/continue, boat connected/disconnected endpoints, airship endpoints, spawn movement, governance/alliance changes, policy changes, quota reduction, insufficient currency, reward replenishment, cancellation, and safe landing. Confirm route validation does not make the Paper server unresponsive.
4. Inspect the final changed-file set for obsolete Waystone/Hearthstone spawn paths, unreferenced SQL resources, stale permissions, and documentation drift. Do not claim runtime smoke success unless the server was launched and the listed scenarios were observed.

**Exit criteria:** The focused tests, full `check`, and available runtime smoke evidence match the approved design; all changed callers, tests, SQL resources, configuration, and living specifications are migrated; no compatibility shim or bypass path remains.
