# Unified PostgreSQL Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every runtime JSON/SQLite persistence path with one shared PostgreSQL database while keeping the embedded web REST API.

**Architecture:** `PostgresDatabase` owns one Hikari pool and initializes common JSONB tables. Concrete PostgreSQL stores use that shared data source for territories, influence, reconciliation, facilities, and expenses; Guilds uses the same data source for its relational schema. The web API receives a concrete PostgreSQL territory store, not a repository interface or supplier.

**Tech Stack:** Java 21, Gradle multi-module build, HikariCP 5.1, PostgreSQL JDBC 42.7, Gson JSONB codecs, JUnit 5, JDK `HttpServer`.

## Global Constraints

- PostgreSQL is mandatory at startup; there is no JSON/SQLite fallback or `database.enabled` switch.
- One shared Hikari data source serves every durable store and the Guilds schema.
- Keep `/`, `/api/health`, `/api/meta`, `/api/territories*`, `/api/resolve`, and `/api/influence` contracts.
- Remove `TerritoryRepository`, `TerritoryStore`, file-backed influence/reconciliation/facility/expense stores, `jdbc:sqlite:`, `guilds.db`, and web store suppliers.
- Keep JSON codecs as serialization helpers only; JSONB documents remain the compatibility format.
- PostgreSQL writes are transactional; staged territory mutations never update live memory before a successful commit.
- Legacy local files are ignored after cutover and are not deleted automatically.
- Do not add a new storage interface or silently fall back when PostgreSQL is unavailable.

---

### Task 1: Create shared PostgreSQL database owner

**Files:**
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/DatabaseSettings.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/DatabaseSettingsLoader.java`
- Create: `common/src/test/java/com/guilds/territory/persist/PostgresDatabaseTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/DatabaseSettingsLoaderTest.java`

**Interfaces:**
- Produces concrete `PostgresDatabase(DatabaseSettings)`, `DataSource dataSource()`, `Connection connection()`, `initializeSchema()`, and `close()` for all later stores.
- `DatabaseSettings` no longer has `enabled`; `jdbcUrl()` always resolves to PostgreSQL.

- [ ] **Step 1: Write failing settings/schema tests**

Add assertions that settings load without `database.enabled`, reject a blank/unsupported JDBC URL, and that a test PostgreSQL URL can initialize the shared tables when `GUILDS_TEST_JDBC_URL` is set. Gate integration tests with JUnit assumptions so the normal unit suite remains deterministic without PostgreSQL.

- [ ] **Step 2: Run targeted tests and confirm failure**

Run: `./gradlew :common:test --tests '*DatabaseSettingsLoaderTest' --tests '*PostgresDatabaseTest'`
Expected: compilation/test failure because the concrete database owner and new settings contract do not exist.

- [ ] **Step 3: Implement the pool and common schema**

Construct Hikari once from `DatabaseSettings`, validate it with `SELECT 1`, create these idempotent tables using one connection, and expose the same `DataSource` to consumers:

```sql
CREATE TABLE IF NOT EXISTS territories (id TEXT PRIMARY KEY, doc JSONB NOT NULL);
CREATE TABLE IF NOT EXISTS influence_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL);
CREATE TABLE IF NOT EXISTS reconciliation_entries (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL);
CREATE TABLE IF NOT EXISTS facilities (id TEXT PRIMARY KEY, doc JSONB NOT NULL);
CREATE TABLE IF NOT EXISTS expenses (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL);
```

Throw `IOException` from construction/schema initialization failures, and make `close()` idempotent.

- [ ] **Step 4: Run targeted tests and confirm pass**

Run: `./gradlew :common:test --tests '*DatabaseSettingsLoaderTest' --tests '*PostgresDatabaseTest'`
Expected: PASS; without `GUILDS_TEST_JDBC_URL`, only non-integration settings tests execute.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/guilds/territory/persist/DatabaseSettings.java common/src/main/java/com/guilds/territory/persist/DatabaseSettingsLoader.java common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java common/src/test/java/com/guilds/territory/persist/DatabaseSettingsLoaderTest.java common/src/test/java/com/guilds/territory/persist/PostgresDatabaseTest.java
git commit -m "feat: add shared PostgreSQL database owner"
```

### Task 2: Replace territory repository seam with concrete PostgreSQL store

