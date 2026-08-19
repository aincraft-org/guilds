# Release 2 Living Settlements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn governed territories into durable player-developed settlements through personal standing, contribution projects, facility tiers, shared guild storage, and recurring repairable invasions.

**Architecture:** Add Paper-free standing, project, facility, storage, and invasion state machines in `api`/`common`; keep inventory UI, Bukkit events, commands, and scheduling in `paper`. Facility anchors remain exact functional blocks while tiers and projects supply gameplay state. Guild storage is a transactional aggregate keyed by guild and active storage facility; contracts consume it through a durable reservation protocol.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper 26.2, JUnit 5, Mockito, HikariCP, PostgreSQL 16+, MySQL 8.0, Gson, Adventure Components.

## Global Constraints

- Release 1 governance, health, and crash-safe contract reservation contracts are prerequisites.
- Personal territory standing is keyed by player and territory, not governing guild; ownership flips preserve it.
- Every reward and facility tier has one observable effect; configuration rejects inert entries.
- Material/currency contributions either commit durable progress or enter an operator-visible compensation state.
- Storage survives inactive/damaged facilities; inactive facilities deny access rather than deleting inventory.
- Contract fulfillment selects exactly one durable material source. It never partially debits player and warehouse inventory.
- Existing `EconomyBridge.chargeExpense` remains the only territory expense path.
- PostgreSQL and MySQL behavior remain equivalent.
- Every non-terminal transition defines restart recovery and stable operation IDs.
- Tests are observed RED before implementation and commit green with behavior.

---

### Task 1: Define personal standing and reward rules

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/standing/PlayerTerritoryStanding.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/standing/StandingRewardDefinition.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/standing/PlayerStandingService.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/standing/PlayerStandingRules.java`
- Create: `api/src/test/java/org/aincraft/guilds/territory/standing/PlayerTerritoryStandingTest.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/standing/PlayerStandingRulesTest.java`

**Interfaces:**

```java
public record PlayerTerritoryStanding(
        UUID playerId,
        String territoryId,
        long points,
        int tier,
        Set<String> chosenRewardIds
) {}

public sealed interface StandingRewardDefinition permits WaystoneReward, GatheringReward,
        FeeReward, StorageReward, TitleReward {
    String id();
    int requiredTier();
}

public interface PlayerStandingService {
    PlayerTerritoryStanding get(UUID playerId, String territoryId);
    AccrualResult accrue(UUID playerId, String territoryId, String sourceId, long points, UUID operationId);
    RewardChoiceResult chooseReward(UUID playerId, String territoryId, String rewardId, UUID operationId);
}
```

- [ ] **Step 1: Write failing invariant tests**

Assert non-negative points, monotonic tiers, immutable choices, one choice per milestone, unknown reward rejection, and duplicate operation idempotency.

- [ ] **Step 2: Run tests RED**

```bash
./gradlew --no-daemon :api:test --tests '*PlayerTerritoryStandingTest'
./gradlew --no-daemon :common:test --tests '*PlayerStandingRulesTest'
```

Expected: missing contracts.

- [ ] **Step 3: Implement immutable contracts and pure rules**

Derive tiers from ordered thresholds. Validate required tier and unused milestone before recording a reward. Keep Bukkit effects outside these types.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :api:test --tests '*PlayerTerritoryStandingTest'
./gradlew --no-daemon :common:test --tests '*PlayerStandingRulesTest'
git add api/src common/src
git commit -m "feat: define personal territory standing"
```

---

### Task 2: Persist standing and idempotent accrual

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/standing/PlayerStandingStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/standing/SqlPlayerStandingStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/standing/DefaultPlayerStandingService.java`
- Create SQL resources: `common/src/main/resources/sql/player-standing/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create: `common/src/test/java/org/aincraft/guilds/territory/standing/SqlPlayerStandingStoreTest.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/standing/DefaultPlayerStandingServiceTest.java`

**Interfaces:**
- Unique state key `(player_id, territory_id)` and unique operation key `operation_id`.
- Service applies rules and store mutation in one transaction and returns the original result for retries.

