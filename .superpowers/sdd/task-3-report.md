# Task 3 Report

Implemented Task 3 Paper invasion configuration loading and guild target resolution.

## Evidence

- RED: initial focused suite had only the original four tests; the added resolver/config paths were not covered.
- GREEN: `./gradlew :paper:test --rerun-tasks --tests '*InvasionConfigLoaderTest' --tests '*GuildInvasionTargetResolverTest'`
- Result: `BUILD SUCCESSFUL`; focused loader/resolver suites passed after a clean rerun.
- Coverage: case-insensitive ambiguity rejection; no claims; no online resident; unavailable world; valid in-claim configured spawn; out-of-claim spawn home fallback; missing/invalid home rejection; exact guild ownership; safe highest-block Y; bundled list-wave schema; and `enabled:false` retaining defaults.

## Scope

No plugin lifecycle wiring was changed.
