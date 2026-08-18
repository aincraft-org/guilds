# Scope-Aware Governance and Invariant Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make guild land permissions local, alliance authority federal, and all affected governance/nullability invariants explicit and fail-closed.

**Architecture:** `GovernanceContext` carries local and optional alliance scopes instead of one overriding government. Immutable guild/alliance snapshots derive electorates from explicit roles, while a single `LandPermissionResolver` evaluates only the local scope. Persistence adapters normalize legacy data and return typed unavailable states; required services use constructor injection.

**Tech Stack:** Java 26, Gradle Kotlin DSL, JUnit 5, Paper 26.2, PostgreSQL/HikariCP, Gson, Error Prone/NullAway, JSpecify.

## Global Constraints

- Guild government controls local membership, plots, common land, local administration, and local policy.
- Alliance government controls alliance membership, diplomacy, influence, territory-wide policy, and alliance treasury.
- Alliance office and sibling membership never grant implicit local `BREAK_BLOCK`, `PLACE_BLOCK`, or `INTERACT` rights.
- Guild democracy electorate is every current resident; alliance democracy electorate is one unique delegate per member guild.
- A voter is not automatically an operational administrator.
- `BOUND_GUILD_UNRESOLVED` and `DATA_UNAVAILABLE` deny ordinary player actions and remain distinguishable through Paper listeners.
- Required values reject null; compatibility defaults exist only in persistence adapters.
- No deprecated aliases, compatibility constructors, or nullable setters remain after call-site migration.
- Tests are written and observed failing before production changes; production and regression tests commit together while green.
- Existing verification assets are changed only where the approved observable contract changed, never weakened to obtain a pass.

---

### Task 1: Strict governance snapshots and land action types

**Files:**
- Modify: `api/src/main/java/com/guilds/territory/model/Government.java`
- Modify: `api/src/main/java/com/guilds/territory/model/GovernmentSeat.java`
- Modify: `api/src/main/java/com/guilds/territory/permission/GuildBody.java`
- Modify: `api/src/main/java/com/guilds/territory/permission/AllianceBody.java`
- Modify: `api/src/main/java/com/guilds/territory/permission/MemberPermissions.java`
- Remove: `api/src/main/java/com/guilds/territory/permission/SovereignAction.java`
- Create: `api/src/main/java/com/guilds/territory/permission/LandAction.java`
- Test: `api/src/test/java/com/guilds/territory/model/GovernmentTest.java`
- Test: `api/src/test/java/com/guilds/territory/model/GovernmentFromRolesTest.java`
- Test: `api/src/test/java/com/guilds/territory/permission/GuildBodyTest.java`
- Test: `api/src/test/java/com/guilds/territory/permission/PermissionRulesTest.java`
- Migrate: every LSP-reported constructor and enum caller in `api/src`, `common/src`, and `paper/src`

**Interfaces:**
- Produces:

```java
public enum LandAction {
    BREAK_BLOCK,
    PLACE_BLOCK,
    INTERACT,
    ENTER,
    PVP_ATTACK
}
```

```java
public record GuildBody(
        String id,
        String name,
        GovernmentForm form,
        String mayorId,
        List<String> assistantIds,
        List<String> memberIds,
        GuildToggles toggles,
        Map<String, MemberPermissions> memberPermissions
) {
    public Government government();
    public boolean containsMember(String holderId);
    public Optional<MemberPermissions> permissionsOf(String holderId);
}
```

```java
public record AllianceBody(
        String id,
        String name,
        GovernmentForm form,
        String capitalGuildId,
        String kingId,
        List<String> ministerIds,
        List<String> memberGuildIds,
        Map<String, String> delegateByGuildId
) {
    public Government government();
    public boolean containsGuild(String guildId);
}
```

- `MemberPermissions` consumes and returns `LandAction`, never governmental actions.
- `Government.monarchy(String)` requires a holder; explicit vacant factories represent vacancies.
- Democracy term input is explicit and size-checked; no nullable list or null elements.

- [ ] **Step 1: Inspect exported symbol references with LSP**

Run LSP `references` for `GuildBody`, `AllianceBody`, `SovereignAction`, `Government.fromRoles`, and each public government factory. Record every caller before editing.

- [ ] **Step 2: Write failing invariant tests**

Add cases equivalent to:

```java
@Test
void stalePermissionKeyIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new GuildBody(
            "g", "Guild", GovernmentForm.MONARCHY, "mayor",
            List.of(), List.of("mayor"), GuildToggles.defaults(),
            Map.of("departed", MemberPermissions.fullBypass())
    ));
}

@Test
void guildDemocracyElectorateEqualsResidents() {
    GuildBody body = new GuildBody(
            "g", "Guild", GovernmentForm.DEMOCRACY, "mayor",
            List.of("assistant"), List.of("mayor", "assistant", "resident"),
            GuildToggles.defaults(), Map.of()
    );
    assertEquals(Set.of("mayor", "assistant", "resident"),
            PolicyRules.electorate(body.government()));
}

@Test
void allianceDemocracyRequiresOneUniqueDelegatePerGuild() {
    assertThrows(IllegalArgumentException.class, () -> new AllianceBody(
            "a", "Alliance", GovernmentForm.DEMOCRACY, "g1", "king",
            List.of(), List.of("g1", "g2"), Map.of("g1", "same", "g2", "same")
    ));
}

@Test
void democracyRejectsMoreHoldersThanSeats() {
    assertThrows(IllegalArgumentException.class,
            () -> Government.democracy(1, List.of("one", "two"), List.of()));
}
```

Also assert defensive copying by mutating input `ArrayList`/`HashMap` after construction.

- [ ] **Step 3: Run RED tests**

Run:

```bash
./gradlew :api:test --tests '*Government*' --tests '*GuildBodyTest' --tests '*PermissionRulesTest'
```

Expected: `FAILED`; failures name stale permission acceptance, mutable snapshot input, duplicate delegates, or holder truncation—not compilation typos.

- [ ] **Step 4: Implement strict models**

Use shared private `requireText`/copy helpers inside each type. Derive role holders with exact switch expressions:

```java
private List<String> authorityIds() {
    return switch (form) {
        case ANARCHY -> List.of();
        case MONARCHY -> List.of(mayorId);
        case OLIGARCHY -> Stream.concat(Stream.of(mayorId), assistantIds.stream()).toList();
        case DEMOCRACY -> memberIds;
    };
}
```

For alliance democracy, iterate `memberGuildIds` in stable order and resolve each key from `delegateByGuildId`; reject missing and duplicate delegate values before deriving the government.

- [ ] **Step 5: Replace `SovereignAction` with `LandAction`**

Use LSP rename where available, then remove `MANAGE_MEMBERSHIP` and `SET_POLICY` from the renamed enum. Migrate mapped Guilds permission bits only to break/place/interact.

- [ ] **Step 6: Migrate all call sites and run GREEN tests**

Run:

```bash
./gradlew :api:test
```

Expected: `BUILD SUCCESSFUL`; no caller constructs a body with an arbitrary `Government`.

- [ ] **Step 7: Commit the green unit**

```bash
git add api/src common/src paper/src
git commit -m "refactor: derive governance seats from body roles"
```

---

### Task 2: Typed two-level governance resolution

**Files:**
- Create: `api/src/main/java/com/guilds/territory/permission/GovernanceContext.java`
- Create: `api/src/main/java/com/guilds/territory/permission/GovernanceResolutionStatus.java`
- Create: `api/src/main/java/com/guilds/territory/permission/GovernanceLookup.java`
- Modify: `api/src/main/java/com/guilds/territory/permission/GovernanceSource.java`
- Replace/narrow: `api/src/main/java/com/guilds/territory/permission/GoverningBody.java`
- Modify: `api/src/main/java/com/guilds/territory/model/LookupResult.java`
- Modify: `common/src/main/java/com/guilds/territory/permission/GovernanceRegistry.java`
- Test: `common/src/test/java/com/guilds/territory/permission/GovernanceRegistryTest.java`
- Test fixture: `common/src/test/java/com/guilds/territory/permission/FakeGovernanceSource.java`

**Interfaces:**

```java
public enum GovernanceResolutionStatus {
    UNCONTAINED,
    LOCAL_TERRITORY,
    BOUND_GUILD,
    BOUND_GUILD_UNRESOLVED,
    ALLIANCE_UNRESOLVED,
    DATA_UNAVAILABLE
}
```

```java
public sealed interface GovernanceLookup<T>
        permits GovernanceLookup.Found, GovernanceLookup.Missing, GovernanceLookup.Unavailable {
    record Found<T>(T value) implements GovernanceLookup<T> {}
    record Missing<T>() implements GovernanceLookup<T> {}
    record Unavailable<T>(String message) implements GovernanceLookup<T> {}
}
```

