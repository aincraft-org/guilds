# Scope-Aware Governance and Invariant Hardening Design

**Date:** 2026-08-08
**Status:** Approved for implementation planning

## Objective

Make governance and permissions match a player-comprehensible federal model:

> A guild government controls its town; an alliance government controls what guilds do together.

At the same time, harden the territory/governance architecture so invalid null, membership, electorate, and persistence states are rejected at their boundaries instead of silently becoming anarchy, wilderness, empty permissions, or a different governing body.

## Current Architecture Findings

The current implementation has several conflicting sources of truth and unenforced invariants:

1. `GovernanceRegistry.resolveForTerritory` replaces a bound guild with its alliance. Callers cannot choose local versus alliance scope.
2. `PermissionServiceImpl.effectiveForm` applies the alliance form to a member guild's plots and unplotted territory chunks. Joining an alliance can therefore change local build rights.
3. `BlockProtection` grants land actions to the effective government's entire electorate and grants basic rights to sibling-alliance members. An alliance king, minister, or delegate can consequently inherit local land authority.
4. Player events are gated independently by territory `ProtectionListener`/`InteractionProtectionListener` and guild `GuildPublicAccessListener`. Correct behavior depends on both permission engines being registered and agreeing.
5. `PermissionRules.allows` accepts an action but ignores it. The same seat membership grants membership management, policy authority, block breaking, block placing, and interaction.
6. `GuildBody.permissionsOf` returns a permission-map entry without first verifying `memberIds`, despite its contract saying permissions are returned only for members. A stale key can grant access.
7. `GuildBody` and `AllianceBody` check only top-level non-null values. They do not defensively copy collections, validate blank or duplicate IDs, enforce role subsets, or prove that a supplied `Government` was derived from their actual members.
8. `Government` factories accept nullable collections, silently drop null/blank holders, truncate holders beyond a requested seat count, accept duplicate holders, and accept unchecked term metadata. `Government.of(null, ...)` silently creates anarchy.
9. A territory with `governedByGuildId` that cannot be resolved falls back through older APIs to territory-local government or permissive paths. The stored binding is therefore not fail-closed.
10. `PermissionServiceImpl.territoryRegistry` is nullable and late-bound. If it is not wired, governed chunks without plot rows are treated as wilderness.
11. `GuildServiceImpl` and `PlotServiceImpl` hold late-bound `PermissionService` references to break dependency cycles. `StandingEngine` accepts a null store only to simplify tests.
12. Guild/alliance form reads and permission reads conflate absence with database failure. A form read failure defaults to monarchy; a permission read failure can become an empty grant set and continue into permissive defaults.
13. `allianceContainingGuild` selects the first match even though the database only prevents duplicate `(alliance_id, guild_id)` pairs, not one guild belonging to multiple alliances.
14. Existing tests deliberately encode alliance supremacy and sibling land access, so they must be revised rather than preserved accidentally.

The focused governance baseline passed before design work:

```text
./gradlew :api:test --tests '*Government*' --tests '*PermissionRules*' --tests '*GuildBodyTest' \
  :common:test --tests '*Governance*' --tests '*BlockProtection*' --tests '*FormPermissionsMatrix*' \
  :paper:test --tests '*PlotPermissionFormTest' --tests '*PermissionServiceImplTest'

BUILD SUCCESSFUL
```

Passing tests confirm the current contract; they do not validate the desired federal contract.

## Player Mental Model

### Guild / local scope

The guild owns local membership, local administration, local policy, guild-owned commons, and plots.

| Local form | Policy electorate | Guild-owned commons |
|---|---|---|
| `ANARCHY` | None | Wild; no formal land permission system |
| `MONARCHY` | Mayor as sovereign | Sovereign may modify; other residents need explicit land grants; ordinary interaction defaults remain |
| `OLIGARCHY` | Mayor and assistants as council | Council may modify; other residents need explicit land grants; ordinary interaction defaults remain |
| `DEMOCRACY` | Every current resident | Residents share guild-owned commons and may modify them |

Plot owners retain absolute rights under every assigned form. A public guild may allow outsiders to place and interact but never to break, preserving the existing public-town behavior. Operational administrators remain the mayor and assistants: being a voter does not let every democratic resident kick members, assign roles, or reconfigure permissions.

### Alliance / federal scope

The alliance owns alliance membership, alliance policy, diplomacy, influence declarations, territory-wide strategy, and alliance treasury decisions.

| Alliance form | Policy electorate |
|---|---|
| `ANARCHY` | None |
| `MONARCHY` | King as sovereign |
| `OLIGARCHY` | King and ministers as council |
| `DEMOCRACY` | One delegate for each member guild |