**Files:**
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresTerritoryStore.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/TerritoryRepository.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/TerritoryStore.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/PostgresTerritoryRepository.java`
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryApiHandler.java`
- Modify: `common/src/main/java/com/guilds/territory/web/TerritoryWebServer.java`
- Modify: `common/src/test/java/com/guilds/territory/web/TerritoryApiPersistenceTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/PostgresTerritoryRepositoryTest.java`

**Interfaces:**
- Produces concrete `PostgresTerritoryStore(PostgresDatabase)`, `loadInto(TerritoryRegistry)`, `save(TerritoryRegistry)`, and `close()`.
- `TerritoryApiHandler` and `TerritoryWebServer` constructors consume `PostgresTerritoryStore` directly; no `Supplier` and no repository interface.

- [ ] **Step 1: Update tests to concrete store and direct constructor**

Change integration tests to construct `PostgresTerritoryStore` from the shared database, and update web tests to use a concrete test database/store or a concrete failing PostgreSQL store fixture. Preserve the test asserting a failed PUT/DELETE returns 500 and leaves the registry unchanged.

- [ ] **Step 2: Run targeted tests and confirm failure**

Run: `./gradlew :common:test --tests '*TerritoryApiPersistenceTest' --tests '*PostgresTerritoryRepositoryTest'`
Expected: compilation failure from removed repository symbols and constructor type mismatches.

- [ ] **Step 3: Implement direct PostgreSQL territory persistence**

Move the existing JSONB load/full-replace transaction into `PostgresTerritoryStore`, using the shared data source and `TerritoryJson`. Load rows ordered by id, parse documents, and replace the registry only after all rows parse. Save with `DELETE FROM territories` and batched `INSERT ... VALUES (?, ?::jsonb)` inside one transaction. Surface SQL/JSON failures as `IOException`.

- [ ] **Step 4: Remove web persistence indirection**

Replace handler fields and constructor parameters with `PostgresTerritoryStore`. Keep the mutation lock, staged registry, save-before-replace sequence, and existing endpoint behavior. Remove null-store fallback messages because the plugin cannot start the web server without PostgreSQL.

- [ ] **Step 5: Run targeted tests and confirm pass**

Run: `./gradlew :common:test --tests '*TerritoryApiPersistenceTest' --tests '*PostgresTerritoryRepositoryTest'`
Expected: PASS when integration URL is configured; deterministic web tests pass with their configured concrete store fixture otherwise.

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/guilds/territory/persist common/src/main/java/com/guilds/territory/web common/src/test/java/com/guilds/territory/web/TerritoryApiPersistenceTest.java common/src/test/java/com/guilds/territory/persist/PostgresTerritoryRepositoryTest.java
git commit -m "feat: make territory persistence PostgreSQL-only"
```

### Task 3: Move influence and economy auxiliary stores to PostgreSQL

**Files:**
- Create: `common/src/main/java/com/guilds/territory/influence/PostgresInfluenceStore.java`
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresReconciliationStore.java`
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresFacilityStore.java`
- Create: `common/src/main/java/com/guilds/territory/persist/PostgresExpenseStore.java`
- Delete: `common/src/main/java/com/guilds/territory/influence/InfluenceStore.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/ReconciliationStore.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/FacilityStore.java`
- Delete: `common/src/main/java/com/guilds/territory/persist/ExpenseStore.java`
- Modify: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java`
- Modify: `common/src/test/java/com/guilds/territory/influence/InfluenceStoreTest.java`
- Modify: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineLifecycleTest.java`
- Modify: `common/src/test/java/com/guilds/territory/influence/InfluenceEngineAccrualTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/ReconciliationStoreTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/FacilityStoreTest.java`
- Modify: `common/src/test/java/com/guilds/territory/persist/ExpenseStoreTest.java`

**Interfaces:**
- Concrete stores accept `PostgresDatabase`; public behavior methods retain the current checked exceptions and model payloads.
- `InfluenceEngine` consumes `PostgresInfluenceStore` directly; no file path or backup-corrupt flow remains.

- [ ] **Step 1: Add PostgreSQL round-trip tests**

Port each JSON-file test to PostgreSQL integration tests. Assert missing rows load as empty, round trips preserve all fields, full replacement removes stale rows, and malformed JSONB fails loudly. Keep influence lifecycle/accrual tests deterministic by using the concrete store against a unique test database/schema or a transaction-isolated fixture.

- [ ] **Step 2: Run targeted tests and confirm failure**

Run: `./gradlew :common:test --tests '*InfluenceStoreTest' --tests '*ReconciliationStoreTest' --tests '*FacilityStoreTest' --tests '*ExpenseStoreTest'`
Expected: compilation failure until the concrete stores and test constructors are added.

- [ ] **Step 3: Implement influence PostgreSQL persistence**

Store the complete influence root document as row id `1` in `influence_state`. `load()` returns an empty state when no row exists. `save()` uses one transaction with `INSERT ... ON CONFLICT (id) DO UPDATE`. Remove file backup/atomic-move methods and preserve version validation in the codec logic.

- [ ] **Step 4: Implement reconciliation, facility, and expense PostgreSQL persistence**

Use the existing JSON conversion routines as JSONB documents. Reconciliation and expenses use their stable idempotency keys; facilities use facility ids. Save operations replace/upsert rows transactionally, load operations return empty collections when no rows exist, and malformed documents throw `IOException`.

- [ ] **Step 5: Run targeted tests and confirm pass**

Run the same targeted Gradle command with `GUILDS_TEST_JDBC_URL` configured for integration coverage. Expected: PASS with no local JSON files created.

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/guilds/territory/influence common/src/main/java/com/guilds/territory/persist common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java common/src/test/java/com/guilds/territory/influence common/src/test/java/com/guilds/territory/persist
git commit -m "feat: persist auxiliary state in PostgreSQL"
```

