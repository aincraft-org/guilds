# Anchor-Based Territory Buildings Design

> Status: approved design, awaiting written-spec review  
> Date: 2026-08-14  
> Scope: free-form physical waystones and trading posts

## Goal

Turn the existing settlement facility directory into a player-facing building system where a single registered block provides behavior while players retain complete freedom to build roleplay structures around it.

The plugin recognizes the functional anchor only. Walls, roofs, neighboring blocks, signs, NPCs, holograms, roads, and decoration are presentation. They never determine whether a facility is valid. The first concrete building types are waystones and trading posts.

## Non-goals

- Multiblock templates, schematics, prescribed footprints, or material palettes.
- Automatic judgment of whether a surrounding structure looks complete.
- Player-issued custom placement items, recipes, item identity, or item recovery in the first implementation.
- An auction house, stock ledger, marketplace listings, banker NPC ownership, or shop UI.
- Arbitrary inventory-triggered teleportation or personal homes.
- Building levels, upgrade trees, or a generic perk catalog before concrete behavior requires them.

## Existing foundation

The implementation extends the existing contracts instead of creating a second building registry:

- `SettlementFacility` identifies a named facility at an exact block coordinate.
- `FacilityRegistry` rejects duplicate IDs and coordinates and requires each location to resolve inside its declared territory.
- `PostgresFacilityStore` persists facility documents through the shared database.
- `FacilityType.TRADING_POST` already exists; `WAYSTONE` is added.
- `BukkitEconomyBridge.resolveFacility` already exposes coordinate-based facility lookup.

`STORAGE` remains compatible and unchanged. It is not given inventory behavior by this feature.

## Domain model

A `SettlementFacility` remains immutable location metadata:

- stable ID;
- display name;
- territory ID;
- `FacilityType`;
- world ID and anchor block coordinates.

The facility does not copy a guild ID. Its current governing guild is resolved through the assigned territory so governance changes take effect immediately without rewriting every facility.

An anchor is **active** when all of the following are true at the time behavior is requested:

1. the world is loaded;
2. the anchor block exists at the registered coordinate;
3. its material is allowed for the facility type;
4. the facility still resolves inside its assigned territory.

An unavailable world, missing block, changed material, or invalid territory relationship makes the anchor inactive. Inactive records remain persisted but grant no behavior. Restoring an allowed block at the coordinate reactivates the facility without rewriting the record.

No production rule inspects neighboring blocks.

## Configuration

Initial configuration:

```yaml
buildings:
  placement-timeout-seconds: 60
  waystone:
    anchor-materials: [LODESTONE]
    warmup-seconds: 5
    cooldown-seconds: 60
  trading-post:
    anchor-materials: [BELL, LECTERN]
```

Configuration loading rejects non-positive placement timeouts, negative warm-up or cooldown values, unknown materials, air-like materials, and empty anchor-material sets for an enabled type. Invalid building configuration disables the building subsystem loudly rather than inventing defaults at runtime.

## Registration workflow

Normal player workflow:

```text
/territory building create <waystone|trading_post> <id> [display name]
/territory building cancel
```

`create` starts a per-player, short-lived placement session. The next right-click on a block attempts registration; it does not consume or modify that block.

Registration validates:

1. the placement session belongs to the interacting player and has not expired;
2. the selected block material is allowed for the requested type;
3. the block is inside exactly one territory;
4. the territory is governed;
5. the player belongs to that governing guild and has facility-management authority;
6. the facility ID and exact coordinate are unused.

Operators holding `azoth.territory.admin` may register facilities in any territory. Ungoverned territory is admin-only.

A failed attempt reports the reason and keeps the session active until cancellation or expiry, except an authorization change or expired session ends it. A successful registration ends the session.

Additional management commands:

```text
/territory building list [territoryId]
/territory building info <id>
/territory building remove <id>
```

List and info report type, assigned territory, coordinate, and current active/inactive state. Player removal requires current management authority for the assigned territory; administrators may remove any facility.

## Authorization boundary

Building management uses a dedicated service boundary that resolves the existing guild membership and authority model. Command and listener code do not independently reproduce guild-role rules.

