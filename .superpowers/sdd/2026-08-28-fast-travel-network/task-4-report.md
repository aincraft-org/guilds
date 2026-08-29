# Task 4 implementation report

Implemented only Task 4 of the approved fast-travel-network plan. The change extends building configuration with typed transport geometry and new facility anchors, adds the three infrastructure tech nodes, exposes capability/effect queries with WAYSTONE-only cooldown reduction, and assigns non-overlapping graph coordinates. No Task 5-10 validators, route/service wiring, wallet code, Gradle/toolchain files, or unrelated files were changed.

## Files changed

- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfig.java`
  - Added immutable `TransportGeometry` with boat entry radius/width, clear boat-space height, bounded search chunk radius/budget, airship platform radius, and clear-sky height.
  - Added positive-value checks and defaults (`2, 3, 2, 32, 256, 2, 16`).
  - Preserved the existing four-argument constructor and waystone timing fields for caller compatibility.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/BuildingConfigLoader.java`
  - Loads the new transport geometry keys and anchor-material lists.
  - Preserves existing waystone, trading-post, storage, bank, placement timeout, warmup, and cooldown defaults/behavior.
  - New anchor defaults are `AMETHYST_BLOCK` for `GUILD_CRYSTAL`, `LODESTONE` for `TELEPORT_TERMINAL`, `OAK_PLANKS` for `BOAT`, and `IRON_BLOCK` for `AIRSHIP`.
- `guilds-paper/src/main/resources/config.yml`
  - Added the four new anchor-material sections and the concrete transport geometry defaults.
- `guilds-paper/src/main/java/org/aincraft/guilds/config/TechTreeConfigLoader.java`
  - Updated inline fallback YAML and added the three new nodes.
- `guilds-paper/src/main/resources/techtree.yml`
  - Updated packaged `fast_travel` description while retaining cost `3`, prerequisite `better_storage`, and `teleport_cooldown_reduction: 0.5`.
  - Added `remote_crystal` (cost `3`), `boat_travel` (cost `3`), and `airship_travel` (cost `4`), each requiring `fast_travel` with explicit infrastructure GUI positions.
  - Packaged and fallback definitions are semantically identical.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/TechTreeService.java`
  - Added `hasCapability`, `getNumericEffect`, and mode-aware `cooldownReduction` contracts.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/TechTreeServiceImpl.java`
  - Implemented unlocked-node capability checks and finite numeric effect lookup.
  - Scoped `teleport_cooldown_reduction` to the `fast_travel` node and `WAYSTONE`; crystal, boat, and airship modes return no inherited reduction.
- `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`
  - Added distinct radial coordinates for the three new infrastructure nodes without changing existing coordinates.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/BuildingConfigLoaderTest.java`
  - Covers legacy defaults, all new anchors, all geometry defaults, and explicit geometry overrides.
- `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`
  - Covers distinct hitboxes for all three new nodes against the configured graph.
- `guilds-paper/src/test/java/org/aincraft/guilds/services/TechTreeServiceCapabilityTest.java`
  - Covers capability unlock queries, numeric effects, WAYSTONE-only reduction scope, and packaged/fallback travel-node parity including costs, prerequisites, descriptions, effects, and positions.

## Ruling

The concrete Task 4 geometry and anchor defaults are:

```text
buildings.transport.boat.entry-radius: 2
buildings.transport.boat.entry-width: 3
buildings.transport.boat.clear-space-height: 2
buildings.transport.boat.search-chunk-radius: 32
buildings.transport.boat.search-chunk-budget: 256
buildings.transport.airship.platform-radius: 2
buildings.transport.airship.clear-sky-height: 16

GUILD_CRYSTAL: AMETHYST_BLOCK
TELEPORT_TERMINAL: LODESTONE
BOAT: OAK_PLANKS
AIRSHIP: IRON_BLOCK
```

These use the existing root `config.yml` and block-material anchor convention. Boat route/search behavior remains out of scope for Task 4.

## Commands and output

1. TDD red attempt before production implementation:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BuildingConfigLoaderTest'
   ```

   Result: `BUILD FAILED` before test compilation because `:guilds-test:mintPlugin` could not resolve `dev.mintychochip.mint:mint-paper:26.8.12.10`; GitHub Packages returned HTTP `401 Unauthorized`.

2. Required focused Task 4 command after implementation:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.territory.building.BuildingConfigLoaderTest' --tests 'org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest' --tests 'org.aincraft.guilds.services.TechTreeServiceCapabilityTest'
   ```

   Result: `BUILD FAILED` during project dependency resolution for the same private Mint artifact, with HTTP `401 Unauthorized`; no focused tests could execute.

3. Offline compile attempt:

   ```text
   ./gradlew --offline :guilds-paper:compileJava
   ```

   Result: `BUILD FAILED`; the Mint artifact is not cached for offline mode. No Gradle configuration was changed.

4. Static whitespace check:

   ```text
   git diff --check
   ```

   Result: no output (clean).

## Commits

- `d5994cc` — `feat: add fast travel building and tech capabilities`
- `4fc5dbc` — `docs: report fast travel config capability task` (initial report commit)
- A final metadata update commit follows to record this report commit hash.

## Concerns

- `MINT_PACKAGES_ACTOR`/`MINT_PACKAGES_TOKEN` (or equivalent GitHub Packages credentials) are unavailable in this environment, so Gradle cannot resolve the known private `mint-paper` dependency. The focused tests and compile could not run; no configuration bypass was introduced.
- Full repository validation remains for the main agent after all task slices land.