The initial delegate for a member guild is its mayor. The domain calls this role `delegate`, not `mayor`, so a later elected-delegate feature will not require another semantic migration.

Alliance office and sibling membership confer no implicit local plot or block rights. A guild can admit sibling residents using public access or explicit grants made in that local guild's permission context.

Alliance anarchy removes formal alliance authority only. It never makes member guilds' local land wild.

### Explicit territory-local governments

A territory without a guild binding may retain an explicitly configured seat-only government for administrative or NPC use. Normal player territories resolve through their bound guild. A missing guild binding means ungoverned; a present but unresolvable guild binding means unresolved and denied, not ungoverned.

## Scope-Aware Domain Model

### Governance context

Replace the ambiguous single effective body with an explicit two-level result:

```java
public record GovernanceContext(
        LocalGoverningBody local,
        Optional<AllianceBody> alliance
) {
}
```

`LocalGoverningBody` is a typed variant of:

- uncontained;
- unbound territory with its explicit local government;
- resolved bound guild;
- bound guild unresolved.

The resolution reason is preserved through every layer. The exact public reason codes are:

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

There is no generic `effectiveGovernmentForTerritory` API after cutover. Callers select one of these operations explicitly:

- local land and local policy use `context.local()`;
- territory-wide policy uses the alliance when present, otherwise the local guild;
- an attacking guild's influence declaration uses its alliance government when present, otherwise its guild government;
- alliance membership/diplomacy use a named alliance directly.

A `BOUND_GUILD_UNRESOLVED` status is never converted into local territory government, anarchy, or wilderness by `resolveForTerritory`, `governingGuildAt`, a listener, or a compatibility overload. Obsolete ambiguous methods are removed rather than retained as aliases.

### Explicit electorate sources

`GuildBody` no longer accepts an arbitrary `Government`. Its immutable inputs are:

- normalized nonblank guild ID and name;
- `GovernmentForm`;
- required mayor ID;
- immutable assistant IDs;
- immutable resident IDs;
- immutable toggles;
- immutable per-member land permissions.

`GuildBody.government()` derives seats from those roles. Its invariants are:

- mayor is a resident;
- assistants are residents;
- all IDs are nonblank and unique;
- permission-map keys are residents;
- democracy seat holders exactly equal resident IDs;
- oligarchy seat holders exactly equal mayor plus assistants;
- monarchy has exactly the mayor sovereign;
- anarchy has no seats.

`AllianceBody` no longer accepts an arbitrary `Government`. Its immutable inputs are:

- normalized nonblank alliance ID and name;
- `GovernmentForm`;
- required capital guild ID and king ID;
- immutable minister IDs;
- immutable member-guild IDs;
- immutable `delegateByGuildId` mapping.

Its invariants are:

- capital guild is a member;
- every delegate key is a member guild;
- every member guild has exactly one delegate when the form is democracy;
- delegate holder IDs are nonblank and unique within the alliance electorate;
- monarchy, oligarchy, and anarchy derive their exact role sets as described above.

The adapter `GuildsGovernanceSource` materializes these role sources from PostgreSQL. `PolicyRules` may continue using government seats because the snapshot itself now proves how those seats were derived. Tests compare the complete electorate with the complete resident/delegate source.

### Separate land and governmental authority

Replace `SovereignAction` with a land-only enum used by member permission grants:

```java
public enum LandAction {
    BREAK_BLOCK,
    PLACE_BLOCK,
    INTERACT,
    ENTER,
    PVP_ATTACK
}
```

`MemberPermissions` stores only `LandAction` values. It cannot store policy or membership authority.

Policy participation remains in `PolicyRules` (`canPropose`, `canVote`, `canDecree`). A narrowly named seat-holder rule is used for non-policy governmental actions such as influence declaration. The caller must first select local or alliance scope. No alliance authority API accepts a `LandAction`, so an alliance seat cannot acquire local block authority through a runtime branch.

Routine administrative roles remain separate from electorates. Mayor/assistants administer a guild; king/ministers administer an alliance. These roles never inherit across scopes.

## Single Land-Permission Resolver

Add a pure API contract consumed by Paper listeners:

```java
public interface LandPermissionResolver {
    PermissionDecision resolve(LandPermissionRequest request);
}
```

`LandPermissionRequest` contains a required actor ID, world ID, coordinates, and `LandAction`. `PermissionDecision` contains:

- allowed/denied;
- stable reason code;
- player-safe explanation;
- resolved local body ID when one exists.

