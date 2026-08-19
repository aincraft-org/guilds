# Release 1 Stable Government Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a green, fail-closed Guilds release with scope-aware governance, one land-permission pipeline, usable guild contracts, operator recovery surfaces, and verified Paper behavior.

**Architecture:** Rebase the approved scope-aware governance design onto the current `org.aincraft.guilds` package tree and versioned SQL migration system. Keep authorization decisions pure in `api`/`common`, adapt Guilds membership and plots in `paper`, and route every actor-facing listener through one resolver. Complete contracts through a Paper inventory coordinator around the transactional SQL service; keep money and material state changes idempotent and observable.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper 26.2, JUnit 5, Mockito, HikariCP, PostgreSQL 16+, MySQL 8.0, Error Prone 2.50.0, SpotBugs, PMD, Checkstyle.

## Global Constraints

- Preserve `api` as public Paper-free contracts, `common` as Paper-free rules/persistence, and `paper` as commands/listeners/world adapters.
- Guild government controls local membership, plots, commons, facilities, and local policy.
- Alliance government controls alliance membership, alliance policy, influence declarations, and federal strategy; it never grants implicit local land rights.
- Invalid, unresolved, or unavailable governance data denies ordinary actions and remains distinguishable in decisions and operator output.
- One current membership source of truth remains in SQL; a guild belongs to at most one alliance.
- Money movement stays on existing Guilds/economy transaction paths. Contract item removal is coordinated with SQL state through explicit reservation/refund behavior.
- Durable state commits before memory/world authority changes. Retries cannot apply a debit, escrow release, influence reset, or contract transition twice.
- PostgreSQL and MySQL remain supported; every schema change uses versioned SQL resources for both dialects or one dialect-neutral migration.
- Tests are observed failing before implementation; each behavior and its tests land in one green atomic commit.
- Do not weaken tests or static analysis to obtain a pass. An Error Prone analyzer exception is a toolchain defect unless a minimal reproduction proves a source defect.
- Runtime verification claims require an actual Paper server and configured SQL database.

---

### Task 1: Pin the supported build toolchain and restore compilation

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildBankEnrollmentServiceImpl.java`
- Test: `paper/src/test/java/org/aincraft/guilds/services/GuildBankEnrollmentServiceTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: current Gradle Java toolchain and Error Prone configuration.
- Produces: one documented JDK version used by local checks and CI; UTC timestamps from an injected `Clock` in `GuildBankEnrollmentServiceImpl`.

- [ ] **Step 1: Reproduce and classify both build findings**

Run:

```bash
./gradlew --no-daemon :paper:compileJava --stacktrace
```

Expected: the source diagnostic identifies `LocalDateTime.now()`; the separate `StringConcatToTextBlock` stack trace originates inside Error Prone 2.50.0. Record the Gradle JVM and toolchain from `./gradlew --version` in the task notes.

- [ ] **Step 2: Add a failing timestamp test**

Add a package-visible constructor accepting `java.time.Clock` to the test expectation, then freeze the clock:

```java
private static final Instant NOW = Instant.parse("2026-08-19T12:34:56Z");
private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

@Test
void enrollmentPersistsUtcTimestampFromClock() {
    GuildBankEnrollmentServiceImpl service = service(CLOCK);
    assertTrue(service.enroll("guild-a").successful());
    assertEquals("2026-08-19T12:34:56", storedUpdatedAt("guild-a"));
}
```

Use the existing fixture/result accessors rather than inventing alternate enrollment semantics.

- [ ] **Step 3: Run the focused test RED**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildBankEnrollmentServiceTest.enrollmentPersistsUtcTimestampFromClock'
```

Expected: compilation fails because the clock-aware constructor does not exist.

- [ ] **Step 4: Inject the clock and remove implicit time-zone use**

Implement:

```java
private final Clock clock;

public GuildBankEnrollmentServiceImpl(DatabaseManager databaseManager) {
    this(databaseManager, Clock.systemUTC());
}

GuildBankEnrollmentServiceImpl(DatabaseManager databaseManager, Clock clock) {
    this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    this.clock = Objects.requireNonNull(clock, "clock");
}

private String now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
}
```

Replace every `LocalDateTime.now()` in this class with `now()`.

- [ ] **Step 5: Stabilize Error Prone without source camouflage**

First run compilation on the declared Java toolchain. If `StringConcatToTextBlock` still crashes, disable only that analyzer check in the existing Error Prone options:

```kotlin
errorprone.disable("StringConcatToTextBlock")
```

Add a comment linking the exact Error Prone version and explaining that this is a crashing suggestion check, not an application suppression. Do not alter the SQL literal solely to evade the analyzer.

- [ ] **Step 6: Document and verify the build contract**

Document the required JDK and commands in README. Run:

```bash
./gradlew --no-daemon :paper:test --tests '*GuildBankEnrollmentServiceTest'
./gradlew --no-daemon :paper:compileJava
```

Expected: both end `BUILD SUCCESSFUL`; no `JavaTimeDefaultTimeZone` diagnostic appears.

- [ ] **Step 7: Commit the green build unit**

```bash
git add build.gradle.kts gradle.properties README.md \
  paper/src/main/java/org/aincraft/guilds/services/impl/GuildBankEnrollmentServiceImpl.java \
  paper/src/test/java/org/aincraft/guilds/services/GuildBankEnrollmentServiceTest.java
git commit -m "fix: stabilize Guilds build toolchain"
```

---

### Task 2: Make governance snapshots strict and action-aware

**Files:**
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/GuildBody.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/AllianceBody.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/MemberPermissions.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/PermissionRules.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/SovereignAction.java`
- Create: `api/src/test/java/org/aincraft/guilds/territory/permission/GuildBodyTest.java`
- Create: `api/src/test/java/org/aincraft/guilds/territory/permission/AllianceBodyTest.java`
- Create: `api/src/test/java/org/aincraft/guilds/territory/permission/PermissionRulesTest.java`
- Migrate: all LSP-reported callers in `api/src`, `common/src`, and `paper/src`