- [ ] **Step 1: Write failing SQL/retry tests**

Cover create-on-first-accrual, concurrent duplicate operation, reward persistence, owner flip with unchanged player key, and restart load.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*PlayerStanding*'
```

Expected: store/service missing.

- [ ] **Step 3: Implement store and service**

Use dialect resources and unique operation IDs. Never derive the standing key from current governing guild.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*PlayerStanding*'
git add common/src
git commit -m "feat: persist personal territory standing"
```

---

### Task 3: Wire standing rewards and player commands

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/standing/PlayerStandingConfigLoader.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/standing/StandingRewardEffectRegistry.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Modify: `paper/src/main/resources/config.yml`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/standing/PlayerStandingConfigLoaderTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/standing/StandingRewardEffectRegistryTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/command/TerritoryCommandPlayerStandingTest.java`

**Interfaces:**

```text
/territory standing me [territory]
/territory standing rewards [territory]
/territory standing choose <territory> <reward-id>
```

Effects: waystone cooldown/cost, gathering multiplier, fee multiplier, warehouse capacity, and Azoth title grant when `TitleService` is available.

- [ ] **Step 1: Write failing config/effect tests**

Reject duplicate IDs, non-monotonic tiers, unknown/inert effects, unavailable mandatory adapters, and unsafe multipliers.

- [ ] **Step 2: Write failing command tests**

Cover location default, locked choice, success, duplicate choice, and persistence failure.

- [ ] **Step 3: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*PlayerStanding*' --tests '*StandingReward*'
```

Expected: missing surface.

- [ ] **Step 4: Implement effect registry and commands**

Query chosen rewards at existing waystone, harvest, fee, storage, and title seams. Do not duplicate reward calculations in commands.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*PlayerStanding*' --tests '*StandingReward*'
git add paper/src
git commit -m "feat: apply territory standing rewards"
```

---

### Task 4: Define settlement project state

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/project/SettlementProject.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/project/ProjectDefinition.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/project/ProjectRequirement.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/project/ProjectStatus.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/project/ProjectRules.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/project/ProjectRulesTest.java`

**Interfaces:**
- Status: `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`.
- Requirement types: material, currency, activity.
- Contribution returns accepted and remainder quantities; completion emits one effect operation ID.

- [ ] **Step 1: Write failing transition tests**

Cover clamping, unsupported requirement, paused denial, exact completion, duplicate completion, cancellation, and immutable contributor totals.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*ProjectRulesTest'
```

Expected: missing state machine.

- [ ] **Step 3: Implement pure rules**

Keep inventory/currency adapters out of the domain and expose explicit accepted quantities.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*ProjectRulesTest'
git add api/src common/src
git commit -m "feat: define settlement project state"
```

---

### Task 5: Persist projects with a completion outbox

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/project/ProjectStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/project/DefaultProjectService.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/persist/SqlProjectStore.java`
- Create SQL resources: `common/src/main/resources/sql/project/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create: `common/src/test/java/org/aincraft/guilds/territory/project/DefaultProjectServiceTest.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/persist/SqlProjectStoreTest.java`

**Interfaces:**
- Aggregate uses optimistic `version` and unique contribution operation IDs.
- Completion writes an effect-outbox row in the same transaction.
- `ProjectEffectPort.apply(projectId, effectId, operationId)` is idempotent.

- [ ] **Step 1: Write failing persistence/crash tests**

Cover retry contribution, concurrent completion, crash before effect, effect retry, and startup reconciliation.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*Project*'
```

Expected: store/service absent.

- [ ] **Step 3: Implement aggregate persistence and outbox**

Reconcile unapplied effects on startup and periodically; unique `(project_id, effect_id)` prevents duplicate application.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Project*'
git add common/src
git commit -m "feat: persist settlement projects"
```

---

### Task 6: Add facility tiers and upkeep effects

