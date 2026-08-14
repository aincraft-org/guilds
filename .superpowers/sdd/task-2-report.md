# Task 2 Report: PostgreSQL Invasion Persistence

## RED

Command:

```bash
./gradlew :common:test --tests '*PostgresInvasionStoreTest'
```

Result: FAILED during `:common:compileTestJava` because `PostgresInvasionStore` was absent. The compiler reported four `cannot find symbol` errors for the store type/constructor.

## GREEN

Command:

```bash
./gradlew :common:test --tests '*PostgresInvasionStoreTest' --tests '*PostgresDatabase*'
```

Result: BUILD SUCCESSFUL. Six actionable tasks were evaluated; selected PostgreSQL invasion and database tests passed (integration tests are environment-assumption based when `AZOTH_TEST_JDBC_URL` is unavailable).

## Changed files

- `common/src/main/java/com/azoth/territory/invasion/PostgresInvasionStore.java`
  - Added singleton-row JSONB persistence at `invasion_state.id = 1`.
  - Added versioned root document containing `version`, `guildDamage`, and `invasions`.
  - Preserved invasion identifiers, guild/world fields, coordinates, status, wave, entities, damage, and timestamps.
  - Added malformed root/version/status/UUID/counter/coordinate/shape validation and PostgreSQL/parser error wrapping.
- `common/src/main/java/com/azoth/territory/persist/PostgresDatabase.java`
  - Added the required `invasion_state` schema statement.
- `common/src/test/java/com/azoth/territory/invasion/PostgresInvasionStoreTest.java`
  - Added complete active/terminal round-trip coverage and unsupported-version coverage.

## Self-review

- `git diff --check` passed.
- Scope is limited to the three Task 2 source/test files plus this report.
- Focused persistence/database command passed after implementation.
- The existing `InvasionStore` interface does not declare checked `IOException`; the implementation therefore preserves the interface contract and rethrows invasion-specific failures as `IllegalStateException` wrapping the detailed `IOException` cause.

## Commit

Pending prescribed atomic commit:

```text
feat: persist guild invasion state
```

## Concerns

The Task 1 `InvasionStore` API exposes unchecked `load`/`save` methods, so checked `IOException` cannot be declared directly without changing the approved interface. SQL/parser failures retain the required invasion-specific `IOException` as the cause of the interface-compatible runtime exception.