The Paper implementation loads the territory, local guild snapshot, plot, ownership, membership, role, toggles, and explicit grants once. It then evaluates this fixed precedence:

1. uncontained wilderness → allow;
2. global administrative bypass → allow;
3. `BOUND_GUILD_UNRESOLVED` or `DATA_UNAVAILABLE` → deny;
4. local anarchy → allow;
5. plot owner → allow;
6. local formal land authority → allow;
7. explicit plot grant → allow;
8. explicit grant in the governing local guild's context → allow;
9. local member default based on local form and action;
10. public outsider default (place/interact, never break);
11. deny.

The local defaults are:

- `BREAK_BLOCK` and `PLACE_BLOCK`: democracy residents; monarchy sovereign; oligarchy council; no other implicit member grants;
- `INTERACT`: all local residents under assigned forms so towns remain usable;
- `ENTER`: local residents or public admission;
- `PVP_ATTACK`: local PvP toggle, with self-target handling kept explicit.

Sibling-alliance membership and alliance seats are absent from this pipeline. Explicit grants are checked only in the governing local guild's context; a grant in a sibling's own guild does not mutate another guild's land.

`ProtectionListener`, `InteractionProtectionListener`, and the relevant Guilds interaction handlers use this resolver for player actions. Duplicate player block/entity/container handlers in `GuildPublicAccessListener` are removed or reduced to behavior not covered by the unified listener. `BlockProtection` is narrowed to actor-free environmental rules such as fire, explosions, mob spawning, entity grief, and boundary mechanics.

## Nullability and Construction Rules

### Core model rules

For exported territory/governance models:

- required constructor inputs reject null;
- IDs and names reject blank values after trimming;
- collections and maps reject null, reject null elements, are defensively copied, and are exposed immutably;
- duplicate semantic IDs are rejected;
- optional state uses a typed variant or `Optional`, not an undocumented nullable pair;
- convenience overloads pass explicit defaults into strict constructors;
- mutators such as `withGovernment(null)` and `withPolicies(null)` reject null; explicit removal methods remain available.

`Government` construction additionally enforces:

- non-null form and seat collection;
- unique seat IDs and unique filled holder IDs;
- holder count never exceeds seat count;
- no silent dropping of null/blank holders;
- no silent truncation of holders or term metadata;
- non-negative term timestamps;
- term metadata only on filled representative seats;
- explicit vacant-seat construction rather than null-as-a-factory-argument;
- `Government.of(null, ...)` is invalid.

Persistence codecs perform legacy normalization before invoking these constructors. Core constructors never infer whether a missing form meant guild monarchy or territory anarchy.

### Required collaborators

- `TerritoryRegistry` becomes a required constructor argument of `GuildsServices` and `PermissionServiceImpl`; `wireTerritoryRegistry` and `setTerritoryRegistry` are deleted.
- `GuildServiceImpl` no longer depends on `PermissionService`. Administrative role checks occur through guild data at the command/service boundary.
- `PlotServiceImpl` no longer depends on `PermissionService` merely to clear a cache. A small shared cache/invalidation collaborator is constructed before both services.
- `StandingEngine` requires a non-null `StandingStore`; tests receive an in-memory fake.
- Constructors of affected services apply `Objects.requireNonNull` to every required collaborator.

`LookupResult` and `GoverningBody` nullable state pairs are replaced by explicit contained/uncontained and typed-body variants where this cutover touches their callers. Callers no longer rely on `isContained()` followed by `orElseThrow()` to assert an unstated invariant.

### Static enforcement

Add JSpecify nullness contracts and NullAway enforcement to the affected `com.guilds.territory` governance/model packages and the new Paper resolver package. Intentional optional values are annotated or represented explicitly. This prevents new raw-null collaborators and ambiguous optional contracts from re-entering silently without requiring an unrelated immediate rewrite of every legacy `org.aincraft.guilds` model.

## Persistence and Migration

Compatibility belongs at storage boundaries:

- missing guild/alliance `governance_form` decodes as the schema default `MONARCHY`;
- absent territory-local government decodes as explicit `ANARCHY`;
- old nullable collections decode to explicit empty collections before strict construction;
- stale permission rows for departed residents are not materialized into `GuildBody` and cannot pass `permissionsOf`;
- form-read or permission-read SQL failure returns `DATA_UNAVAILABLE`, not a default form or empty permission set.

Add a schema migration that:

