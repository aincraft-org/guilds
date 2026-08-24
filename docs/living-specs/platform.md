# Platform (Build, Quality, CI) — Living Spec

> Status: active  
> Last updated: 2026-08-08  
> Related: `docs/superpowers/specs/2026-08-06-static-analysis-ci-hooks-design.md`

## Intent

Keep the multi-module Java 21 Gradle build **honest and enforceable**: the same
quality gate runs locally, in git hooks, and in CI. Product domains should not
each invent build policy.

Success looks like: `./gradlew check` is the bar; Error Prone, SpotBugs, PMD,
Checkstyle, and tests fail the build; hooks are installable in one script.

## Boundaries

### In scope

- Gradle modules: `guilds-api`, `guilds-common`, `guilds-paper` (+ root aggregation).
- Tooling: Error Prone, SpotBugs, PMD, Checkstyle (pinned versions).
- GitHub Actions workflow(s).
- Repository git hooks via `scripts/install-git-hooks.sh` / `.githooks`.
- Java toolchain 21; Paper/run-paper local server task conventions.
- Release metadata (`version`, `plugin.yml` substitution).

### Out of scope / non-goals

- Product gameplay rules.
- Blanket `ignoreFailures` baselines that hide debt.
- Non-Gradle build systems.

## Invariants

1. Analyzer failures **fail the build**.
2. Config is **centralized** (root + `config/` rule files), not copy-pasted per module.
3. Pre-commit and CI invoke the **same conceptual gate** (`check`).
4. Tool versions are **pinned**.

## Implementation guidance

- Prefer root `build.gradle.kts` + `config/checkstyle|pmd|spotbugs`.
- When adding a module, wire the same plugins; don’t invent a second style.
- Keep `runServer` squaremap pin documented in README when bumping Paper.
- Atomic commits for product work should still pass `./gradlew check` before push.

### Do not

- Disable SpotBugs/PMD globally to land a feature.
- Commit only IDE formatter settings as a substitute for CI rules.

## Current

### Capability (shipped)

- [x] Multi-module Gradle layout (`guilds-api` / `guilds-common` / `guilds-paper`)
- [x] Error Prone, SpotBugs, PMD, Checkstyle integrated
- [x] GitHub Actions CI workflow
- [x] Installable pre-commit hooks running `./gradlew check`
- [x] `runServer` local Paper path with squaremap download
- [x] Release version line (e.g. 1.1.0) wired into plugin metadata

### Open on the current surface

- [ ] Document required JDK and common troubleshooting in README if missing
- [ ] Keep tool pins refreshed deliberately (not floating)

### Current notes

Static analysis was introduced as a first-class design; treat regressions as
release blockers, not nits.

## Next

- [ ] Dependency vulnerability scanning if ops requires it
- [ ] Testcontainers-based Postgres integration job (optional CI profile)
- [ ] Coverage reporting threshold (only if team wants a number)

## Future

- [ ] Reproducible release attestation / signed artifacts
- [ ] Multi-JDK matrix (only if supporting more than 21)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-06 | Same gate local + CI + hooks | No “works on my machine” quality |
| 2026-08-06 | Pin analyzer versions | Reproducible builds |
| 2026-08-06 | Fail build on findings | Debt does not accumulate silently |

## Open questions

- [ ] Enforce coverage floors?
- [ ] Codeberg/Forgejo CI in addition to GitHub Actions?