**Interfaces:**
- Produces: `GuildBody.containsMember(String)`, membership-checked `GuildBody.permissionsOf(String)`, strict `AllianceBody.containsGuild(String)`, and action-aware `PermissionRules.allows(Government, String, SovereignAction)`.
- Invariant: construction rejects stale permission subjects, duplicate members/delegates, and missing required authority holders.

- [ ] **Step 1: Inventory exported call sites with LSP**

Use LSP references for `GuildBody`, `AllianceBody`, `permissionsOf`, `PermissionRules.allows`, and `SovereignAction`. Record every constructor and permission caller before editing.

- [ ] **Step 2: Write failing snapshot tests**

Add these contracts:

```java
@Test
void permissionsOfRejectsDepartedSubject() {
    GuildBody body = guildBody(List.of("mayor"), Map.of("departed", MemberPermissions.fullBypass()));
    assertThrows(IllegalArgumentException.class, body::validate);
}

@Test
void unknownSubjectHasNoPermissions() {
    GuildBody body = guildBody(List.of("mayor"), Map.of());
    assertTrue(body.permissionsOf("departed").isEmpty());
}

@Test
void allianceRejectsDuplicateGuildMembership() {
    assertThrows(IllegalArgumentException.class,
            () -> allianceBody(List.of("guild-a", "guild-a")));
}
```

Adapt construction helpers to the existing record/class signatures; the observable assertions are fixed.

- [ ] **Step 3: Write failing action tests**

Parameterize government form, holder role, and action. At minimum assert:

```java
assertTrue(PermissionRules.allows(monarchy, "mayor", SovereignAction.SET_POLICY));
assertFalse(PermissionRules.allows(monarchy, "mayor", SovereignAction.MANAGE_MEMBERSHIP)
        && !monarchyHasMembershipAuthority);
assertFalse(PermissionRules.allows(monarchy, "outsider", SovereignAction.BREAK_BLOCK));
```

Where the current domain intentionally grants the sovereign both governmental actions, encode that exact matrix. The key contract is that `action` participates in the decision and unsupported actions deny.

- [ ] **Step 4: Run API tests RED**

```bash
./gradlew --no-daemon :api:test --tests '*GuildBodyTest' --tests '*AllianceBodyTest' --tests '*PermissionRulesTest'
```

Expected: failures show stale grants, duplicate memberships, or ignored actions.

- [ ] **Step 5: Implement strict immutable snapshots**

- Defensively copy every member, role, and permissions collection.
- Reject null/blank IDs and duplicate member IDs.
- Reject permission keys not present in the current member set.
- Make `permissionsOf` return empty for non-members before map lookup.
- Require alliance capital to be a member guild.
- Reject duplicate alliance guilds and duplicate democracy delegates.

Implement action-aware permission selection with an exhaustive switch over `SovereignAction`; do not use a default allow branch.

- [ ] **Step 6: Migrate callers and run GREEN tests**

```bash
./gradlew --no-daemon :api:test :common:test --tests '*GovernanceRegistryTest' --tests '*FormPermissionsMatrixSmokeTest'
```

Expected: `BUILD SUCCESSFUL`; all snapshot consumers use validated membership.

- [ ] **Step 7: Commit strict governance models**

```bash
git add api/src common/src paper/src
git commit -m "fix: validate governance membership and actions"
```

---

### Task 3: Resolve local and alliance governance explicitly

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/GovernanceContext.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/GovernanceResolutionStatus.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/GovernanceLookup.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/GovernanceSource.java`
- Modify: `api/src/main/java/org/aincraft/guilds/territory/permission/GoverningBody.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/permission/GovernanceRegistry.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/permission/FakeGovernanceSource.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/permission/GovernanceRegistryTest.java`
- Migrate: all LSP-reported callers of ambiguous effective-government methods

**Interfaces:**
- Produces:

```java
public enum GovernanceResolutionStatus {
    UNCONTAINED,
    LOCAL_TERRITORY,
    BOUND_GUILD,
    BOUND_GUILD_UNRESOLVED,
    ALLIANCE_UNRESOLVED,
    DATA_UNAVAILABLE
}

public sealed interface GovernanceLookup<T>
        permits GovernanceLookup.Found, GovernanceLookup.Missing, GovernanceLookup.Unavailable {
    record Found<T>(T value) implements GovernanceLookup<T> {}
    record Missing<T>() implements GovernanceLookup<T> {}
    record Unavailable<T>(String message) implements GovernanceLookup<T> {}
}

public record GovernanceContext(
        GovernanceResolutionStatus status,
        GoverningBody local,
        Optional<AllianceBody> alliance
) {}
```

- `GovernanceRegistry.contextForTerritory(String)` and `contextAt(String, int, int)` return explicit context.
- Stored guild binding never falls back to territory-local authority when unresolved.

- [ ] **Step 1: Write failing resolution tests**

Cover every status, including:

```java
@Test
void missingBoundGuildFailsClosed() {
    registerBoundTerritory("everfall", "missing-guild");
    GovernanceContext context = registry.contextForTerritory("everfall");
    assertEquals(GovernanceResolutionStatus.BOUND_GUILD_UNRESOLVED, context.status());
}