**Files:**
- Modify: `api/src/main/java/org/aincraft/guilds/territory/model/SettlementFacility.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/model/FacilityTier.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/persist/PostgresFacilityStore.java`
- Modify SQL resources: `common/src/main/resources/sql/facility/create-postgres.sql`, `create-mysql.sql`, `select.sql`
- Create: `common/src/main/java/org/aincraft/guilds/territory/project/FacilityProjectEffectPort.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/upkeep/UpkeepAssessment.java`
- Modify tests: facility/upkeep test suites

**Interfaces:**
- Tier integer `[1, configuredMax]`; active/damaged state is separate.
- Upgrade effect uses optimistic version and applies `tier + 1` once.
- Upkeep takes configured per-tier cost.

- [ ] **Step 1: Write failing tier tests**

Cover legacy tier 1, invalid tier, concurrent upgrade, inactive retention, and upkeep coefficient.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*Facility*' --tests '*Upkeep*'
```

Expected: tier absent.

- [ ] **Step 3: Implement tier persistence/effect**

Preserve exact anchor semantics and never validate surrounding RP blocks.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Facility*' --tests '*Upkeep*'
git add api/src common/src
git commit -m "feat: add settlement facility tiers"
```

---

### Task 7: Add crash-safe project contributions and boards

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/project/ProjectContributionCoordinator.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/project/ProjectContributionReservationStore.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/project/BukkitProjectContributionPort.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/project/ProjectContributionRecoveryTask.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/project/ProjectBoardListener.java`
- Create SQL resources: `common/src/main/resources/sql/project-contribution/{create-postgres.sql,create-mysql.sql,select.sql}`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Create tests: project contribution coordinator, recovery, and listener tests

**Interfaces:**

```text
/territory project list [territory]
/territory project start <territory> <definition>
/territory project pause <project-id>
/territory project cancel <project-id>
/territory project recover <project-id>
```

Inventory reservation reuses the exact Release 1 PDC escrow protocol and states: `PREPARED`, `ESCROWED`, `APPLIED`, `REFUND_PENDING`, `REFUNDED`, `MANUAL_REVIEW`. It stores exact payload, pre-removal fingerprints, expected post-removal fingerprints, and checksum. Currency reservations use the existing economy idempotency ledger.

- [ ] **Step 1: Write failing contribution/recovery tests**

Cover local authority, accepted/remainder quantity, currency reservation, every Release 1 inventory escrow crash combination, offline reconciliation, completion announcement, retry, and manual-review exposure.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*ProjectContribution*' --tests '*ProjectBoard*'
```

Expected: missing Paper surface.

- [ ] **Step 3: Implement durable reservation and recovery**

Reuse the Release 1 escrow component rather than copying its state machine. Project application and reservation `APPLIED` commit together. `PREPARED` recovery advances only when escrow and exact post-removal fingerprints prove the debit; pre-removal fingerprints discard an escrow duplicate; mixed state becomes `MANUAL_REVIEW`. Currency compensation and item restoration occur exactly once.

- [ ] **Step 4: Implement board listener and admin commands**

Use Release 1 local governance for administration. Player contribution eligibility comes from explicit project config.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*Project*' --tests '*GuildsServicesWiringTest'
git add common/src paper/src
git commit -m "feat: add crash-safe settlement project boards"
```

---

### Task 8: Define durable guild warehouses

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/storage/GuildWarehouseService.java`
- Create: `api/src/main/java/org/aincraft/guilds/storage/WarehouseItem.java`
- Create: `api/src/main/java/org/aincraft/guilds/storage/WarehouseSnapshot.java`
- Create: `common/src/main/java/org/aincraft/guilds/storage/WarehouseStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/storage/DefaultGuildWarehouseService.java`
- Create: `common/src/main/java/org/aincraft/guilds/storage/SqlWarehouseStore.java`
- Create SQL resources: `common/src/main/resources/sql/warehouse/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create tests: `DefaultGuildWarehouseServiceTest`, `SqlWarehouseStoreTest`

**Interfaces:**
- Capacity unit: slots. One slot stores one exact serialized item identity up to max stack size.
- `deposit`, `withdraw`, `reserve`, `commitReservation`, and `releaseReservation` accept unique operation IDs.
- Capacity derives from active facility tiers plus standing modifiers.
- Withdrawals append immutable audit rows in the same transaction.

- [ ] **Step 1: Write failing aggregate tests**

Cover merge, capacity, custom metadata identity, partial rejection, reservation visibility/release/commit, duplicate operations, inactive denial, reduced-capacity retention, and audit rows.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*Warehouse*'
```

