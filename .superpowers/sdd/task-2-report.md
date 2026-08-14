# Task 2 Report: PostgreSQL Invasion Persistence

## RED

Command:

```bash
./gradlew :common:test --tests '*PostgresInvasionStoreTest' --tests '*PostgresDatabase*'
```

Result: Initial regression tests compiled and passed against the existing implementation except for the newly asserted unsupported-version cause/message and malformed guildDamage cases, which drove the implementation changes below.

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

Created atomic commit:

```text
fb50aac fix: validate invasion persistence state
```

## Concerns

The Task 1 `InvasionStore` API exposes unchecked `load`/`save` methods, so checked `IOException` cannot be declared directly without changing the approved interface. SQL/parser failures retain the required invasion-specific `IOException` as the cause of the interface-compatible runtime exception.

## Strict integral JSON parsing follow-up

- Added malformed JSON coverage for version `1.5`, fractional counters/waves/timestamps, non-integral exponents, and integer/long overflow.
- Replaced Gson lossy `getAsInt()`/`getAsLong()` conversion with exact `BigDecimal.toBigIntegerExact()` parsing plus field-specific range checks.
- Focused command: `./gradlew :common:test --tests '*PostgresInvasionStoreTest'` — BUILD SUCCESSFUL (PostgreSQL integration tests are environment-assumption based when `AZOTH_TEST_JDBC_URL` is unavailable).
- Enforced the stricter integral JSON number grammar (`-?[0-9]+`) before exact `BigInteger` conversion, rejecting exponent and decimal lexemes even when mathematically integral.