@Test
void unavailableGuildDataIsDistinctFromMissingGuild() {
    source.failGuildLookup("everfall-guild", "database unavailable");
    assertEquals(GovernanceResolutionStatus.DATA_UNAVAILABLE,
            registry.contextForTerritory("everfall").status());
}
```

Also assert an unresolved alliance leaves the local guild identifiable but denies federal authority.

- [ ] **Step 2: Run registry tests RED**

```bash
./gradlew --no-daemon :common:test --tests '*GovernanceRegistryTest'
```

Expected: compilation or assertions fail because current lookup cannot represent all states.

- [ ] **Step 3: Implement typed source results and context**

Change source reads to `GovernanceLookup<GuildBody>` and `GovernanceLookup<AllianceBody>`. Resolve stored bindings exactly once. Preserve unavailable messages for logging; never cache `Unavailable` as an empty body.

- [ ] **Step 4: Remove ambiguous effective-government methods**

Use LSP references for `resolveForTerritory`, `effectiveGovernmentForTerritory`, `governingGuildForTerritory`, and location variants. Migrate each caller to explicit `context.local()` or `context.alliance()` selection, then delete the ambiguous method.

- [ ] **Step 5: Run API/common tests GREEN**

```bash
./gradlew --no-daemon :api:test :common:test
```

Expected: `BUILD SUCCESSFUL`; no stored binding silently falls back.

- [ ] **Step 6: Commit typed governance resolution**

```bash
git add api/src common/src
git commit -m "refactor: resolve governance scopes explicitly"
```

---

### Task 4: Scope policy and influence authority

**Files:**
- Modify: `api/src/main/java/org/aincraft/guilds/territory/model/PolicyRules.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/permission/GovernanceRegistry.java`
- Modify: `common/src/main/java/org/aincraft/guilds/territory/influence/InfluenceEngine.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/permission/GovernanceRegistryTest.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/influence/InfluenceEngineLifecycleTest.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/influence/InfluenceEngineAccrualTest.java`

**Interfaces:**
- `GovernanceRegistry.policyGovernment(String territoryId)` returns typed federal policy authority.
- `GovernanceRegistry.influenceGovernment(String guildId)` returns alliance authority when allied, local guild authority otherwise.
- `DATA_UNAVAILABLE` and `ALLIANCE_UNRESOLVED` deny policy/declaration mutations with explicit results.

- [ ] **Step 1: Write failing scoped-authority tests**

```java
@Test
void alliedGuildDeclarationUsesAllianceAuthority() {
    setupAlliedAttacker("attacker", "guild-mayor", "alliance-king");
    assertEquals(DeclareStatus.NOT_AUTHORIZED,
            declareAs("guild-mayor").status());
    assertNotEquals(DeclareStatus.NOT_AUTHORIZED,
            declareAs("alliance-king").status());
}
```

Also cover independent guild authority, unresolved alliance, unavailable data, and local policy voting.

- [ ] **Step 2: Run scoped tests RED**

```bash
./gradlew --no-daemon :common:test --tests '*GovernanceRegistryTest' --tests '*InfluenceEngine*'
```

Expected: current authority selection grants the wrong scope or cannot express the failure.

- [ ] **Step 3: Implement explicit scope selection**

Select alliance authority only for federal policy/influence operations. Local land and local plot decisions must never call these selectors.

- [ ] **Step 4: Run GREEN tests and commit**

```bash
./gradlew --no-daemon :common:test --tests '*GovernanceRegistryTest' --tests '*InfluenceEngine*'
git add api/src common/src
git commit -m "fix: scope policy and influence authority"
```

Expected: `BUILD SUCCESSFUL` before commit.

---

### Task 5: Introduce one local land-permission decision pipeline

**Files:**
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/LandAction.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/LandPermissionRequest.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/PermissionDecision.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/PermissionDecisionReason.java`
- Create: `api/src/main/java/org/aincraft/guilds/territory/permission/LandPermissionResolver.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/permission/LandPermissionFacts.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/permission/LandPermissionRules.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/permission/LandPermissionRulesTest.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/permission/FormPermissionsMatrixSmokeTest.java`
- Modify: `common/src/test/java/org/aincraft/guilds/territory/permission/BlockProtectionTest.java`

**Interfaces:**

```java
public enum LandAction {
    BREAK_BLOCK,
    PLACE_BLOCK,
    INTERACT,
    INTERACT_ENTITY,
    ENTER,
    PVP_ATTACK
}

public record LandPermissionRequest(
        String actorId,
        String worldId,
        int blockX,
        int blockZ,
        LandAction action
) {}

public record PermissionDecision(
        boolean allowed,
        PermissionDecisionReason reason,
        String message,
        Optional<String> localGuildId
) {}
```

`PermissionDecisionReason` includes `WILDERNESS`, `ADMIN_BYPASS`, `BOUND_GUILD_UNRESOLVED`, `ALLIANCE_UNRESOLVED`, `DATA_UNAVAILABLE`, `LOCAL_ANARCHY`, `PLOT_OWNER`, `LOCAL_AUTHORITY`, `PLOT_GRANT`, `GUILD_GRANT`, `MEMBER_DEFAULT`, `PUBLIC_DEFAULT`, and `DENIED`.

- [ ] **Step 1: Write the full failing permission matrix**

Parameterize local form, actor kind, action, and result. Required rows:

```java
arguments(MONARCHY, ALLIANCE_OFFICER, BREAK_BLOCK, false),
arguments(OLIGARCHY, SIBLING_MEMBER, PLACE_BLOCK, false),
arguments(DEMOCRACY, LOCAL_MEMBER, BREAK_BLOCK, true),
arguments(MONARCHY, LOCAL_MEMBER, INTERACT, true),
arguments(MONARCHY, PUBLIC_OUTSIDER, BREAK_BLOCK, false),
arguments(MONARCHY, PUBLIC_OUTSIDER, PLACE_BLOCK, true)
```

Repeat every row under all alliance forms and assert the local result is unchanged. Add unresolved and unavailable denial reasons.

- [ ] **Step 2: Run matrix tests RED**

```bash
./gradlew --no-daemon :common:test --tests '*LandPermissionRulesTest' --tests '*FormPermissionsMatrixSmokeTest'
```

Expected: missing types or alliance-supremacy rows fail.

- [ ] **Step 3: Implement the ordered pure evaluator**

Evaluate in this fixed order:

1. uncontained wilderness;
2. operator/admin bypass fact;
3. resolution failure;
4. local anarchy;
5. plot ownership;
6. local authority;
7. plot grant;
8. guild grant;
9. form-gated member default;
10. public outsider default;
11. deny.

Never inspect alliance seats or sibling membership in `LandPermissionRules`.

- [ ] **Step 4: Run pure permission tests GREEN**

```bash
./gradlew --no-daemon :common:test --tests '*LandPermission*' --tests '*FormPermissionsMatrixSmokeTest' --tests '*BlockProtectionTest'
```

Expected: `BUILD SUCCESSFUL`; alliance form changes do not alter local decisions.

