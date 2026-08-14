# Guild Mob Invasions Design

## Purpose

Add administrator-triggered mob invasions against guild-owned plots. An invasion behaves like a raid: it has escalating waves, a shared bossbar, a defender victory condition, and a destructive failure condition. This first version establishes the invasion runtime and command surface; automatic scheduling can later call the same start operation.

## Scope

### Included

- Administrator commands to start, stop, and inspect an invasion.
- One concurrent active invasion per guild; different guilds may be invaded concurrently.
- Three configurable, escalating mob waves.
- Raid-style bossbar feedback.
- Destruction of ordinary structural blocks within the target guild's claimed plots.
- A persistent guild-wide damage marker from 0% through 100%.
- Persistent terminal outcomes: `DEFENDED`, `DEVASTATED`, and `CANCELLED`.
- Safe cancellation when the plugin or server restarts.

### Excluded

- Automatic or periodic invasion scheduling.
- Player-triggered invasions.
- Repairing or resetting accumulated guild damage.
- Reconstructing destroyed blocks.
- Building detection or per-building state.
- Per-plot damage markers.
- Claim removal, guild deletion, resident removal, or ownership transfer.
- Resuming an active invasion after restart.

## Administrator Command Contract

Commands:

```text
/territory invasion start <guild>
/territory invasion stop <guild>
/territory invasion status <guild>
```

All three operations require `territory.admin.invasion`, with access granted by default to server operators and the console.

`start` resolves `<guild>` by guild name. It rejects:

- an unknown or ambiguous guild name;
- a guild without any claimed plot;
- an unavailable target world;
- an invalid target location;
- a guild without an online resident;
- a guild that already has an active invasion.

The invasion center is the configured guild spawn when that location lies within one of the guild's claimed plots. Otherwise, it is the center of the guild home chunk at the world's safe highest block. If neither location resolves inside a claim owned by the target guild, `start` rejects the invasion.

`stop` rejects a guild without an active invasion. A successful stop removes only that invasion's tagged mobs and bossbar, records `CANCELLED`, and retains accumulated guild damage.

`status` reports the guild name, lifecycle state, current wave, living invasion mob count, guild damage percentage, world, and invasion center. When no invasion is active, it reports the most recent persisted outcome and current guild damage when available.

Every successful or rejected administrative operation gives a clear sender message. Successful starts and stops log the administrator identity, target guild, invasion identifier, and result.

## Architecture

Use a dedicated invasion service rather than wrapping Minecraft's native village raid implementation.

The domain service owns invasion validation, state transitions, wave progression, damage accounting, outcomes, and persistence. Paper adapters own command parsing, target-location resolution, mob spawning, entity tags, bossbars, event translation, and runtime cleanup.

The central runtime record contains:

- invasion identifier;
- target guild identifier and display name;
- target world and center coordinates;
- lifecycle state;
- current wave index;
- living tagged entity identifiers;
- accumulated destroyed-block count;
- configured block budget;
- start and terminal timestamps.

Valid active transitions are:

```text
ACTIVE -> DEFENDED
ACTIVE -> DEVASTATED
ACTIVE -> CANCELLED
```

Terminal states never transition back to active. Starting a later invasion creates a new invasion identifier while retaining the guild's accumulated damage marker.

## Wave Model

Each invasion has exactly three waves in this version. Configuration defines the entity type and count entries for each wave, so operators may create raid-themed or other compositions without code changes.

A wave begins by spawning all configured mobs at validated locations around the invasion center. Every mob receives persistent tags containing the invasion identifier and target guild identifier. The next wave begins only after every living tagged mob from the current wave has died or otherwise been validly removed.

If an entity cannot be spawned at a safe location, the spawner retries other configured positions within a bounded attempt count. If no entity in a required wave can be spawned, the invasion ends as `CANCELLED` and reports the operational failure; it must not award a defense or devastation result.

Defenders win when all tagged mobs in wave three are cleared. The service records `DEFENDED` and cleans up the bossbar and any stale tagged entities.

## Destruction And Damage

Only mobs tagged for the active invasion receive destructive privileges. Ordinary mobs, native raid mobs, and mobs from another invasion continue through existing guild and territory protection unchanged.

A tagged mob may damage blocks only when all conditions hold:

1. the block lies in a plot claimed by the mob's target guild;
2. the invasion is still active;
3. the block material belongs to the configured ordinary-structure allowlist;
4. the block is not in a protected category.

