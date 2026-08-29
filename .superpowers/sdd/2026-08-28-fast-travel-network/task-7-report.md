# Task 7 implementation report

## Summary

Generalized the active Waystone travel surface into FastTravelAccess, FastTravelSelections, FastTravelService, and FastTravelListener. The generalized service evaluates active endpoints, exact mode compatibility, membership/capabilities, guild/alliance relationship, territory policy, same-territory rules, boat route state, landing/protection, canonical cost, and only then a durable travel-currency reservation. Warmup, cancellation, final revalidation, reservation release/commit, per-mode cooldowns, and WAYSTONE-only technology cooldown reduction are included.

## Files changed

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelAccess.java`
  - Replaces WaystoneAccess.
  - Preserves deterministic same-governing-guild WAYSTONE filtering without applying transport boundary policy.
  - Adds local terminal-to-own-crystal handling, crystal remote/alliance/policy/capability checks, boat and airship endpoint/capability/alliance/policy/world checks, and active endpoint validation.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelSelections.java`
  - Renamed from WaystoneSelections for generalized interactive endpoint selection.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelService.java`
  - Replaces WaystoneTravelService.
  - Adds the required CompletionStage-based `start`, `cancel`, mode-aware cooldown query, pending query, recovery, and stop API.
  - Enforces pre-reservation check ordering, consumes boat scalar route results, reserves only after landing/cost checks, stores immutable pending identifiers/mode/cost/reservation/expiry/route data, rechecks authorization/route/landing/cost before arrival, releases on cancellation/failure/expiry/invalidation, and commits only after successful teleport preparation.
  - Marshals route/database callbacks to the Paper scheduler and exposes the callback bridge used by command messages.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelListener.java`
  - Renamed from WaystoneTravelListener; movement, damage, death, and quit all cancel pending travel.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingCommand.java`
  - Migrates generalized selection/service types, adds new facility completions and parsing, adds generalized travel completions, and reports asynchronous categorized travel outcomes.
  - Maps validator IllegalArgumentException categories in remove failures.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java`
  - Migrates generalized access/selection/service types, supports crystal/terminal/boat/airship endpoint interaction, starts terminal local flow directly, and maps validator categories in registration/removal diagnostics.
- `guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java`
  - Migrates lifecycle field/getter/registration references to FastTravel names and registers FastTravelListener.
  - Leaves full currency/tech/alliance/boat composition wiring to Task 9 as required by the task boundary.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelAccessTest.java`
  - Renamed from WaystoneAccessTest; preserves WAYSTONE ordering coverage and adds endpoint mismatch and local terminal-to-own-crystal coverage.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelSelectionsTest.java`
  - Renamed from WaystoneSelectionsTest.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelServiceTest.java`
  - Renamed from WaystoneTravelServiceTest and updated for asynchronous reservation-backed start/cancellation.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingCommandTest.java`
  - Migrates the generalized constructor.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingLifecycleWiringTest.java`
  - Verifies `getFastTravelService` and FastTravelService return type.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingListenerStorageTest.java`
  - Migrates generalized access/selection constructor references.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingListenerTest.java`
  - Migrates generalized access/selection constructor references.
- Removed obsolete active `WaystoneAccess.java`, `WaystoneSelections.java`, `WaystoneTravelService.java`, `WaystoneTravelListener.java` and their renamed tests. No obsolete Waystone class symbol remains in active `guilds-paper/src/main` or `src/test` Java sources.

## TDD and verification commands

1. Focused red run before implementation:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.FastTravelSelectionsTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingListenerTest'
```

Result: `BUILD FAILED` before test compilation/execution. Gradle could not resolve `dev.mintychochip.mint:mint-paper:26.8.12.10`; GitHub Packages returned HTTP 401 Unauthorized.

2. Required focused run after implementation:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.FastTravelSelectionsTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingListenerTest'
```

Result: `BUILD FAILED` in 2s (`4 actionable tasks: 4 up-to-date`) before test execution, due to the same unavailable/private Mint artifact and GitHub Packages HTTP 401 resolution blocker.

3. Offline focused compile attempt after implementation:

```text
./gradlew --offline :guilds-paper:compileJava
```

Result: `BUILD FAILED` before project compilation because the same Mint artifact was not cached. No Gradle/toolchain/build-file workaround was made.

4. Attempt excluding the Mint task:

```text
./gradlew --offline :guilds-paper:compileJava -x :guilds-test:mintPlugin
```

Result: Gradle still resolved the `:guilds-test:mintPlugin` configuration and failed because the Mint artifact was unavailable offline.

5. Patch whitespace check:

```text
git diff --check
```

Result: no output.

6. Source delimiter sanity check over `FastTravelService.java`, `FastTravelAccess.java`, `BuildingCommand.java`, and `BuildingListener.java`:

Result: all four files reported balanced delimiters. This is a syntax sanity check only, not a substitute for Java compilation.

## Follow-up review corrections

- Added `FastTravelFacilityValidator` to the full FastTravelAccess composition seam. WAYSTONE activity retains the legacy FacilityAnchorValidator path; transport endpoints delegate to the live facility validator and are denied as inactive when no validator is supplied.
- Fixed `FastTravelService.start` to create and complete its returned future on route errors, null stages, callback scheduling failures, reservation failures, and successful outcomes.
- Changed warmup scheduling to return its actual result, so a `putIfAbsent` race reports `PENDING_TRIP` and releases the losing reservation instead of reporting `STARTED`.
- Wrapped final cost recalculation so runtime cost errors release the reservation before returning.
- Added a regression for transport denial without a configured facility validator and corrected the focused service fixture to provide its authorization decision.

Focused regressions were rerun after these corrections with the same required Gradle test selector command; Gradle again failed before test execution in 3s (`4 actionable tasks: 4 up-to-date`) because the Mint GitHub Packages dependency returned HTTP 401.

## Commit

- `d22cd1c` — `feat: generalize waystone travel into fast travel`
- `05b45dc` — `fix: enforce transport validation before fast travel`

## Concerns

- **Mint private dependency credentials** — the required focused Gradle command and compile could not reach test compilation/execution because GitHub Packages returned HTTP 401 for the known private Mint dependency.
- **Java 26 compiler unavailable** — the environment has no independently usable Java 26 compiler; no build/toolchain change was made.
- **Task 9 composition wiring** — GuildsPlugin currently constructs the legacy-compatible no-currency FastTravelService constructor. Task 9 must inject the shared TravelCurrencyService, FastTravelCostCalculator, BoatRouteService, TechTreeService, GuildService, ResidentService, and AllianceService and invoke startup recovery.
- **Dedicated LSP/rename_file tool unavailable** — active references were exhaustively searched and migrated with filesystem rename operations; no Waystone class aliases or deprecated wrappers were left in active Java sources.
