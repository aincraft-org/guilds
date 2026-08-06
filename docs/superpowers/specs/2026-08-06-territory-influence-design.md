# Territory Influence System — Design Spec

**Date:** 2026-08-06
**Status:** Approved (design gate passed; rename workstream approved as separate phase)
**Scope owner:** Azoth Territory (api / common / paper modules)

## 1. Motivation

Add a New World–style territory **influence race** to the Azoth Territory
plugin: rival guilds accrue influence inside enemy territories through
activity; at 100% a guild may **declare**, and after a countdown the
territory **flips** to the challenger. The race is gated on **alliance**
membership — guilds in the same alliance cannot contest each other, and
unaffiliated guilds cannot play at all (either direction).

Terms are guilds (towns) and alliances (nations): the guilds subsystem is
the single source of truth, the territory layer only records bindings.
This spec also covers a terminology workstream replacing "town"/"nation"
vocabulary with "guild"/"alliance" in docs and code (see §10).

## 2. Goals / Non-goals

Goals:
- Per-territory influence bars (one per attacking guild), `[0, cap]`.
- Four accrual sources: PvP kills, PvE kills, block break/place, crafting.
- Push-pull defense: owner-guild activity reduces attacker bars.
- Alliance gate: both sides must be alliance members; same alliance blocks.
- Declare command → configurable countdown → territory ownership flip.
- Post-flip cooldown (default 7 days) before the next race; bar reset.
- Restart-safe persistence (`influence.json`) with recovery revalidation.
- Read API for external systems (later war plugins).
- Unit-testable pure-domain engine in `common/`; thin Paper wiring.

Non-goals (explicitly out of scope, per project README):
- Actual war/siege battles, PvP events, or combat mechanics.
- Paid influence (treasury purchases).
- Passive decay over time.
- Influence coloring of the web map UI (JSON exposure only in v1).
- Changing guilds DB role keys unless storage format verifies safe (§10.4).

## 3. Concepts

- **Race:** for a governed territory T (owner guild G), each attacker guild A
  has an influence bar `[0, cap]` (cap default 100, configurable).
- **Eligibility** (attacker A vs territory T), all must hold:
  1. T exists and `T.governedByGuildId` = G, and G resolves to a `GuildBody`.
  2. A resolves to a `GuildBody`, and `A.id != G.id`.
  3. `allianceContainingGuild(A)` and `allianceContainingGuild(G)` both
     present (unaffiliated guilds cannot play).
  4. `alliance(A) != alliance(G)` (same-alliance guilds cannot contest).
  5. T is not in a post-flip cooldown (`now >= cooldownUntilEpochMs`).
  6. No active declaration on T.
- **Actor guild:** a player's primary guild = first guild listing them as
  member (`GovernanceRegistry.primaryGuildForMember`), stable id order.
- **Defender:** any member of the owner guild G (not alliance-mates).

## 4. Influence sources

Configurable values (defaults):

| Source | Value | Trigger |
|--------|-------|---------|
| `pvp-kill` | 10.0 | Player kills a player inside T; **no accrual if killer's alliance == victim's alliance** |
| `pve-kill` | 0.5 | Player kills a non-player entity inside T |
| `block-break` | 0.1 | Player breaks a block inside T |
| `block-place` | 0.1 | Player places a block inside T |
| `craft` | 0.2 | Player completes a craft inside T (once per craft event) |

- Only eligible attackers accrue. Events by ineligible actors are no-ops.
- **Defender push-pull:** a defender's event subtracts the same source value
  (times `defender-multiplier`, default 1.0) from **every** attacker bar with
  value > 0 on T. Defender events require T governed by G (revalidated) and
  no active declaration; cooldown implies no bars, so no-op.
- Bars clamp to `[0, cap]`. Accrual is integer-typed double; persistence
  rounds to 2 decimals.

## 5. Race lifecycle

1. Bar reaches `cap` → guild A is **declarable** on T (eligibility
   re-checked at declare time).
2. `/territory declare <territory> [confirm]` — actor's primary guild A must
   be declarable; actor must hold the guild's **sovereign seat** (leader) per
   its government form; `confirm` required. Sets `declaration{guildId=A,
   declaredAt=now, flipAt=now + declare-countdown-hours}` (default 24h).
   - While a declaration is active: no accrual (attacker or defender), no
     other declarations. The race is settled.