```java
public record GovernanceContext(
        GovernanceResolutionStatus status,
        LocalGoverningBody local,
        Optional<AllianceBody> alliance
) {
    public Government localGovernment();
    public Optional<Government> allianceGovernment();
    public Optional<Government> collectiveGovernment();
}
```

- [ ] **Step 1: Write failing resolution tests**

Cover all six statuses. The critical assertion is:

```java
@Test
void boundMissingGuildDoesNotFallBackToTerritoryGovernment() {
    registerBoundTerritory("t", "missing-guild", Government.monarchy("local-admin"));
    GovernanceContext context = governance.contextForTerritory("t");
    assertEquals(GovernanceResolutionStatus.BOUND_GUILD_UNRESOLVED, context.status());
    assertTrue(context.alliance().isEmpty());
}
```

Add a distinct `Unavailable` source case and an alliance-unavailable case whose local guild still resolves.

- [ ] **Step 2: Run RED registry test**

```bash
./gradlew :common:test --tests '*GovernanceRegistryTest'
```

Expected: test fails because current `resolveForTerritory` falls back or cannot distinguish failure.

- [ ] **Step 3: Implement typed source and context**

Change `GovernanceSource.guild` and alliance lookup to typed `GovernanceLookup`. Implement context resolution without fallback when the stored guild ID exists.

- [ ] **Step 4: Remove ambiguous effective APIs**

Use LSP references for `resolveForTerritory`, `effectiveGovernmentForTerritory`, `governingGuildForTerritory`, and `governingGuildAt`. Migrate callers to `contextForTerritory` plus explicit `localGovernment` or `collectiveGovernment`, then delete old methods.

- [ ] **Step 5: Make touched lookup states explicit**

Replace `LookupResult`/`GoverningBody` null pairs with contained/uncontained or typed variants. Pattern-match instead of checking then calling `orElseThrow`.

- [ ] **Step 6: Run GREEN API/common tests**

```bash
./gradlew :api:test :common:test
```

Expected: `BUILD SUCCESSFUL`; no ambiguous governance fallback method remains.

- [ ] **Step 7: Commit the green unit**

```bash
git add api/src common/src
git commit -m "refactor: resolve local and alliance governance explicitly"
```

---

### Task 3: Scoped policy and influence authority

**Files:**
- Modify: `api/src/main/java/com/guilds/territory/model/PolicyRules.java`
- Modify: `common/src/main/java/com/guilds/territory/permission/GovernanceRegistry.java`
- Modify: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java`
- Modify: `api/src/main/java/com/guilds/territory/influence/InfluenceService.java`
- Test: `api/src/test/java/com/guilds/territory/model/PolicyRulesTest.java`
- Test: `common/src/test/java/com/guilds/territory/permission/GovernanceRegistryTest.java`
- Test: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineLifecycleTest.java`

**Interfaces:**
- `GovernanceRegistry.policyGovernment(String territoryId)` returns typed collective authority.
- `GovernanceRegistry.influenceGovernment(String guildId)` returns alliance government when the guild is allied, guild government otherwise.
- A narrowly named `AuthorityRules.isSeatHolder(Government, String)` replaces action-insensitive `PermissionRules.allows`.

- [ ] **Step 1: Write failing scoped-authority tests**

```java
@Test
void alliedGuildDeclarationUsesAllianceSeats() {
    setupGuildInAlliance("attacker", Government.monarchy("guild-mayor"),
            Government.monarchy("alliance-king"));
    assertEquals(DeclareStatus.NOT_AUTHORIZED,
            engine.declare("target", "attacker", "guild-mayor", now).status());
    assertNotEquals(DeclareStatus.NOT_AUTHORIZED,
            engine.declare("target", "attacker", "alliance-king", now).status());
}
```

Also test independent guild authority and local-democracy resident rejection from alliance authority.

- [ ] **Step 2: Run RED authority tests**

```bash
./gradlew :api:test --tests '*PolicyRulesTest'
./gradlew :common:test --tests '*GovernanceRegistryTest' --tests '*InfluenceEngine*'
```

Expected: allied declaration test fails because current engine checks guild seats.

- [ ] **Step 3: Implement explicit scope selection**

Select collective policy government and attacker influence government through `GovernanceContext`. Preserve `ALLIANCE_UNRESOLVED`/`DATA_UNAVAILABLE` as authorization failures.