### Task 4: Port Guilds database configuration and migrations

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/config/DatabaseConfig.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/DatabaseManager.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/SchemaInitializer.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/AddGuildRenameMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/AddAllianceRenameMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/migration/AddPlotTypeSystemMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/PermissionMigration.java`
- Modify: all migration files containing `PRAGMA`, `sqlite_master`, or `INSERT OR REPLACE`
- Modify: `paper/src/test/java/org/aincraft/guilds/database/migration/GuildRenameMigrationTest.java`
- Create: `paper/src/test/java/org/aincraft/guilds/database/migration/PostgresSchemaInitializerTest.java`
- Modify: `paper/build.gradle.kts`

**Interfaces:**
- `DatabaseConfig` consumes the shared `PostgresDatabase`/`DataSource`; it no longer accepts a file or SQLite URL.
- `GuildsServices` later passes the shared data source into `DatabaseManager`; all service constructors remain unchanged because they already consume `DatabaseManager`.

- [ ] **Step 1: Add fresh/upgrade migration integration tests**

Use `GUILDS_TEST_JDBC_URL` and isolated table names/schema cleanup to assert a fresh `SchemaInitializer` creates every registered table and that a second run is idempotent. Seed legacy guild/town/nation objects and verify both rename migrations complete on PostgreSQL.

- [ ] **Step 2: Run migration tests and confirm failure**

Run: `./gradlew :paper:test --tests '*PostgresSchemaInitializerTest' --tests '*GuildRenameMigrationTest'`
Expected: failure against PostgreSQL because SQLite metadata queries and upserts are not portable.

- [ ] **Step 3: Replace SQLite metadata probes**

Implement metadata helpers using `information_schema.tables`, `information_schema.columns`, and `pg_indexes` with schema-qualified safe checks. Replace all `PRAGMA table_info`, `sqlite_master`, and SQLite-only index checks.

- [ ] **Step 4: Replace SQLite write syntax and harden DDL**

Convert `INSERT OR REPLACE` to `INSERT ... ON CONFLICT (...) DO UPDATE`, use PostgreSQL-compatible booleans/defaults, and make column/index creation idempotent. Preserve migration version numbers and descriptions so existing PostgreSQL installs upgrade in order.

- [ ] **Step 5: Remove SQLite dependency and file configuration**

Make `DatabaseConfig` use the shared data source, remove file existence/creation methods, and delete `org.xerial:sqlite-jdbc` from `paper/build.gradle.kts`.

- [ ] **Step 6: Run migration tests and confirm pass**

Run: `GUILDS_TEST_JDBC_URL=... ./gradlew :paper:test --tests '*PostgresSchemaInitializerTest' --tests '*GuildRenameMigrationTest'`. Expected: PASS and no `jdbc:sqlite:` usage.

- [ ] **Step 7: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/config/DatabaseConfig.java paper/src/main/java/org/aincraft/guilds/database paper/src/test/java/org/aincraft/guilds/database paper/build.gradle.kts
git commit -m "feat: port Guilds schema migrations to PostgreSQL"
```