3. `/territory declare cancel <territory>` — same authority cancels; bars
   intact, race resumes immediately.
4. On tick (or recovery at load): if `flipAt <= now`, **revalidate**:
   - T still exists and is governed by G; A still exists; both still have
     alliances; alliance(A) != alliance(G).
   - Valid → flip: execute the §6 journal flip (commit `pendingFlip`
     marker → apply ownership + persist territories.json → finalize
     influence state with cooldown), broadcast server-wide.
   - Invalid → cancel declaration, log warning, bars retained (inert).

## 6. State & persistence (`influence.json`)

```json
{
  "version": 1,
  "territories": {
    "everfall": {
      "ownerGuildId": "everfall-town",
      "cooldownUntilEpochMs": 0,
      "bars": { "rival-guild": 62.5, "other-guild": 100.0 },
      "declaration": {
        "guildId": "rival-guild",
        "declaredAtEpochMs": 1780000000000,
        "flipAtEpochMs": 1780086400000
      }
    }
  }
}
```

- **No comments in the file** — literal JSON, stable format for tooling.
- `ownerGuildId` is a snapshot of `T.governedByGuildId` at state creation.
- `pendingFlip` — journal marker present only during flip execution and
  crash recovery (§6 below); absent in steady state.
- **Persistence cadence:** bar mutations accumulate in memory. A batched
  flush (periodic — default every 60 s — plus on plugin disable) writes the
  file via temp-file + atomic move (mirrors `FacilityStore`). Declaration
  and flip transitions **persist synchronously and atomically** on the
  event that creates them — they are rare, load-bearing transitions.
- Missing file → empty state (backward compatible).
- **Corrupt file** (parse/version failure on load): never silently discarded.
  The file is first moved aside as
  `influence.json.corrupt-<epochMs>` (preserved for manual recovery), then
  the engine starts with an empty state and logs SEVERE pointing at the
  backup. If even the preservation move fails, the influence subsystem
  fails closed (all operations no-op) until the file is removed or fixed.

### Recovery on load (eager, per territory entry)

Run in this order — journal first, then per-entry rules:

0. **Journal**: if `pendingFlip` is present, run the journal recovery
   (see below) before any per-entry rule.
1. Territory no longer registered → drop the entry.
2. `ownerGuildId` != current `governedByGuildId` → external rebind (no
   marker involved): reset bars + declaration, **keep** cooldown, log.
3. Overdue declaration (`flipAt <= now`, no marker) → run the §5.4
   revalidation **before applying**; valid → execute the journal flip
   (steps 1–3 below); invalid → cancel declaration + log, never auto-flip
   an ineligible takeover.
4. Cooldown expired → normal (no action; `cooldownUntilEpochMs` left in
   place, cleared lazily or on next state write).

### Flip write ordering & crash recovery (journal marker)

A flip updates two persisted documents (territories.json owner + influence
state). The influence state carries a **journal marker** so a crash mid-flip
can never lose the takeover or its cooldown:

```json
"pendingFlip": {
  "territoryId": "everfall",
  "oldOwnerGuildId": "everfall-town",
  "newOwnerGuildId": "rival-guild",
  "flipAtEpochMs": 1780086400000,
  "cooldownUntilEpochMs": 1780691200000
}
```

Flip sequence (all writes atomic temp-file moves):

1. **Commit the flip**: write influence.json with `pendingFlip` set
   (old and new owner pinned; cooldown precomputed; `declaration` cleared;
   `bars` reset; `ownerGuildId` still the old owner). Synchronous, atomic.
2. **Apply ownership**: `territories.register(T.withGoverningGuild(
   newOwnerGuildId))` + `TerritoryStore.save` (atomic).
3. **Finalize**: write influence.json with `ownerGuildId = newOwnerGuildId`,
   `cooldownUntilEpochMs` from the marker, `pendingFlip` cleared.

Recovery on load: if `pendingFlip` is present, first check that the
territory's current `governedByGuildId` still equals
`pendingFlip.oldOwnerGuildId`:

- **Owner moved on** (external rebind during the crash window) → the flip
  is void: clear `pendingFlip`, write no cooldown, keep `declaration`
  cleared and `bars` reset, log. Never overwrite the new owner.