- [ ] **Step 5: Commit the pure permission domain**

```bash
git add api/src common/src
git commit -m "feat: define local land permission decisions"
```

---

### Task 6: Cut Paper permissions and listeners over to one resolver

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/LandPermissionResolverImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/PermissionService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/PermissionServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsGovernanceSource.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/listener/ProtectionListener.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/listener/InteractionProtectionListener.java`
- Modify/delete duplicate actor handlers discovered by LSP in `paper/src/main/java/org/aincraft/guilds/listeners/`
- Modify: `paper/src/test/java/org/aincraft/guilds/territory/GuildsServicesWiringTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/listener/LandPermissionListenerTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/services/PlotPermissionFormTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/services/PermissionServiceImplTest.java`

**Interfaces:**
- `LandPermissionResolverImpl.resolve(LandPermissionRequest)` resolves territory once, plot once, current memberships once, and returns one `PermissionDecision`.
- Actor-facing listeners depend only on `LandPermissionResolver`.
- `BlockProtection` retains actor-free environmental transfer/grief decisions.

- [ ] **Step 1: Write failing facts-adapter tests**

Assert that SQL exceptions become `DATA_UNAVAILABLE`, explicit grants are read only for the local guild, and the same request performs one territory lookup and one plot lookup.

- [ ] **Step 2: Write failing listener integration tests**

Use a counting fake resolver:

```java
resolver.returnDecision(PermissionDecision.denied(
        PermissionDecisionReason.BOUND_GUILD_UNRESOLVED, "Missing governing guild"));
listener.onBreak(event);
assertTrue(event.isCancelled());
assertEquals(1, resolver.invocations());
```

Cover break, place, block interaction, entity interaction, PvP, bypass, and unavailable-data denial.

- [ ] **Step 3: Run Paper tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*PlotPermissionFormTest' \
  --tests '*PermissionServiceImplTest' --tests '*LandPermissionListenerTest' \
  --tests '*GuildsServicesWiringTest'
```

Expected: missing resolver or duplicate engines cause failures.

- [ ] **Step 4: Implement the Paper facts adapter**

Resolve live Guilds membership, local permissions, plot owner/grants, and public toggles. Catch storage failures at the adapter boundary and return `DATA_UNAVAILABLE`; do not turn them into empty grants.

- [ ] **Step 5: Inject one resolver into actor-facing listeners**

Map each Bukkit event to one `LandAction`, resolve once, cancel from the returned decision, and log resolution faults with rate limiting through the existing logger seam. Delete duplicate actor access handlers after LSP confirms all registrations/callers migrated.

- [ ] **Step 6: Run Paper permission tests GREEN**

```bash
./gradlew --no-daemon :paper:test --tests '*PlotPermissionFormTest' \
  --tests '*PermissionServiceImplTest' --tests '*LandPermissionListenerTest' \
  --tests '*GuildsServicesWiringTest' --tests '*ProtectionListener*'
```

Expected: `BUILD SUCCESSFUL`; one resolver invocation per event.

- [ ] **Step 7: Commit the Paper cutover**

```bash
git add paper/src common/src
git commit -m "refactor: route land events through one resolver"
```

---

### Task 7: Enforce governance invariants in versioned SQL

**Files:**
- Create: `common/src/main/resources/sql/migrations/guilds/V23__governance-invariants.sql`
- Modify: `common/src/main/resources/sql/migrations/guilds/manifest`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/AllianceServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsGovernanceSource.java`
- Create: `paper/src/test/java/org/aincraft/guilds/database/migration/GovernanceInvariantMigrationTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/database/MySqlSchemaInitializerTest.java`
- Modify: `paper/src/test/java/org/aincraft/guilds/services/GuildServiceImplLoadTest.java`

**Interfaces:**
- Migration V23 rejects cross-alliance duplicate guild membership and invalid alliance capital membership before adding uniqueness constraints.
- Service validation reports an explicit conflict before attempting the SQL mutation.
- Governance source returns `GovernanceLookup.Unavailable` on SQL failure and never caches it.

- [ ] **Step 1: Inspect current alliance schema**

Read `V11__nations.sql`, `V18__rename-nations.sql`, current manifest, and `AllianceServiceImpl` queries. Use the actual table and column names in V23; do not infer them from archived migrations.

- [ ] **Step 2: Write failing migration tests**

Create fixtures for:

- one guild in two alliances;
- capital guild absent from member rows;
- valid single-alliance memberships;
- equivalent MySQL bootstrap.

Assert invalid rows produce an exception naming every conflicting guild ID and valid rows gain the unique constraint.

- [ ] **Step 3: Run migration tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*GovernanceInvariantMigrationTest' --tests '*MySqlSchemaInitializerTest'
```

Expected: V23 is absent and duplicate membership remains allowed.

- [ ] **Step 4: Add V23 and service preflight**

Use dialect-neutral SQL when supported. If constraint syntax differs, add dialect-specific migration resources through the existing `SqlMigrationRunner` convention rather than executing vendor checks from Paper code. Validate membership in `AllianceServiceImpl` before insert, then rely on the SQL unique constraint for races.

- [ ] **Step 5: Propagate typed source failures**

Map `SQLException` to `GovernanceLookup.Unavailable(e.getMessage())`; never return `Missing` for query failure. Cache only `Found` or authoritative `Missing` results according to existing cache policy.

- [ ] **Step 6: Run persistence tests GREEN**

```bash
./gradlew --no-daemon :paper:test --tests '*GovernanceInvariantMigrationTest' \
  --tests '*MySqlSchemaInitializerTest' --tests '*GuildServiceImplLoadTest'
```

Expected: `BUILD SUCCESSFUL` against configured dialect fixtures; unavailable data remains distinguishable.

- [ ] **Step 7: Commit durable governance invariants**

```bash
git add common/src/main/resources/sql/migrations/guilds \
  paper/src/main/java/org/aincraft/guilds/services/impl/AllianceServiceImpl.java \
  paper/src/main/java/org/aincraft/guilds/GuildsGovernanceSource.java \
  paper/src/test/java/org/aincraft/guilds
git commit -m "fix: enforce one alliance per guild"
```

