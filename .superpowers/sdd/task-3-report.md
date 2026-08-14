# Task 3 Report

Implemented Task 3 Paper invasion configuration loading and guild target resolution.

## Evidence

- RED: added `malformedWaveCountsAreRejectedBeforeConversion` with valid three-wave structures containing fractional, string, NaN, infinite, and overflowing counts; the focused loader test failed before validation.
- GREEN: `./gradlew :paper:test --rerun-tasks --tests '*InvasionConfigLoaderTest'`
- Result: `BUILD SUCCESSFUL`; all five loader tests passed.
- Validation now requires a `Number` with finite, integral, in-range numeric value before conversion; non-positive values retain the existing positive-count rejection.
- Scope: `InvasionConfigLoader`, `InvasionConfigLoaderTest`, and this report only.

## Concerns

- YAML parser support for special floating-point tokens is covered by the `.nan` and `.inf` fixtures; both are rejected through the same controlled `IllegalArgumentException` path.
No plugin lifecycle wiring was changed.