- **Owner unchanged** → revalidate eligibility (owner guild exists,
  attacker guild exists, both allied, alliances differ,
  `flipAtEpochMs <= now`):
  - Eligible → run steps 2–3 (idempotent: re-registering the same owner is
    harmless; the cooldown comes from the marker, so the new owner is never
    left contestable).
  - Ineligible → cancel: clear `pendingFlip`, keep `declaration` cleared and
    `bars` reset, write no cooldown, log.

The marker is written only at flip execution (`flipAt <= now` already
holds), so recovery always completes, never re-arms, and never double-applies.
`tickFlips` also processes a stale marker with `flipAt > now` defensively
(wait for due time), though this cannot occur in normal operation.

## 7. API surface

`api/src/main/java/com/azoth/territory/influence/` (pure domain, no Bukkit):

- `enum InfluenceSource { PVP_KILL, PVE_KILL, BLOCK_BREAK, BLOCK_PLACE, CRAFT }`
- `record InfluenceBar(String guildId, double value)`
- `record Declaration(String guildId, long declaredAtEpochMs, long flipAtEpochMs)`
- `record TerritoryInfluenceState(String territoryId, String ownerGuildId,
  long cooldownUntilEpochMs, List<InfluenceBar> bars, Declaration declaration)`
- `record DeclareResult(DeclareStatus status, String message)` with
  `enum DeclareStatus { DECLARED, CANCELLED, NOT_ELIGIBLE, NOT_AT_CAP,
  NOT_AUTHORIZED, RACE_ACTIVE, TERRITORY_UNKNOWN, UNGOVERNABLE, DISABLED }`
- `interface InfluenceService`:
  - `Optional<TerritoryInfluenceState> influence(String territoryId)`
  - `List<TerritoryInfluenceState> all()`
  - `DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs)`
  - `DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs)`
  - `boolean isDeclarable(String territoryId, String guildId, long nowEpochMs)`
  - `boolean isCooldownActive(String territoryId, long nowEpochMs)`

`common`:
- `InfluenceEngine implements InfluenceService` — all rules (§3–§5),
  thread-safe (synchronized mutations), constructed with
  `(TerritoryRegistry, GovernanceRegistry, InfluenceConfig)`.
- The public interface above is the external surface. The engine additionally
  exposes two methods used only by the Paper layer (not on the interface):
  `accrue(String territoryId, String guildId, InfluenceSource source,
  long nowEpochMs)` — records an activity event (returns the updated bar or
  `Optional.empty()` when ineligible) — and
  `tickFlips(long nowEpochMs)` — applies due flips (§5.4) and returns the
  number of territories flipped. The flip tick task, listener events, and
  load recovery call these; external consumers never do.
- `InfluenceStore` — JSON persistence (§6), mirrors `FacilityStore`.
- `InfluenceConfig` (pure values record) — parsed in paper from
  `config.yml` via `InfluenceConfig.fromBukkit` (mirrors `EconomyConfig`).

## 8. Paper wiring

- `InfluenceConfig.fromBukkit(FileConfiguration)` — `influence` block:
  `enabled`, `cap`, `values.*`, `defender-multiplier`,
  `declare-countdown-hours`, `post-flip-cooldown-days`, `flush-seconds`.
- `listener/InfluenceListener` — Bukkit events → engine:
  `PlayerDeathEvent` (PvP + PvE), `BlockBreakEvent`, `BlockPlaceEvent`,
  `CraftItemEvent`. Resolves world/coords → `registry.resolve` → territory;
  actor → `primaryGuildForMember`. Registered only when `enabled`.
- Flip tick: Bukkit scheduler repeating task (60 s interval) calling
  `engine.tickFlips(now)`; also invoked on load recovery and at declare.
- Commands (extend `TerritoryCommand`):
  - `/territory influence [territory]` — bars, owner, cooldown, declaration.
  - `/territory declare <territory> [confirm]`
  - `/territory declare cancel <territory>`
  - `/territory influence set <territory> <guild> <value>` (admin,
    permission `azothterritory.admin`)
  - `/territory influence reset <territory>` (admin)
- Plugin lifecycle: construct engine + store in `onEnable` (after
  governance), register listener + tick + commands; flush store in
  `onDisable`.

## 9. Web exposure

- `GET /api/territories/{id}` response gains a read-only `influence` object
  (bars, declaration, cooldown) when the engine is enabled.