1. checks for a guild ID appearing in more than one `alliance_members` row across alliances and aborts with the conflicting IDs if found;
2. adds uniqueness for `alliance_members.guild_id` after validation;
3. adds valid-value checks for guild/alliance `governance_form` values;
4. validates that every alliance capital is present in `alliance_members`;
5. validates guild mayors and assistants against `guild_residents` during snapshot materialization.

The migration does not choose a winner for contradictory governance data. Silent repair would change player authority without an auditable decision.

## Failure Behavior

Programmer and persisted-structure violations fail at construction/startup with an exception naming the field and relevant entity ID.

Runtime authorization returns a decision rather than throwing:

- `BOUND_GUILD_UNRESOLVED`: deny ordinary player action and preserve that code through the Paper event listener;
- `ALLIANCE_UNRESOLVED`: local land still uses the resolved local guild, but alliance governmental actions deny;
- `DATA_UNAVAILABLE`: deny and log the underlying storage error without caching an empty result;
- `UNCONTAINED`: allow wilderness behavior;
- global administrative bypass: allow recovery even for unresolved governed land.

Player messages remain safe and concise; logs contain body IDs and the underlying diagnostic exception.

## Verification Strategy

All behavior changes follow red-green-refactor. Production changes and their regression tests land in the same green atomic commit.

### Snapshot and model invariants

Tests prove:

- a stale non-member permission key is rejected and cannot grant;
- mutating source collections after construction cannot mutate a body;
- null, blank, duplicate, and inconsistent role IDs are rejected;
- mayor/assistant/member and capital/delegate/member relationships are enforced;
- excess holders and term entries are rejected rather than truncated;
- strict constructors reject null while persistence tests prove legacy normalization at the edge.

### Complete electorate contracts

Tests compare complete sets:

- guild democracy electorate equals all and only current residents;
- alliance democracy has one and only one unique delegate per member guild;
- monarchy and oligarchy contain all and only their declared role holders;
- membership/role changes produce fresh snapshots with fresh electorates.

### Local land matrix

A parameterized matrix covers each local form against:

- plot owner;
- local formal authority;
- ordinary local member;
- sibling-alliance member;
- alliance king/minister/delegate who is not local authority;
- outsider;
- public outsider;

for break, place, and interact. Changing only the alliance form must never change any local land result. Existing alliance-supremacy assertions in `BlockProtectionTest`, `FormPermissionsMatrixSmokeTest`, and `PlotPermissionFormTest` are deliberately revised.

### Scoped governmental actions

Tests prove:

- local policy uses local guild seats;
- territory/alliance policy uses alliance seats when an alliance exists;
- influence declaration uses alliance seats for an allied attacker and guild seats for an independent attacker;
- alliance seats remain authorized for their alliance actions;
- no alliance API accepts a `LandAction`;
- a guild resident does not gain alliance authority merely from local democracy.

### Fail-closed integration

A Paper-level regression test constructs a territory with a stored guild binding whose source cannot resolve that guild, sends a real listener-facing land request, and asserts:

- the decision code is `BOUND_GUILD_UNRESOLVED`;
- the event is cancelled for an ordinary player;
- the code was not converted to local government, anarchy, or wilderness;
- an explicit global administrative bypass can recover the land.

A storage-failure test similarly preserves `DATA_UNAVAILABLE`. Another integration test proves one resolver invocation per event and confirms duplicate player-action handlers are no longer registered.

### Composition and full verification

Focused tests cover each TDD slice. Final verification runs:

```text
./gradlew test
./gradlew check
./gradlew :paper:shadowJar
```

The built plugin is then started with the repository's Paper run configuration and exercised for one local-democracy action, one alliance-authority rejection on local land, one alliance-level authorization, and one unresolved-binding denial. Completion requires clean output from the changed path; no warning is treated as success.

## Documentation Changes

Update `README.md` to state the federal rule, local and alliance democracy electorates, local land matrix, explicit sibling-grant behavior, unresolved-binding denial, and the separation between voters and operational administrators. Remove all claims that an alliance form replaces local guild property rules or that sibling residents automatically share local commons.

## Non-Goals

- Player-run elections for replacing the default alliance delegate are not introduced; the current mayor supplies the initial delegate.
- A generic voting workflow for every administrative command is not introduced. Policy voting remains the collective decision mechanism; routine administrators remain named roles.
- No compatibility aliases retain the old alliance-supremacy resolver or nullable setters.
- Legacy mutable Guilds models outside the affected governance and permission composition paths are not comprehensively rewritten in this change.
- Economy, standing accrual rules, influence scoring, and territory geometry are unchanged except where required to consume the new explicit governance resolution.
