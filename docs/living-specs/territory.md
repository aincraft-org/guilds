# Territory (Spatial) — Living Spec

> Status: active  
> Last updated: 2026-08-29
> Related: `docs/superpowers/specs/2026-08-08-admin-map-editor-design.md` (editing UX lives under **map** / **web-api**)

## Intent

Own the **spatial truth** of large-map regions: named territories with outer
boundaries, nested zones, and deterministic `resolve(world, x, z)` for every
gameplay and API consumer.

Success looks like: no overlapping claims in a world; every gameplay system
(protection, tax, influence, standing) shares one registry; mutations that
violate spatial rules fail closed before persistence.

## Boundaries

### In scope

- `Territory`, `Zone`, `ZoneType` (`WILDERNESS`, `CLAIMABLE`), `Boundary`
  (polygon XZ vertices and/or chunk sets, union semantics).
- `TerritoryRegistry`: register / unregister / replaceAll, spatial resolve,
  same-world non-overlap (edge/corner touch OK).
- Zone non-overlap inside a territory; default zone type when contained but
  no named zone hits.
- Optional `governedByGuildId` binding field on territory (identity only —
  governance semantics live in **governance**).
- Territory-local government attachment when unbound (seat-only admin/NPC use).
- Durable load/save of territory documents via **persistence**
  (`PostgresTerritoryStore` + `TerritoryJson`).
- Admin command surfaces that list/lookup/reload/save territory state
  (`/territory …` spatial subcommands).
