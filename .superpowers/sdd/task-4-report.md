
## P1 Fix Addendum
- Corrected PDC reads to query the exact plugin namespace/key pair rather than iterating unrelated keys.
- Corrected mob spawn Y to highest floor plus one and retained two-passable-block, loaded-chunk, claim, and bounded-attempt checks.
- Added stateful bossbar lifecycle (`open`, `update`, `reconcile`, `remove`) with one bar per invasion, original spawned-total progress denominator, in-place title/color/progress mutation, viewer show/hide reconciliation, and terminal cleanup.
- Focused verification: `./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest'` — BUILD SUCCESSFUL.

## P2 Fix Addendum
- Bossbar wave opens now retain the same BossBar instance while resetting the per-wave spawned denominator.
- Canonical UUID parsing accepts only lowercase hexadecimal with hyphens; exact namespace/key reads are covered, including decoy namespace rejection.
- Spawner radius sampling uses overflow-safe side calculations and rejects unsampleable radii before spawning.
- Focused verification: `./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest' --tests '*InvasionConfigLoaderTest'` — BUILD SUCCESSFUL.

## Final Task 4 P2 Addendum
- Bossbar reconnect handling now explicitly shows the current bar to a newly connected Player instance even when its UUID was previously tracked.
- Spawner samples coordinates with long arithmetic, rejects out-of-int-range candidates, checks chunk loading before height lookup, and applies an injectable solid-floor predicate so passable cobweb-like floors are rejected without Paper RegistryAccess-dependent `Material.isSolid`.
- Focused verification: `./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest' --tests '*InvasionMobSpawnerTest'` — BUILD SUCCESSFUL.