Expected: missing aggregate.

- [ ] **Step 3: Implement aggregate/store**

Use canonical item payload plus stable content hash. Lock the guild aggregate for mutation and persist state/audit atomically.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :api:test --tests '*Warehouse*'
./gradlew --no-daemon :common:test --tests '*Warehouse*'
git add api/src common/src
git commit -m "feat: add durable guild warehouses"
```

---

### Task 9: Add warehouse UI and permission adapter

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/storage/WarehouseInventoryController.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/WarehouseItemCodec.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/WarehouseListener.java`
- Create: `paper/src/main/java/org/aincraft/guilds/storage/WarehousePermissionAdapter.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/resources/guilds-config.yml`
- Create tests: warehouse codec/controller/listener tests

**Interfaces:**
- Active local `STORAGE` anchor opens the UI.
- Deposit/withdraw map to existing permissions; view-only cannot mutate.
- Each click invokes one versioned service operation; UI never blind-replaces the aggregate.

- [ ] **Step 1: Write failing codec/UI tests**

Round-trip vanilla/custom items; cover pagination, stale version, shift-click, permission denial, inactive/foreign facility, capacity, reopen, and SQL failure.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*Warehouse*'
```

Expected: Paper surface absent.

- [ ] **Step 3: Implement explicit-action UI**

Refresh from returned snapshots after each operation and route every mutation through the service.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*Warehouse*' --tests '*BuildingListener*'
git add paper/src
git commit -m "feat: add guild warehouse inventory UI"
```

---

### Task 10: Fulfill contracts from warehouse reservations

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/WarehouseContractInventoryPort.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/ContractFulfillmentCoordinator.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildContractBrigadierCommand.java`
- Modify tests: contract coordinator and warehouse adapter tests

**Interfaces:**

```text
/guild contract fulfill <contract-id> <player|warehouse>
```

One source per operation. Warehouse reservation and contract terminal transition share one SQL transaction when both use the shared database; release on failure remains durable.

- [ ] **Step 1: Write failing source tests**

Cover explicit source, no fallback, warehouse permission, reservation release, atomic commit, and retry.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*Contract*' --tests '*WarehouseContract*'
```

Expected: player source only.

- [ ] **Step 3: Implement warehouse adapter and transaction seam**

Reserve exact material identity and commit reservation with contract fulfillment. Reject ambiguous custom items unless configured.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*Contract*' --tests '*Warehouse*'
git add paper/src
git commit -m "feat: fulfill contracts from guild warehouses"
```

---

### Task 11: Define recurring invasion lifecycle and repair state

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/invasion/InvasionThreat.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/invasion/InvasionSchedule.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/invasion/SettlementDamage.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/invasion/InvasionLifecycleEngine.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/invasion/InvasionEngine.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/invasion/InvasionLifecycleEngineTest.java`

**Interfaces:**
- `ELIGIBLE -> WARNED -> ACTIVE -> DEFENDED|DEVASTATED|CANCELLED -> COOLDOWN -> ELIGIBLE`.
- Threat/schedule ticks use stable period IDs.
- Damage targets facility state and repair requirements; never warehouse contents.
- Interrupted `ACTIVE` still recovers to `CANCELLED`.

- [ ] **Step 1: Write failing lifecycle tests**

