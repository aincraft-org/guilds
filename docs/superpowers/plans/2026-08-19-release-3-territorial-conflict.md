# Release 3 Territorial Conflict Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect influence, settlement investment, guild organization, and fortification through a restart-safe territorial war with deterministic Minecraft-native capture objectives.

**Architecture:** Build a Paper-free war aggregate/state machine in `api`/`common`, persisted through shared SQL with stable operation IDs and an outbox for cross-domain effects. Paper owns roster commands, scheduling, region detection, player transport, respawn waves, bossbars, and cleanup. War result application uses an idempotent saga that updates territory governance, influence, policies, upkeep, facilities, standing projections, API, and squaremap or records an operator-visible reconciliation.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper 26.2, JUnit 5, Mockito, HikariCP, PostgreSQL 16+, MySQL 8.0, Adventure Components, squaremap 1.3.15.

## Global Constraints

- Releases 1 and 2 are prerequisites.
- Influence is the only declaration-threshold source.
- One territory has at most one scheduled or active war slot, enforced in SQL and domain validation.
- War state is Paper-free and deterministic; Bukkit state is runtime projection only.
- A battle result commits exactly once before ownership effects begin.
- Cross-domain effects use durable idempotent operation records.
- Fortification money uses `EconomyBridge.chargeExpense`; no second treasury path.
- Personal territory standing survives transfer.
- Alliance relationships grant explicit war effects only, never implicit local land rights.
- Cleanup occurs on victory, defeat, cancellation, disable, and restart.
- Tests are observed RED before implementation and commit green with behavior.

---

### Task 1: Define war lifecycle and invariants

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarId.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/TerritoryWar.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarStatus.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarResult.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarRoster.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarRules.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/war/WarRulesTest.java`

**Interfaces:**

```java
public enum WarStatus {
    DECLARED, PREPARING, ROSTER_LOCKED, ACTIVE,
    ATTACKER_VICTORY, DEFENDER_VICTORY, CANCELLED,
    RECONCILIATION_REQUIRED
}

public record TerritoryWar(
        WarId id,
        String territoryId,
        String attackerGuildId,
        String defenderGuildId,
        Instant declaredAt,
        Instant scheduledAt,
        WarStatus status,
        WarRoster attackerRoster,
        WarRoster defenderRoster,
        long version
) {}
```

- [ ] **Step 1: Write failing lifecycle tests**

Cover transitions, terminal immutability, schedule floor, roster uniqueness/team limits, lock, cancellation policy, and version conflicts.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*WarRulesTest'
```

Expected: domain absent.

- [ ] **Step 3: Implement immutable contracts/rules**

Pass `Instant now`; use exhaustive transition switches.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :api:test --tests '*War*'
./gradlew --no-daemon :common:test --tests '*WarRulesTest'
git add api/src common/src
git commit -m "feat: define territorial war lifecycle"
```

---

### Task 2: Persist wars and enforce one territory slot

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/SqlWarStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/DefaultWarService.java`
- Create SQL resources: `common/src/main/resources/sql/war/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create tests: `SqlWarStoreTest`, `DefaultWarServiceTest`

**Interfaces:**
- Unique non-terminal war slot per territory.
- Versioned writes reject stale transitions.
- Scheduler operation IDs prevent duplicate starts.

- [ ] **Step 1: Write failing persistence/concurrency tests**

Cover round trip, concurrent declarations, duplicate start tick, transition retry, terminal result once, and restart load.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*War*'
```

Expected: store absent.

- [ ] **Step 3: Implement dialect stores/service**

Use row locks/versions plus dialect-safe unique slot enforcement.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*War*'
git add common/src
git commit -m "feat: persist territorial wars"
```

---

### Task 3: Turn influence declarations into wars

**Files:**
- Modify: `common/src/main/java/org/aincraft/guilds/territory/influence/InfluenceEngine.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarDeclarationService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/influence/InfluenceStatusFormatter.java`
- Create/modify declaration and formatter tests

**Interfaces:**
- Declaration consumes existing influence eligibility and Release 1 federal authority.
- Success creates one `DECLARED` war and reserves war slot.
- Ownership no longer flips at declaration.

- [ ] **Step 1: Write failing declaration tests**

Cover threshold, cooldown, authority, occupied slot, absent defender, retry, unavailable governance, and atomic operation.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*WarDeclaration*' --tests '*Influence*'
```

Expected: no war coordinator.

