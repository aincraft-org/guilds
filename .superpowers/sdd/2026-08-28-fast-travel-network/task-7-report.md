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

## Latest review corrections

- Added a cancellable in-flight attempt registered before route/currency work. Cancellation invalidates callbacks and releases any reservation that arrives after cancellation; warmup insertion races report `PENDING_TRIP` rather than `STARTED`.
- Added ACTIVE/COMMITTING/TERMINAL attempt transitions so cancellation cannot release a reservation after teleport has begun.
- Added an explicit reservation-duration constructor seam with checked overflow; the legacy constructor remains only for compatibility and defaults to the documented fallback until Task 9 supplies the shared configured duration.
- Moved remote-crystal capability checking ahead of alliance and policy checks, and tightened terminal-to-crystal same-territory handling so only same-guild local terminal-to-own-crystal is exempt.
- Main-thread marshalling now fails rather than running callbacks inline when scheduler submission returns null.
- Focused regressions were rerun after these fixes; Gradle again failed before test execution in 2s (`4 actionable tasks: 4 up-to-date`) on the same Mint HTTP 401 blocker.

## Round-two review corrections

- Restored the `guilds` and `stopped` service fields and verified the constructor dependency assignments.
- Revalidated authorization on the Paper thread after asynchronous route completion, before landing/teleport/commit, and used that fresh decision for cooldown identity.
- Wrapped the scheduled completion body so unexpected landing/protection/cost/teleport failures terminal-clean and release reservations.
- Added `FastTravelAccess.FastTravelSnapshot`, an immutable preloaded identity/capability/alliance seam for composition roots; when supplied, access decisions do not query Resident/Guild/TechTree/Alliance services, allowing JDBC work to occur before main-thread listing/authorization.

Focused regressions were rerun after this round with the required selector command; Gradle failed before execution in 5s (`4 actionable tasks: 4 up-to-date`) on the known Mint HTTP 401 dependency blocker.

## Round-three review corrections

- Boat route endpoints now resolve an adjacent liquid entry cell on the Paper thread instead of routing from the persisted structural anchor block.
- Alliance authorization now requires the traveler guild and both endpoint guilds to share the current alliance; same-guild travel remains valid.
- In-flight attempts carry expiry and outcome state; recovery, cancellation, and stop invalidate unresolved work, complete returned futures, and release reservations exactly once.
- Final completion rechecks endpoint records, authorization, and reservation expiry immediately before teleport; stale replacements, governance changes, and expired trips fail without teleport/commit. Fresh final authorization identity drives WAYSTONE cooldown reduction.
- Added a preloaded `FastTravelSnapshot` seam for resident membership, capabilities, alliances, and WAYSTONE authorization, plus preloaded cooldown-reduction data to avoid production JDBC reads on main callbacks.
- Building travel is available without the management permission gate, and terminal interaction starts an eligible own crystal while retaining optional remote destinations.

Focused selector verification was rerun after these changes; Gradle failed before execution in 3s (`4 actionable tasks: 4 up-to-date`) because the Mint dependency remained unavailable with HTTP 401.

## Round-four review corrections

- Restored the in-flight attempt map and ensured attempt expiry/future completion is initialized before asynchronous route or reservation work.
- Added the missing unrelated-alliance rejection while retaining same-guild travel.
- Boat routing now resolves exact `Material.WATER` cells with clear vertical entry space around persisted anchors, including adjacent-water structural anchors.
- Commit cooldown reduction uses the refreshed authorization identity and accepts preloaded reductions without a main-thread GuildService lookup; legacy fallback remains only for empty reduction maps.
- WAYSTONE snapshots retain membership-only behavior by default instead of requiring a synthetic capability.
- Final endpoint, authorization, expiry, and terminal UI guards remain in place; travel permission bypass is retained for resident clickable commands.

The required focused selector command was rerun after this round and failed before execution in 3s (`4 actionable tasks: 4 up-to-date`) on the known Mint HTTP 401 blocker.

## Commit

- `d22cd1c` — `feat: generalize waystone travel into fast travel`
- `05b45dc` — `fix: enforce transport validation before fast travel`
- `ccb2b9b` — `fix: make fast travel attempts cancellable`
- `37c11c4` — `fix: revalidate fast travel completion`
- `be01f11` — `fix: harden fast travel routing and recovery`
- `aa8be84` — `fix: guard waystone snapshot authorization`
- `1aa1c74` — `fix: finalize fast travel lifecycle guards`

## Concerns

- **Mint private dependency credentials** — required focused tests remain blocked before compilation/execution by GitHub Packages HTTP 401.
- **Java 26 compiler unavailable** — no build/toolchain change was made.
- **Task 9 composition wiring** — production must inject configured reservation duration, preloaded FastTravelSnapshot, cooldown reductions, shared services, and startup recovery; no Task 9 wiring was changed.
- **Dedicated LSP/rename_file tool unavailable** — active references were manually verified and migrated without aliases or deprecated wrappers.

## Round-one review fixes

- Made post-arrival commit failures idempotently release reservations, while
  committed and already-committed outcomes finish without a refund.
- Allowed terminal release from both ACTIVE and COMMITTING, guarded the entire
  completion path, and made synchronous `start` failures clean up attempts and
  complete their futures.
- Reordered WAYSTONE identity resolution after endpoint checks and before
  relationship authorization; replaced mutable cooldown maps with concurrent
  inner maps.
- Treat production boat-route scheduler submission failure as `UNAVAILABLE`.
- Enforced fail-closed main-thread entry for Bukkit-facing travel APIs and
  marshalled reservation callbacks before warmup registration.

Verification:

```text
./gradlew --offline :guilds-paper:compileJava :guilds-paper:compileTestJava
BUILD SUCCESSFUL

./gradlew --offline :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FastTravelAccessTest' --tests 'org.aincraft.guilds.territory.building.FastTravelSelectionsTest' --tests 'org.aincraft.guilds.territory.building.FastTravelServiceTest' --tests 'org.aincraft.guilds.territory.building.BuildingCommandTest' --tests 'org.aincraft.guilds.territory.building.BuildingListenerTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest'
BUILD SUCCESSFUL; 29 tests completed, 0 failed

git diff --check
clean
```

Focused regressions cover post-arrival release/refund semantics, teleport
failure from COMMITTING, synchronous and off-main start failure cleanup,
cooldown concurrent access, self-ID and WAYSTONE identity ordering, and null
boat scheduler submission.

Concerns: Gradle reports pre-existing Error Prone warnings in unrelated
composition and service classes; no project-wide test suite was run.
