# Guild Upgrade Freeform Radial Tech Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a $360^\circ$ organic radial tech web on the native $128 \times 128$ MapGUI screen for `/g upgrade`, deriving node progression dynamically from `TechTreeService` / `TechTreeNode` with geometric pixel shapes (Hexagon, Shield, Coin, Diamond), stepped raster splines, and an in-map modal inspector.

**Architecture:** Replace the 4-column lane grid in `GuildUpgradeGraphLayout` with a radial layout engine that consumes `Collection<TechTreeNode>` from `TechTreeService`, injects a synthetic `guild_hearth` apex root at $(64, 64)$, and computes $360^\circ$ radial coordinates and shapes. `GuildUpgradeScreen` renders custom pixel shapes, stepped Bezier splines, traveling energy sparks, and in-map detail modal overlays on a native $128 \times 128$ pixel buffer.

**Tech Stack:** Java 21, Paper 1.21.x, MapGUI 2.0.0 (`de.flog99.mapgui`), JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-28-guild-upgrade-freeform-tree-design.md`

## Global Constraints

- **Viewport Budget:** All rendering and interaction must fit strictly within a native $128 \times 128$ pixel MapGUI buffer (no external UI, no bottom bars, no top-right upgrade buttons).
- **Dynamic Data Authority:** `techtree.yml` via `TechTreeService.getAllNodes()` remains the sole authority for node names, costs, prerequisites, descriptions, and effect modifiers. Zero hardcoded costs/lore in layout classes.
- **Topology:** Central synthetic `guild_hearth` root at $(64, 64)$ + 16 dynamic `TechTreeNode` perks forming a single connected DAG with visible cross-discipline links.
- **Discipline Geometries:** ⬡ Hexagon (`INFRASTRUCTURE`), 🛡 Shield (`DEFENSE`), 🪙 Coin (`COMMERCE`), ✦ Diamond (`CULTURE`).
- **Clean Cutover:** Remove legacy `slots/laneCells/origin/nodeX/nodeY` methods and migrate all callers.

---

### Task 1: Dynamic Radial Layout Engine & Clean Cutover

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`
- Modify: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`

**Interfaces:**
- Consumes: `Collection<TechTreeNode>`, `TechTreeBranch`, `TechTreeNode.getEffectivePrerequisites()`
- Produces: `GuildUpgradeGraphLayout.ShapeType`, `GuildUpgradeGraphLayout.LayoutNode`, `GuildUpgradeGraphLayout.SplineEdge`, `GuildUpgradeGraphLayout.layout(Collection<TechTreeNode>)`, `GuildUpgradeGraphLayout.edges(Collection<TechTreeNode>)`, `GuildUpgradeGraphLayout.findNodeAt(Map<String, LayoutNode>, int x, int y)`

- [ ] **Step 1: Write the failing unit tests for dynamic radial layout and cutover**

In `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`:

```java
package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class GuildUpgradeGraphLayoutTest {

    private TechTreeNode node(String id, String name, TechTreeBranch branch, int cost, List<String> prereqs) {
        TechTreeNode n = new TechTreeNode(id);
        n.setName(name);
        n.setBranch(branch);
        n.setCost(cost);
        n.setPrerequisites(prereqs);
        return n;
    }

    private List<TechTreeNode> sampleNodes() {
        return List.of(
            node("better_storage", "Better Storage", TechTreeBranch.INFRASTRUCTURE, 2, List.of()),
            node("fast_travel", "Fast Travel", TechTreeBranch.INFRASTRUCTURE, 3, List.of("better_storage")),
            node("reinforced_walls", "Reinforced Walls", TechTreeBranch.DEFENSE, 2, List.of()),
            node("market_stall", "Market Stall", TechTreeBranch.COMMERCE, 2, List.of()),
            node("heritage_monument", "Heritage Monument", TechTreeBranch.CULTURE, 2, List.of())
        );
    }

    @Test
    void layoutInjectsSyntheticGuildHearthAndPositionsNodes() {
        List<TechTreeNode> nodes = sampleNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        assertThat(layout).containsKey("guild_hearth");
        GuildUpgradeGraphLayout.LayoutNode hearth = layout.get("guild_hearth");
        assertThat(hearth.x()).isEqualTo(64);
        assertThat(hearth.y()).isEqualTo(64);
        assertThat(hearth.shape()).isEqualTo(GuildUpgradeGraphLayout.ShapeType.CORE);

        for (TechTreeNode n : nodes) {
            assertThat(layout).containsKey(n.getId());
            GuildUpgradeGraphLayout.LayoutNode ln = layout.get(n.getId());
            assertThat(ln.x()).isBetween(8, 120);
            assertThat(ln.y()).isBetween(14, 118);
        }
    }

    @Test
    void edgesConnectRootNodesToGuildHearth() {
        List<TechTreeNode> nodes = sampleNodes();
        List<GuildUpgradeGraphLayout.SplineEdge> edges = GuildUpgradeGraphLayout.edges(nodes);

        assertThat(edges).contains(
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "better_storage"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "reinforced_walls"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "market_stall"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "heritage_monument"),
            new GuildUpgradeGraphLayout.SplineEdge("better_storage", "fast_travel")
        );
    }

    @Test
    void findNodeAtResolvesNodeWithinHitbox() {
        List<TechTreeNode> nodes = sampleNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        GuildUpgradeGraphLayout.LayoutNode found = GuildUpgradeGraphLayout.findNodeAt(layout, 64, 64);
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo("guild_hearth");

        GuildUpgradeGraphLayout.LayoutNode hitEdge = GuildUpgradeGraphLayout.findNodeAt(layout, 69, 64);
        assertThat(hitEdge).isNotNull();
        assertThat(hitEdge.id()).isEqualTo("guild_hearth");

        GuildUpgradeGraphLayout.LayoutNode miss = GuildUpgradeGraphLayout.findNodeAt(layout, 0, 0);
        assertThat(miss).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: FAIL (missing methods / types).

- [ ] **Step 3: Implement Dynamic Radial Layout in `GuildUpgradeGraphLayout`**

In `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`:

```java
package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuildUpgradeGraphLayout {

    public static final String HEARTH_ID = "guild_hearth";

    public enum ShapeType {
        CORE, HEXAGON, SHIELD, COIN, DIAMOND
    }

    public record LayoutNode(
            String id,
            String name,
            TechTreeBranch branch,
            ShapeType shape,
            int x,
            int y,
            TechTreeNode rawNode
    ) {}

    public record SplineEdge(String fromId, String toId) {}

    private record Coord(int x, int y) {}

    private static final Map<String, Coord> RADIAL_COORDS = Map.ofEntries(
        // Center
        Map.entry(HEARTH_ID, new Coord(64, 64)),

        // Infrastructure (NW Quadrant)
        Map.entry("better_storage", new Coord(42, 44)),
        Map.entry("fast_travel", new Coord(26, 32)),
        Map.entry("advanced_farming", new Coord(22, 52)),
        Map.entry("auto_sorter", new Coord(10, 64)),

        // Defense (NE Quadrant)
        Map.entry("reinforced_walls", new Coord(86, 44)),
        Map.entry("guard_towers", new Coord(102, 32)),
        Map.entry("healing_aura", new Coord(106, 52)),
        Map.entry("turret_system", new Coord(118, 64)),

        // Commerce (SW Quadrant)
        Map.entry("market_stall", new Coord(42, 84)),
        Map.entry("bulk_trading", new Coord(22, 76)),
        Map.entry("merchant_caravan", new Coord(30, 100)),
        Map.entry("trade_empire", new Coord(50, 114)),

        // Culture (SE Quadrant)
        Map.entry("heritage_monument", new Coord(86, 84)),
        Map.entry("grand_library", new Coord(106, 76)),
        Map.entry("festival_grounds", new Coord(98, 100)),
        Map.entry("cultural_nexus", new Coord(78, 114))
    );

    private GuildUpgradeGraphLayout() {}

    public static ShapeType shapeForBranch(TechTreeBranch branch) {
        if (branch == null) return ShapeType.CORE;
        return switch (branch) {
            case INFRASTRUCTURE -> ShapeType.HEXAGON;
            case DEFENSE -> ShapeType.SHIELD;
            case COMMERCE -> ShapeType.COIN;
            case CULTURE -> ShapeType.DIAMOND;
        };
    }

    public static Map<String, LayoutNode> layout(Collection<TechTreeNode> nodes) {
        Map<String, LayoutNode> result = new LinkedHashMap<>();

        // 1. Inject Synthetic Guild Hearth
        result.put(HEARTH_ID, new LayoutNode(
                HEARTH_ID, "Guild Hearth", null, ShapeType.CORE, 64, 64, null
        ));

        // 2. Map Dynamic Nodes
        for (TechTreeNode node : nodes) {
            Coord c = RADIAL_COORDS.getOrDefault(node.getId(), new Coord(64, 64));
            ShapeType shape = shapeForBranch(node.getBranch());
            result.put(node.getId(), new LayoutNode(
                    node.getId(), node.getName(), node.getBranch(), shape, c.x(), c.y(), node
            ));
        }

        return Collections.unmodifiableMap(result);
    }

    public static List<SplineEdge> edges(Collection<TechTreeNode> nodes) {
        List<SplineEdge> result = new ArrayList<>();
        for (TechTreeNode node : nodes) {
            List<String> prereqs = node.getEffectivePrerequisites();
            if (prereqs.isEmpty()) {
                result.add(new SplineEdge(HEARTH_ID, node.getId()));
            } else {
                for (String p : prereqs) {
                    result.add(new SplineEdge(p, node.getId()));
                }
            }
        }
        return result;
    }

    public static LayoutNode findNodeAt(Map<String, LayoutNode> layout, int x, int y) {
        if (layout == null) return null;
        for (LayoutNode node : layout.values()) {
            if (Math.abs(x - node.x()) <= 6 && Math.abs(y - node.y()) <= 6) {
                return node;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: implement dynamic radial layout engine from TechTreeNode snapshots"
```

---

### Task 2: Pixel Shape Renderers, Splines & Viewport Cutover

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`

**Interfaces:**
- Consumes: `GuildUpgradeGraphLayout.layout()`, `GuildUpgradeGraphLayout.edges()`, `TechTreeService.getAllNodes()`
- Produces: `GuildUpgradeScreen.drawPixelShape()`, `GuildUpgradeScreen.drawSteppedSpline()`, clean removal of legacy lane-grid methods

- [ ] **Step 1: Refactor `GuildUpgradeScreen` to consume dynamic layout nodes and paint native 128x128 pixel shapes**

In `GuildUpgradeScreen.java`:
- Replace old `slots`, `laneCells`, `panX`, `panY` with `Map<String, GuildUpgradeGraphLayout.LayoutNode> layoutNodes`.
- Implement `drawPixelCore`, `drawPixelHex`, `drawPixelShield`, `drawPixelCoin`, `drawPixelDiamond`.
- Implement direct straight one-pixel Bresenham edges between node centers with state-specific solid/dashed rendering; do not bend or curve prerequisite links.
- Render minimal top bar `VALHALLA • LVL X • Y TP` without button or legend clutter.

- [ ] **Step 2: Compile and verify clean cutover**

Run: `./gradlew :guilds-paper:compileJava`
Expected: BUILD SUCCESSFUL (zero references to legacy `slots`/`laneCells`/`origin`).

- [ ] **Step 3: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: wire radial layout to GuildUpgradeScreen pixel renderers"
```

---

### Task 3: In-Map Modal Inspector & Interactive Action Dispatch

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`

**Interfaces:**
- Consumes: `GuildProjectService`, `GuildLevelService`
- Produces: `GuildUpgradeScreen.paintModal()`, `GuildUpgradeScreen.handleClick()`

- [ ] **Step 1: Implement in-map modal overlay rendering and click hit-testing**

In `GuildUpgradeScreen.java`:
- Render $108 \times 100\text{px}$ in-map slate overlay when a node is selected.
- Display node name, description, effect bonus, prerequisite status, and action button.
- Dispatch `projectService.startProject()` / `clearActiveProject()` on click and repaint.

- [ ] **Step 2: Run test suite**

Run: `./gradlew :guilds-paper:test`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 3: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: implement in-map modal inspector and research actions"
```

---

### Task 4: Full Suite Integration & Verification

**Files:**
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/commands/GuildTopCommandTest.java`

- [ ] **Step 1: Run full plugin test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 2: Commit final verification**

```bash
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit --allow-empty -m "chore: verify full test suite on dynamic radial upgrade web"
```

---

### Task 5: Pixel Shape and Straight Edge Refinement

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`

**Interfaces:**
- Consumes: `GuildUpgradeGraphLayout.LayoutNode`, `GuildUpgradeGraphLayout.SplineEdge`, dynamic `TechTreeNode` snapshots
- Produces: clear Minecraft-readable pixel node glyphs and straight prerequisite edges on the native 128x128 surface

- [ ] **Step 1: Replace curved splines with straight pixel edges**

In `GuildUpgradeScreen.java`, remove cubic Bezier control-point and spline-point helpers. Draw every prerequisite link with a direct one-pixel Bresenham line from source center to target center. Keep the web topology unchanged so every existing cross-discipline edge remains visible.

Render edge state independently from node shape:
- mastered target: solid emerald `EDGE_MASTERED`
- active or available target from an unlocked source: dashed amber `EDGE_FRONTIER`
- otherwise: dim slate `EDGE_LOCKED`

Animate a small square spark by linear interpolation along mastered/frontier edges only.

- [ ] **Step 2: Replace shape patterns with readable Minecraft pixel glyphs**

In `GuildUpgradeScreen.java`, use hard-edged 1px raster patterns with a dark outline, colored face, one-pixel highlight, and a distinct silhouette:
- `CORE`: gold lodestone/hearth diamond with white center spark
- `HEXAGON`: cyan chiseled hexagonal stone
- `SHIELD`: rose iron shield with pointed lower edge
- `COIN`: amber emerald/gold octagonal coin with inset center mark
- `DIAMOND`: violet amethyst diamond with a highlight corner

Keep progression state orthogonal to category shape:
- mastered: dark green face + bright green border
- active: dark amber face + amber pulse outline
- available: dark face + category-colored border
- locked: near-black face + muted slate border

Do not add sector labels, filters, bottom legends, or a top-right upgrade button.

- [ ] **Step 3: Add pure renderer/layout assertions**

Extend `GuildUpgradeGraphLayoutTest.java` with assertions that all configured cross-discipline edges remain in `edges(nodes)` and all fixed 16-node positions remain distinct from the hearth. Keep the fixture constructor as `new TechTreeNode(id)` followed by real setters.

- [ ] **Step 4: Run focused verification**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS when the repository dependency and Java toolchain are available; otherwise record the exact dependency/toolchain blocker.

- [ ] **Step 5: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: refine Minecraft pixel nodes and straight tech web edges"
```

---

### Task 6: Finalize Modal Action State and Bounds

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`

**Interfaces:**
- Consumes: current `activeProjectId`, `GuildProjectService.getActiveProjectId(Guild)`
- Produces: modal action bounds at `y=98..110` and safe single-active-project action state

- [ ] **Step 1: Align modal action rectangle and hitbox**

Set `ACTION_Y` to `98` and keep `ACTION_HEIGHT` at `12`, so the painted action rectangle and `withinInclusive` click test cover the specified `y=98..110` range. Preserve the prerequisite rows ending at `y=74`.

- [ ] **Step 2: Disable starts while another project is active**

Update `actionAvailable(TechTreeNode node)` to return false when `activeProjectId` is non-null and differs from `node.getId()`. Render a specific non-action label such as `[PROJECT ACTIVE]` for this case rather than `[START]` or `[LOCKED PREREQS]`; do not call `startProject` for a second project.

- [ ] **Step 3: Run focused verification and commit**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS when repository dependency credentials/toolchain are available; otherwise record the exact blocker.

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "fix: align modal action bounds and guard active project state"
```

---

### Task 7: Harden Dynamic Layout Bounds and Invalid Branches

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`

**Interfaces:**
- Consumes: dynamic `TechTreeNode` snapshots and `TechTreeBranch` values
- Produces: fallback centers within the accepted viewport margins and a non-Hearth shape for null/invalid branches

- [ ] **Step 1: Constrain fallback coordinates**

Change all fallback candidate regions and the global scan to centers in `x=10..118` and `y=22..114`. Preserve the ±6 hitbox non-overlap guarantee and explicit capacity failure.

- [ ] **Step 2: Reserve CORE for the synthetic Hearth**

Add a non-core invalid/unknown shape (or explicitly reject malformed nodes) so `shapeForBranch(null)` cannot return `CORE`. Ensure `GuildUpgradeScreen.isUnlocked` and the Hearth style check by `GuildUpgradeGraphLayout.HEARTH_ID`, not by shape alone. Add a locked renderer case for the invalid shape if one is introduced.

- [ ] **Step 3: Add boundary and malformed-branch tests**

Use `new TechTreeNode(id)` plus setters. Assert fallback centers stay within `x=10..118, y=22..114`, and a null-branch node is not assigned `CORE` or treated as unlocked. Keep all fixed YAML IDs and existing edge tests.

- [ ] **Step 4: Run focused verification and commit**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS when credentials/toolchain are available; otherwise record the exact blocker.

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "fix: constrain dynamic layout bounds and reserve Hearth shape"
```

---

### Task 8: Make Active Project Clearing Conditional

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/services/GuildProjectService.java`
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/GuildProjectServiceImpl.java`
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/services/GuildProjectServiceImplTest.java`

**Interfaces:**
- Produces: `GuildProjectService.clearActiveProject(Guild guild, String expectedProjectId)` with an atomic expected-ID check

- [ ] **Step 1: Add the conditional clear contract and transaction guard**

Add an overload `boolean clearActiveProject(Guild guild, String expectedProjectId)`. Its implementation must select the current active ID under the existing transaction lock, return `false` when it differs from `expectedProjectId`, and only execute the clear update when they match. Keep the existing one-argument method for existing command compatibility.

- [ ] **Step 2: Use fresh state and conditional clear in the upgrade modal**

In `GuildUpgradeScreen.dispatchAction`, if the fresh active ID differs from the cached screen ID, refresh and return without dispatching. For a selected active node whose IDs still match, call `clearActiveProject(actionGuild, selectedId)`, never the unconditional overload. A concurrent replacement therefore fails safely instead of clearing the replacement; an externally cleared node does not restart from a stale clear click.

- [ ] **Step 3: Add service regression coverage**

Extend `GuildProjectServiceImplTest` with an expected-ID mismatch case that leaves the active project intact and does not execute the clear update, plus a matching-ID case that clears successfully. Use the existing transaction harness and JUnit assertions.

- [ ] **Step 4: Run focused service/UI verification and commit**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.services.GuildProjectServiceImplTest" --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS when credentials/toolchain are available; otherwise record the exact blocker.

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/services/GuildProjectService.java guilds-paper/src/main/java/org/aincraft/guilds/services/impl/GuildProjectServiceImpl.java guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java guilds-paper/src/test/java/org/aincraft/guilds/services/GuildProjectServiceImplTest.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "fix: make active project clearing conditional and atomic"
```
