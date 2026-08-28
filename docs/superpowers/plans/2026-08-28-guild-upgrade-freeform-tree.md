# Guild Upgrade Freeform Radial Tech Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a $360^\circ$ organic radial tech web on the native $128 \times 128$ MapGUI screen for `/g upgrade`, featuring pixel geometric shapes (Hexagon, Shield, Coin, Diamond), stepped raster splines, and an in-map modal inspector.

**Architecture:** Replace the 4-column lane grid in `GuildUpgradeGraphLayout` with a single connected radial DAG originating from a central `guild_hearth` lodestone node at $(64, 64)$ with 16 perks radiating outward across 3 concentric rings. `GuildUpgradeScreen` renders custom pixel shapes, stepped Bezier splines, traveling energy sparks, and in-map detail modal overlays on a native $128 \times 128$ pixel buffer.

**Tech Stack:** Java 21, Paper 1.21.x, MapGUI 2.0.0 (`de.flog99.mapgui`), JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-28-guild-upgrade-freeform-tree-design.md`

## Global Constraints

- **Viewport Budget:** All rendering and interaction must fit strictly within a native $128 \times 128$ pixel MapGUI buffer.
- **Topology:** 1 Central Guild Hearth root $(64, 64)$ + 16 Tech Nodes = 17 total nodes forming a single connected DAG with zero disconnected components.
- **Discipline Geometries:** ⬡ Hexagon (`INFRASTRUCTURE`), 🛡 Shield (`DEFENSE`), 🪙 Coin (`COMMERCE`), ✦ Diamond (`CULTURE`).
- **Hit Targets:** Generous $\pm 6\text{px}$ hitboxes for all nodes.
- **Main-Thread Discipline:** All `Screen.paint()` logic must be pure raster rendering from cached snapshot data with zero main-thread database queries.

---

### Task 1: Pure Radial Graph Geometry & Coordinates

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`

**Interfaces:**
- Produces: `GuildUpgradeGraphLayout.RadialNode`, `GuildUpgradeGraphLayout.getRadialNodes()`, `GuildUpgradeGraphLayout.findNodeAt(int x, int y)`, `GuildUpgradeGraphLayout.edges()`

- [ ] **Step 1: Write the failing unit tests for radial geometry and reachability**

In `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`:

