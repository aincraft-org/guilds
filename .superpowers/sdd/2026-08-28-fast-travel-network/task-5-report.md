# Task 5 implementation report

Implemented only Task 5 of the approved fast-travel-network plan. The implementation adds transport anchor outcome categories, main-thread transport validation, governance-derived activity reconciliation, quota/cardinality checks, and durable-before-live facility mutation boundaries. `SettlementFacility` remains a location-only persisted record; no active or owner fields were added.

## Files changed

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/AnchorStatus.java`
  - Retained `ACTIVE`, `WORLD_UNAVAILABLE`, `WRONG_MATERIAL`, and `OUTSIDE_TERRITORY`.
  - Added explicit geometry, spawn, governance, capability, quota, cardinality, boat-entry, airship-platform, and airship-clearance outcomes.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FacilityAnchorValidator.java`
  - Exposes category/is-active helpers on anchor results.
  - Reports missing/invalid transport geometry before world/block access while preserving existing exact-anchor behavior for legacy facilities.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelFacilityValidator.java`
  - Added main-thread-only `validateCandidate`, `validateBoatAnchor`, `validateAirshipAnchor`, `validateCrystalSpawn`, and `isActive` operations.
  - Resolves effective ownership from current territory governance and checks current guild capability nodes (`fast_travel`, `boat_travel`, `airship_travel`).
  - Counts candidate records by effective owner, territory, and facility type; inactive persisted records therefore remain in quotas and global crystal/terminal cardinality.
  - Enforces one persisted crystal and one persisted terminal per effective guild.
  - Requires exact crystal spawn world/block equality and spawn containment in a territory governed by the same guild.
  - Performs bounded boat shoreline/open-water inspection with clear boat space and bounded airship platform/vertical-clearance inspection; no 3D/world-scale construction search is performed.
  - Reconciles governance, capability, cardinality, current anchor, and crystal spawn for runtime activity. Quotas are enforced during candidate construction/reactivation, not used to disable an already-existing active endpoint after a quota reduction.
  - Every result exposes a status/category/message for command/listener diagnostics.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelFacilityState.java`
  - Added immutable runtime activity snapshot carrying the persisted facility, active flag, and failure reason without modifying persisted facility data.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FacilityMutationService.java`
  - Added optional fast-travel candidate validation.
  - Register, replacement, and explicit `reactivate(String)` now validate a copied candidate registry, save durably, and publish to live state only after save success.
  - Added replacement overloads and retained all existing constructor/callback compatibility.
  - Removal uses the same durable-then-live publication boundary; failed saves leave live state and lifecycle callbacks untouched.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FacilityAnchorValidatorTest.java`
  - Added missing and invalid transport geometry outcome coverage.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelFacilityValidatorTest.java`
  - Added exact crystal spawn/world matching, moved-spawn inactivity, governance loss/rebind, inactive global-cardinality reservation, independent per-type quotas, lowered-quota existing-facility behavior, bounded boat shoreline validation, airship platform/clearance validation, and bounded local-inspection coverage.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FacilityMutationServiceTest.java`
  - Added failed durable-save rollback and validator-before-save/reactivation coverage.

## TDD and validation commands

1. Initial red focused test:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FastTravelFacilityValidatorTest'
   ```

   Result: `BUILD FAILED` during `:guilds-test:mintPlugin` dependency resolution before test compilation. Gradle could not resolve `dev.mintychochip.mint:mint-paper:26.8.12.10`; GitHub Packages returned HTTP `401 Unauthorized`.

2. Required focused Task 5 command after implementation:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.FacilityAnchorValidatorTest' --tests 'org.aincraft.guilds.territory.building.FastTravelFacilityValidatorTest' --tests 'org.aincraft.guilds.territory.building.FacilityMutationServiceTest'
   ```

   Result: `BUILD FAILED` before test execution for the same private `mint-paper` resolution failure (GitHub Packages HTTP `401 Unauthorized`).

3. Production compile attempt:

   ```text
   ./gradlew --no-daemon :guilds-paper:compileJava
   ```

   Result: `BUILD FAILED` during `:guilds-test:mintPlugin` dependency resolution for the same private `mint-paper` HTTP `401 Unauthorized` blocker. No Gradle/toolchain configuration was changed.

4. Scoped whitespace check:

   ```text
   git diff --check
   ```

   Result: no output (clean).

5. Direct parser sanity attempt:

   ```text
   javac -proc:none -XDrawDiagnostics FastTravelFacilityValidator.java
   ```

   Result: unavailable because `javac` is not installed in this environment. The approved environment note also records Java 26 as unavailable.

## Commits

- `fc5f3eb` — `feat: validate fast travel facility construction`
- `1a317e6` — `fix: scope transport geometry checks to boat and airship`

## Concerns

- `MINT_PACKAGES_ACTOR`/`MINT_PACKAGES_TOKEN` (or equivalent GitHub Packages credentials) are unavailable, so the required focused tests and module compile could not reach test compilation/execution.
- Java 26/`javac` is unavailable locally; no build/toolchain changes were made.
- Full repository validation remains the main agent's responsibility after sibling Task 5-10 changes land.