Cover eligibility, warning, duplicate tick, postponement, reward, damage, cooldown, restart cancellation, and repair completion.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*InvasionLifecycleEngineTest'
```

Expected: scheduler lifecycle absent.

- [ ] **Step 3: Implement pure lifecycle**

Produce adapter commands `Warn`, `Start`, `ApplyDamage`, `CreateRepairProject`, and `EnterCooldown`.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Invasion*'
git add common/src
git commit -m "feat: add recurring settlement invasion lifecycle"
```

---

### Task 12: Persist and schedule invasions

**Files:**
- Modify: `common/src/main/java/org/aincraft/guilds/territory/invasion/PostgresInvasionStore.java`
- Modify SQL resources: `common/src/main/resources/sql/invasion/create-postgres.sql`, `create-mysql.sql`, `select.sql`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/invasion/InvasionScheduleTask.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/invasion/InvasionConfigLoader.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/invasion/InvasionRuntime.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Modify: `paper/src/main/resources/config.yml`
- Modify/create invasion store/scheduler tests

**Interfaces:**
- One global task scans due settlements.
- Start delegates to existing validated runtime; no duplicate spawn path.
- Invalid schedule config disables automatic scheduling only.

- [ ] **Step 1: Write failing persistence/scheduler tests**

Cover restart, duplicate period, warning, start delegation, postponement, cancellation, and task shutdown.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*PostgresInvasionStoreTest'
./gradlew --no-daemon :paper:test --tests '*InvasionScheduleTaskTest'
```

Expected: fields/task absent.

- [ ] **Step 3: Extend store/config and wire task**

Persist lifecycle, threat, timestamps, and operation IDs. Reuse existing admin runtime.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Invasion*'
./gradlew --no-daemon :paper:test --tests '*Invasion*'
git add common/src paper/src
git commit -m "feat: schedule recurring guild invasions"
```

---

### Task 13: Apply invasion rewards, damage, and repairs

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/invasion/InvasionOutcomeCoordinator.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/project/RepairProjectFactory.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/invasion/InvasionRuntime.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Create outcome/repair tests

**Interfaces:**
- One reward/damage operation ID per invasion.
- Damage reduces configured facility tier/status within bounds and creates one repair project.
- Repair restores only recorded damage.

- [ ] **Step 1: Write failing outcome tests**

Cover reward once, damage once, no storage deletion, exact repair requirements, idempotent repair, and cancelled no-op.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*RepairProjectFactoryTest'
./gradlew --no-daemon :paper:test --tests '*InvasionOutcomeCoordinatorTest'
```

Expected: coordinator absent.

- [ ] **Step 3: Implement effect coordination**

Use project outbox and operator-visible reconciliation for pending downstream effects.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Repair*' --tests '*Project*'
./gradlew --no-daemon :paper:test --tests '*Invasion*'
git add common/src paper/src
git commit -m "feat: repair settlement invasion damage"
```

---

### Task 14: Document and verify Living Settlements

**Files:**
- Modify: `README.md`
- Modify: `docs/living-specs/{standing,economy,guild-storage,territory,guilds}.md`
- Create: `docs/operations/release-2-smoke-test.md`

**Interfaces:**
- Produces player/operator docs and direct evidence for every Release 2 acceptance criterion.

- [ ] **Step 1: Update documentation**

Document standing, rewards, projects, tiers, warehouse permissions/capacity/audit, contract source selection, and invasion repair/recovery.

- [ ] **Step 2: Run full automated gate**

```bash
./gradlew --no-daemon clean check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run actual Paper smoke scenarios**

Verify standing reward effect and ownership preservation; project contribution/restart/completion; facility upgrade; warehouse capacity/inactivity retention; player and warehouse contract sources; automatic invasion warning/start/outcome/cooldown; repair project restoration.

- [ ] **Step 4: Request code review and resolve findings**

Use requesting-code-review; fix each concern atomically and rerun affected scenarios.

- [ ] **Step 5: Re-run completion gate**

```bash
./gradlew --no-daemon check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit docs/evidence**

```bash
git add README.md docs/living-specs docs/operations/release-2-smoke-test.md
git commit -m "docs: publish Living Settlements gameplay"
```