- [ ] **Step 3: Implement declaration coordination**

Preserve accrual; move ownership change to result saga only.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*WarDeclaration*' --tests '*Influence*'
./gradlew --no-daemon :paper:test --tests '*InfluenceStatusFormatterTest'
git add common/src paper/src
git commit -m "feat: turn influence declarations into wars"
```

---

### Task 4: Add minimal diplomacy

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/diplomacy/DiplomaticRelation.java`
- Create: `api/src/main/java/org/aincraft/guilds/diplomacy/DiplomacyService.java`
- Create: `common/src/main/java/org/aincraft/guilds/diplomacy/DefaultDiplomacyService.java`
- Create: `common/src/main/java/org/aincraft/guilds/diplomacy/SqlDiplomacyStore.java`
- Create SQL resources: `common/src/main/resources/sql/diplomacy/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create diplomacy tests

**Interfaces:**

```java
public enum DiplomaticRelation { ALLIED, NEUTRAL, HOSTILE, TRUCE }
```

Relations are symmetric/versioned/audited. Truce expires to neutral. Current alliance membership implies allied and cannot be overridden.

- [ ] **Step 1: Write failing relation tests**

Cover symmetry, alliance implication, hostile, truce expiry, retry, unavailable data, and audit.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :api:test --tests '*Diplomacy*'
./gradlew --no-daemon :common:test --tests '*Diplomacy*'
```

Expected: absent.

- [ ] **Step 3: Implement minimal service/store**

Do not add treaties, embargoes, or reputation.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Diplomacy*'
git add api/src common/src
git commit -m "feat: add war diplomacy relations"
```

---

### Task 5: Add roster eligibility and commands

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarRosterService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarRosterEligibilityAdapter.java`
- Create: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/WarBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java`
- Create roster/command tests

**Interfaces:**

```text
/war status [territory]
/war roster join <war-id> <attacker|defender>
/war roster leave <war-id>
/war roster add <war-id> <player> <side>
/war roster remove <war-id> <player>
/war roster lock <war-id>
```

Eligibility resolves current membership/diplomacy per mutation; player cannot occupy both sides.

- [ ] **Step 1: Write failing roster tests**

Cover membership, ally support, neutral/truce denial, duplicate side, cap, lock, cutoff, reconnect identity, unavailable data.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*WarRoster*'
./gradlew --no-daemon :paper:test --tests '*WarCommandTest'
```

Expected: absent.

- [ ] **Step 3: Implement service/adapter/commands**

Never cache eligibility across mutations.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*WarRoster*'
./gradlew --no-daemon :paper:test --tests '*WarCommandTest'
git add common/src paper/src
git commit -m "feat: add territorial war rosters"
```

---

### Task 6: Define capture objective state machine

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarObjective.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/ObjectiveStatus.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/CaptureRules.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarBattleEngine.java`
- Create capture/engine tests

**Interfaces:**
- Outdoor captures unlock the final fort point.
- Progress consumes elapsed duration, eligible side counts, and fortification modifier.
- Timer expiry yields defender victory; final capture yields attacker victory.

- [ ] **Step 1: Write failing objective tests**

Cover empty/contested capture, advantage, decay, ordering, lock, modifier, duplicate tick, timer, terminal state.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*CaptureRules*' --tests '*WarBattleEngine*'
```

Expected: absent.

- [ ] **Step 3: Implement pure engine**

Clamp progress `[0,1]`; persist accepted tick IDs.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*War*'
git add api/src common/src
git commit -m "feat: add war capture objectives"
```

---

### Task 7: Configure and validate war fields

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/war/WarField.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarFieldStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/SqlWarFieldStore.java`
- Create SQL resources: `common/src/main/resources/sql/war-field/{create-postgres.sql,create-mysql.sql,select.sql}`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Create field/store/command tests

**Interfaces:**

```text
/territory warfield set-staging <territory> <attacker|defender>
/territory warfield add-point <territory> <point-id> <outdoor|fort>
/territory warfield remove-point <territory> <point-id>
/territory warfield validate <territory>
```

Regions lie inside territory; staging/objectives do not overlap; one final fort point exists.

- [ ] **Step 1: Write failing geometry tests**

Cover outside, overlap, missing final, duplicate IDs, world mismatch, atomic replacement, valid field.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*WarField*'
./gradlew --no-daemon :paper:test --tests '*WarField*CommandTest'
```

Expected: absent.

