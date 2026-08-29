# Agent Instructions

This file applies to the whole repository. More-specific `AGENTS.md` files add
or override these instructions for their subtree. Preserve unrelated work in
the working tree; do not reset, stash, or rewrite changes you did not make.

## Repository shape

Guilds is a multi-module Java 26 Gradle project for one Paper plugin, with a
separate React/Vite client:

- `guilds-api/` — public, Paper-free contracts and value models. Keep Bukkit
  types out of this module.
- `guilds-common/` — Paper-free shared implementation: territory domain logic,
  persistence, economy, governance, influence, standing, upkeep, invasion, and
  the embedded JDK HTTP API.
- `guilds-paper/` — the only production Paper plugin. It contains Bukkit/Paper
  adapters, listeners, commands, resources, and the integrated Guilds subsystem
  under `org.aincraft.guilds`.
- `guilds-test/` — local `run-paper` harness and test-server support; it is not
  a second production plugin artifact.
- `web/` — separate React/Vite frontend for the public page and territory
  editor. It communicates with the Paper API through same-origin `/api/*`
  routes and a server-only Vercel proxy.
- `docs/living-specs/` — active domain catalogs. `docs/archived-*` and older
  one-shot documents are historical reference, not current authority.
- `config/` — centralized Checkstyle, PMD, and SpotBugs configuration.
- `scripts/` — repository helpers, including Git hook installation and local
  squaremap artifact construction.

The production deliverable is the shadow JAR from `:guilds-paper`, normally at
`guilds-paper/build/libs/guilds-<version>.jar`. Do not split the integrated
Guilds subsystem into another plugin JAR without an explicit product decision.

## Before changing behavior

1. Read the relevant active catalog in `docs/living-specs/` before designing or
   implementing a domain change. The index is
   `docs/living-specs/README.md`.
2. Keep the catalog and implementation aligned: promote parked work from
   `Future` to `Next`/`Current` before implementing it, update shipped/open
   checkboxes when work lands, and record material decisions in the catalog.
3. Read the current root `README.md` and the module build file for the path you
   will change. For frontend work, also read `web/README.md`.
4. Keep changes focused. Do not revive archived code or introduce a second
   catalog, parallel implementation, or compatibility shim without a stated
   requirement.

## Architectural invariants

- Durable state has one SQL source of truth and one shared HikariCP pool.
  PostgreSQL is the default; MySQL is selectable through `database.type`.
  Do not add JSON-file, SQLite, or per-store fallback backends.
- Versioned shared persistence schema changes belong in
  `guilds-common/src/main/resources/sql/migrations/`. Guilds service query and
  DML statements are separate classpath resources under
  `guilds-paper/src/main/resources/sql/`; keep them in that existing path.
  Preserve the portable-dialect conventions rather than adding PostgreSQL-only
  SQL to shared migrations.
- A mutation that claims persistence must succeed durably before it advances
  gameplay memory. In particular, web territory writes stage and validate a
  registry state, save it, then replace the in-memory registry.
- Territory overlap and zone validation belong in the domain model/registry,
  not only in HTTP or Paper handlers. Consumers should use the shared registry
  and `LookupResult` rather than creating a second spatial authority.
- Guild membership, roles, alliances, and permissions are owned by the Guilds
  SQL schema and exposed to territory code through the governance bridge. Do
  not reintroduce standalone in-memory membership authority.
- Economy money movement goes through the existing economy bridge/payment-rail
  boundaries. Do not call Vault or another provider directly from unrelated
  domain code.
- Keep `guilds-api` and `guilds-common` domain code free of Bukkit/Paper types.
  Put server lifecycle, event, command, inventory, and physical interaction
  code in `guilds-paper`.
- Keep secrets out of source control, generated assets, URLs, browser storage,
  logs, and test output. This includes database credentials, API tokens,
  keystores, and deployment variables.

## Toolchain and dependencies

- Use the repository Gradle wrapper (`./gradlew`); the wrapper is pinned to
  Gradle 9.6.1.
- The root build configures a Java 26 toolchain. CI also provisions Temurin 26.
- Frontend development requires Node.js 20 or newer and npm.
- Dependencies from the private Mint GitHub Packages repository may require
  `MINT_PACKAGES_ACTOR` and `MINT_PACKAGES_TOKEN` (or the corresponding GitHub
  Actions credentials). Do not commit credentials or local Gradle property
  files.
- The Gradle settings and module build files are authoritative for dependency
  versions and task wiring. Do not float or casually upgrade pinned versions.

## Build and test commands

Run commands from the repository root unless noted otherwise.

### Java / Paper

