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
- RED: `./gradlew :common:test --tests '*InvasionEngineTest'` — initial regression compile failed because `mobRemovedSequence` was not yet implemented.
- GREEN/final: `./gradlew :common:test --tests '*InvasionEngineTest'` — `BUILD SUCCESSFUL`, 9 tests completed, all passed.
- Added regression coverage for cancel rollback/index restoration, final mob removal rollback/index restoration, recover rollback/index restoration, and explicit `[WAVE_CLEARED, NEXT_WAVE]` sequence.

## Self-review
Persistence failures now restore the record and active guild index for cancel and final removal; recover snapshots and restores all records/indexes on save failure. The transition API keeps legacy `mobRemoved` while adding `mobRemovedSequence`, explicitly exposing wave-cleared followed by next-wave/defended. Changes are limited to the Task 1 invasion engine and focused test. Existing lifecycle tests pass.

## Commit
Fix commit is created after verification in this worktree.

## Concerns
The store boundary remains synchronous; persistence failures are fail-closed and preserve coherent in-memory state. No concerns for Task 1 scope.