- Fast-travel facility types (`GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, `AIRSHIP`) and immutable territory-local travel policy.
- Governance-derived transport ownership and active/inactive endpoint lifecycle.
- Same-mode endpoint authorization, territory boundary policy, and bounded boat/airship physical validation.

### Out of scope / non-goals

- Guild/plot claim systems as the primary land model (guild plots are **guilds**).
- Administrator-triggered guild mob invasions scoped to guild plots; automatic scheduling, repair/reset, and reconstruction are not part of the current scope.
- Drawing UI (see **map** admin editor).
- Influence races, standing, tax math (other domains).

## Invariants

1. **No territory overlap** in the same world (shared edge/corner allowed).
2. **No zone overlap** inside a territory (same edge rule).
3. **Resolve determinism:** a point is uncontained, or exactly one territory;
   at most one named zone; else territory `defaultZoneType`.
4. **Atomic replaceAll:** full-set validation; registry unchanged if validation fails.
5. **Registry is gameplay memory truth** after load; durable write must succeed
   before treating a mutation as committed (coordinate with web-api mutation order).
6. Pure domain models and registry: **no Bukkit types**.
7. Transport ownership is derived from the territory's current governing guild;
   facility records are location-only and do not create a second membership
   authority.
8. A persisted transport record can be inactive when governance, capability,
   spawn, or physical-anchor checks fail; inactive records remain persisted and
   count toward facility quotas and global crystal/terminal cardinality.
9. Fast-travel endpoints use exactly matching compatibility modes, except that
   a local terminal may send an eligible resident to its own crystal in the
   same territory. Cross-territory transport requires both territory policies.
10. Boat routing proves bounded connectivity and returns only scalar distance;
    no route path is durable facility or territory data.

## Implementation guidance

| Layer | Location |
|-------|----------|
| Models | `guilds-api/.../model` (`Territory`, `Zone`, `Boundary`, …) |
| Registry | `guilds-api/.../registry/TerritoryRegistry` |
| JSON codec | `guilds-common/.../persist/TerritoryJson` |
| Postgres | `guilds-common/.../persist/PostgresTerritoryStore` |
| Commands | `guilds-paper/.../command/TerritoryCommand` |
| Fast travel models and policy | `guilds-api/.../model` (`FastTravelPolicy`, `FastTravelMode`) |
| Transport validation and travel flow | `guilds-paper/.../territory/building` |

- Prefer constructing invalid territories to **throw at model/registry** rather
  than load silently wrong geometry.
- Consumers should take `LookupResult` / territory id once; avoid re-resolve
  races mid-transaction when possible.
- Do not reintroduce file-backed `TerritoryStore` or dual backends.

### Testing

- Overlap rejection (territory + zone), edge-touch allowed.
- Resolve inside/outside, default zone vs named zone.
- `replaceAll` rolls back on partial invalid set.
- `FastTravelPolicy` validation, independent quota/boundary settings, defaults,
  and inactive-record counting.
- Transport authorization and construction checks: governance, capability,
  cardinality, anchor activity, mode compatibility, and same-territory rules.
- Bounded boat cache reuse/invalidation and scalar-only route results.

### Do not

- Treat plot chunks as a second territory registry.
- Soft-fail overlap on API load.
- Put spatial rules only in the web handler — keep them in registry/model.

## Current

### Capability (shipped)

- [x] Territory / zone / boundary models with validation
- [x] `TerritoryRegistry` non-overlap + resolve
- [x] `LookupResult` contained / zone type / territory id
- [x] Postgres-backed load/save of territory documents
- [x] `/territory` lookup, list, reload, save (spatial ops)
- [x] Optional `governedByGuildId` on territory documents
- [x] Free-form physical facilities use one exact anchor block; Paper runtime smoke pending
- [x] `/territory building` create/cancel/list/info/remove implemented; Paper runtime smoke pending
- [x] Active/inactive anchor lifecycle is implemented; Paper restoration smoke pending
- [x] Same-guild waystone travel and generalized fast travel are implemented; live teleport/protection smoke pending
- [x] Trading-post `TradingPostInteractEvent` implemented; live event observation pending
- [x] `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP` registration, construction validation, and interactions implemented; multi-territory Paper smoke pending
- [x] Governance-derived transport ownership, global crystal/terminal cardinality, and per-territory quotas implemented; live governance/quota-change smoke pending
- [x] `FastTravelPolicy` persistence and backward default implemented; focused codec tests pass
- [x] Bounded BOAT route connectivity/cache and AIRSHIP launch validation implemented; live route/launch smoke pending

### Open on the current surface

- [ ] Operator docs: recommended boundary authoring path (API vs future editor)
- [ ] Explicit validation error messages stable enough for admin tooling clients
- [ ] Paper runtime smoke across real facilities, multiple territories, safe landing, and transport policy changes

### Current notes

Admin interactive draw is **not** territory-domain work; it is **map** + **web-api**.
Spatial rules must not change for the editor — editor is a client of registry validation.
Non-transport building validity uses one exact anchor block and never inspects
neighboring blocks. Players may build arbitrary markets, banks, towers, shrines,
roads, NPC scenes, or other RP structures around the registered functional
anchor. Transport anchors are the deliberate exception: BOAT checks a bounded
shoreline water-entry window, while AIRSHIP checks a bounded launch platform and
clear vertical sky near its anchor.

### Fast-travel semantics (current)

Facilities are location-only records. The effective owner is the guild that
currently governs the facility's territory; an ungoverned territory provides no
usable transport endpoint. Losing governance, the required upgrade, the
physical anchor, or the guild-spawn match makes a persisted transport record
inactive immediately. A later governance rebind must pass the current owner,
capability, quota, anchor, and policy checks before the record is usable again.

`GUILD_CRYSTAL` is the guild spawn destination and an interactive crystal
endpoint. It must match the guild's current persisted spawn world and block
coordinates, and that spawn must resolve inside a territory governed by the
same guild. A guild has at most one persisted crystal; moving its spawn leaves
the old record retained but inactive until it is explicitly removed or
replaced. `TELEPORT_TERMINAL` is the guild's local departure structure and is
also limited to one persisted record per guild. It sends an eligible resident
to the guild's own crystal; allied crystal destinations require the separate
remote-crystal capability and all normal authorization checks.

`BOAT` endpoints must use matching BOAT endpoints and pass the configured local
shoreline/entry-window check. `AIRSHIP` endpoints must use matching AIRSHIP
endpoints and pass the configured launch-platform and clear-sky checks. Airship
travel does not perform a three-dimensional corridor search. Cross-world travel
is not supported.

An immutable `FastTravelPolicy` independently sets per-facility-type quotas and
the transport modes allowed to cross the territory boundary. Quotas count
persisted records by territory, type, and effective owning guild, including
inactive records. Creation, deletion/replacement, and explicit reactivation
validate a candidate registry before its durable save; lowering a quota does
not disable existing facilities, but blocks new creation until records are
removed. A quota never grants boundary permission, and a boundary allowance
never creates capacity.

BOAT route checks capture bounded, Paper-safe chunk snapshots in batches,
analyze immutable water masks off-thread, and run a bounded search. Results are
cached by world, endpoint pair, and water-topology revision; water changes
invalidate changed chunks, neighboring boundaries, and dependent entries. The
cache retains geometry/connectivity only, and a pending, invalidated, unloaded,
or budget-exhausted scan cannot start travel. Governance, alliance, upgrades,
activity, and territory policy are always checked again at travel time.

For BOAT, AIRSHIP, and remote CRYSTAL travel, origin and destination territory
IDs must differ and both territories must allow that mode. A terminal-to-own
crystal trip is the local exception when both endpoints are in one territory;
if they are in different territories, CRYSTAL boundary policy applies.

## Next

- [ ] Chunk-medium authoring helpers if admin editor needs server-side chunk↔polygon conversion utilities (only if not pure client-side)
- [ ] Stronger public error codes for overlap/invalid geometry on REST (with **web-api**)

## Future

- [ ] Additional zone types beyond WILDERNESS / CLAIMABLE (product decision)
- [ ] Multi-world transfer / rename tooling
- [ ] Automatic topology simplify / large-polygon performance indexes

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| (core) | Polygon and/or chunk-set boundaries, union | Authoring flexibility for large maps |
| (core) | Edge-touch not overlap | Adjacent regions without gaps/fights |
| 2026-08-06+ | Territory payload remains JSONB document | Avoid premature relational normalization |
| 2026-08-29 | Transport ownership follows current territory governance; inactive location records remain durable and quota/cardinality-counted | Avoid a second membership authority while preserving explicit removal and revalidation |

## Open questions

- [ ] Should `CLAIMABLE` gain gameplay semantics beyond label, or stay cartographic?
- [ ] Max vertices / max chunks per territory for performance caps?
