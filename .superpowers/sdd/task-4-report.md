
## P1 Fix Addendum
- Corrected PDC reads to query the exact plugin namespace/key pair rather than iterating unrelated keys.
- Corrected mob spawn Y to highest floor plus one and retained two-passable-block, loaded-chunk, claim, and bounded-attempt checks.
- Added stateful bossbar lifecycle (`open`, `update`, `reconcile`, `remove`) with one bar per invasion, original spawned-total progress denominator, in-place title/color/progress mutation, viewer show/hide reconciliation, and terminal cleanup.
- Focused verification: `./gradlew :paper:test --tests '*InvasionMobTagsTest' --tests '*InvasionBossBarsTest'` — BUILD SUCCESSFUL.