### Task 5: Wire one database through the Paper plugin

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/resources/config.yml`
- Modify: `common/build.gradle.kts`
- Modify: `README.md`
- Modify: Paper wiring tests covering plugin services

**Interfaces:**
- Plugin owns one `PostgresDatabase` field and passes it to every concrete store and `GuildsServices`.
- `getStore()` returns `PostgresTerritoryStore`; `reloadTerritories()` and `saveTerritories()` call the concrete store directly.

- [ ] **Step 1: Update wiring tests for mandatory PostgreSQL**

Assert that plugin wiring has no `database.enabled` branch, no local path store, and that web construction receives a concrete PostgreSQL store. Keep service/listener wiring tests otherwise unchanged.

- [ ] **Step 2: Run wiring tests and confirm failure**

Run: `./gradlew :paper:test --tests '*GuildsServicesWiringTest' --tests '*PluginMetadataTest'`
Expected: compilation/test failure from old constructors, imports, and config fields.

- [ ] **Step 3: Construct shared database before Guilds**

In `onEnable`, load mandatory settings, construct and initialize `PostgresDatabase`, construct all concrete stores from it, then invoke `new GuildsServices(this, database)`. Remove `dataFile`, `createStore`, `describeStore`, `TerritoryStore`, and null-store fallback logic. On any database initialization error, log SEVERE and disable the plugin cleanly rather than starting gameplay with non-durable state.

- [ ] **Step 4: Wire shutdown and reload**

Flush PostgreSQL influence/reconciliation state, save territories, stop web/Guilds services, and close the shared database once. Ensure reload only reloads from PostgreSQL and never touches a legacy file.

- [ ] **Step 5: Update config/docs/dependencies**

Remove `database.enabled` and JSON fallback comments from `config.yml`, document mandatory PostgreSQL and the JSONB tables in README, and ensure the PostgreSQL driver is shaded exactly once through the common dependency.

- [ ] **Step 6: Run wiring tests and confirm pass**

Run: `./gradlew :paper:test --tests '*GuildsServicesWiringTest' --tests '*PluginMetadataTest'`. Expected: PASS with no SQLite/file-store references.

- [ ] **Step 7: Commit**

```bash
git add paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java paper/src/main/java/org/aincraft/guilds/GuildsServices.java paper/src/main/resources/config.yml common/build.gradle.kts README.md paper/src/test/java
git commit -m "feat: wire the plugin to one PostgreSQL database"
```

### Task 6: Remove obsolete APIs and verify the complete cutover

**Files:**
- Modify: all remaining source/tests/docs references found by the search commands below
- Delete: any remaining JSON/SQLite store classes and their file-only tests

- [ ] **Step 1: Search for forbidden fallback and indirection paths**

Run:

```bash
builtin grep -RInE 'jdbc:sqlite:|sqlite-jdbc|guilds\.db|TerritoryRepository|TerritoryStore|database\.enabled|influence\.json|reconciliation\.json|facilities\.json|expenses\.json' api common paper README.md
```

Expected: zero production references; historical archived docs may be updated or explicitly excluded only if they are not shipped or compiled.

- [ ] **Step 2: Remove stale imports/API call sites**

Delete stale classes, update tests and JavaDocs, and ensure `TerritoryApiHandler`/`TerritoryWebServer` expose only the concrete PostgreSQL store dependency. Do not remove public HTTP or domain service APIs.

- [ ] **Step 3: Run all tests and build**

Run: `./gradlew test build`. Expected: all unit tests pass; PostgreSQL integration tests are skipped only when `GUILDS_TEST_JDBC_URL` is absent, and all modules compile without SQLite.

- [ ] **Step 4: Run PostgreSQL integration verification**

With a disposable PostgreSQL database configured, run the common and Paper integration tests, then inspect the database tables for `territories`, `influence_state`, `reconciliation_entries`, `facilities`, `expenses`, and all Guilds tables. Expected: one PostgreSQL database contains every durable store and no local persistence files are created.

- [ ] **Step 5: Commit final cleanup**

```bash
git add api common paper README.md
git commit -m "refactor: remove alternate persistence APIs"
```
