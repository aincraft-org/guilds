# Guilds Plugin Identity and Package Consolidation Plan

> Execute this plan in the current repository after the approved design specification at `docs/superpowers/specs/2026-07-04-guilds-plugin-identity-design.md`.

## Goal

Move the Paper Guilds implementation from `org.aincraft.guilds` to `dev.mintychochip.guilds`, rename the Paper entrypoint and plugin identity to Guilds, preserve territory behavior and persistence identifiers, and update tests/documentation with no compatibility shims.

## Task 1: Establish the package move

**Files:** all Java files under `paper/src/main/java/org/aincraft/guilds/` and `paper/src/test/java/org/aincraft/guilds/`, plus every source/test caller importing that namespace.

1. Move the implementation source tree to `paper/src/main/java/dev/mintychochip/guilds/`.
2. Move the implementation test tree to `paper/src/test/java/dev/mintychochip/guilds/` where package layout permits; retain test package structure under the new root.
3. Replace package declarations and imports from `org.aincraft.guilds` to `dev.mintychochip.guilds` in the moved tree and callers.
4. Keep public API classes already under `dev.mintychochip.guilds` distinct; resolve same-package imports explicitly where implementation models and API models share names.
5. Update source-level tests that read or assert implementation paths and class names.
6. Do not retain an old namespace adapter.

Acceptance: no production or test Java source uses `org.aincraft.guilds`; the Guilds composition root and all service implementations compile from `dev.mintychochip.guilds`.

## Task 2: Rename the Paper entrypoint

**Files:** `paper/src/main/java/dev/mintychochip/territory/GuildsTerritoryPlugin.java`, all callers/tests, `paper/src/main/resources/plugin.yml`.

1. Move the main class to `paper/src/main/java/dev/mintychochip/guilds/GuildsPlugin.java`.
2. Change its package and class name to `dev.mintychochip.guilds.GuildsPlugin`.
3. Update self-references, territory command constructors, listeners, tests, source-path checks, reflection checks, and imports.
4. Update `plugin.yml` to `name: Guilds` and `main: dev.mintychochip.guilds.GuildsPlugin`.
5. Preserve all existing commands, permissions, soft dependencies, lifecycle wiring, and territory subsystem behavior.
6. Keep the existing GitHub URL unchanged.

Acceptance: the descriptor names exactly one plugin, `GuildsPlugin` loads as the main `JavaPlugin`, and no `GuildsTerritoryPlugin` class or reference remains.

## Task 3: Rename artifact and runtime identity

**Files:** `paper/build.gradle.kts`, root/module Gradle descriptions/comments, `paper/src/main/resources/config.yml`, runtime web/logger classes, relevant tests.

1. Set sources, thin, and shadow JAR base names to `guilds`.
2. Update run-server comments or task wiring that refers to the old artifact.
3. Change plugin-facing descriptions and comments to Guilds terminology.
4. Change the web health service value and thread/logger names tied directly to the old product identity.
5. Change the default Mint client binding to `Guilds`.
6. Preserve database names, database schema, table names, and other migration-sensitive persistence identifiers.

Acceptance: Gradle produces `guilds-<version>.jar` as the delivery artifact and runtime-facing identity strings no longer report GuildsTerritory, except intentionally retained external URL/database identifiers.

## Task 4: Update documentation and contract tests

**Files:** `README.md`, plugin metadata tests, Guilds integration/wiring tests, any affected focused tests.

1. Update README title, artifact examples, run-server text, data-folder path, package examples, and plugin entrypoint examples.
2. Update metadata tests to assert `Guilds` and `dev.mintychochip.guilds.GuildsPlugin`.
3. Update integration tests to assert the new Guilds implementation namespace and reject the old namespace/main class.
4. Update test fixture paths and source scanning paths.
5. Preserve tests for territory behavior and existing Guilds service graph wiring.

Acceptance: tests check the new observable contracts rather than the old identity, and documentation describes the actual built artifact and runtime data directory.

## Task 5: Focused verification

Run:

```bash
./gradlew :paper:test --tests '*PluginMetadataTest' --tests '*GuildsIntegrationTest' --tests '*GuildsServicesWiringTest' --no-daemon --console=plain
./gradlew :paper:jar :paper:shadowJar --no-daemon --console=plain
```

Check that the focused tests pass and the expected `guilds` artifact exists. If failures reveal missed references, correct the source and rerun the focused checks.

## Task 6: Full verification and audit

Run:

```bash
./gradlew clean build --no-daemon --console=plain
```

Then audit tracked source/config/docs for stale names. Allowed historical strings are limited to the retained GitHub URL and persistence identifiers explicitly preserved by the design. Confirm no generated stale source directories remain and inspect `git status --short` for only intentional changes.
