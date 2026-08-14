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

- RED: `./gradlew :common:test --tests '*InvasionEngineTest'` — 11 tests completed, 2 failed (new final-removal and devastation signal/state regressions).
- GREEN/final: `./gradlew :common:test --tests '*InvasionEngineTest'` — `BUILD SUCCESSFUL`, 13 tests completed, all passed.
- Added regression coverage proving final removal persistence failure returns only `NO_CHANGE` while ACTIVE record/index remain, and devastation persistence failure returns `NO_CHANGE` with damage/status rolled back.

Persistence failure transitions now derive from committed mutation outcomes: failed final removal and devastation persistence return `NO_CHANGE`, with record/index and damage/status restored. Successful wave progression retains explicit `[WAVE_CLEARED, NEXT_WAVE]`.

## Commit
Fix commit is created after verification in this worktree.

## Concerns
The store boundary remains synchronous; persistence failures are fail-closed and preserve coherent in-memory state. No concerns for Task 1 scope.
 
## API contract follow-up
- RED: `./gradlew :common:test --tests com.azoth.territory.invasion.InvasionEngineTest` — 11 tests completed, 4 failed because callers expected the new ordered list contract while the old scalar/helper API remained.
- GREEN: `./gradlew :common:test --tests com.azoth.territory.invasion.InvasionEngineTest` — `BUILD SUCCESSFUL`; 11 tests completed, all passed.
- `InvasionEngine.mobRemoved(UUID, UUID, long)` is now the sole public removal method and returns the complete ordered transition list, including `WAVE_CLEARED` before `NEXT_WAVE`/`DEFENDED`; rollback and no-op paths return singleton `NO_CHANGE`.