```java
package org.aincraft.guilds.gui;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;

class GuildUpgradeGraphLayoutTest {

    @Test
    void allNodesFitWithin128x128Bounds() {
        Map<String, GuildUpgradeGraphLayout.RadialNode> nodes = GuildUpgradeGraphLayout.radialNodes();
        assertThat(nodes).hasSize(17); // 1 Hearth + 16 Tech perks

        for (GuildUpgradeGraphLayout.RadialNode node : nodes.values()) {
            assertThat(node.x()).isBetween(8, 120);
            assertThat(node.y()).isBetween(14, 118);
        }
    }

    @Test
    void allNodesReachableFromGuildHearth() {
        Map<String, GuildUpgradeGraphLayout.RadialNode> nodes = GuildUpgradeGraphLayout.radialNodes();
        Set<String> visited = new HashSet<>();
        visited.add("guild_hearth");

        boolean progress = true;
        while (progress) {
            progress = false;
            for (GuildUpgradeGraphLayout.RadialNode n : nodes.values()) {
                if (!visited.contains(n.id()) && visited.containsAll(n.prereqs())) {
                    visited.add(n.id());
                    progress = true;
                }
            }
        }

        assertThat(visited).containsExactlyInAnyOrderElementsOf(nodes.keySet());
    }

    @Test
    void findNodeAtResolvesGenerousHitbox() {
        GuildUpgradeGraphLayout.RadialNode hearth = GuildUpgradeGraphLayout.findNodeAt(64, 64);
        assertThat(hearth).isNotNull();
        assertThat(hearth.id()).isEqualTo("guild_hearth");

        // Test boundary of 6px hit radius
        GuildUpgradeGraphLayout.RadialNode nearHearth = GuildUpgradeGraphLayout.findNodeAt(69, 64);
        assertThat(nearHearth).isNotNull();
        assertThat(nearHearth.id()).isEqualTo("guild_hearth");

        GuildUpgradeGraphLayout.RadialNode miss = GuildUpgradeGraphLayout.findNodeAt(64, 75);
        assertThat(miss).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: FAIL (compilation errors / missing radial methods).

- [ ] **Step 3: Implement Radial Coordinates and Hitbox Geometry in `GuildUpgradeGraphLayout`**

In `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java`:

```java
package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuildUpgradeGraphLayout {

    public enum ShapeType {
        CORE, HEXAGON, SHIELD, COIN, DIAMOND
    }

    public record RadialNode(
            String id,
            String name,
            TechTreeBranch branch,
            ShapeType shape,
            int cost,
            int x,
            int y,
            List<String> prereqs,
            String lore,
            String effect
    ) {}

    public record SplineEdge(String fromId, String toId) {}

    private static final Map<String, RadialNode> RADIAL_NODES;

    static {
        Map<String, RadialNode> m = new LinkedHashMap<>();

        // 0. Central Lodestone Apex Root (Guild Hearth)
        m.put("guild_hearth", new RadialNode(
                "guild_hearth", "Guild Hearth", null, ShapeType.CORE, 0, 64, 64,
                List.of(), "Sacred lodestone anchor", "Conduits active"));

        // Infrastructure (⬡ Hexagon)
        m.put("better_storage", new RadialNode(
                "better_storage", "Better Storage", TechTreeBranch.INFRASTRUCTURE, ShapeType.HEXAGON, 2, 42, 44,
                List.of("guild_hearth"), "Bank capacity +50%", "Storage +50%"));
        m.put("fast_travel", new RadialNode(
                "fast_travel", "Fast Travel", TechTreeBranch.INFRASTRUCTURE, ShapeType.HEXAGON, 3, 26, 32,
                List.of("better_storage"), "Spawn warp cooldown -50%", "Cooldown -50%"));
        m.put("advanced_farming", new RadialNode(
                "advanced_farming", "Advanced Farming", TechTreeBranch.INFRASTRUCTURE, ShapeType.HEXAGON, 3, 22, 52,
                List.of("better_storage"), "Farm crop yield +25%", "Crops +25%"));
        m.put("auto_sorter", new RadialNode(
                "auto_sorter", "Auto Sorter", TechTreeBranch.INFRASTRUCTURE, ShapeType.HEXAGON, 5, 10, 64,
                List.of("fast_travel", "advanced_farming"), "Automatic chest sorting", "Auto-sort chest"));

        // Defense (🛡 Shield)
        m.put("reinforced_walls", new RadialNode(
                "reinforced_walls", "Reinforced Walls", TechTreeBranch.DEFENSE, ShapeType.SHIELD, 2, 86, 44,
                List.of("guild_hearth"), "TNT damage -25% in claim", "TNT resist +25%"));
        m.put("guard_towers", new RadialNode(
                "guard_towers", "Guard Towers", TechTreeBranch.DEFENSE, ShapeType.SHIELD, 3, 102, 32,
                List.of("reinforced_walls"), "Arrow damage +15% in claim", "Arrows +15%"));
        m.put("healing_aura", new RadialNode(
                "healing_aura", "Healing Aura", TechTreeBranch.DEFENSE, ShapeType.SHIELD, 4, 106, 52,
                List.of("reinforced_walls"), "Regeneration I in claim", "Regen I"));
        m.put("turret_system", new RadialNode(
                "turret_system", "Turret System", TechTreeBranch.DEFENSE, ShapeType.SHIELD, 6, 118, 64,
                List.of("guard_towers", "healing_aura"), "Automated crossbow turrets", "Border turrets"));

        // Commerce (🪙 Coin)
        m.put("market_stall", new RadialNode(
                "market_stall", "Market Stall", TechTreeBranch.COMMERCE, ShapeType.COIN, 2, 42, 84,
                List.of("guild_hearth"), "Merchant trade tax -50%", "Tax -50%"));
        m.put("bulk_trading", new RadialNode(
                "bulk_trading", "Bulk Trading", TechTreeBranch.COMMERCE, ShapeType.COIN, 3, 22, 76,
                List.of("market_stall"), "Trade batch size 4x", "Batch size 4x"));
        m.put("merchant_caravan", new RadialNode(
                "merchant_caravan", "Merchant Caravan", TechTreeBranch.COMMERCE, ShapeType.COIN, 4, 30, 100,
                List.of("market_stall"), "Treasury +500g daily", "Treasury +500g"));
        m.put("trade_empire", new RadialNode(
                "trade_empire", "Trade Empire", TechTreeBranch.COMMERCE, ShapeType.COIN, 6, 50, 114,
                List.of("bulk_trading", "merchant_caravan"), "0% alliance trade tax", "0% alliance tax"));

        // Culture (✦ Diamond)
        m.put("heritage_monument", new RadialNode(
                "heritage_monument", "Heritage Monument", TechTreeBranch.CULTURE, ShapeType.DIAMOND, 2, 86, 84,
                List.of("guild_hearth"), "Guild XP gain +20%", "Guild XP +20%"));
        m.put("grand_library", new RadialNode(
                "grand_library", "Grand Library", TechTreeBranch.CULTURE, ShapeType.DIAMOND, 3, 106, 76,
                List.of("heritage_monument"), "Enchant/repair cost -30%", "Enchants -30%"));
        m.put("festival_grounds", new RadialNode(
                "festival_grounds", "Festival Grounds", TechTreeBranch.CULTURE, ShapeType.DIAMOND, 4, 98, 100,
                List.of("heritage_monument"), "Weekly event buffs", "Weekly buffs"));
        m.put("cultural_nexus", new RadialNode(
                "cultural_nexus", "Cultural Nexus", TechTreeBranch.CULTURE, ShapeType.DIAMOND, 6, 78, 114,
                List.of("grand_library", "festival_grounds"), "Resident cap +10 members", "Cap +10"));

        RADIAL_NODES = Collections.unmodifiableMap(m);
    }

    private GuildUpgradeGraphLayout() {}

    public static Map<String, RadialNode> radialNodes() {
        return RADIAL_NODES;
    }

    public static RadialNode getNode(String id) {
        return RADIAL_NODES.get(id);
    }

    public static RadialNode findNodeAt(int x, int y) {
        for (RadialNode node : RADIAL_NODES.values()) {
            if (Math.abs(x - node.x()) <= 6 && Math.abs(y - node.y()) <= 6) {
                return node;
            }
        }
        return null;
    }

    public static List<SplineEdge> edges() {
        List<SplineEdge> result = new ArrayList<>();
        for (RadialNode node : RADIAL_NODES.values()) {
            for (String prereq : node.prereqs()) {
                if (RADIAL_NODES.containsKey(prereq)) {
                    result.add(new SplineEdge(prereq, node.id()));
                }
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :guilds-paper:test --tests "org.aincraft.guilds.gui.GuildUpgradeGraphLayoutTest"`
Expected: PASS (all 3 tests pass).

- [ ] **Step 5: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayout.java guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: implement 360-degree radial tech web layout and geometry"
```

---

### Task 2: Pixel Shape Rasterizers & Stepped Bezier Splines

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`

**Interfaces:**
- Consumes: `GuildUpgradeGraphLayout.radialNodes()`, `GuildUpgradeGraphLayout.edges()`
- Produces: `GuildUpgradeScreen.drawPixelShape()`, `GuildUpgradeScreen.drawSteppedSpline()`

- [ ] **Step 1: Implement the shape rasterizers and spline painter in `GuildUpgradeScreen`**

Add rasterization helper methods to `GuildUpgradeScreen.java`:

```java
private void drawPixelCore(Painter p, int x, int y, Color fill, Color border) {
    p.fill(new Rect(x - 3, y - 3, 7, 7), fill);
    p.fill(new Rect(x - 2, y - 4, 5, 1), border);
    p.fill(new Rect(x - 2, y + 4, 5, 1), border);
    p.fill(new Rect(x - 4, y - 2, 1, 5), border);
    p.fill(new Rect(x + 4, y - 2, 1, 5), border);
    p.fill(new Rect(x - 3, y - 3, 1, 1), border);
    p.fill(new Rect(x + 3, y - 3, 1, 1), border);
    p.fill(new Rect(x - 3, y + 3, 1, 1), border);
    p.fill(new Rect(x + 3, y + 3, 1, 1), border);
    p.fill(new Rect(x - 1, y - 1, 3, 3), Color.WHITE);
}

private void drawPixelHex(Painter p, int x, int y, Color fill, Color border) {
    p.fill(new Rect(x - 2, y - 3, 5, 7), fill);
    p.fill(new Rect(x - 3, y - 2, 7, 5), fill);
    p.fill(new Rect(x - 2, y - 4, 5, 1), border);
    p.fill(new Rect(x - 2, y + 4, 5, 1), border);
    p.fill(new Rect(x - 3, y - 3, 1, 1), border);
    p.fill(new Rect(x + 3, y - 3, 1, 1), border);
    p.fill(new Rect(x - 3, y + 3, 1, 1), border);
    p.fill(new Rect(x + 3, y + 3, 1, 1), border);
    p.fill(new Rect(x - 4, y - 2, 1, 5), border);
    p.fill(new Rect(x + 4, y - 2, 1, 5), border);
}

private void drawPixelShield(Painter p, int x, int y, Color fill, Color border) {
    p.fill(new Rect(x - 3, y - 3, 7, 5), fill);
    p.fill(new Rect(x - 2, y + 2, 5, 2), fill);
    p.fill(new Rect(x - 1, y + 4, 3, 1), fill);
    p.fill(new Rect(x, y + 5, 1, 1), fill);
    p.fill(new Rect(x - 3, y - 4, 7, 1), border);
    p.fill(new Rect(x - 4, y - 3, 1, 5), border);
    p.fill(new Rect(x + 4, y - 3, 1, 5), border);
    p.fill(new Rect(x - 3, y + 2, 1, 1), border);
    p.fill(new Rect(x + 3, y + 2, 1, 1), border);
    p.fill(new Rect(x - 2, y + 3, 1, 2), border);
    p.fill(new Rect(x + 2, y + 3, 1, 2), border);
    p.fill(new Rect(x - 1, y + 5, 1, 1), border);
    p.fill(new Rect(x + 1, y + 5, 1, 1), border);
    p.fill(new Rect(x, y + 6, 1, 1), border);
}

private void drawPixelCoin(Painter p, int x, int y, Color fill, Color border) {
    p.fill(new Rect(x - 2, y - 3, 5, 7), fill);
    p.fill(new Rect(x - 3, y - 2, 7, 5), fill);
    p.fill(new Rect(x - 2, y - 4, 5, 1), border);
    p.fill(new Rect(x - 2, y + 4, 5, 1), border);
    p.fill(new Rect(x - 4, y - 2, 1, 5), border);
    p.fill(new Rect(x + 4, y - 2, 1, 5), border);
    p.fill(new Rect(x - 3, y - 3, 1, 1), border);
    p.fill(new Rect(x + 3, y - 3, 1, 1), border);
    p.fill(new Rect(x - 3, y + 3, 1, 1), border);
    p.fill(new Rect(x + 3, y + 3, 1, 1), border);
}

private void drawPixelDiamond(Painter p, int x, int y, Color fill, Color border) {
    p.fill(new Rect(x - 1, y - 3, 3, 7), fill);
    p.fill(new Rect(x - 3, y - 1, 7, 3), fill);
    p.fill(new Rect(x - 2, y - 2, 5, 5), fill);
    p.fill(new Rect(x, y - 4, 1, 1), border);
    p.fill(new Rect(x, y + 4, 1, 1), border);
    p.fill(new Rect(x - 4, y, 1, 1), border);
    p.fill(new Rect(x + 4, y, 1, 1), border);
    p.fill(new Rect(x - 1, y - 3, 1, 1), border);
    p.fill(new Rect(x + 1, y - 3, 1, 1), border);
    p.fill(new Rect(x - 2, y - 2, 1, 1), border);
    p.fill(new Rect(x + 2, y - 2, 1, 1), border);
    p.fill(new Rect(x - 3, y - 1, 1, 1), border);
    p.fill(new Rect(x + 3, y - 1, 1, 1), border);
    p.fill(new Rect(x - 1, y + 3, 1, 1), border);
    p.fill(new Rect(x + 1, y + 3, 1, 1), border);
    p.fill(new Rect(x - 2, y + 2, 1, 1), border);
    p.fill(new Rect(x + 2, y + 2, 1, 1), border);
    p.fill(new Rect(x - 3, y + 1, 1, 1), border);
    p.fill(new Rect(x + 3, y + 1, 1, 1), border);
}

private void drawSteppedSpline(Painter p, int x0, int y0, int x1, int y1, Color color, boolean isDashed) {
    int dx = x1 - x0;
    int dy = y1 - y0;
    int cx1 = (int) Math.round(x0 + dx * 0.45 - dy * 0.15);
    int cy1 = (int) Math.round(y0 + dy * 0.45 + dx * 0.15);
    int cx2 = (int) Math.round(x0 + dx * 0.55 + dy * 0.15);
    int cy2 = (int) Math.round(y0 + dy * 0.55 - dx * 0.15);

    int steps = Math.max(12, (int) Math.round(Math.hypot(dx, dy)));
    for (int i = 0; i <= steps; i++) {
        if (isDashed && (i / 2) % 2 == 1) continue;
        double t = (double) i / steps;
        double invT = 1.0 - t;
        int px = (int) Math.round(Math.pow(invT, 3) * x0 + 3 * Math.pow(invT, 2) * t * cx1 + 3 * invT * Math.pow(t, 2) * cx2 + Math.pow(t, 3) * x1);
        int py = (int) Math.round(Math.pow(invT, 3) * y0 + 3 * Math.pow(invT, 2) * t * cy1 + 3 * invT * Math.pow(t, 2) * cy2 + Math.pow(t, 3) * y1);
        if (px >= 0 && px < 128 && py >= 0 && py < 128) {
            p.fill(new Rect(px, py, 1, 1), color);
        }
    }
}
```

- [ ] **Step 2: Build and run existing tests to verify compilation**

Run: `./gradlew :guilds-paper:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: add pixel shape rasterizers and stepped bezier splines"
```

---

### Task 3: In-Map Modal Inspector & State Machine

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java`

**Interfaces:**
- Produces: `GuildUpgradeScreen.paintModal()`, `GuildUpgradeScreen.handleModalClick()`

- [ ] **Step 1: Implement In-Map Modal Overlay Rendering & Click Handlers**

In `GuildUpgradeScreen.java`:

```java
private GuildUpgradeGraphLayout.RadialNode selectedNode = null;
private boolean modalOpen = false;

private void paintModal(Painter p, Rect bounds) {
    if (!modalOpen || selectedNode == null) return;

    // Dim backdrop
    p.fill(bounds, new Color(8, 10, 16, 220));

    // Modal slate (108x100)
    p.fill(new Rect(10, 14, 108, 100), new Color(26, 30, 43));
    p.fill(new Rect(10, 14, 108, 1), new Color(52, 61, 82));
    p.fill(new Rect(10, 113, 108, 1), new Color(52, 61, 82));
    p.fill(new Rect(10, 14, 1, 100), new Color(52, 61, 82));
    p.fill(new Rect(117, 14, 1, 100), new Color(52, 61, 82));

    // Title & Close Button
    p.textLine(26, 24, selectedNode.name().toUpperCase(), Color.WHITE, false);
    p.fill(new Rect(106, 18, 9, 8), new Color(239, 68, 68));
    p.textLine(108, 24, "X", Color.WHITE, false);

    // Lore & Effects
    p.textLine(14, 40, selectedNode.lore(), new Color(148, 163, 184), false);
    p.textLine(14, 60, "EFFECT: " + selectedNode.effect(), new Color(56, 189, 248), false);

    // Action Button
    boolean isUnlocked = isUnlocked(selectedNode.id());
    boolean isActive = selectedNode.id().equals(activeProjectId);
    boolean isAvail = canUnlock(selectedNode);

    if (isUnlocked) {
        p.fill(new Rect(14, 98, 100, 12), new Color(20, 83, 45));
        p.textLine(44, 106, "MASTERED", new Color(34, 197, 94), false);
    } else if (isActive) {
        p.fill(new Rect(14, 98, 100, 12), new Color(127, 29, 29));
        p.textLine(38, 106, "CLEAR ACTIVE", new Color(252, 165, 165), false);
    } else if (isAvail) {
        int cost = selectedNode.cost();
        int tp = viewerGuild != null ? viewerGuild.getTechPoints() : 0;
        if (tp >= cost) {
            p.fill(new Rect(14, 98, 100, 12), new Color(217, 119, 6));
            p.textLine(38, 106, "START (" + cost + " TP)", Color.WHITE, false);
        } else {
            p.fill(new Rect(14, 98, 100, 12), new Color(51, 65, 85));
            p.textLine(40, 106, "NEED " + cost + " TP", new Color(100, 116, 139), false);
        }
    } else {
        p.fill(new Rect(14, 98, 100, 12), new Color(30, 41, 59));
        p.textLine(30, 106, "LOCKED PREREQS", new Color(71, 85, 105), false);
    }
}
```

- [ ] **Step 2: Connect MapGUI Click Events to Modal & Action Dispatch**

In `GuildUpgradeScreen.java`:

```java
private void handleClick(int x, int y) {
    if (modalOpen && selectedNode != null) {
        // Close Button (106..115, 18..26)
        if (x >= 106 && x <= 115 && y >= 18 && y <= 26) {
            modalOpen = false;
            return;
        }

        // Action Button (14..114, 98..110)
        if (x >= 14 && x <= 114 && y >= 98 && y <= 110) {
            if (selectedNode.id().equals(activeProjectId)) {
                if (viewerGuild != null) {
                    projectService.clearActiveProject(viewerGuild);
                    refresh(player());
                }
            } else if (canUnlock(selectedNode) && viewerGuild != null && viewerGuild.getTechPoints() >= selectedNode.cost()) {
                projectService.startProject(viewerGuild, selectedNode.id());
                refresh(player());
            }
            return;
        }

        // Click outside closes modal
        if (x < 10 || x > 118 || y < 14 || y > 114) {
            modalOpen = false;
        }
        return;
    }

    GuildUpgradeGraphLayout.RadialNode clicked = GuildUpgradeGraphLayout.findNodeAt(x, y);
    if (clicked != null) {
        selectedNode = clicked;
        modalOpen = true;
    }
}
```

- [ ] **Step 3: Run build and tests**

Run: `./gradlew :guilds-paper:test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit -m "feat: add in-map modal inspector and research click actions"
```

---

### Task 4: Full Suite Smoke & Visual Verification

**Files:**
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/gui/GuildUpgradeGraphLayoutTest.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/commands/MapCommandTest.java`

- [ ] **Step 1: Run full plugin test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (all unit and integration tests pass).

- [ ] **Step 2: Commit final verification**

```bash
git -c user.name="mintychochip" -c user.email="mintychochip@users.noreply.github.com" commit --allow-empty -m "chore: verify full test suite on 128x128 radial upgrade web"
```
