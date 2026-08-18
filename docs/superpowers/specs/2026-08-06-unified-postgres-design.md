# Unified PostgreSQL Persistence — Design Spec

**Date:** 2026-08-06
**Status:** Approved for implementation from the requested PostgreSQL-only cutover
**Scope:** `api`, `common`, and `paper` modules

## 1. Problem and decision

The plugin currently has multiple durable stores: the territory registry may use
`territories.json` or PostgreSQL, influence state uses `influence.json`, economy
reconciliation uses `reconciliation.json`, facility and expense stores use JSON,
and the Guilds subsystem uses a separate SQLite `guilds.db`. Territory web code
also routes persistence through `TerritoryRepository` and a supplier, creating a
backend indirection that permits the stores to diverge.

The plugin will use one PostgreSQL database and one shared Hikari data source for
all durable state. PostgreSQL is mandatory at startup; there is no JSON/SQLite
fallback, backend toggle, or alternate durable store. The embedded map UI and
`/api/*` HTTP API remain public application APIs. “Remove indirection APIs” means
removing persistence abstraction seams, not removing the web API or domain
service contracts.

Legacy local files are not read after this cutover and are not deleted by the
plugin. Operators may retain them as backups while importing data externally.
This avoids silent data loss while ensuring they cannot become a second source
of truth.

## 2. Goals and non-goals

### Goals

- Make PostgreSQL the only runtime database for territories, influence,
  reconciliation, facilities, expenses, and Guilds data.
- Share one connection pool and one configured database across all stores.
- Preserve the existing Guilds schema and migrate its SQLite-specific migration
  checks and upsert syntax to PostgreSQL-compatible SQL.
- Remove `TerritoryRepository`, `TerritoryStore`, file-backed store classes,
  backend switches, and web store suppliers.
- Keep the existing `/`, `/api/health`, `/api/meta`, territory, resolve, and
  influence endpoints and their response contracts.
- Keep failed writes transactional and visible; never update in-memory state
  ahead of a failed PostgreSQL commit.
- Make schema creation idempotent and safe for a fresh PostgreSQL database.

### Non-goals

- No new standalone web service.
- No normalized rewrite of territory JSON into many relational tables; the
  existing document format remains JSONB for territory payload compatibility.
- No automatic destructive deletion of operator-owned legacy files.
- No removal of gameplay/service interfaces that are part of the public domain
  API.

## 3. Architecture

```text
GuildsTerritoryPlugin
        |
        v
PostgresDatabase (one Hikari pool, mandatory)
        |
        +--> PostgresTerritoryStore       -> territories (JSONB)
        +--> PostgresInfluenceStore       -> influence_state (JSONB)
        +--> PostgresReconciliationStore  -> reconciliation_entries (JSONB)
        +--> PostgresFacilityStore        -> facilities (JSONB)
        +--> PostgresExpenseStore         -> expenses (JSONB)
        +--> Guilds DatabaseManager       -> existing Guilds relational schema
        +--> Schema bootstrap/migrations -> all tables in the same database

TerritoryApiHandler -> TerritoryRegistry + PostgresTerritoryStore
```

`PostgresDatabase` is a concrete lifecycle owner for the shared data source. It
exposes the connection/data-source operations needed by concrete stores; no
storage interface is introduced. Store classes are concrete PostgreSQL
implementations and are constructed once by the plugin. Schema bootstrap runs
before data loads, and `onDisable` closes the one pool after flushing state.

The web server and API handler receive a concrete `PostgresTerritoryStore`, not
a `Supplier<TerritoryRepository>`. Mutation serialization and stage → save →
replace behavior remain in the handler so a failed database write leaves the
live registry unchanged.

## 4. PostgreSQL schema

Guilds tables remain relational and continue to be managed by the existing
versioned migrations. New shared tables are created idempotently:

```sql
CREATE TABLE IF NOT EXISTS territories (
    id TEXT PRIMARY KEY,
    doc JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS influence_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    doc JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS reconciliation_entries (
    idempotency_key TEXT PRIMARY KEY,
    doc JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS facilities (
    id TEXT PRIMARY KEY,
    doc JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS expenses (
    idempotency_key TEXT PRIMARY KEY,
    doc JSONB NOT NULL
);
```

