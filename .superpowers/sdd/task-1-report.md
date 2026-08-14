# Task 1 Report

## Changed files
- `common/src/main/java/com/azoth/territory/invasion/InvasionStatus.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionConfig.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionRecord.java`
- `common/src/main/java/com/azoth/territory/invasion/GuildDamage.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionState.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionStore.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionStartStatus.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionStartResult.java`
- `common/src/main/java/com/azoth/territory/invasion/Wave.java`
- `common/src/main/java/com/azoth/territory/invasion/MobEntry.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionTransition.java`
- `common/src/main/java/com/azoth/territory/invasion/InvasionEngine.java`
- `common/src/test/java/com/azoth/territory/invasion/InvasionEngineTest.java`

## Tests and commands
- RED: `./gradlew :common:test --tests '*InvasionEngineTest'` — expected compilation failure because invasion types were absent.
- GREEN/final: `./gradlew :common:test --tests '*InvasionEngineTest'` — `BUILD SUCCESSFUL`, 5 tests completed.

## Self-review
The implementation is Paper-independent and uses immutable records/list snapshots. Lifecycle tests cover guild start idempotency, entity tracking, wave progression, defense, damage saturation and accumulation, cancellation, recovery, and failed persistence rollback. Unknown and duplicate removals are no-ops. Changed scope is limited to the invasion domain package and its focused test.

## Commit
`5a3b69d` — `feat: add guild invasion lifecycle engine`

## Concerns
The store boundary is synchronous and runtime persistence failures are handled fail-closed by restoring in-memory mutations. No concerns identified for the requested scope.