- [ ] **Step 4: Delete action-insensitive permission rules**

Keep policy eligibility in `PolicyRules`; use `AuthorityRules.isSeatHolder` only after scope is chosen. No governmental API accepts `LandAction`.

- [ ] **Step 5: Run GREEN tests and commit**

```bash
./gradlew :api:test :common:test
git add api/src common/src
git commit -m "fix: scope policy and influence authority"
```

Expected: tests end `BUILD SUCCESSFUL` before commit.

---

### Task 4: One local land permission pipeline

**Files:**
- Create: `api/src/main/java/com/guilds/territory/permission/LandPermissionRequest.java`
- Create: `api/src/main/java/com/guilds/territory/permission/LandPermissionResolver.java`
- Create: `api/src/main/java/com/guilds/territory/permission/PermissionDecision.java`
- Create: `api/src/main/java/com/guilds/territory/permission/PermissionDecisionReason.java`
- Create: `common/src/main/java/com/guilds/territory/permission/LandPermissionFacts.java`
- Create: `common/src/main/java/com/guilds/territory/permission/LandPermissionRules.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/LandPermissionResolverImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/PermissionService.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/PermissionServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsGovernanceSource.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/PermBrigadierCommand.java`
- Test: create `common/src/test/java/com/guilds/territory/permission/LandPermissionRulesTest.java`
- Test: revise `common/src/test/java/com/guilds/territory/permission/BlockProtectionTest.java`
- Test: revise `common/src/test/java/com/guilds/territory/permission/FormPermissionsMatrixSmokeTest.java`
- Test: revise `paper/src/test/java/org/aincraft/guilds/services/PlotPermissionFormTest.java`

**Interfaces:**

```java
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
        Optional<String> localBodyId
) {}
```

`PermissionDecisionReason` includes `WILDERNESS`, `ADMIN_BYPASS`, `BOUND_GUILD_UNRESOLVED`, `DATA_UNAVAILABLE`, `LOCAL_ANARCHY`, `PLOT_OWNER`, `LOCAL_AUTHORITY`, `PLOT_GRANT`, `GUILD_GRANT`, `MEMBER_DEFAULT`, `PUBLIC_DEFAULT`, and `DENIED`.

- [ ] **Step 1: Write the full failing matrix**

Parameterize local form, actor kind, action, and expected result. Include invariant rows:

```java
arguments(MONARCHY, ALLIANCE_KING, BREAK_BLOCK, false),
arguments(OLIGARCHY, SIBLING_MEMBER, PLACE_BLOCK, false),
arguments(DEMOCRACY, LOCAL_MEMBER, BREAK_BLOCK, true),
arguments(MONARCHY, LOCAL_MEMBER, INTERACT, true),
arguments(MONARCHY, PUBLIC_OUTSIDER, BREAK_BLOCK, false),
arguments(MONARCHY, PUBLIC_OUTSIDER, PLACE_BLOCK, true)
```

Run the same local facts under every alliance form and assert identical decisions.

- [ ] **Step 2: Run RED matrix tests**

```bash
./gradlew :common:test --tests '*LandPermissionRulesTest'
./gradlew :paper:test --tests '*PlotPermissionFormTest'
```

Expected: old alliance/sibling expectations or missing resolver produce intended failures.

- [ ] **Step 3: Implement the pure ordered evaluator**

Encode one return point per precedence stage; never inspect alliance seats or sibling membership.

- [ ] **Step 4: Implement the Paper facts adapter**

Resolve territory context once, plot once, and grants once. Convert storage exceptions to `DATA_UNAVAILABLE`. Check grants only in the governing local guild context.

- [ ] **Step 5: Remove legacy location evaluation from `PermissionService`**

Migrate `canBuild`, `canDestroy`, `canSwitch`, item/entity location callers to `LandPermissionResolver`; delete `effectiveForm` and sibling-grant logic.

- [ ] **Step 6: Run GREEN permission tests**

```bash
./gradlew :common:test --tests '*LandPermission*' --tests '*BlockProtection*'
./gradlew :paper:test --tests '*PlotPermissionFormTest' --tests '*PermissionServiceImplTest'
```

Expected: `BUILD SUCCESSFUL` and all alliance-form invariance rows pass.

- [ ] **Step 7: Commit the green unit**

```bash
git add api/src common/src paper/src
git commit -m "feat: resolve local land permissions in one pipeline"
```

