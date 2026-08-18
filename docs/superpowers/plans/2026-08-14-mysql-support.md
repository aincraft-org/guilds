# MySQL Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add selectable MySQL persistence compatible with PebbleHost-style MySQL 8.x deployments while preserving PostgreSQL and verifying the pushed build through the remote nightly release workflow.

**Architecture:** Add a backend-neutral pooled database abstraction and dialects for PostgreSQL and MySQL. Stores receive dialect-generated schema, JSON, and upsert SQL rather than embedding PostgreSQL syntax; Paper selects the backend from `database.type`.

**Tech Stack:** Java 26, Gradle Kotlin DSL, HikariCP 5.1.0, PostgreSQL JDBC 42.7.13, MySQL Connector/J 9.4.x, JUnit 5, GitHub Actions.

## Global Constraints

- `database.type` values are `postgresql` and `mysql`; default is `postgresql`.
- MySQL targets standard MySQL 8.x-compatible SQL and must avoid PostgreSQL-only casts and `ON CONFLICT`.
- Existing PostgreSQL configuration and tests remain compatible.
- MySQL integration tests skip unless `GUILDS_TEST_MYSQL_JDBC_URL` is set.
- Do not overwrite unrelated pre-existing working-tree changes.
- Commit logical units atomically; push the feature branch, dispatch the existing Nightly Release workflow, and verify its run plus `nightly` release assets.

---

### Task 1: Backend settings and dialect contracts

**Files:**
- Create: `common/src/main/java/com/guilds/territory/persist/DatabaseType.java`
- Create: `common/src/main/java/com/guilds/territory/persist/DatabaseDialect.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/DatabaseSettings.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/DatabaseSettingsLoader.java`
- Test: `common/src/test/java/com/guilds/territory/persist/DatabaseSettingsTest.java`

**Interfaces:**
- `DatabaseType` exposes `POSTGRESQL`, `MYSQL`, and `parse(String)`.
- `DatabaseDialect` exposes `type()`, `driverClassName()`, `acceptsJdbcUrl(String)`, `schemaStatements()`, `jsonValueExpression()`, `documentUpsertSql(String,String)`, and `singletonUpsertSql(String,String)`.
- `DatabaseSettings.type()` returns `DatabaseType`.

- [ ] Write failing tests for PostgreSQL defaults, MySQL parsing, MySQL URL derivation, explicit URL precedence, and invalid type rejection.
- [ ] Run `./gradlew :common:test --tests '*DatabaseSettingsTest'`; expect failure because type support is absent.
- [ ] Implement enum, settings field/constructor compatibility, loader parsing, and URL derivation (`jdbc:mysql://host:port/name?...`).
- [ ] Run focused tests and verify pass.
- [ ] Commit `feat: add selectable mysql database settings`.

### Task 2: Dialect-backed database owner and schema

**Files:**
- Create: `common/src/main/java/com/guilds/territory/persist/DatabaseDialect.java` implementation classes as needed
- Create: `common/src/main/java/com/guilds/territory/persist/Database.java`
- Modify: `common/src/main/java/com/guilds/territory/persist/PostgresDatabase.java`
- Test: `common/src/test/java/com/guilds/territory/persist/DatabaseDialectTest.java`

**Interfaces:**
- `Database` exposes `dataSource()`, `connection()`, `initializeSchema()`, `dialect()`, and `close()`.
- `DatabaseFactory.open(DatabaseSettings)` creates the selected backend.

- [ ] Write failing dialect tests asserting MySQL schema uses `JSON`, MySQL upserts use `ON DUPLICATE KEY UPDATE`, and PostgreSQL SQL remains unchanged.
- [ ] Run focused tests; expect failure.
- [ ] Implement dialects and factory, explicit driver loading, URL validation, Hikari pool setup, connection probe, and schema initialization.
- [ ] Adapt `PostgresDatabase` as a compatibility wrapper or preserve it while introducing shared behavior without breaking current callers.
- [ ] Run focused tests and `./gradlew :common:test`; verify pass.
- [ ] Commit `feat: add mysql database dialect`.

