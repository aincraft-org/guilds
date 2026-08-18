# Persistence — Living Spec

> Status: active  
> Last updated: 2026-08-17  
> Related: `docs/superpowers/specs/2026-08-06-unified-postgres-design.md`

## Intent

**One remote PostgreSQL database** and **one shared connection pool** for all
durable plugin state: territories, influence, standing, facilities, expenses,
upkeep, reconciliation, and Guilds relational schema.

Success looks like: mandatory Postgres at startup; no JSON/SQLite runtime
fallback; failed writes never advance in-memory authority; schema bootstrap
idempotent on fresh databases.

## Boundaries

### In scope

- `PostgresDatabase` lifecycle (Hikari), `DatabaseSettings` loading.
- Concrete stores: territory, influence, standing, facility, expense, upkeep,
  reconciliation.
- Guilds `DatabaseManager` + migrations on the same database.
- Territory JSONB document codec (`TerritoryJson`) and related JSONB payloads.
- Startup order: connect → schema → load → enable gameplay systems.
- Shutdown: flush where needed → close pool.

### Out of scope / non-goals

- Automatic destructive deletion of legacy local files (operators may keep backups).
- Fully normalized relational rewrite of territory geometry.
- Multi-region active-active replication design (ops concern outside plugin).
- New abstract `Repository` dual-backend seams.

## Invariants

1. **Postgres mandatory** — plugin does not run durable mode without it.
2. **Single pool** shared by territory + guilds.
3. **No dual write** to legacy JSON/SQLite as second truth.
4. **Memory after durable success** for mutations that claim persistence.
5. Schema create/migrate **idempotent** and safe on empty DB.
6. Domain store classes remain free of Bukkit (guilds SQL may live in paper).

## Implementation guidance

| Piece | Location |
|-------|----------|
| Pool / settings | `common/.../persist` |
| Territory/economy/influence/standing stores | `common/.../persist` + domain packages |
| Guilds schema | `paper/.../org.aincraft.guilds.database` |
| Runtime SQL | `common/src/main/resources/dev/mintychochip/{guilds,territory}/sql` via `NamedSql` |
| Plugin wiring | `GuildsTerritoryPlugin` |

- Prefer JSONB documents where already established (territory, influence state)
  rather than drive-by normalization.
- Guilds SQL must stay PostgreSQL-compatible (no SQLite-only upsert dialect).
- Runtime service/store SQL lives as classpath `.sql` files with `:name` placeholders.
  `NamedSql` rewrites them to prepared statements; a name may repeat and is bound once.
  Keep dialect-generated upserts in Java. Leave identifier interpolation (`{{table}}`)
  only for closed-set table/column names.
- Logging: connection failures should fail enable loudly.

### Testing

- Store round-trips (where testcontainers/env available) or codec unit tests.
- Migration idempotency smoke.
- Web mutation failure does not replace registry.

### Do not

- Add `storage.backend` toggles.
- Read legacy `territories.json` / `influence.json` after cutover.
- Open a second Hikari pool for guilds.

## Current

### Capability (shipped)

- [x] Shared `PostgresDatabase` / settings loader
- [x] `PostgresTerritoryStore`
- [x] `PostgresInfluenceStore`
- [x] `PostgresStandingStore`
- [x] `PostgresFacilityStore` / `PostgresExpenseStore` / `PostgresUpkeepStore`
- [x] `PostgresReconciliationStore`
- [x] Guilds on same Postgres with migrations
- [x] Mandatory DB for plugin runtime paths
- [x] Classpath `.sql` + named-parameter execution for runtime service/store queries

### Open on the current surface

- [ ] Operator migration guide from legacy JSON/SQLite backups (external import notes)
- [ ] Backup/restore runbook link from README
- [ ] Connection pool sizing defaults documented

### Current notes

Unified Postgres superseded earlier “optional Postgres repository” designs.
Living specs in other domains must not reintroduce file stores.

## Next

- [ ] Schema inventory doc (table list + owners) generated or hand-maintained
- [ ] Health check that validates pool + critical tables
- [ ] Optional read-only replica config (only if product needs it)

## Future

- [ ] Normalize hot query paths out of JSONB if profiling demands
- [ ] Multi-server write fencing / leadership (if multiple Paper writers appear)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-06 | PostgreSQL only, shared pool | End dual-store drift |
| 2026-08-06 | Keep territory JSONB documents | Compatibility; avoid big-bang rewrite |
| 2026-08-06 | Do not auto-delete legacy files | Operator safety |
| 2026-08-06 | Remove repository dual-backend seams | One code path |
| 2026-08-17 | Classpath `.sql` + `NamedSql` named params | Reviewable SQL without an ORM |

## Open questions

- [ ] Support multiple Paper servers writing the same DB simultaneously?
- [ ] Minimum PostgreSQL version to document?
