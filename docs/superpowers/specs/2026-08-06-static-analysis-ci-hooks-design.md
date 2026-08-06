# Static Analysis, CI, and Git Hooks Design

## Goal

Enforce Error Prone, SpotBugs, PMD, and Checkstyle across the `api`, `common`, and `paper` Gradle modules. The same build gate must run in local development, GitHub Actions, and the repository's pre-commit hook.

## Scope and constraints

- The repository is a three-module Gradle Kotlin DSL build targeting Java 21.
- There is no existing CI workflow, analyzer configuration, or hook framework.
- Existing persistence edits in the worktree are unrelated and must remain untouched.
- Analyzer failures are build failures; no blanket `ignoreFailures` or baseline that hides existing defects.
- Configuration is centralized in the root build script and checked-in rule files rather than duplicated per module.
- The implementation adds GitHub Actions because the configured `origin` remote is GitHub.

## Versioned tooling

Pin the following versions so builds do not change behavior because a repository publishes a new release:

| Tool | Integration | Version |
| --- | --- | --- |
| Error Prone | `net.ltgt.errorprone` Gradle plugin | 5.1.0 |
| Error Prone | `com.google.errorprone:error_prone_core` | 2.50.0 |
| SpotBugs | `com.github.spotbugs` Gradle plugin | 6.5.10 |
| SpotBugs | analysis engine | 4.10.3 |
| PMD | Gradle built-in plugin tool version | 7.26.0 |
| Checkstyle | Gradle built-in plugin tool version | 13.9.0 |

These versions were checked against the Gradle Plugin Portal and Maven Central during design.

## Architecture

The root `build.gradle.kts` will:

1. Declare the external plugin versions with `apply false`.
2. Apply Error Prone, SpotBugs, PMD, and Checkstyle inside the existing `subprojects` block so every Java module receives the same policy.
3. Add the Error Prone core dependency to each module's `errorprone` configuration.
4. Configure Checkstyle and PMD to use explicit files under `config/`, emit XML and HTML reports, and fail on violations.
5. Configure SpotBugs with the pinned engine, maximum effort, XML and HTML reports, and failure on violations.
6. Keep Error Prone enabled for Java source-set compilation and suppress only generated-code warnings through its supported option.

Checked-in rule files:

- `config/checkstyle/checkstyle.xml`: a small, explicit baseline of source-format and import rules suitable for the existing codebase.
- `config/pmd/pmd.xml`: a small, explicit set of correctness and maintainability rules.
- SpotBugs uses its standard detectors; an exclusion file is added only if a concrete, reviewed false positive is found during verification.

The `check` lifecycle remains the contract. Analyzer tasks are configured by their plugins and are dependencies of `check`, while normal tests continue to run unchanged.

## CI

Add `.github/workflows/ci.yml` triggered on pushes and pull requests. The workflow will:

- check out the repository;
- install Temurin Java 21;
- use the checked-in Gradle wrapper and `gradle/actions/setup-gradle` caching;
- run `./gradlew --no-daemon build`, which includes tests, `check`, and the Paper shadow artifact;
- upload `**/build/reports/**` on failure to make analyzer diagnostics available in the job summary.

No analyzer-specific duplicate command is needed in CI: the workflow exercises the same `check` graph developers run locally.

## Local pre-commit hook

Add an executable `.githooks/pre-commit` that resolves the repository root and runs:

```bash
./gradlew --no-daemon check
```

Add an executable `scripts/install-git-hooks.sh` that sets the local repository configuration `core.hooksPath` to `.githooks`. Document installation and removal in `README.md`. The hook is versioned but opt-in; cloning the repository does not mutate a developer's Git configuration automatically.

## Verification strategy

- Inspect the Gradle task graph to confirm analyzer tasks exist under all three modules and are reachable from `check`.
- Run `./gradlew --no-daemon check` and `./gradlew --no-daemon build`.
- Confirm reports are generated for each analyzer.
- Execute the hook installer and verify the configured hook path; restore the prior local hook configuration after the check.
- Exercise a temporary, isolated violation through the hook to prove it rejects a failing `check`, then restore the source tree.
- Ensure unrelated pre-existing worktree changes remain unmodified.
