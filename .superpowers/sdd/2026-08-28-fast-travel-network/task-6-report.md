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
- `5003caf` — `fix: reject mismatched boat snapshot worlds`
- `74e848f` — `docs: report bounded boat routing task`
## Concerns

- **Mint private dependency credentials** — `MINT_PACKAGES_ACTOR`/`MINT_PACKAGES_TOKEN` (or equivalent GitHub Packages credentials) were unavailable. The required focused tests and Paper module compile could not reach test compilation or execution.
- **Java 26 compiler unavailable** — only a Java 25 runtime was visible and no `javac` executable was installed; no repository workaround was made.
- **Composition-root lifecycle wiring** — the Task 6 deliverables include production constructors and listener/service lifecycle methods, but `GuildsPlugin` registration and shutdown composition-root edits were intentionally left to the later travel wiring boundary because the brief explicitly limits this task's file set and excludes Task 7-10 wiring. The next integration task must register `BoatWaterChangeListener` and close the shared `BoatRouteService` from plugin shutdown rather than constructing a second cache/service.

## Review round 1

Addressed all three Task 6 review findings:

- `BoatWaterChangeListener` now accepts the configured clear-boat-space height and scans the full vertical range around a changed block, so changes at `waterY + clearBoatSpaceHeight` invalidate dependent routes.
- `BoatWaterMask` rejects navigable cells outside its declared chunk. `BoatWaterSnapshot` likewise rejects foreign endpoint clear-space cells before analysis or budget accounting.
- The Paper snapshot producer now bounds the capture region to both the configured search radius and hard chunk budget, builds an endpoint-to-endpoint corridor first, and then expands deterministic endpoint-centered rings. With the shipped 256-chunk budget, endpoint chunks remain reachable even when they are more than 16 chunks apart rather than being rejected by a square-root span gate.

Added focused regressions for foreign mask/snapshot cells and a clear-space block change two blocks above water. The required focused command was rerun after these fixes and produced the same pre-compilation Mint HTTP 401 failure recorded above. `git diff --check` and a Java delimiter-balance sanity check produced no output/errors. Fixes are committed in `9e3febf`.

## Review round 2

Addressed the follow-up compile and bounded-capture findings:

- The private service constructor now initializes the final `snapshotProducer` field with `Objects.requireNonNull`, and `BoatRouteServiceTest` restores its `org.junit.jupiter.api.Test` import.
- Removed the square-root chunk-span gate. The Paper producer now emits the endpoint-to-endpoint corridor before deterministic rings around both endpoints, while retaining hard search-radius and configured chunk-budget bounds. Completion accounts for chunks captured in the current batch as well as prior batches.
- Added `corridorCapturesEndpointsBeyondSqrtBudget`, exercising endpoints 17 chunks apart (within the 32-chunk radius and 256-chunk budget) and proving bounded capture reaches worker analysis rather than returning permanent `PENDING`.

The required focused command was rerun after the fixes:

```text
./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BoatRouteCalculatorTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteCacheTest' --tests 'org.aincraft.guilds.territory.building.BoatRouteServiceTest'
```

Exact result:

```text
BUILD FAILED in 2s
4 actionable tasks: 4 up-to-date

FAILURE: Build failed with an exception.

* What went wrong:
Could not resolve all files for configuration ':guilds-test:mintPlugin'.
Could not resolve dev.mintychochip.mint:mint-paper:26.8.12.10.
Could not GET 'https://maven.pkg.github.com/aincraft-org/mint/dev/mintychochip/mint/mint-paper/26.8.12.10/mint-paper-26.8.12.10.pom'. Received status code 401 from server: Unauthorized
```

Independent checks after the fixes:

- `git diff --check` — no output.
- Java delimiter-balance check over `BoatRouteService.java` and `BoatRouteServiceTest.java` — both reported `delimiters balanced`.
- No formatter, linter, project-wide suite, Gradle/toolchain edit, or Mint workaround was used.

Additional commit:

- `ea30da9` — `fix: prioritize long boat route corridors`