Store-specific keys are derived from the existing JSON formats. Full-replace
stores use one transaction with delete/upsert semantics. Influence and
reconciliation writes use transactions so a state transition cannot be
partially persisted. JSON codecs remain serialization helpers; they are not
alternate persistence backends.

## 5. Guilds migration portability

`SchemaInitializer` remains the source of truth for Guilds schema versions, but
all migration code must work on PostgreSQL. SQLite-only metadata checks are
replaced with `information_schema.tables`, `information_schema.columns`, and
`pg_indexes` queries. `INSERT OR REPLACE` becomes `INSERT ... ON CONFLICT ...
DO UPDATE`. PostgreSQL-safe `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` and
explicitly idempotent index/table creation are used where applicable.

Migration tests exercise a fresh PostgreSQL schema and a schema with every
migration already applied. The migration path must cover the legacy rename
migrations (`AddGuildRenameMigration`, `AddAllianceRenameMigration`) and every
other migration that currently queries `sqlite_master` or `PRAGMA`.

`sqlite-jdbc` is removed from the Paper module and no `jdbc:sqlite:` URL or
`guilds.db` path remains in production code/configuration.

## 6. Configuration and lifecycle

`database.enabled` is removed. The remaining `database.host`, `port`, `name`,
`user`, `password`, `ssl`, `pool-size`, and optional `jdbc-url` configure the
mandatory shared PostgreSQL pool. Missing or unreachable PostgreSQL is a loud
startup failure for persistence and web startup; the plugin must not construct a
JSON store or continue as if data were durable.

Plugin startup order:

1. Load PostgreSQL settings and construct the shared pool.
2. Validate connectivity and initialize common tables.
3. Initialize Guilds migrations against the same data source.
4. Construct concrete PostgreSQL stores and load registry/influence/
   reconciliation state.
5. Start gameplay and web services.

Shutdown flushes influence and reconciliation state, saves the territory
registry, then closes the shared pool. Legacy files are ignored, not rewritten.

## 7. Indirection removal

Delete or replace:

- `TerritoryRepository` and `TerritoryStore`.
- `PostgresTerritoryRepository` in favor of the concrete shared-database store.
- JSON-backed `InfluenceStore`, `ReconciliationStore`, `FacilityStore`, and
  `ExpenseStore`.
- `DatabaseSettings.enabled` and the territory backend factory/fallback branch.
- `Supplier<TerritoryRepository>` fields and constructor parameters in the web
  server and API handler.
- `TerritoryStore.DEFAULT_FILE_NAME` and all local JSON-path wiring.

Keep:

- `TerritoryJson` and other codecs as pure JSONB/API serialization helpers.
- `TerritoryRegistry`, gameplay services, `InfluenceService`, and Guilds service
  interfaces.
- All existing HTTP endpoint paths and authentication/reverse-proxy behavior.

## 8. Error handling and consistency

- Pool/schema initialization errors fail loudly and prevent web startup.
- Every store converts SQL failures to the existing checked error contract at
  its concrete boundary.
- Territory API mutations stage a registry copy, commit PostgreSQL, then replace
  live state under the existing mutation lock.
- Influence declaration/flip persistence and reconciliation writes are
  transactional; failed writes preserve retryable in-memory state.
- Corrupt JSONB documents fail load rather than silently resetting state.

## 9. Testing and verification

Add or update tests for:

- PostgreSQL settings with no enabled/fallback flag.
- Fresh shared schema creation and idempotent reinitialization.
- Territory, influence, reconciliation, facility, and expense round trips and
  full-replace behavior against `GUILDS_TEST_JDBC_URL`.
- PostgreSQL Guilds migration bootstrap and upgrade checks, including rename
  migrations and all former SQLite metadata/upsert paths.
- Web API mutation failure: HTTP 500 with unchanged in-memory registry.
- No production reference to `jdbc:sqlite:`, `guilds.db`, local store classes,
  or `TerritoryRepository`.

Verification runs the complete Gradle test/build suite. PostgreSQL integration
checks run against a configured disposable PostgreSQL database when
`GUILDS_TEST_JDBC_URL` is available; unit tests remain deterministic when it is
not.
