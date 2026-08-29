# Task 6 implementation report

Implemented only Task 6 of the approved fast-travel-network plan. The change adds immutable Paper-free boat water masks and snapshots, a scalar-only bounded A* connectivity calculator, revisioned geometry cache with neighboring-chunk invalidation, a main-thread snapshot/worker-analysis service, and water-change event filtering. No generalized travel authorization, route-path persistence, reward/currency behavior, or Task 7-10 travel wiring was added.

## Files changed

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterMask.java`
  - Adds immutable chunk water masks containing world-coordinate navigable surface cells.
  - Exposes only immutable cell/chunk value data and no Bukkit references.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterSnapshot.java`
  - Adds immutable per-world/per-chunk captures with navigable cells and endpoint clear-space cells.
  - Defensively copies all collections and validates mask/chunk identity.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteResult.java`
  - Adds `CONNECTED`, `DISCONNECTED`, `PENDING`, and `UNAVAILABLE` states.
  - Retains only a scalar navigable distance for connected results; no route path is represented.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteCalculator.java`
  - Performs immutable-snapshot bounded A* search over four-way surface neighbors.
  - Enforces chunk and discovered-node budgets, returning `PENDING` before exceeding either budget.
  - Returns `UNAVAILABLE` for missing/invalid endpoints or inconsistent snapshots and `DISCONNECTED` only after the bounded search is exhausted.
  - Performs no Bukkit/world access and stores no predecessors or paths.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteCache.java`
  - Keys entries by world UUID, lexicographically normalized endpoint pair, and current water revision.
  - Stores geometry result plus private sampled-chunk dependency indexes only; transient pending/unavailable results are not cached.
  - Rejects stale-revision writes and invalidates entries whose dependencies or endpoint chunks are in the changed chunk's 3x3 neighborhood.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatRouteService.java`
  - Provides bounded batch capture through an injected main-thread executor and dispatches immutable snapshots to an injected worker executor.
  - Provides a Paper constructor using the Bukkit scheduler and a bounded loaded-chunk producer that checks water plus configured clear boat space.
  - Produces `PENDING` on capture budget/no-progress exhaustion and `UNAVAILABLE` when the world or a required chunk is unavailable.
  - Deduplicates same-revision requests, reuses cache entries, rejects stale worker results, and clears pending work/stops its owned executor on `close`/`shutdown`.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/boat/BoatWaterChangeListener.java`
  - Handles block place/break, fluid movement, and piston extend/retract events.
  - Filters changes to water, adjacent water, shoreline, or clear-space-affecting blocks before invalidating the affected chunk; cache invalidation covers neighboring chunks.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteCalculatorTest.java`
  - Covers connected scalar distance, disconnected masks, hard node budget, unavailable endpoints, and defensive immutability.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteCacheTest.java`
  - Covers cache reuse, endpoint-order normalization, revision changes, changed/neighboring-chunk invalidation, and stale-revision write rejection.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BoatRouteServiceTest.java`
  - Covers main-thread capture versus worker analysis, cache reuse, unavailable loaded-world prerequisites, no worker analyzer invocation for unavailable captures, and capture budget pending behavior.

## TDD and validation commands

1. Initial red focused test, run before implementation:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BoatRouteCalculatorTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteCacheTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest'
   ```

   Result: `BUILD FAILED` before test compilation/execution. Gradle stopped while resolving `:guilds-test:mintPlugin`; GitHub Packages returned HTTP `401 Unauthorized` for `dev.mintychochip.mint:mint-paper:26.8.12.10`.

2. Required focused Task 6 command after implementation:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BoatRouteCalculatorTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteCacheTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest'
   ```

   Result: `BUILD FAILED` before test execution for the same private Mint dependency resolution failure (GitHub Packages HTTP `401 Unauthorized`).

3. Production compile attempt:

   ```text
   ./gradlew :guilds-paper:compileJava
   ```

   Result: `BUILD FAILED` during `:guilds-test:mintPlugin` resolution for `dev.mintychochip.mint:mint-paper:26.8.12.10` with HTTP `401 Unauthorized`. No Gradle/toolchain configuration was changed.

4. Scoped patch check:

   ```text
   git diff --check
   ```

   Result: no output (clean).

5. Local compiler availability check:

   ```text
   which java && java -version && which javac || true
   ```

   Result: only a Java 25 runtime was available; `javac` was not installed. A direct source compile therefore could not be performed independently of Gradle.

## Commit

- `82bea5d` — `feat: add bounded boat route connectivity`

## Concerns

- **Mint private dependency credentials** — `MINT_PACKAGES_ACTOR`/`MINT_PACKAGES_TOKEN` (or equivalent GitHub Packages credentials) were unavailable. The required focused tests and Paper module compile could not reach test compilation or execution.
- **Java 26 compiler unavailable** — only a Java 25 runtime was visible and no `javac` executable was installed; no repository workaround was made.
- **Composition-root lifecycle wiring** — the Task 6 deliverables include production constructors and listener/service lifecycle methods, but `GuildsPlugin` registration and shutdown composition-root edits were intentionally left to the later travel wiring boundary because the brief explicitly limits this task's file set and excludes Task 7-10 wiring. The next integration task must register `BoatWaterChangeListener` and close the shared `BoatRouteService` from plugin shutdown rather than constructing a second cache/service.
