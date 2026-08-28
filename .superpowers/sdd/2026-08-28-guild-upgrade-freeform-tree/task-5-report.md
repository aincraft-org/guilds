# Task 5 Report

Status: DONE_WITH_CONCERNS

Implemented inline after two visual-refinement workers stalled:

- Replaced curved Bezier/spline rendering with direct straight Bresenham pixel edges.
- Added state-specific edge styles: solid emerald mastered, dashed amber frontier/active, dim slate locked.
- Changed energy sparks to linear interpolation along straight edges.
- Replaced the previous flat patterns with outlined/detail Minecraft pixel sprites for the Hearth, hexagon, shield, coin, and diamond shapes.
- Added orthogonal `NodeStyle` state colors and dedicated gold Hearth treatment.
- Preserved native 128x128 screen, dynamic TechTreeNode layout, header guard, symmetric ±6 hitbox, and no bottom/top-right controls.
- Corrected the dash flag so only frontier/active edges are dashed; locked edges remain solid dim slate.

Commits:
- `e3aaff7 feat: refine Minecraft pixel nodes and straight tech web edges`
- `89f9625 fix: keep locked prerequisite edges solid`

Verification:
- `git diff --check` passed before both commits.
- `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"` reached Gradle configuration but failed resolving `dev.mintychochip.mint:mint-paper:26.8.12.10` from GitHub Packages with HTTP 401.

Concerns:
- Java/Gradle compile and runtime MapGUI rendering remain unverified because the private dependency is unavailable in this environment.

## Reviewed refinement

Status: FIXED

Fixes applied:

- Frontier edges now require an unlocked source and a successful `canUnlockNode(viewerGuild, targetId)` check. Active projects remain frontier edges, while mastered targets stay solid emerald; prerequisite-ready targets without enough tech points remain solid dim locked edges with no frontier spark.
- Added a shared safe availability helper so rendering treats service failures as locked without interrupting the paint path.
- Reworked the hexagon into a clearly chiseled six-sided raster silhouette and the coin into a distinct octagonal raster coin, both using the existing `drawPattern` renderer.
- Added `*` highlight tokens to HEXAGON, SHIELD, COIN, and DIAMOND while retaining their `+` detail tokens.

Verification:

- `git diff --check` passed.
- `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"` was attempted and blocked during Gradle configuration: dependency `dev.mintychochip.mint:mint-paper:26.8.12.10` could not be resolved from GitHub Packages because the request returned HTTP 401 Unauthorized (`guilds-test/build.gradle.kts:38`).

Concerns:

- The focused test could not execute because the private GitHub Packages dependency requires credentials; Java/Gradle compilation and runtime rendering remain unverified in this environment.
