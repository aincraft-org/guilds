# Territory Standing & Harvest Bonuses — Design Spec

**Date:** 2026-08-07
**Status:** Draft for review (brainstorming)
**Scope owner:** Guilds Territory (api / common / paper modules)

## 1. Motivation

New World–style **territory development**: a guild that governs a territory
rewards its members' activity inside that territory with **standing**, which
raises the territory's **development tier**. Higher tiers grant:

- **Harvest multipliers** — extra drops from ores/crops (block drops) and
  from mobs killed inside the territory (mob drops).
- **Influence bonuses** — the owning guild's influence accrual in *other*
  territories is multiplied (a developed home makes the guild more effective
  at contesting rivals).

This is a development curve, not a race: standing never decays, has no
declaration/flip lifecycle, and is keyed **per territory per governing guild**.

## 2. Goals / Non-goals

Goals:
- Per-territory, per-guild standing `[0, cap]` (default cap 500).
- Three accrual sources: PvP kills, PvE kills, block breaks.
- Configurable tier thresholds with harvest and influence multipliers,
  loaded from **`bonuses.json`** under the plugin data folder.
- Harvest bonus applies to **block drops** and **mob drops** for
  governing-guild members inside the territory.
- Influence bonus: engine hook multiplies an actor guild's influence accrual.
- Separate pure-domain engine in `common/`, thin Paper wiring.
- Admin readout/reset command.
- Read API for external consumers (later `crafting`/`world` plugins).

Non-goals (explicitly out of scope):
- Per-player standing (roadmap P9 `missions` owns that).
- Decay, cooldowns, declaration, war/siege interactions.
- Touching the influence engine's state machine (only a read hook into it).
- Block *placement* or crafting as standing sources (farm-spam).
- Taxes or treasury effects from tiers (P8 settlements owns those).

## 3. Concepts

- **Standing**: `standing[territoryId][guildId]` clamped `[0, cap]`.
- **Actor guild**: the governing guild (`Territory.governedByGuildId`).
- **Development tier**: highest config tier whose `threshold <= standing`.
- **Eligible actor**: a member of the governing guild acting inside the
  territory.
- **Harvest bonus scope**: governing-guild members inside the territory only.
  Outsiders and rival guilds get nothing.

## 4. Accrual

| Source | Value | Trigger |
|--------|-------|---------|
| `PVP_KILL` | 10.0 | Player kills a player inside T (eligible actor) |
| `PVE_KILL` | 0.5 | Player kills a non-player entity inside T |
| `BLOCK_BREAK` | 0.15 | Player breaks a block inside T |

- Only eligible actors accrue; events by anyone else are no-ops.
- No defender push-pull: the owner is the only actor.
- No decay.

## 5. Tier table (`bonuses.json`)

```json
{
  "version": 1,
  "cap": 500.0,
  "sources": {
    "pvp-kill": 10.0,
    "pve-kill": 0.5,
    "block-break": 0.15
  },
  "tiers": [
    { "level": 1, "threshold": 0,     "harvest_multiplier": 1.0, "influence_multiplier": 1.0 },
    { "level": 2, "threshold": 100,   "harvest_multiplier": 1.2, "influence_multiplier": 1.1 },
    { "level": 3, "threshold": 300,   "harvest_multiplier": 1.5, "influence_multiplier": 1.25 }
  ]
}
```

- `tiers` sorted by `level`; first tier must start at `threshold` 0.
- `harvest_multiplier` / `influence_multiplier` must be `>= 1.0`.
- Missing/invalid `bonuses.json` → plugin logs SEVERE, subsystem disabled
  (fail closed) — never silently invents values.

## 6. Harvest bonus mechanics

- Fires at `BlockBreakEvent` (block drops) and `EntityDeathEvent` (mob
  drops) when the actor is an eligible governing-guild member inside the
  territory.
- **Multiplied drops only**: multiply the **base** drop count by
  `harvest_multiplier`; Fortune/Looting enchantments are **unaffected**
  (their own rolls are applied by vanilla; the bonus adds extra drops
  *after* the vanilla roll).
- Drop multiplication per-source:
  - Block drops: intercept `BlockBreakEvent` result; compute base drops
    `block.getDrops(hand)`; add extra copies of those drops (no
    `ItemStack` mutation of originals).
  - Mob drops: intercept `EntityDeathEvent`, parse `event.getDrops()`,
    add extra copies of each drop.
- Double-dipping prevented: the same event is not processed twice (the
  listener is a single handler per event type).