### Task 3: Migrate durable stores to backend-neutral SQL

**Files:**
- Modify all durable stores under `common/src/main/java/com/guilds/territory/` currently importing `PostgresDatabase`.
- Modify: `common/src/main/java/com/guilds/territory/influence/InfluenceEngine.java`, `common/src/main/java/com/guilds/territory/standing/StandingEngine.java`, and web types only if constructor types require migration.
- Modify integration test helpers to accept the shared database type.
- Test: existing persistence tests plus new backend-neutral SQL tests where needed.

**Interfaces:**
- Stores accept `Database` and use `database.dialect()` for JSON parameter expressions and upserts.
- No store may contain `?::jsonb` or `ON CONFLICT` after migration.

- [ ] Add/adjust failing tests for at least territory and invasion round trips through dialect SQL.
- [ ] Run relevant tests; expect SQL/API failures.
- [ ] Replace PostgreSQL-only statements in territory, influence, standing, upkeep, invasion, expense, facility, and reconciliation stores with dialect-generated SQL; preserve ordering, transactions, and error semantics.
- [ ] Run `./gradlew :common:test`; verify PostgreSQL test suite still passes/skips according to environment.
- [ ] Commit `feat: migrate stores to database dialects`.

### Task 4: Wire Paper configuration and package Connector/J

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Modify: `paper/src/main/resources/config.yml`
- Modify: `common/build.gradle.kts`
- Modify: `README.md`
- Test: Paper configuration/wiring tests or focused common tests covering the factory selection.

- [ ] Add failing wiring/config assertions for `database.type=mysql` selecting MySQL and defaulting to PostgreSQL.
- [ ] Run focused test; expect failure.
- [ ] Replace direct `PostgresDatabase` construction with `DatabaseFactory`, update field/store types, and make startup/shutdown logs backend-neutral.
- [ ] Add MySQL Connector/J runtime dependency and preserve PostgreSQL dependency for dual support.
- [ ] Document PebbleHost configuration, port 3306, JDBC URL override, and the fact that public PebbleHost docs do not guarantee a minor version.
- [ ] Run `./gradlew test build`; verify pass and inspect shadow JAR dependencies.
- [ ] Commit `feat: wire mysql backend into paper plugin`.

### Task 5: Real MySQL integration coverage

**Files:**
- Create: `common/src/test/java/com/guilds/territory/MySqlTestDatabase.java`
- Create: `common/src/test/java/com/guilds/territory/persist/MySqlDatabaseIntegrationTest.java`
- Modify: representative store integration tests only if shared helpers require it.

- [ ] Write integration tests for schema initialization and representative territory/invasion save-load round trips using `GUILDS_TEST_MYSQL_JDBC_URL`; skip with an explicit reason when unset.
- [ ] Run with the environment unset; verify clean skips.
- [ ] If a MySQL service is available, run with the URL and verify real round trips.
- [ ] Run full `./gradlew test`.
- [ ] Commit `test: cover mysql persistence integration`.

### Task 6: Push and verify remote release workflow

**Files:**
- Modify workflow only if verification exposes a concrete compatibility problem; otherwise no workflow change.

- [ ] Review status/diff and ensure unrelated pre-existing files are excluded.
- [ ] Create or use an isolated feature branch; push atomic commits to `origin`.
- [ ] Verify GitHub CLI/authentication and repository target `aincraft-org/territories`.
- [ ] Dispatch `.github/workflows/nightly.yml` on the pushed branch or required release ref using repository-supported tooling.
- [ ] Poll/read back the workflow run until completed; require success.
- [ ] Read back the `nightly` release and require plugin and sources JAR assets matching the workflow’s calculated version.
- [ ] Run final local verification if remote failures require a fix, push a new atomic commit, and repeat verification.