---

### Task 5: Paper listener cutover and fail-closed integration

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/listener/ProtectionListener.java`
- Modify: `paper/src/main/java/com/guilds/territory/listener/InteractionProtectionListener.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/listeners/GuildPublicAccessListener.java`
- Modify: `common/src/main/java/com/guilds/territory/permission/BlockProtection.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Create test: `paper/src/test/java/com/guilds/territory/permission/LandPermissionListenerTest.java`
- Test: `paper/src/test/java/com/guilds/territory/GuildsServicesWiringTest.java`

**Interfaces:**
- Actor-based listeners depend on `LandPermissionResolver`.
- `BlockProtection` retains only actor-free environmental operations.

- [ ] **Step 1: Write failing listener integration tests**

Use a counting fake resolver and a real listener-facing event/request. Assert one call and cancellation for `BOUND_GUILD_UNRESOLVED`:

```java
assertEquals(PermissionDecisionReason.BOUND_GUILD_UNRESOLVED, decision.reason());
assertTrue(event.isCancelled());
assertEquals(1, resolver.invocationCount());
```

Add bypass allow and interaction/entity action mapping cases.

- [ ] **Step 2: Run RED listener tests**

```bash
./gradlew :paper:test --tests '*LandPermissionListenerTest' --tests '*GuildsServicesWiringTest'
```

Expected: failure because listeners still use separate engines or cannot expose the reason.

- [ ] **Step 3: Inject one resolver into all actor listeners**

Map each Bukkit event to one `LandAction` and cancel from the returned decision. Preserve the reason for logging/player output.

- [ ] **Step 4: Remove duplicate handlers and registrations**

Delete actor-based Guild public-access handlers now covered by the resolver. Keep environmental behavior registered once.

- [ ] **Step 5: Run GREEN Paper tests and commit**

```bash
./gradlew :paper:test --tests '*LandPermissionListenerTest' --tests '*GuildsServicesWiringTest' --tests '*PluginMetadataTest'
git add common/src paper/src
git commit -m "refactor: route player land events through one resolver"
```

Expected: `BUILD SUCCESSFUL`; one resolver invocation per event.

---

### Task 6: Constructor-only service composition

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/PermissionServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/PlotServiceImpl.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/PermissionCache.java`
- Modify: `common/src/main/java/com/guilds/territory/standing/StandingEngine.java`
- Modify fixture: `paper/src/test/java/org/aincraft/guilds/GuildsServiceTestFixture.java`
- Tests: existing wiring, permission cache, and standing tests

**Interfaces:**
- `GuildsServices(JavaPlugin, PostgresDatabase, TerritoryRegistry)` requires registry.
- `PermissionCache` is shared by permission reads and permission-writing services.
- `StandingEngine(..., StandingStore, Logger)` rejects null store.

- [ ] **Step 1: Write failing null/cycle tests**

Assert constructors reject null required collaborators and that test composition has no post-construction setter calls.

- [ ] **Step 2: Run RED composition tests**

```bash
./gradlew :paper:test --tests '*GuildsServicesWiringTest' --tests '*PermissionServiceImplTest'
./gradlew :common:test --tests '*StandingEngine*'
```

Expected: old nullable constructors/setters violate new assertions.

- [ ] **Step 3: Pass registry during construction**

Delete `wireTerritoryRegistry` and `setTerritoryRegistry`; update plugin and fixtures.

- [ ] **Step 4: Break permission cycles**

Remove `GuildServiceImpl.permissionService`; resolve admin roles from guild data. Replace `PlotServiceImpl.permissionService.clearCache()` with shared `PermissionCache.invalidateAll()` after committed writes only.

- [ ] **Step 5: Require a standing store**

Provide a deterministic in-memory test store implementing the real interface; remove all null test arguments.

- [ ] **Step 6: Run GREEN composition tests and commit**

```bash
./gradlew :common:test --tests '*StandingEngine*'
./gradlew :paper:test --tests '*GuildsServicesWiringTest' --tests '*PermissionServiceImplTest'
git add common/src paper/src
git commit -m "refactor: require governance service dependencies"
```

Expected: `BUILD SUCCESSFUL`; no nullable service setter remains.

---

### Task 7: Persistence constraints and typed failures

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/database/migration/AddGovernanceInvariantMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/SchemaInitializer.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/AllianceServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildServiceImpl.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsGovernanceSource.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/TerritoryJson.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/PostgresTerritoryStore.java`
- Create test: `paper/src/test/java/org/aincraft/guilds/database/migration/GovernanceInvariantMigrationTest.java`
- Test: `common/src/test/java/com/guilds/territory/model/GovernmentTerritoryTest.java`
- Test: `common/src/test/java/com/guilds/territory/persist/PostgresTerritoryStoreTest.java`

