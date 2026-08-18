# MySQL Support Design

## Goal

Add selectable MySQL persistence while preserving PostgreSQL, targeting the MySQL 8.x-compatible SQL subset suitable for PebbleHost-managed databases.

## Compatibility target

PebbleHost public documentation confirms MySQL availability but does not publish a definitive server minor version. The implementation therefore targets standard MySQL 8.x behavior and avoids features newer than the broadly supported MySQL 5.7/8.x JSON and upsert syntax. Runtime startup will validate the selected JDBC URL and report connection errors clearly.

## Configuration

`database.type` selects the backend:

- `postgresql` (default; existing behavior)
- `mysql`

Existing `database.host`, `port`, `name`, `user`, `password`, `ssl`, `pool-size`, and `jdbc-url` keys remain. An explicit JDBC URL wins. MySQL defaults to port 3306 and derives a Connector/J URL with timezone, public-key, and SSL options appropriate for managed hosting. PostgreSQL defaults remain unchanged.

## Architecture

A shared `DatabaseDialect` supplies backend-specific:

- JDBC driver class and URL validation
- schema DDL
- JSON parameter binding expression/type
- singleton/document upsert SQL

`PostgresDatabase` remains as a compatibility-facing implementation for current callers, while a backend-neutral database owner is introduced for common stores. Store APIs and domain behavior remain unchanged. PostgreSQL stores continue to use PostgreSQL SQL; MySQL uses the same table names and logical schema with MySQL-compatible types and `ON DUPLICATE KEY UPDATE`.

The schema is:

- document tables: `territories`, `facilities`, `expenses`, `reconciliation_entries`
- singleton state tables: `influence_state`, `standing_state`, `upkeep_state`, `invasion_state`

MySQL JSON columns use `JSON NOT NULL`; PostgreSQL retains `JSONB NOT NULL`.

## Data flow

1. Paper plugin loads flattened configuration.
2. Loader creates settings including backend type.
3. Database factory creates the selected pooled database and explicitly registers the shaded JDBC driver.
4. Startup opens a connection and initializes the dialect schema before stores load state.
5. Existing stores use the shared database abstraction for connections and dialect SQL.
6. On shutdown, stores flush through the same abstraction and the pool closes.

## Error handling

- Unknown backend type fails configuration loading with a clear message.
- A backend/URL mismatch fails before service wiring.
- Driver absence fails with a backend-specific message.
- Connection and schema failures disable plugin startup exactly as PostgreSQL currently does.
- Store errors retain `IOException` behavior and identify the selected backend.

## Testing

- Unit tests cover backend defaults, MySQL URL derivation, explicit URL precedence, invalid backend rejection, and dialect SQL/schema invariants.
- Existing PostgreSQL tests remain unchanged and continue to run when `GUILDS_TEST_JDBC_URL` is set.
- New MySQL integration tests use `GUILDS_TEST_MYSQL_JDBC_URL` and skip when unset; they initialize the real schema and round-trip representative territory/invasion state through the MySQL path.
- Full Gradle tests and build verify packaging includes Connector/J and PostgreSQL drivers.

## Delivery verification

Changes are committed atomically and pushed to a feature branch. The existing GitHub Actions Nightly Release workflow is dispatched after push. Completion requires read-back evidence of a successful remote workflow run and the rolling `nightly` release containing both plugin and sources JAR assets. If remote authentication or Actions permissions prevent dispatch, the local push and exact blocker are reported without fabricating release evidence.