- No stacking with other plugins' multipliers (out of scope; future
  `world` P11 may compose them).

## 7. Influence bonus hook

- The InfluenceEngine calls a new hook when processing an accrual event:
  `StandingService.influenceMultiplierFor(guildId)` — the **max**
  `influence_multiplier` across all territories the guild currently
  governs (max tier per territory).
- Effective accrual for the actor guild in a foreign territory:
  `accrual * influenceMultiplierFor(actorGuild)`.
- The influence engine itself is unchanged otherwise: no state mutations,
  no persistence coupling, no lifecycle changes. Only the supplied
  multiplier is read at accrual time.
- This is a read-only commodity of the standing engine; the influence
  engine remains the authority on its own accrual events.

## 8. API surface

`api/src/main/java/com/guilds/territory/standing/` (pure domain):

- `enum StandingSource { PVP_KILL, PVE_KILL, BLOCK_BREAK }`
- `record StandingBar(String guildId, double value)`
- `record TerritoryStandingState(String territoryId, String ownerGuildId,
  List<StandingBar> bars)`
- `record StandingTier(int level, double threshold, double harvestMultiplier,
  double influenceMultiplier)`
- `interface StandingService`:
  - `Optional<TerritoryStandingState> standing(String territoryId)`
  - `List<TerritoryStandingState> all()`
  - `double harvestMultiplierFor(String territoryId, String guildId)`
  - `double influenceMultiplierFor(String guildId)`
  - `Optional<StandingTier> tierFor(String territoryId, String guildId)`
  - admin: `boolean adminSet(String territoryId, String guildId, double value)`,
    `boolean adminReset(String territoryId)`

`common`:

- `StandingEngine implements StandingService` — pure-domain synchronized
  engine (mirrors `InfluenceEngine` shape, minus declaration/flip):
  constructed with `(TerritoryRegistry, GovernanceRegistry,
  StandingConfig, PostgresStandingStore, Logger)`.
- `StandingConfig` — immutable record of `cap`, source values,
  `List<StandingTier>`; validation: `cap > 0`, source values non-negative,
  tiers non-empty, first threshold 0, thresholds ascending,
  multipliers `>= 1.0`; `defaults()` matches §5.
- `PostgresStandingStore` — mirrors `PostgresInfluenceStore`; JSON doc
  `{version, territories: {id: {ownerGuildId, bars: {...}}}}` in its own
  row/column.
- `StandingJsonConfigLoader` — reads `bonuses.json` from the data folder
  (BUILT-IN defaults when the file is absent; file present but invalid →
  load failure → subsystem disabled).

