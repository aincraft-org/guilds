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