- [ ] **Step 3: Implement using existing territory geometry**

Do not create a second spatial registry.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*WarField*'
./gradlew --no-daemon :paper:test --tests '*WarField*'
git add api/src common/src paper/src
git commit -m "feat: configure territorial war fields"
```

---

### Task 8: Add Paper battle runtime and HUD

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarRuntime.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarParticipantTracker.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarObjectiveTask.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarBossBars.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Create runtime/tracker/task/bossbar tests

**Interfaces:**
- Tracker counts rostered, online, alive players in bounds.
- Runtime persists transitions before broadcasting.
- No per-player polling task.

- [ ] **Step 1: Write failing runtime tests**

Cover filtering, entry/exit, world change, death, spectator, objective tick, UI audience/progress, disable, restart discovery.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*WarRuntime*' --tests '*WarObjective*' --tests '*WarBossBars*'
```

Expected: absent.

- [ ] **Step 3: Implement runtime/UI**

Convert Paper positions to pure engine facts; render committed snapshots only.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*WarRuntime*' --tests '*WarObjective*' --tests '*WarBossBars*'
git add paper/src
git commit -m "feat: run territorial capture battles"
```

---

### Task 9: Add respawn, reconnect, AFK, and staging protection

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarRespawnService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarReconnectService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarAfkMonitor.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarProtectionListener.java`
- Modify: `paper/src/main/resources/config.yml`
- Create lifecycle/protection tests

**Interfaces:**
- Death queues next respawn wave to side staging.
- Reconnect grace restores state; expired grace follows configured spectator policy.
- AFK removes capture contribution before optional roster ejection.

- [ ] **Step 1: Write failing lifecycle tests**

Cover wave boundary, safe teleport, reconnect, repeated disconnect, AFK exclusion, staging protection, cleanup.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*WarRespawn*' --tests '*WarReconnect*' --tests '*WarAfk*' --tests '*WarProtection*'
```

Expected: absent.

- [ ] **Step 3: Implement player lifecycle adapters**

Use Paper/Folia entity schedulers for player mutations.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*WarRespawn*' --tests '*WarReconnect*' --tests '*WarAfk*' --tests '*WarProtection*'
git add paper/src
git commit -m "feat: manage territorial war participants"
```

---

### Task 10: Enforce war-side friendly fire

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarFriendlyFireListener.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/war/WarRosterEligibilityAdapter.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/listener/ProtectionListener.java`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/war/WarFriendlyFireListenerTest.java`

**Interfaces:**
- Active-war side identity controls participant combat inside field.
- Same side denies; opposite allows; nonparticipant falls through to ordinary protection.

- [ ] **Step 1: Write failing matrix tests**

Cover same/opposite/nonparticipant, ally, truce, neutral, hostile, unavailable diplomacy, inactive battle.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*WarFriendlyFire*'
```

Expected: ordinary PvP path controls all.

- [ ] **Step 3: Implement scoped override**

Override only active participants inside war field.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*WarFriendlyFire*' --tests '*ProtectionListener*'
git add paper/src
git commit -m "feat: enforce war-side friendly fire"
```

---

### Task 11: Fund and apply fortifications

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/FortificationState.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/FortificationService.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/FortificationRules.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/SqlFortificationStore.java`
- Create SQL resources: `common/src/main/resources/sql/fortification/{create-postgres.sql,create-mysql.sql,select.sql}`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/project/FacilityProjectEffectPort.java`
- Create fortification tests

**Interfaces:**
- Effects: capture resistance, defender respawn modifier, supply access, repair level.
- Expense key: `fortification:<territory>:<level>:<operation>`.
- Project and expense success precede level increase.

- [ ] **Step 1: Write failing tests**

Cover bounds, calculation, insufficient treasury, duplicate expense, concurrent level, prerequisite, damage, repair.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*Fortification*'
```

Expected: absent.

- [ ] **Step 3: Implement service/store/adapter**

War engine consumes immutable facts and never debits treasury.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*Fortification*' --tests '*EconomyBridgeExpenseTest'
git add common/src
git commit -m "feat: fund territorial fortifications"
```

---