---

### Task 8: Make contract transitions concurrency-safe and expirable

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/models/GuildContract.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/GuildContractService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildContractServiceImpl.java`
- Create: `common/src/main/resources/sql/migrations/guilds/V24__contract-expiry.sql`
- Modify: `common/src/main/resources/sql/migrations/guilds/manifest`
- Modify: `paper/src/test/java/org/aincraft/guilds/services/GuildContractServiceImplTest.java`

**Interfaces:**
- `GuildContract.Status` includes `OPEN`, `FULFILLED`, `CANCELLED`, and `EXPIRED`.
- `createContract(..., Instant expiresAt)` stores an explicit future expiry; the command layer will supply the configured default.
- `fulfillContract` and `cancelContract` lock the row and transition only from `OPEN`.
- `expireContracts(Instant now)` atomically refunds and marks each due contract once.

- [ ] **Step 1: Write failing transition tests**

Cover:

```java
@Test
void concurrentFulfillAndCancelApplyExactlyOneTransition() { /* run two calls, assert one winner */ }

@Test
void expiryRefundsEscrowExactlyOnce() { /* call expire twice, assert one refund */ }

@Test
void expiredContractCannotBeFulfilled() { /* assert EXPIRED result */ }
```

Use the existing SQL fixture and deterministic `Clock`; do not mock transaction boundaries.

- [ ] **Step 2: Run service tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildContractServiceImplTest'
```

Expected: missing expiry API or a race permits ambiguous results.

- [ ] **Step 3: Add V24 expiry columns and indexes**

Add `expires_at`, `terminal_at`, and an index supporting `status = 'OPEN' AND expires_at <= ?`. Backfill existing open contracts with a documented default interval from their `created_at`.

- [ ] **Step 4: Lock and transition contract rows**

Inside each transaction, select the contract row `FOR UPDATE`, validate owner/status/expiry, apply balance/progress exactly once, then write terminal status. For expiry, lock due rows in stable ID order and refund each escrow before marking it expired.

- [ ] **Step 5: Run contract tests GREEN**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildContractServiceImplTest'
```

Expected: `BUILD SUCCESSFUL`; one terminal transition wins and repeated expiry is a no-op.

- [ ] **Step 6: Commit contract domain hardening**

```bash
git add common/src/main/resources/sql/migrations/guilds \
  paper/src/main/java/org/aincraft/guilds/models/GuildContract.java \
  paper/src/main/java/org/aincraft/guilds/services/GuildContractService.java \
  paper/src/main/java/org/aincraft/guilds/services/impl/GuildContractServiceImpl.java \
  paper/src/test/java/org/aincraft/guilds/services/GuildContractServiceImplTest.java
git commit -m "fix: make guild contract transitions idempotent"
```

---

### Task 9: Add crash-safe player inventory coordination for contract fulfillment

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractInventoryPort.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractFulfillmentCoordinator.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractInventoryReservationStore.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/SqlContractInventoryReservationStore.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/BukkitContractInventoryPort.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractReservationRecoveryTask.java`
- Create: `common/src/main/resources/sql/migrations/guilds/V25__contract-inventory-reservations.sql`
- Modify: `common/src/main/resources/sql/migrations/guilds/manifest`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Create: `paper/src/test/java/org/aincraft/guilds/services/ContractFulfillmentCoordinatorTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/services/ContractReservationRecoveryTaskTest.java`

**Interfaces:**

```java
public enum ReservationStatus {
    PREPARED,
    ESCROWED,
    FULFILLED,
    REFUND_PENDING,
    REFUNDED,
    MANUAL_REVIEW
}

public record ContractInventoryReservation(
        UUID operationId,
        UUID playerId,
        String guildId,
        String contractId,
        String materialKey,
        int amount,
        byte[] escrowItemPayload,
        ReservationStatus status,
        Instant updatedAt
) {}

public interface ContractInventoryPort {
    EscrowSelection select(UUID operationId, UUID playerId, String materialKey, int amount);
    void placeEscrow(EscrowSelection selection);
    EscrowInspection inspect(UUID operationId, UUID playerId);
    void clearEscrow(UUID operationId, UUID playerId);
    void restoreEscrow(UUID operationId, UUID playerId);
}
```

`ContractFulfillmentCoordinator.fulfill(UUID operationId, UUID playerId, String guildId, String contractId)` uses a durable reservation journal. A retry with the same operation ID resumes the recorded state; a different operation ID cannot fulfill a terminal contract or remove items again.

The state machine is:

```text
PREPARED
→ ESCROWED
→ FULFILLED

ESCROWED
→ REFUND_PENDING
→ REFUNDED
```

`PREPARED` durably records the exact serialized payload, pre-removal slot fingerprints, and expected post-removal slot fingerprints before inventory mutation. The player-owned thread then writes a plugin-owned, operation-tagged escrow payload to the player's persistent data container (PDC) and removes those exact quantities from ordinary slots in one callback. The PDC payload includes `operationId`, exact serialized stacks, both fingerprint sets, and a checksum. Paper does not provide an atomic transaction across PDC and inventory, so recovery must prove which side of the callback completed; PDC presence alone is never proof of removal.

After the callback verifies both escrow and post-removal fingerprints, the coordinator persists `ESCROWED`. Recovery reconciles `PREPARED` by comparing both durable PDC escrow and current source-slot fingerprints: escrow present plus exact post-removal fingerprints advances to `ESCROWED`; escrow present plus exact pre-removal fingerprints means removal did not happen, so recovery removes the escrow copy and leaves ordinary items untouched; escrow absent plus exact pre-removal fingerprints may retry; any mixed/changed state becomes `MANUAL_REVIEW`. It never advances from PDC presence alone. `ESCROWED` with fulfilled contract clears escrow and advances to `FULFILLED`; otherwise it restores escrow exactly once and advances through `REFUND_PENDING` to `REFUNDED`.

- [ ] **Step 1: Write failing coordinator state-machine tests**

Cover:

