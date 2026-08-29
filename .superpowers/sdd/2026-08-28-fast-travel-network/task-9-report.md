# Task 9 report: remove spawn bypasses and wire fast travel lifecycle

## Implemented

- Removed the direct `/g spawn` command, its handlers/help/permission descriptor entries, and all hearthstone configuration, services, listener registration, implementation, and tests.
- Preserved `GuildService.getGuildSpawn` and `/g setspawn` for persisted crystal-spawn reconciliation.
- Removed `GuildService.canTeleportToSpawn` after removing all callers.
- Added one shared `TravelCurrencyService`/`TravelCurrencyConfig` to `GuildsServices`, passing it to quest, movement, and tech consumers while retaining the existing `DatabaseManager` pool.
- Added expired/orphan reservation recovery before command/listener registration and during normal shutdown. A failed startup recovery leaves the subsystem disabled, so transport interactions are not registered.
- Wired `FastTravelListener` and `BoatWaterChangeListener` through the service composition root.
- Extended `GuildsPlugin.startBuildings` to construct the live facility validator, boat route service, generalized access/service/listener graph, cooldown/reduction configuration, and shared currency/cost service.
- Reconciled persisted guild-crystal records against `GuildService.getGuildSpawn` without deleting or mutating mismatches.
- Stopped travel and route services during plugin disable, allowing pending reservation release to run through the travel service shutdown path.
- Updated focused command, descriptor, facility-type, lifecycle, shared-currency, and spawn-preservation tests.

## Verification

Compile:

```text
./gradlew --offline :guilds-paper:compileJava :guilds-paper:compileTestJava
```

Passed (`BUILD SUCCESSFUL`).

Exact focused suite from the brief:

```text
./gradlew --offline :guilds-paper:test --tests 'org.aincraft.guilds.GuildsServicesWiringTest' --tests 'org.aincraft.guilds.territory.building.BuildingLifecycleWiringTest' --tests 'org.aincraft.guilds.commands.brigadier.GuildBrigadierCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTypeTest' --tests 'org.aincraft.guilds.GuildsIntegrationTest'
```

Passed (`BUILD SUCCESSFUL`): 6 `GuildsIntegrationTest`, 1 `GuildBrigadierCommandTest`, 2 `BuildingCommandTypeTest`, and 2 `BuildingLifecycleWiringTest` tests. The 4 `GuildsServicesWiringTest` tests were skipped because `GUILDS_TEST_JDBC_URL` is not configured in this environment; no test failed.

Additional focused service test:

```text
./gradlew --offline :guilds-paper:test --tests 'org.aincraft.guilds.services.GuildServiceImplTest'
```

Passed (`BUILD SUCCESSFUL`): 1 test.

## Concerns / follow-up

- The database-backed composition-root tests could not execute without `GUILDS_TEST_JDBC_URL`; they remain covered by their existing assumption guard and should be run in the integration environment.
- The full project check is intentionally deferred to Task 10.
- Test output includes existing Mockito dynamic-agent and JVM warning messages; they did not cause failures.

## Fix round 1

### Changes

- Added `FastTravelService.stopAsync()` with an idempotent shutdown barrier that transitions every attempt terminal, tracks each pending currency release and in-flight reservation pipeline, and completes exceptionally if a release fails or is unobservable. `GuildsPlugin.onDisable()` now awaits this stage before closing route services, the Guilds subsystem, and the shared database.
- Restored `startWebIfEnabled()` and `registerPlaceholderExpansion()` on the normal initial-enable path.
- Added a preloaded immutable identity/capability/alliance snapshot provider to production `FastTravelAccess` wiring; access tests verify supplied snapshots bypass live identity services.
- Passed an empty static cooldown-reduction map in production so WAYSTONE reductions are read dynamically from the current tech/guild services. Added a post-construction reduction-change test.
- Added removal-only facility validation that checks candidate registry invariants without requiring the removed transport's current physical anchor or guild spawn. Added moved-crystal removal and durable-save rollback coverage.
- Declared `guilds.guild.setspawn` as a child of `guilds.guild.*` in both runtime descriptors and added descriptor assertions.
- Fixed the database wiring fixture to mock `GuildsPlugin` (including its territory registry), and added listener-wiring assertions.

### Fix-round verification

```text
./gradlew --offline :guilds-paper:compileJava :guilds-paper:compileTestJava
```

Passed (`BUILD SUCCESSFUL`).

```text
./gradlew --offline :guilds-paper:test --tests 'org.aincraft.guilds.GuildsServicesWiringTest' --tests 'org.aincraft.guilds.territory.building.BuildingLifecycleWiringTest' --tests 'org.aincraft.guilds.commands.brigadier.GuildBrigadierCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTypeTest' --tests 'org.aincraft.guilds.GuildsIntegrationTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.FacilityMutationServiceTest'
```

Passed (`BUILD SUCCESSFUL`): 6 integration, 1 Brigadier command, 2 facility-type, 2 lifecycle, 5 facility-mutation, 7 access, and 9 fast-travel service tests. The 5 database-backed `GuildsServicesWiringTest` tests were skipped because `GUILDS_TEST_JDBC_URL` is not configured; no test failed.

`git diff --check` produced no diagnostics.

### Fix-round concerns

- Database-backed wiring remains skipped in this environment and must be run with `GUILDS_TEST_JDBC_URL`.
- Full project validation remains deferred to Task 10.
- Existing Mockito dynamic-agent/JVM and Error Prone warnings remain non-failing.

## Fix round 2

### Changes

- Replaced the shutdown-only release counter with a single lock-protected operation admission registry. Reservation pipelines register before invoking `currency.reserve`; shutdown atomically closes admission and waits for all admitted operations.
- Currency releases register their completion before invoking `currency.release`, including releases initiated before shutdown or from late reservation callbacks. Null and exceptional release stages fail the shutdown stage closed.
- Added deterministic tests for pre-shutdown releases, stop/reserve races, and null release failures.
- Updated WAYSTONE `reachable` and delegated `destinations` to use supplied snapshots for membership/authorization rather than live `BuildingAuthorization`; legacy callers still use the live fallback.
- Added destination snapshot behavior coverage.

### Fix-round 2 verification

```text
./gradlew --offline :guilds-paper:compileJava :guilds-paper:compileTestJava
```

Passed (`BUILD SUCCESSFUL`).

```text
./gradlew --offline :guilds-paper:test --tests 'org.aincraft.guilds.GuildsServicesWiringTest' --tests 'org.aincraft.guilds.territory.building.BuildingLifecycleWiringTest' --tests 'org.aincraft.guilds.commands.brigadier.GuildBrigadierCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTypeTest' --tests 'org.aincraft.guilds.GuildsIntegrationTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest' --tests 'org.aincraft.guilds.territory.building.FacilityMutationServiceTest'
```

Passed (`BUILD SUCCESSFUL`): 40 tests (6 integration, 1 Brigadier command, 6 boat-route, 2 facility-type, 2 lifecycle, 5 facility-mutation, 7 access, and 11 fast-travel service). The 5 database-backed `GuildsServicesWiringTest` tests were skipped because `GUILDS_TEST_JDBC_URL` is not configured; no test failed.

`git diff --check` produced no diagnostics.

### Fix-round 2 concerns

- Database-backed wiring remains skipped in this environment and must be run with `GUILDS_TEST_JDBC_URL`.
- Full project validation remains deferred to Task 10.
- Existing Mockito dynamic-agent/JVM and Error Prone warnings remain non-failing.