### Task 12: Implement idempotent war result saga

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarResultSaga.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarResultStep.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/WarResultReconciliation.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/war/SqlWarResultStore.java`
- Create SQL resources: `common/src/main/resources/sql/war-result/{create-postgres.sql,create-mysql.sql,select.sql}`
- Create: `common/src/test/java/org/aincraft/guilds/territory/war/WarResultSagaTest.java`

**Interfaces:**
- Steps: result, territory binding, influence cooldown, policy deactivation, upkeep handoff, facility damage, standing projection, map/API refresh.
- Each step has stable operation ID; failure stops and exposes reconciliation; retry resumes first incomplete.

- [ ] **Step 1: Write failing saga tests**

Cover both victories, failure at each step, retry, duplicate/conflicting result, restart resume.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*WarResultSagaTest'
```

Expected: absent.

- [ ] **Step 3: Implement durable journal**

Persist result before effects; every adapter is idempotent by war-derived operation ID.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*WarResult*'
git add common/src
git commit -m "feat: reconcile territorial war results"
```

---

### Task 13: Apply transfer effects and projections

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/PaperWarResultAdapters.java`
- Modify existing territory/influence/policy/upkeep/facility/standing APIs only for required idempotent overloads
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/squaremap/TerritorySquaremapBridge.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/web/TerritoryApiHandler.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Create adapter/API/map/recovery tests

**Interfaces:**

```text
/territory war reconcile <war-id>
/territory war result <war-id>
```

Personal standing survives; facilities remain with damage; old local policies deactivate; upkeep changes next period; influence resets/cools down.

- [ ] **Step 1: Write failing adapter/projection tests**

Cover every semantic, duplicate call, downstream failure, pending projection, operator retry.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :common:test --tests '*War*Api*'
./gradlew --no-daemon :paper:test --tests '*WarResult*' --tests '*Squaremap*'
```

Expected: absent.

- [ ] **Step 3: Implement adapters/projections**

Commands call services, never direct SQL.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :common:test --tests '*War*'
./gradlew --no-daemon :paper:test --tests '*War*' --tests '*Squaremap*'
git add common/src paper/src
git commit -m "feat: apply territorial war transfers"
```

---

### Task 14: Schedule, cancel, and recover wars

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarScheduleTask.java`
- Create: `paper/src/main/java/org/aincraft/guilds/territory/war/WarRecoveryService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Modify: `paper/src/main/resources/config.yml`
- Create scheduler/recovery tests

**Interfaces:**
- Scheduler locks due war and writes start operation ID.
- Cancellation follows configured pre-active/active policy and cleans runtime.
- Recovery cleans stale projections then resumes durable active state within configured window or cancels deterministically.

- [ ] **Step 1: Write failing tests**

Cover duplicate tick, missing world, invalid field, roster shortfall, start, active restart, stale UI, resume window, cancel, disable.

- [ ] **Step 2: Run RED**

```bash
./gradlew --no-daemon :paper:test --tests '*WarSchedule*' --tests '*WarRecovery*'
```

Expected: absent.

- [ ] **Step 3: Implement scheduler/recovery**

Never infer victory from missing runtime objects.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew --no-daemon :paper:test --tests '*WarSchedule*' --tests '*WarRecovery*' --tests '*WarRuntime*'
git add paper/src
git commit -m "feat: recover scheduled territorial wars"
```

---

### Task 15: Document and verify Territorial Conflict

**Files:**
- Modify: `README.md`
- Modify: `docs/living-specs/{influence,governance,economy,territory,map,guilds}.md`
- Create: `docs/living-specs/war.md`
- Create: `docs/operations/release-3-smoke-test.md`

**Interfaces:**
- Produces player/admin reference and direct Release 3 evidence.

- [ ] **Step 1: Document commands/rules/recovery**

Cover declaration, rosters, objectives, respawn/reconnect/AFK, diplomacy, fortification, transfer, cancellation, reconciliation, permissions.

- [ ] **Step 2: Run automated gate**

```bash
./gradlew --no-daemon clean check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run attacker-victory Paper smoke**

Drive influence, declare, schedule, roster, capture, verify one result and all transfer projections.

- [ ] **Step 4: Run defender/cancellation/restart smoke**

Verify timer defense, pre-active/active cancellation, reconnect, active restart, stale cleanup, reconciliation retry.

- [ ] **Step 5: Request code review and resolve findings**

Use requesting-code-review; fix atomically and rerun affected scenarios.

- [ ] **Step 6: Re-run completion gate**

```bash
./gradlew --no-daemon check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit docs/evidence**

```bash
git add README.md docs/living-specs docs/operations/release-3-smoke-test.md
git commit -m "docs: publish Territorial Conflict gameplay"
```