Protected categories always include:

- inventories and containers;
- functional blocks and workstations;
- valuable/resource blocks;
- portals and gateway blocks;
- the guild home anchor;
- command, structure, barrier, and unbreakable blocks;
- blocks outside the target guild's claims.

The allowlist is explicit configuration. This makes “ordinary structure” deterministic and prevents future Minecraft materials from becoming destructible by default.

Damage accounting uses a fixed configurable block budget. Every eligible block actually destroyed during an active invasion contributes one unit. Guild damage is:

```text
damagePercent = min(100, floor(accumulatedDestroyedBlocks * 100 / blockBudget))
```

Damage is guild-wide and persists across invasions and restarts. No action in this version reduces it.

When the marker reaches 100%, the service records `DEVASTATED`, stops wave progression, removes remaining tagged mobs, and closes the bossbar. Guild claims, residents, ownership, and stored resources remain unchanged.

## Bossbar

Online residents of the target guild see the bossbar regardless of location. Other players see it while within a configurable radius of the invasion center. Visibility is reconciled as players join, quit, change worlds, or move into and out of the radius.

The title contains:

```text
<Guild> Invasion — Wave <current>/3 — Damage <percent>%
```

The progress fraction represents living mobs in the current wave divided by the number spawned for that wave. The bar is red during normal combat and becomes purple when guild damage is at least 75%. Terminal outcomes send chat messages; the bar is removed rather than retained as a result display.

## Persistence And Restart

Persist the current guild damage marker and invasion history in PostgreSQL. Active runtime state is also persisted sufficiently to identify an interrupted invasion, but active mobs and bossbars are not resumed.

On startup, every persisted `ACTIVE` invasion is atomically changed to `CANCELLED`. Its existing guild damage is retained. Any loaded entity carrying an invasion tag for a non-active invasion is removed. This prevents duplicate waves and stale protection bypasses.

On normal plugin disable, active invasions follow the same cancellation path before database shutdown.

Persistence failure during a destructive state transition is fail-closed: stop further wave spawning and destructive privileges, clean up runtime entities and UI, log the failure, and leave the invasion unavailable for continued play until its state is safely recorded.

## Protection Integration

Existing protection listeners currently block hostile spawns, entity grief, and explosions inside protected guild territory. The invasion implementation must not weaken those general rules.

The Paper invasion listeners recognize only plugin-created persistent invasion tags. They authorize the narrow guild-scoped destructive path directly and do not treat `RAID`, `VILLAGE_INVASION`, or `CUSTOM` spawn reasons alone as proof of an invasion.

Every destructive event re-resolves the affected plot and owning guild. Entity tags are identity hints, not authorization by themselves.

## Configuration

Add an `invasions` configuration section containing:

- `enabled`;
- `damage.block-budget`;
- explicit destructible material allowlist;
- three ordered wave definitions with entity type/count entries;
- spawn radius and bounded spawn attempts;
- bossbar nearby-player radius;
- delay between cleared waves.

Invalid configuration disables the invasion subsystem at startup with exact validation errors. It does not disable the rest of the territory plugin.

## Testing And Verification

Domain tests cover:

- one active invasion per guild and concurrent invasions for different guilds;
- wave progression only after all current-wave mobs are cleared;
- `DEFENDED`, `DEVASTATED`, and `CANCELLED` transitions;
- fixed-budget percentage calculation and 100% saturation;
- retained accumulated damage across later invasions;
- interrupted-active recovery to `CANCELLED`;
- persistence failure closing destructive behavior.

Paper-facing tests cover:

- command permission and validation outcomes;
- guild spawn and home-chunk fallback resolution;
- persistent entity-tag recognition;
- exact target-guild claim checks for destruction;
- protected material categories;
- bossbar audience, title, and progress updates;
- cleanup on stop, devastation, defense, and disable.

Runtime verification starts an invasion against a test guild, observes all three waves and bossbar updates, confirms ordinary structural blocks can be destroyed only in that guild's claims, confirms protected/outside blocks remain intact, clears all waves for `DEFENDED`, runs a second invasion to 100% for `DEVASTATED`, and verifies restart cancellation retains damage without respawning mobs.

## Future Scheduling Extension

A scheduler will select eligible guild identifiers and call the same validated invasion-service start operation used by the admin command. Scheduling must not duplicate command parsing, spawn logic, or state transitions. Eligibility policy, cadence, warnings, cooldowns, and random selection remain a separate design.