```bash
# Full local build; CI runs this conceptual path.
./gradlew --no-daemon build

# Quality gate: tests plus Error Prone, SpotBugs, PMD, and Checkstyle.
./gradlew --no-daemon check

# Faster test-only pass when static analysis is not the subject of the change.
./gradlew test

# Target one module or a focused test class when iterating.
./gradlew :guilds-api:test
./gradlew :guilds-common:test
./gradlew :guilds-paper:test --tests 'fully.qualified.TestClass'
```

Use the narrowest useful test while iterating, then run the applicable full
quality gate before delivery. Tests use JUnit Platform. Database integration
coverage may be environment-gated; `GUILDS_TEST_MYSQL_JDBC_URL` enables the
MySQL integration path when a suitable database is available.

Install the repository pre-commit hook once per clone when appropriate:

```bash
./scripts/install-git-hooks.sh
```

The hook runs `./gradlew --no-daemon check`. Do not bypass it to hide analyzer
or test failures. Generated output under `build/`, `run/`, and similar ignored
paths is not a source change.

### Local Paper smoke test

The `:guilds-test:runServer` task boots the local Paper harness and loads the
current plugin/test artifacts plus its configured integration plugins. It needs
its runtime JARs and a reachable SQL database; consult
`guilds-test/build.gradle.kts` and the current README before starting it. The
squaremap path may use the configured upstream/local artifact selection, and
`./scripts/build-squaremap-local.sh` builds the repository-local Rust-backed
artifacts when that path is selected.

Prefer the process supervisor for a running server. Never use broad `pkill`
patterns that can terminate the supervised Paper process; stop a verified
process by name or PID and check locks before rebuilding runtime artifacts.

### Frontend

```bash
cd web
npm ci
npm test -- --run
npm run typecheck
npm run build
```

Use `npm run dev -- --host 127.0.0.1` for the Vite client. Use `vercel dev`
only when testing the complete server-side `/api/*` proxy path, with a valid
server-only `GUILDS_API_ORIGIN`; never expose that origin or a Paper API token
to browser code or `VITE_*` variables. For UI changes, verify the actual page
at both `/` and `/editor` when the relevant local services are available.

## Code and test placement

- Put public contracts and pure value/domain tests in `guilds-api`.
- Put Paper-free persistence, domain engines, codecs, and HTTP behavior in
  `guilds-common`; test database/dialect and commit-order invariants there.
- Put Paper wiring, commands, listeners, GUI, integrations, and server smoke
  behavior in `guilds-paper`. New Guilds tests belong with this module; the
  MockBukkit suite under `docs/archived-guilds-test/` is historical.
- Put frontend tests beside the relevant `web/src` component/module using the
  existing Vitest and Testing Library setup.
- Test observable behavior and failure paths: invalid geometry, atomic
  persistence, permission/form matrices, escrow/refund behavior, offline
  identity, auth/cookie handling, and UI state transitions. Avoid tests that
  only assert implementation details.
- Add or update tests when a changed contract is not already covered. Do not
  weaken assertions, disable analyzers, or use broad ignores to make a change
  pass.

## Style and implementation rules

- Follow the surrounding code and existing module seams. Checkstyle currently
  rejects wildcard imports; keep imports explicit and let the configured
  quality tools define the Java gate.
- Prefer small services with explicit interfaces and constructor wiring through
  the existing composition roots (`GuildsPlugin`, `GuildsServices`, and
  `GuildsGovernanceSource`). Avoid new global state or duplicate registries.
- Keep SQL parameterized and portable across the supported dialects. Use the
  existing SQL helpers, migration runner, and resource-loading conventions.
- Keep API/auth error behavior explicit and fail closed where the active living
  spec requires it. Never log raw credentials or tokens.
- Default to a clean cutover: migrate every caller and remove obsolete
  aliases, deprecated paths, and compatibility shims. Preserve public API or
  persisted-data compatibility only when an explicit product/spec requirement
  calls for it; document and test that intentional compatibility in the same
  change.

## Delivery checklist

Before reporting completion:

- Review the diff for accidental edits, generated files, secrets, and stale
  documentation.
- Run the focused tests for the changed behavior.
- Run `./gradlew --no-daemon check` for Java/Paper changes, or the frontend
  test/typecheck/build commands for `web/` changes; run both when both surfaces
  changed.
- Perform the relevant runtime smoke test for Paper, HTTP, or UI behavior when
  the required local services are available.
- Update the relevant active living spec and user-facing README/docs when the
  behavior or operator workflow changed.
- Report exact commands run and any environment-gated checks that were not
  available. Do not claim a deployment, authenticated flow, or passing check
  that was not actually exercised.