- insufficient inventory never creates a reservation;
- `PREPARED` stores exact payload plus pre/post fingerprints before mutation;
- retry after crash with escrow and exact post-removal fingerprints advances to `ESCROWED` without removing again;
- retry after crash with escrow and exact pre-removal fingerprints removes the escrow duplicate and does not fulfill;
- retry after crash without escrow and exact pre-removal fingerprints may retry the move;
- mixed fingerprints enter `MANUAL_REVIEW`;
- successful contract transition stores `FULFILLED` and clears escrow once;
- service rejection stores `REFUND_PENDING` and restores escrow once;
- retry with a new operation ID after terminal fulfillment cannot escrow items.

Use a real SQL fixture for reservation/contract atomicity and a recording inventory port for player inventory behavior.

- [ ] **Step 2: Write failing crash-recovery tests**

Seed each journal/PDC combination as if the process stopped:

```java
seedReservation(PREPARED, escrowAbsent(), preRemovalSlots());
seedReservation(PREPARED, escrowPresent(), preRemovalSlots());
seedReservation(PREPARED, escrowPresent(), postRemovalSlots());
seedReservation(PREPARED, escrowPresent(), mixedSlots());
seedReservation(ESCROWED, escrowPresent(), postRemovalSlots());
seedReservation(REFUND_PENDING, escrowPresent(), postRemovalSlots());
```

Assert:

- `PREPARED` without escrow and exact pre-removal fingerprints may retry;
- `PREPARED` with escrow and exact pre-removal fingerprints clears only escrow and leaves ordinary stacks untouched;
- `PREPARED` with escrow and exact post-removal fingerprints advances to `ESCROWED`;
- `PREPARED` with any mixed or changed fingerprint state becomes operator-visible `MANUAL_REVIEW`;
- `ESCROWED` plus fulfilled contract clears escrow and reconciles to `FULFILLED`;
- `ESCROWED` plus open contract reconciles to `REFUND_PENDING`;
- online-player refund restores exact serialized stacks and marks `REFUNDED`;
- offline-player escrow remains in PDC and reconciliation waits for player join;
- overflow restoration runs only on the owning entity thread;
- repeated recovery never restores or clears the same escrow twice.

- [ ] **Step 3: Run coordinator/recovery tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*ContractFulfillmentCoordinatorTest' \
  --tests '*ContractReservationRecoveryTaskTest'
```

Expected: compilation fails because the journal and recovery task do not exist.

- [ ] **Step 4: Add V25 reservation journal**

Create `contract_inventory_reservations` with:

- unique `operation_id`;
- player, guild, and contract IDs;
- material and amount;
- exact serialized escrow payload, pre-removal fingerprints, expected post-removal fingerprints, and checksum;
- constrained status including `MANUAL_REVIEW`;
- created/updated timestamps;
- index on non-terminal status and update time.

Add a uniqueness rule preventing more than one non-terminal reservation per contract where supported; enforce the same invariant under row lock in the service for both dialects.

- [ ] **Step 5: Implement durable PDC escrow**

`BukkitContractInventoryPort` must:

1. parse and validate the Bukkit material key;
2. select exact source slots and serialize exact stack payloads without mutation;
3. calculate exact pre-removal and expected post-removal fingerprints;
4. persist `PREPARED` with payload, both fingerprint sets, and checksum;
5. on the owning entity thread, verify current slots still match pre-removal fingerprints;
6. write the operation-tagged escrow payload to the player's PDC;
7. remove selected quantities from ordinary slots;
8. verify PDC checksum and exact post-removal fingerprints before returning;
9. persist `ESCROWED`.

If an ordinary exception occurs inside the callback, restore the pre-mutation snapshot and remove the PDC escrow. A process crash is reconciled from escrow plus fingerprints: pre-removal means no debit and escrow must be discarded; post-removal means escrow owns the debit; anything else requires manual review. Serialization must round-trip Bukkit item metadata.

- [ ] **Step 6: Make fulfillment and reservation terminal state atomic**

Add an overload/internal transaction seam in `GuildContractServiceImpl` that accepts the reservation operation ID. Under one database transaction:

1. lock reservation row;
2. require `ESCROWED`;
3. verify escrow checksum recorded by the reservation;
4. lock contract row;
5. transition contract and balances/progress;
6. mark the SQL reservation ready for fulfillment reconciliation.

After SQL commit, clear the tagged PDC escrow on the player-owned thread and mark `FULFILLED`. If the process stops before clearing, recovery sees the fulfilled contract and clears the same escrow without returning it. For a rejected transition, persist `REFUND_PENDING`. A transaction failure leaves durable `ESCROWED`; recovery restores it unless the contract records fulfillment by that operation ID.

- [ ] **Step 7: Implement startup, periodic, and player-join recovery**

`ContractReservationRecoveryTask` scans non-terminal rows and reconciles them with tagged PDC escrow and current source slots. Offline-player rows wait for join. For `PREPARED`, apply the exact four-way decision above; never infer removal from PDC presence. Mark `REFUNDED` only after restore/drop completes, and mark `FULFILLED` only after operation-tagged escrow is absent. Expose `MANUAL_REVIEW` with fingerprints and checksum through Release 1 recovery commands.

- [ ] **Step 8: Run GREEN tests**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildContractServiceImplTest' \
  --tests '*ContractFulfillmentCoordinatorTest' \
  --tests '*ContractReservationRecoveryTaskTest' \
  --tests '*GuildsServicesWiringTest'
```

Expected: `BUILD SUCCESSFUL`; every modeled crash window resolves through durable PDC escrow to `FULFILLED`, `REFUNDED`, or explicit `MANUAL_REVIEW` without guessing.

- [ ] **Step 9: Commit recoverable inventory coordination**

```bash
git add common/src/main/resources/sql/migrations/guilds \
  paper/src/main/java/org/aincraft/guilds/services \
  paper/src/main/java/org/aincraft/guilds/services/impl \
  paper/src/main/java/org/aincraft/guilds/GuildsServices.java \
  paper/src/test/java/org/aincraft/guilds/services
git commit -m "feat: journal contract inventory reservations"
```