The first implementation maps management to the nearest existing guild permission representing settlement configuration. If no exact permission exists, the implementation plan must introduce one explicit permission and wire it through the existing form-aware permission service. It must not infer authority from display rank names.

Waystone use is initially limited to members of the destination territory's governing guild. Travel between two waystones is allowed only when origin and destination are governed by that same guild. Operators do not receive gameplay travel bypass merely from being operators.

Alliance travel, public travel, tolls, and policy-driven access are deferred.

## Persistence transaction boundary

The current `PostgresFacilityStore.save(FacilityRegistry)` serializes a complete registry. Mutating the live registry before calling it would expose an uncommitted facility if the database write failed.

Building mutations therefore use a staged snapshot:

1. copy the live facility list;
2. apply the proposed registration or removal to a separate candidate `FacilityRegistry` backed by the same territory registry;
3. let candidate registration/replace validation reject duplicate IDs, duplicate coordinates, or invalid territory placement;
4. save the candidate snapshot in one database transaction;
5. only after commit succeeds, replace the live registry from the candidate list.

The store gains a snapshot-oriented save boundary if necessary; it must not require the live registry to mutate first. A failed database transaction leaves the live registry byte-for-byte equivalent at the model level. Registration/removal commands report failure and do not claim success.

Concurrent building mutations are serialized through one Paper-main-thread coordinator. The coordinator owns staging, persistence, and live replacement so two commands cannot stage from the same stale snapshot.

## Anchor lifecycle

### Authorized breaking

A block-break listener resolves a facility at the exact broken coordinate.

- An unauthorized actor's break is cancelled.
- An authorized actor requests durable removal through the same staged mutation coordinator.
- The event is cancelled until removal succeeds.
- The listener removes the candidate facility durably during the cancellable event; after success it leaves the event uncancelled so Bukkit performs the original block break.
- If persistence fails, the event remains cancelled and the facility remains live.

Environmental or non-player block changes cannot perform synchronous authorization. They are cancelled for registered anchors where the event API permits cancellation. Changes outside observable Bukkit paths are handled by use-time validation and leave the record inactive rather than silently deleting it.

### Chunk and world handling

There is no periodic full-world scan. Active state is evaluated:

- when list/info requests status;
- when a player interacts with an anchor;
- when waystone travel selects an origin or destination;
- when a relevant block event targets the coordinate.

Validation does not force-load a world or chunk merely to report status. A destination chunk may be loaded only as part of an authorized teleport attempt, using the normal Paper-safe path.

## Waystone behavior

A player must right-click an active waystone to begin travel. There is no inventory item or command that teleports from an arbitrary location.

The interaction presents reachable active waystones belonging to the same governing guild. The first implementation may use a deterministic text list plus a selection command if no existing GUI convention fits; it must expose the reachable-set calculation independently of presentation.

Travel flow:

1. validate the origin anchor and actor access;
2. resolve the selected destination by stable facility ID;
3. validate destination type, active state, governing guild, and access again;
4. calculate a safe landing position adjacent to or above the destination anchor;
5. verify territory teleport protection permits entry;
6. begin configurable warm-up;
7. cancel warm-up if the player moves to another block, takes damage, disconnects, dies, the origin/destination becomes invalid, or authorization changes;
8. revalidate destination and protection immediately before teleport;
9. teleport;
10. start cooldown only after successful teleport.

No valid safe landing means no teleport and no cooldown. The implementation must not remove blocks, place temporary blocks, teleport into liquids or solid collision, or bypass protection to manufacture a destination.

Cooldown is per player and runtime-memory-only initially, matching the existing hearthstone model. Server restart clears it by design. Waystone cooldown state remains separate from guild hearthstone cooldown unless a later design unifies them.

## Trading-post behavior

A right-click on an active trading-post anchor resolves:

- the `SettlementFacility`;
- its territory;
- its current governing guild, if any;
- the interacting player.

The core plugin reports the trading-post name and territory and fires one cancellable `TradingPostInteractEvent` carrying the facility, territory, nullable governing guild ID, and player. External commerce integrations subscribe through Bukkit's established event system; no parallel callback registry is introduced.