**Eligibility rule**: an event accrues standing only when the actor's
primary guild equals the territory's governing guild. Bars are keyed by
that owner guild; there is exactly one bar per territory (the owner's).

## 9. Paper wiring

- `StandingConfig` parsed in paper from `bonuses.json` via
  `StandingJsonConfigLoader` (file location: plugin data folder).
- `listener/StandingListener` — Bukkit events → engine:
  `PlayerDeathEvent` (PvP), `EntityDeathEvent` (PvE),
  `BlockBreakEvent`. Resolves world/coords → `registry.resolve` → territory;
  actor → `primaryGuildForMember`; eligible = actor guild == owner guild.
- `listener/HarvestBonusListener` — computes bonus at `BlockBreakEvent` /
  `EntityDeathEvent` (see §6); applies only for eligible governing-guild
  members; multiplies base drops; Fortune/Looting untouched.
- Influence hook: `InfluenceEngine.accrue(...)` reads
  `standingService.influenceMultiplierFor(guildId)` before adding to the
  bar (wired at construction in `GuildsTerritoryPlugin`).
- Commands (extend `TerritoryCommand`):
  - `/territory standing [territory]` — bars, owner, tier readout.
  - `/territory standing set <territory> <guild> <value>` (admin)
  - `/territory standing reset <territory>` (admin)
- Plugin lifecycle: construct engine + store in `onEnable` (after
  governance), register listeners + commands; flush store on `onDisable`.

## 10. Persistence

- One PostgreSQL row per territory, JSONB document (mirrors
  `PostgresInfluenceStore`): `standing_territories` table, columns
  `territory_id TEXT PRIMARY KEY`, `owner_guild_id TEXT`,
  `bars JSONB`, `version INT`.
- Missing row → empty for that territory (backward compatible).
- Corrupt row → log SEVERE, subsystem fails closed (no operations) until
  fixed — mirror of influence store behavior.
- Batched flush (periodic, default 60 s, plus on plugin disable) writes
  bar mutations; admin set/reset persist synchronously.

## 11. Testing

`common/src/test/java/com/guilds/territory/standing/`:
- `StandingEngineAccrualTest` — eligibility (unowned territory, outsider,
  non-governing guild), per-source values, clamp at cap, owner-change
  reset.
- `StandingEngineTierTest` — tier selection at thresholds, multipliers
  for known/max tiers, influence multiplier across multiple governed
  territories.
- `StandingJsonConfigLoaderTest` — defaults when missing, validation
  failures, parsing from a temp file.
- `PostgresStandingStoreTest` — round-trip with `PostgresTestDatabase`
  (mirrors `PostgresInfluenceStoreTest` if present; else
  `PostgresTerritoryStoreTest` style).

Paper:
- `StandingListenerTest` — event mapping (PvP/PvE/block) with mocked
  Bukkit locations; `HarvestBonusListenerTest` — base drops multiplied,
  Fortune unaffected, eligibility gating.
- `InfluenceEngineStandingHookTest` — accrual honored with
  `influenceMultiplierFor` > 1.0.

## 12. Config sketch (packaged default)

Packaged under `paper/src/main/resources/bonuses.json`:

```json
{
  "version": 1,
  "cap": 500.0,
  "sources": {
    "pvp-kill": 10.0,
    "pve-kill": 0.5,
    "block-break": 0.15
  },
  "tiers": [
    { "level": 1, "threshold": 0,     "harvest_multiplier": 1.0, "influence_multiplier": 1.0 },
    { "level": 2, "threshold": 100,   "harvest_multiplier": 1.2, "influence_multiplier": 1.1 },
    { "level": 3, "threshold": 300,   "harvest_multiplier": 1.5, "influence_multiplier": 1.25 }
  ]
}
```

## 14. Acceptance criteria (exit gate)

- [ ] Governing-guild member kills a mob inside the territory → the owner's
      bar in that territory increases by `pve-kill`; an outsider's kill is a
      no-op.
- [ ] Governing-guild member breaks a block inside the territory → bar
      increases by `block-break`; breaking outside the territory is a no-op.
- [ ] Bar clamps at `cap`; no overflow past it.
- [ ] Tier selection: standing `99` → tier 1; `100` → tier 2; `300.5` →
      tier 3 (tiers saturate).
- [ ] `harvestMultiplierFor` returns the owner's tier multiplier; non-owner
      guilds get `1.0`.
- [ ] Harvest bonus listener: block break by an owner member inside the
      territory yields `base * multiplier` drops (extra copies added), and
      Fortune on the tool does not inflate the multiplier; outsider breaker
      gets vanilla drops.
- [ ] Mob death inside the territory by an owner member multiplies drops;
      death outside by anyone is vanilla.
- [ ] `influenceMultiplierFor(guildId)` returns `1.0` for a guild with no
      governed territory, and the max tier multiplier across its governed
      territories otherwise.
- [ ] InfluenceEngine accrual for an actor guild with `influenceMultiplier`
      > 1 uses the multiplied value; the influence state's existing lifecycle
      (declare/cancel/flip/cooldown) is unchanged.
- [ ] Owner-change (territory rebind, including influence flip) resets the
      standing bar; cooldown is untouched (mirrors influence rule 2).
- [ ] `bonuses.json` missing → built-in defaults; present but invalid →
      subsystem disabled with SEVERE log (no partial state).
- [ ] State persists to PostgreSQL: restart reloads bars; admin
      set/reset round-trips.
- [ ] `/territory standing` renders bars, owner, tier; admin set/reset
      commands work and persist.

## 15. Design decisions (locked)

1. **Separate standing module** (Approach A) — not folded into
   `InfluenceEngine`: different lifecycle, no declaration state, no
   persistence coupling. Shares only the read-only influence multiplier
   hook.
2. **Harvest = block drops + mob drops**, one tier multiplier.
3. **Bonus applies to governing-guild members only**; outsiders/rivals
   excluded.
4. **Standing accrues to the governing guild only**; no attacker/second
   accrual path.
5. **Influence bonus from own-territory development tier** (max across
   governed territories) applied to that guild's accrual in foreign
   territories.
6. **Fortune/Looting unaffected**; the bonus multiplies base drops only.
7. **`bonuses.json` is the config source**; state persists to PostgreSQL.