---

### Task 10: Expose guild contracts through Brigadier commands

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildContractBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Create: `paper/src/test/java/org/aincraft/guilds/commands/GuildContractCommandTest.java`
- Modify: `paper/src/main/resources/plugin.yml` only if command metadata is required by the existing Brigadier registration pattern

**Interfaces:**

```text
/guild contract list [page]
/guild contract post <material> <amount> <payment> [duration]
/guild contract fulfill <contract-id>
/guild contract cancel <contract-id>
/guild contract history [page]
```

- Posting and cancellation require the existing guild financial/contract authority determined by `PermissionService`.
- Fulfillment requires current guild membership and player inventory.
- Console may list/history but cannot fulfill inventory-backed contracts.

- [ ] **Step 1: Write failing command tests**

Cover command registration, permissions, membership, console rejection, pagination, invalid material/amount/payment/duration, post success, fulfillment coordinator routing, cancellation ownership, and terminal status display.

- [ ] **Step 2: Run command tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildContractCommandTest'
```

Expected: command root is absent.

- [ ] **Step 3: Implement the command surface**

Use the project’s existing Paper Brigadier argument and message conventions. Render contract IDs in a copyable form, show material/amount/payment/expiry/status, and return exact service failure messages without exposing SQL details.

- [ ] **Step 4: Register and verify commands**

```bash
./gradlew --no-daemon :paper:test --tests '*GuildContractCommandTest' --tests '*PluginMetadataTest'
```

Expected: `BUILD SUCCESSFUL`; every subcommand is discoverable and permission-gated.

- [ ] **Step 5: Commit player contract commands**

```bash
git add paper/src/main/java/org/aincraft/guilds/commands \
  paper/src/main/java/org/aincraft/guilds/GuildsServices.java \
  paper/src/main/resources/plugin.yml \
  paper/src/test/java/org/aincraft/guilds/commands/GuildContractCommandTest.java
git commit -m "feat: add player guild contract commands"
```

---

### Task 11: Add contract expiry scheduling and notifications

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractExpiryTask.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/ContractNotificationPort.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/BukkitContractNotificationPort.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Modify: `paper/src/main/resources/guilds-config.yml`
- Create: `paper/src/test/java/org/aincraft/guilds/services/ContractExpiryTaskTest.java`

**Interfaces:**
- `ContractExpiryTask.run(Instant now)` calls `expireContracts(now)` and notifies affected guild residents once per returned transition.
- Config keys: `contracts.default-duration`, `contracts.expiry-scan-interval` using the project’s existing duration parser or explicit seconds if none exists.

- [ ] **Step 1: Write failing scheduler tests**

Assert due scans use injected `Clock`, notify only newly expired contracts, continue after one offline guild, and cancel cleanly during plugin disable.

- [ ] **Step 2: Run scheduler tests RED**

```bash
./gradlew --no-daemon :paper:test --tests '*ContractExpiryTaskTest'
```

Expected: missing task/config behavior.

- [ ] **Step 3: Implement and wire expiry scheduling**

Schedule one repeating task, not one task per contract. Keep expiry SQL work off entity threads according to existing plugin scheduling conventions; schedule notifications back to appropriate Paper/Folia contexts.

- [ ] **Step 4: Run scheduler/wiring tests GREEN**

```bash
./gradlew --no-daemon :paper:test --tests '*ContractExpiryTaskTest' --tests '*GuildsServicesWiringTest'
```

Expected: `BUILD SUCCESSFUL`; disable cancels the task.

- [ ] **Step 5: Commit expiry operations**

```bash
git add paper/src/main/java/org/aincraft/guilds/services \
  paper/src/main/java/org/aincraft/guilds/services/impl/BukkitContractNotificationPort.java \
  paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java \
  paper/src/main/resources/guilds-config.yml \
  paper/src/test/java/org/aincraft/guilds/services/ContractExpiryTaskTest.java
git commit -m "feat: expire and notify guild contracts"
```

---

### Task 12: Add operator health and recovery commands

**Files:**
- Create: `common/src/main/java/org/aincraft/guilds/territory/ops/SystemHealth.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/ops/SystemHealthService.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/ops/RecoveryAuditEntry.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/persist/RecoveryAuditStore.java`
- Create: `common/src/main/java/org/aincraft/guilds/territory/persist/SqlRecoveryAuditStore.java`
- Create: SQL resources under `common/src/main/resources/sql/ops/` for PostgreSQL/MySQL create/insert/select
- Modify: `paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
- Create: `common/src/test/java/org/aincraft/guilds/territory/ops/SystemHealthServiceTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/territory/command/TerritoryCommandRecoveryTest.java`

**Interfaces:**

```text
/territory health
/territory reconciliation list
/territory influence reset <territory>
/territory influence cancel <territory>
/territory standing show <territory>
/territory standing reset <territory>
/territory governance diagnose <territory>
```

Every mutation writes `RecoveryAuditEntry(actorId, action, targetId, occurredAt, outcome)` after a committed operation. Failed audit persistence makes the administrative mutation fail before execution unless the operation itself is a database-health diagnostic.

- [ ] **Step 1: Write failing health tests**

Assert healthy pool + critical tables reports healthy; missing critical table and failed query report named unhealthy components without throwing away other component results.

- [ ] **Step 2: Write failing command tests**

Cover permissions, read-only console support, explicit confirmation for destructive reset commands if the existing command convention supports it, audit entry creation, idempotent influence cancellation/reset, standing read/reset, and governance status/message display.

- [ ] **Step 3: Run health/recovery tests RED**

```bash
./gradlew --no-daemon :common:test --tests '*SystemHealthServiceTest'
./gradlew --no-daemon :paper:test --tests '*TerritoryCommandRecoveryTest'
```

Expected: missing types and subcommands.

- [ ] **Step 4: Implement health checks and audit persistence**

Validate the Hikari connection, current migration version, and critical tables for territory, guilds, alliance membership, influence, standing, expenses, contracts, and facilities. Use `DatabaseMetaData` or dialect resources already established by `SqlSupport`; do not interpolate identifiers from command input.