- `GET /api/influence` — all territory influence states.
- `TerritoryWebServer` constructor gains an influence supplier; map UI
  unchanged in v1.

## 10. Terminology workstream (approved, separate phase)

### 10.1 README

Rewrite the governance/sovereignty and block-protection sections to speak
only of **guilds** and **alliances**: remove "town", "towns", "nation",
"nations" as vocabulary; `/town` → `/guild`, `/nation` → `/alliance`;
"member-town resident" → "member-guild member". Add a Territory Influence
System section describing this spec's features. Archived docs under
`docs/archived-guilds/` stay untouched (history).

### 10.2 Code rename (Nation → Alliance)

`paper/src/main/java/org/aincraft/guilds/`:
- `models/Nation.java` → `models/Alliance.java` (class `Alliance`).
- `services/NationService.java` → `services/AllianceService.java`;
  `services/impl/NationServiceImpl.java` → `services/impl/AllianceServiceImpl.java`.
- `commands/brigadier/NationBrigadierCommand.java` →
  `commands/brigadier/AllianceBrigadierCommand.java`. Command literal
  becomes `alliance`; **aliases `/n` and `/nation` retained** (registered
  redirects) for player compatibility.
- `listeners/NationListener.java` → `listeners/AllianceListener.java`.
- `GuildsGovernanceSource`, `GuildsServices`, `BrigadierCommandRegistry`,
  `SchemaInitializer`: identifiers + wiring updated; `getNationService()`
  → `getAllianceService()`, `setNationForm` → `setAllianceForm`.
- API-module Javadocs mentioning "nation" (e.g. `GovernanceSource`,
  `AllianceBody`) updated to alliance vocabulary.
- Paper tests referencing `Nation*` updated.

### 10.3 Database migration

- New idempotent migration (next version) renames tables
  `nations` → `alliances`, `nation_members` → `alliance_members`,
  `nation_ministers` → `alliance_ministers`, `nation_relations` →
  `alliance_relations`, and recreates indexes under new names — following
  the `AddGuildRenameMigration` precedent (guarded ALTERs, commit at end).
- `AddNationMigration` (v11) is reworked to create the `alliance_*` tables
  for fresh installs; the rename migration is a no-op when no `nations`
  tables exist. Version numbers unchanged (already-applied tracking stays
  consistent).
- Data is preserved; no column drops.

### 10.4 Compatibility-sensitive keys (verify before touching)

- Permission-role key `"nation"` (`Permission.NATION`,
  `RoleArgumentType.ROLE_TYPES`, `PlotBrigadierCommand.getRoleFlagIndex`):
  **verify storage format first**. If role permissions are stored as
  bitmask/columns, rename the key to `"alliance"` with a migration; if
  stored as text keys, keep `"nation"` as the storage key (display can be
  renamed) and document it.
- `plugin.yml` / `guilds-config.yml` permission nodes `guilds.nation.*`:
  renamed to `guilds.alliance.*` with `guilds.nation.*` kept as aliases if
  the permission system supports them; otherwise document the node rename.
- SQL column names inside `alliance_*` tables (`capital_guild_id`, etc.)
  keep their current names unless the migration plan explicitly covers them.

## 11. Testing

`common/src/test/java/com/azoth/territory/influence/`:
- `InfluenceEngineTest` — eligibility matrix (unowned, same guild, same
  alliance, unaffiliated attacker, unaffiliated owner, cooldown, active
  declaration); per-source accrual values; clamp at cap; defender push-pull
  subtracts from all bars; same-alliance PvP kill accrues nothing;
  declare requires cap + authority + eligibility; declaration locks the
  race; cancel resumes; flip rebinds owner, resets bars, sets cooldown,
  persists; flip revalidation failure cancels; owner-change resets state.
- `InfluenceStoreTest` — round-trip; missing file → empty; owner-mismatch
  recovery; overdue-flip recovery (valid flip applies, invalid cancels).

Paper: `InfluenceConfigTest` (parsing/defaults) — mirrors `EconomyConfig`
test style if present.

## 12. Config sketch

```yaml
influence:
  enabled: true
  cap: 100.0
  values:
    pvp-kill: 10.0
    pve-kill: 0.5
    block-break: 0.1
    block-place: 0.1
    craft: 0.2
  defender-multiplier: 1.0
  declare-countdown-hours: 24
  post-flip-cooldown-days: 7
  flush-seconds: 60
```
