# Persistence — Living Spec

> Status: active  
> Last updated: 2026-08-19  
> Related: `docs/superpowers/specs/2026-08-06-unified-postgres-design.md`,
> `docs/superpowers/specs/2026-08-14-mysql-support-design.md`

## Intent

**One remote SQL database** and **one shared connection pool** for all durable
plugin state: territories, influence, standing, facilities, expenses, upkeep,
reconciliation, and Guilds relational schema. PostgreSQL is the default;
MySQL 8.x is a selectable backend (`database.type: mysql`) for managed hosts
such as PebbleHost.

Success looks like: mandatory SQL at startup; no JSON/SQLite runtime
fallback; failed writes never advance in-memory authority; schema bootstrap
idempotent on fresh databases on both backends.

## Boundaries

### In scope

- `Database` / `DatabaseFactory` lifecycle (Hikari), `DatabaseSettings` loading.
- Concrete stores: territory, influence, standing, facility, expense, upkeep,
  reconciliation.
- Guilds `DatabaseManager` + migrations on the same database.
- Territory document codec (`TerritoryJson`) and related JSON payloads
  (PostgreSQL JSONB, MySQL JSON).
- Startup order: connect → schema → load → enable gameplay systems.
- Shutdown: flush where needed → close pool.

### Out of scope / non-goals

- Automatic destructive deletion of legacy local files (operators may keep backups).
- Fully normalized relational rewrite of territory geometry.
- Multi-region active-active replication design (ops concern outside plugin).
- New abstract `Repository` dual-backend seams.
- Rewriting historical Postgres-only design docs under `docs/superpowers/`.

## Invariants

1. **SQL mandatory** — plugin does not run durable mode without a reachable
   PostgreSQL or MySQL database.
2. **Single pool** shared by territory + guilds.
3. **No dual write** to legacy JSON/SQLite as second truth.
4. **Memory after durable success** for mutations that claim persistence.
5. Schema create/migrate **idempotent** and safe on empty DB on both backends.
6. Domain store classes remain free of Bukkit (guilds SQL may live in paper).

## Implementation guidance

| Piece | Location |
|-------|----------|
| Pool / settings / dialect | `HikariDatabase` via `DatabaseFactory` |
| Shared SQL helpers | `SqlSupport` (upsert, TEXT→VARCHAR, catalog, indexes) |
| Versioned SQL | `common/src/main/resources/sql/migrations/{track}` |
| Territory/economy/influence/standing stores | `common/.../persist` + domain packages |
| Guilds schema | `paper/.../org.aincraft.guilds.database` |
| Plugin wiring | `GuildsPlugin` |

- Territory document stores use `DatabaseDialect` (`JSONB`/`ON CONFLICT` vs
  `JSON`/`ON DUPLICATE KEY UPDATE`).
- Schema changes live in versioned SQL files
  (`V{n}__{slug}.sql` + `manifest`). Java only runs guarded hooks (renames,
  seeds). `SqlMigrationRunner` records checksums in `sql_schema_migrations`.
- Guilds relational SQL goes through `SqlSupport` so migrations stay portable.
  Do not add PostgreSQL-only `ON CONFLICT`, `RETURNING`, `BYTEA`, or
  `CREATE INDEX IF NOT EXISTS` in new Guilds SQL.
- Prefer JSON documents where already established (territory, influence state)
  rather than drive-by normalization.
- Logging: connection failures should fail enable loudly and name the selected
  backend.

### Testing

- Store round-trips (where testcontainers/env available) or codec unit tests.
- `GUILDS_TEST_MYSQL_JDBC_URL` gates MySQL integration tests; they skip when unset.
- Migration idempotency smoke on both backends when env is present.
- Web mutation failure does not replace registry.

### Do not

- Add `storage.backend` file/SQLite toggles.
- Read legacy `territories.json` / `influence.json` after cutover.
- Open a second Hikari pool for guilds.

## Current

### Capability (shipped)

- [x] Shared `Database` / settings loader (`postgresql` default, `mysql` selectable)
- [x] `PostgresTerritoryStore` (dialect-backed; works on MySQL)
- [x] `PostgresInfluenceStore`
- [x] `PostgresStandingStore`
- [x] `PostgresFacilityStore` / `PostgresExpenseStore` / `PostgresUpkeepStore`
- [x] `PostgresReconciliationStore`
- [x] Guilds on the same SQL database with portable migrations
- [x] Mandatory DB for plugin runtime paths
- [x] `SqlSupport` helpers for upsert, identifier types, and catalog checks
- [x] Single `HikariDatabase` pool owner
- [x] Versioned SQL resources for persist + Guilds schema (`sql/migrations/`)
- [x] Guilds service DML loaded from `paper/src/main/resources/sql/` via `SqlStatements`

### Open on the current surface

- [ ] Operator migration guide from legacy JSON/SQLite backups (external import notes)
- [ ] Backup/restore runbook link from README
- [ ] Connection pool sizing defaults documented

### Current notes

Unified SQL superseded earlier “optional Postgres repository” designs. MySQL
is a second SQL dialect, not a second source of truth. Living specs in other
domains must not reintroduce file stores.

## Next

- [ ] Schema inventory doc (table list + owners) generated or hand-maintained
- [ ] Health check that validates pool + critical tables
- [ ] Optional read-only replica config (only if product needs it)

## Future

- [ ] Normalize hot query paths out of JSON documents if profiling demands
- [ ] Multi-server write fencing / leadership (if multiple Paper writers appear)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-06 | PostgreSQL only, shared pool | End dual-store drift |
| 2026-08-06 | Keep territory JSONB documents | Compatibility; avoid big-bang rewrite |
| 2026-08-06 | Do not auto-delete legacy files | Operator safety |
| 2026-08-06 | Remove repository dual-backend seams | One code path |
| 2026-08-19 | MySQL 8.x selectable; PostgreSQL remains default | Managed hosts (PebbleHost) expose MySQL, not Postgres |
| 2026-08-19 | Versioned SQL resources + one Hikari pool | Keep SQL out of Java; schema history is files + checksums |
| 2026-08-19 | Guilds service queries as classpath SQL | Match extras/mint: one `?` statement per file under `/sql` |

## Open questions

- [ ] Support multiple Paper servers writing the same DB simultaneously?
- [x] Minimum PostgreSQL version to document? 16+ in practice for local docker; MySQL 8.0 for the selectable path.