- [ ] **Step 5: Implement recovery adapters**

Expose narrowly named idempotent operations on the existing influence, standing, expense/reconciliation, and governance services. Commands call services rather than editing tables directly. Record actor, target, timestamp, and result.

- [ ] **Step 6: Run health/recovery tests GREEN**

```bash
./gradlew --no-daemon :common:test --tests '*SystemHealthServiceTest'
./gradlew --no-daemon :paper:test --tests '*TerritoryCommandRecoveryTest' \
  --tests '*TerritoryCommandStandingTest' --tests '*Influence*'
```

Expected: `BUILD SUCCESSFUL`; retries do not apply state twice.

- [ ] **Step 7: Commit operator recovery surfaces**

```bash
git add common/src/main/java/org/aincraft/guilds/territory/ops \
  common/src/main/java/org/aincraft/guilds/territory/persist \
  common/src/main/resources/sql/ops \
  common/src/test/java/org/aincraft/guilds/territory/ops \
  paper/src/main/java/org/aincraft/guilds/territory/command/TerritoryCommand.java \
  paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java \
  paper/src/test/java/org/aincraft/guilds/territory/command/TerritoryCommandRecoveryTest.java
git commit -m "feat: add Guilds health and recovery commands"
```

---

### Task 13: Document operations and align living specs

**Files:**
- Modify: `README.md`
- Create: `docs/operations/database-backup-restore.md`
- Create: `docs/operations/legacy-import.md`
- Create: `docs/operations/release-1-smoke-test.md`
- Modify: `docs/living-specs/governance.md`
- Modify: `docs/living-specs/guilds.md`
- Modify: `docs/living-specs/economy.md`
- Modify: `docs/living-specs/influence.md`
- Modify: `docs/living-specs/standing.md`
- Modify: `docs/living-specs/persistence.md`
- Modify: `docs/living-specs/platform.md`

**Interfaces:**
- Produces: exact backup/restore commands for PostgreSQL and MySQL, supported legacy-import boundaries, recovery command reference, and a reproducible Paper smoke checklist.

- [ ] **Step 1: Write operator documentation**

Document:

- supported JDK and `./gradlew --no-daemon check`;
- PostgreSQL `pg_dump`/`pg_restore` and MySQL `mysqldump`/`mysql` commands using placeholders that cannot be mistaken for real credentials;
- required maintenance window and restore verification;
- legacy JSON/SQLite import inputs, conflict policy, dry-run, and backup prerequisite;
- every health/recovery command and permission;
- local versus alliance governance rules;
- contract post/fulfill/cancel/expiry behavior.

- [ ] **Step 2: Reconcile living-spec checkboxes against code**

Flip only capabilities verified by focused tests. Remove stale claims such as editor/session work that source and tests already prove, or leave them open with an exact missing behavior. Record decisions for local/alliance scope, contract expiry, and fail-closed health.

- [ ] **Step 3: Validate documentation references**

Run repository link/path checks if configured, then:

```bash
./gradlew --no-daemon :paper:processResources :common:processResources
```

Expected: `BUILD SUCCESSFUL`; every referenced path and command exists.

- [ ] **Step 4: Commit Release 1 documentation**

```bash
git add README.md docs/operations docs/living-specs
git commit -m "docs: publish stable government operations"
```

---

### Task 14: Verify Release 1 end to end

**Files:**
- Modify only when verification finds a release-blocking defect: affected source/test files
- Update: `docs/operations/release-1-smoke-test.md` with observed environment and results

**Interfaces:**
- Consumes: all Release 1 behavior.
- Produces: automated and real-runtime evidence for the release acceptance criteria.

- [ ] **Step 1: Run focused database suites**

With PostgreSQL and MySQL test URLs configured according to existing fixtures:

```bash
./gradlew --no-daemon :common:test --tests '*Postgres*' --tests '*MySql*'
./gradlew --no-daemon :paper:test --tests '*Migration*' --tests '*GuildContract*'
```

Expected: every configured test passes; unavailable external database fixtures report skips rather than false passes.

- [ ] **Step 2: Run the full automated gate**

```bash
./gradlew --no-daemon clean check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`; the shadow JAR exists under `paper/build/libs/` with the current project version.

- [ ] **Step 3: Start the real Paper server**

Use the harness process manager to start:

```bash
./gradlew :paper:runServer
```

Wait for the Paper ready banner and Guilds enable confirmation. Configure a real test SQL database and required soft dependencies before claiming runtime success.

- [ ] **Step 4: Execute the governance smoke matrix**

Create two guilds in one alliance plus one outsider. Exercise:

- local democracy member build allow;
- monarchy ordinary member break deny and interaction default;
- alliance officer local break deny;
- sibling alliance resident local rights deny;
- explicit local grant allow;
- unresolved governing guild deny with diagnostic output;
- database-unavailable decision deny.

Record exact commands and observed decisions in the smoke document.

- [ ] **Step 5: Execute world and contract smoke paths**

Exercise:

- territory create/save/reload and overlap rejection;
- anchor restoration after restart;
- waystone warm-up, movement cancellation, damage cancellation, safe landing, cooldown;
- trading-post event observation through a test listener;
- influence declaration, cancellation, reset, flip, and cooldown;
- contract post, insufficient inventory, fulfill, cancel, expiry refund, duplicate retry;
- `/territory health` and each recovery read/mutation.

Record observed state before and after restart.

- [ ] **Step 6: Request code review and resolve evidence-backed findings**

Use the requesting-code-review skill against the Release 1 commits and approved roadmap. Apply each distinct fix in its own atomic commit with focused verification.

- [ ] **Step 7: Rerun completion verification**

```bash
./gradlew --no-daemon check :paper:shadowJar
```

Expected: `BUILD SUCCESSFUL`. Re-run any runtime scenario affected by review fixes.

- [ ] **Step 8: Commit smoke evidence if changed**

```bash
git add docs/operations/release-1-smoke-test.md
git commit -m "docs: record Release 1 runtime verification"
```

Do not commit generated server state, credentials, `.javadoc-backup/`, or database dumps.
