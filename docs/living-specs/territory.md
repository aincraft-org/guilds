# Territory (Spatial) — Living Spec

> Status: active  
> Last updated: 2026-08-17  
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

## Implementation guidance

| Layer | Location |
|-------|----------|
| Models | `api/.../model` (`Territory`, `Zone`, `Boundary`, …) |
| Registry | `api/.../registry/TerritoryRegistry` |
| JSON codec | `common/.../persist/TerritoryJson` |
| Postgres | `common/.../persist/PostgresTerritoryStore` |
| Commands | `paper/.../command/TerritoryCommand` |

- Prefer constructing invalid territories to **throw at model/registry** rather
  than load silently wrong geometry.
- Consumers should take `LookupResult` / territory id once; avoid re-resolve
  races mid-transaction when possible.
- Do not reintroduce file-backed `TerritoryStore` or dual backends.

### Testing

- Overlap rejection (territory + zone), edge-touch allowed.
- Resolve inside/outside, default zone vs named zone.
- `replaceAll` rolls back on partial invalid set.

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
- [ ] Free-form physical facilities implemented through one exact anchor block; Paper runtime smoke pending
- [x] Building commands live on `/guilds building` (guild-owned)
- [ ] Active/inactive anchor lifecycle implemented; Paper restoration smoke pending
- [ ] Same-guild waystone travel implemented; live teleport/protection smoke pending
- [ ] Trading-post `TradingPostInteractEvent` implemented; live event observation pending
- [x] `STORAGE` building type is placeable; item bank UI implemented (**guild-storage**)

### Open on the current surface

- [ ] Operator docs: recommended boundary authoring path (API vs future editor)
- [ ] Explicit validation error messages stable enough for admin tooling clients

### Current notes

Admin interactive draw is **not** territory-domain work; it is **map** + **web-api**.
Spatial rules must not change for the editor — editor is a client of registry validation.
Building validity never inspects neighboring blocks. Players may build arbitrary
markets, banks, towers, shrines, roads, NPC scenes, or other RP structures around
the registered functional anchor.

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
| 2026-08-06+ | No JSON file fallback | Single durable truth with Guilds |
| 2026-08-17 | Building commands leave `/territory` | Guilds own buildings in a region; `/territory building` is a pointer |

## Open questions

- [ ] Should `CLAIMABLE` gain gameplay semantics beyond label, or stay cartographic?
- [ ] Max vertices / max chunks per territory for performance caps?