External commerce or NPC integrations may delegate an interaction to the exact anchor coordinate. The integration receives only validated active-facility context. The building subsystem does not own listings, inventories, prices, stock, banker entities, or transaction settlement.

If no integration consumes the interaction, the player still receives useful identity/status output. This makes the physical location observable without pretending a marketplace exists.

## Player-facing RP freedom

The anchor may be placed inside any player-created presentation that respects ordinary land protection:

- a market stall or large bazaar;
- a bank with a banker NPC;
- a shrine, gatehouse, tower, or roadside stone;
- indoor or outdoor construction;
- a rebuilt facade around the unchanged anchor.

The facility remains functional while neighboring construction changes. NPC removal, sign edits, roof destruction, or aesthetic rebuilding do not invalidate it. Only the registered anchor and territorial/governance rules matter.

## Error handling

- Duplicate ID or coordinate: reject before persistence.
- Outside or ambiguously resolved territory: reject.
- Invalid anchor material: reject without modifying the block.
- Unauthorized registration/removal/break: deny explicitly.
- Expired placement session: clear it and require a new command.
- Unknown or unloaded world during use/status: inactive.
- Database failure: live registry unchanged; interaction or block break fails closed.
- Missing safe destination: no teleport and no cooldown.
- Protection denial: no teleport and no cooldown.
- Missing commerce integration: show facility identity; do not fabricate trading behavior.

## Components

### API/common

- Add `FacilityType.WAYSTONE`.
- Preserve `SettlementFacility` as immutable location metadata.
- Extend `FacilityRegistry` only with snapshot/query operations genuinely needed by the coordinator; keep validation centralized.
- Make facility-store persistence accept the staged candidate snapshot without mutating live state.
- Add pure reachable-waystone/access calculation where it can remain Bukkit-free.

### Paper

- Building configuration loader.
- Placement-session service.
- Serialized building mutation coordinator.
- `/territory building ...` command surface and tab completion.
- Anchor interaction and block lifecycle listener.
- Active-anchor validator using Bukkit world/block state.
- Waystone travel service, warm-up listener, and selection presentation.
- Cancellable `TradingPostInteractEvent` integration seam.
- Plugin startup/shutdown wiring.

## Testing and verification

### Pure tests

- `WAYSTONE` persists and reloads with existing facility types.
- Registry still rejects unknown territories, outside locations, duplicate IDs, and duplicate exact coordinates.
- Candidate registration/removal validates without changing the live registry.
- Database save failure leaves the live registry unchanged.
- Same-governing-guild waystone reachability; reject other guilds, ungoverned territory, inactive anchors, and non-waystone facilities.

### Paper behavior tests

- Placement session ownership, expiry, cancellation, retry, and success.
- Anchor-material, territory, governance, membership, and authority validation.
- Registration persists before live visibility.
- Authorized break removes durably; persistence failure and unauthorized break cancel the event.
- Missing/wrong anchor is inactive; restoring the allowed block reactivates it.
- Neighboring block changes do not affect active state.
- Waystone interaction requires an active origin.
- Safe destination, protection, warm-up cancellation, revalidation, successful teleport, and post-success cooldown.
- Trading-post interaction emits one validated context and remains useful with no consumer.

### Runtime smoke verification

On a Paper development server with the configured database:

1. create a territory governed by a test guild;
2. register two lodestone waystones through command-then-click;
3. build arbitrary, different structures around both anchors;
4. confirm neighboring construction changes do not alter activity;
5. travel between the anchors and observe warm-up/cooldown;
6. remove or replace one lodestone and confirm it becomes inactive;
7. restore the lodestone and confirm reactivation;
8. register a trading post inside a free-form bank/market build;
9. interact and observe resolved identity/context;
10. force a facility-store failure in the dedicated integration test and verify the live registry does not change.

## Delivery order

1. Staged facility persistence and mutation coordinator.
2. Building configuration and anchor active-state validation.
3. Command-then-click registration, list/info/remove, and lifecycle protection.
4. Concrete waystone travel behavior.
5. Concrete trading-post interaction seam.
6. Focused tests, Paper smoke scenario, living-spec updates, and operator/player command documentation.

Each behavioral unit and its tests is committed atomically. The design document is a separate documentation commit.