**Interfaces:**
- Source reads return `GovernanceLookup`, including `Unavailable` on SQL errors.
- Migration adds one-guild/one-alliance and valid-form constraints only after diagnostic preflight.

- [ ] **Step 1: Write failing migration and decoder tests**

Create duplicate cross-alliance membership, absent capital membership, invalid form, SQL read failure, legacy missing guild form, and absent territory-local government cases.

- [ ] **Step 2: Run RED persistence tests**

```bash
./gradlew :paper:test --tests '*GovernanceInvariantMigrationTest'
./gradlew :common:test --tests '*GovernmentTerritoryTest' --tests '*PostgresTerritoryStoreTest'
```

Expected: current schema permits cross-alliance duplicates and current readers default failures.

- [ ] **Step 3: Implement diagnostic migration**

Preflight query groups `alliance_members` by `guild_id HAVING COUNT(DISTINCT alliance_id) > 1`; throw with all conflicting IDs. Validate capitals, then add uniqueness and form checks.

- [ ] **Step 4: Propagate typed read failures**

Do not catch SQL exceptions into monarchy or empty grants. Return `Unavailable`; do not cache it.

- [ ] **Step 5: Normalize legacy null only in codecs**

Map missing guild/alliance form to monarchy and absent territory-local government to anarchy before invoking strict constructors.

- [ ] **Step 6: Run GREEN persistence tests and commit**

```bash
./gradlew :paper:test --tests '*GovernanceInvariantMigrationTest' --tests '*GuildRenameMigrationTest'
./gradlew :common:test --tests '*GovernmentTerritoryTest' --tests '*PostgresTerritoryStoreTest'
git add common/src paper/src
git commit -m "fix: enforce durable governance invariants"
```

Expected: `BUILD SUCCESSFUL`; corrupt data fails with IDs and no silent winner.

---

### Task 8: Null analysis, documentation, review, and smoke verification

**Files:**
- Modify: `build.gradle.kts`
- Modify module build files if compile-only JSpecify dependencies are module-specific
- Create/modify affected `package-info.java` nullness declarations
- Modify: `README.md`

**Interfaces:**
- NullAway/JSpecify covers affected `com.guilds.territory` model/governance packages and the new Paper land resolver package.

- [ ] **Step 1: Verify official NullAway/JSpecify integration**

Use the librarian workflow to confirm current dependency versions and Java 26/Error Prone options from official sources before editing Gradle.

- [ ] **Step 2: Enable scoped null analysis**

Annotate intentional optional state; fix findings at source. Do not suppress affected packages or annotate required collaborators nullable.

- [ ] **Step 3: Update player-facing documentation**

README must state the federal rule, both democracy electorates, local land table, no implicit sibling rights, voter/admin separation, and fail-closed unresolved bindings. Remove alliance-supremacy claims.

- [ ] **Step 4: Run full automated verification**

```bash
./gradlew test
./gradlew check
./gradlew :paper:shadowJar
```

Expected: every command ends `BUILD SUCCESSFUL`; shadow JAR exists at `paper/build/libs/guilds-1.1.0.jar`.

- [ ] **Step 5: Run the Paper smoke scenario**

Start `./gradlew :paper:runServer` through the harness process manager. Observe the ready banner, then exercise local-democracy allow, alliance-office local denial, alliance-level authority allow, and unresolved-binding denial. Record exact observed decisions. If PostgreSQL configuration blocks startup, report that exact external prerequisite and rely only on completed real integration fixtures—never claim a live pass.

- [ ] **Step 6: Request code review and resolve findings**

Use the requesting-code-review workflow. Apply only evidence-backed fixes and rerun affected focused checks.

- [ ] **Step 7: Run completion verification and commit docs/tooling**

```bash
./gradlew test check :paper:shadowJar
git add build.gradle.kts api common paper README.md
git commit -m "chore: enforce governance null contracts"
```

Split README into `docs: explain federal governance permissions` if it is independently reviewable from tooling. Confirm final status is clean.
