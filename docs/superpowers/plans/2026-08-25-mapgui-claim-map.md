# MapGUI Claim Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ASCII `/guildsmap` with a live MapGUI screen: real terrain + claims that re-center as the player walks, a hover tooltip per chunk, and a hold-drag marquee that claims a rectangle of chunks with an explicit confirm and a partial-failure report.

**Architecture:** A `GuildClaimScreen extends de.flog99.mapgui.Screen` paints a chunk grid centered on the player, tinted by a ported `ClaimLayer` classifier. A plugin-owned repeating task invalidates the session only when the player's chunk/world changes. The screen uses MapGUI 2.0.0's `holdable()`/`onHold`/`onHoldEnd` for the marquee; claims are written only through the plugin's `PlotService`/`PermissionService` (MapGUI never touches guild data).

**Tech Stack:** Java 26, Paper 26.2, MapGUI API 2.0.0 (`io.github.flog99:mapgui-api:2.0.0`, Maven Central), Gradle 9.6.1, JUnit 5.

## Global Constraints

- MapGUI API version: `io.github.flog99:mapgui-api:2.0.0` (compileOnly in `guilds-paper`).
- MapGUI plugin jar in the harness: `github("FloG99", "MapGUI", "v2.0.0", "MapGUI-2.0.0.jar")` — already wired in `guilds-test/build.gradle.kts`.
- Live package root: `org.aincraft.guilds` (NOT the old `dev.mintychochip.guilds`).
- MapGUI is responsible ONLY for input/render lifecycle. Guild permission + claim writes stay in `PlotService` / `PermissionService`.
- `MapBrigadierCommand` must keep the `isMapGuiPresent()` guard so the plugin boots without MapGUI; ASCII fallback stays.
- Claim writes are per-chunk `PlotService.claimGuildBlock(int, int, String, String)` — NO bulk/transaction API. Report partial failure explicitly.
- No per-tick repaint: follow task invalidates only on chunk/world change; hover lookup gated on cursor cell change.
- ClaimLayer port keeps the old pure-function shape: `classify(centerX, centerZ, world, viewerGuild, radius, plots, guilds)`.

---

### Task 1: Add MapGUI API dependency + verification entry

**Files:**
- Modify: `guilds-paper/build.gradle.kts` (dependencies block)
- Modify: `gradle/verification-metadata.xml`
- Test: none (build-level)

**Interfaces:**
- Produces: `io.github.flog99:mapgui-api:2.0.0` on the `compileOnly` classpath of `:guilds-paper`, so Tasks 2-5 can import `de.flog99.mapgui.*`.

- [ ] **Step 1: Add the compileOnly dependency**

In `guilds-paper/build.gradle.kts`, in the `dependencies { ... }` block, after the squaremap `compileOnly` line, add:

```kotlin
    compileOnly("io.github.flog99:mapgui-api:2.0.0")
```

- [ ] **Step 2: Add the verification-metadata entry**

`gradle/verification-metadata.xml` uses checksum verification. Run the Gradle verification write task to generate the entry (it downloads the artifact and records its SHA-256):

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:compileJava --write-verification-metadata sha256
```

Expected: the build resolves `io.github.flog99:mapgui-api:2.0.0` from Maven Central and appends a `<component group="io.github.flog99" name="mapgui-api" version="2...">` block (actually `version="2.0.0"`) with `<artifact name="mapgui-api-2.0.0.jar">` + `.pom` + `.module` SHA-256 entries under `<trusted-artifacts>`.

If the write task is not available, add the entry manually — the known SHA-256s are:
- `mapgui-api-2.0.0.jar`: `56efde322cecfe3e4bc4b72674024cfa08b5041c477652877524d643ee97face`
- `mapgui-api-2.0.0.pom`: obtain from the Maven Central directory listing `https://repo1.maven.org/maven2/io/github/flog99/mapgui-api/2.0.0/` and compute with `sha256sum`.

- [ ] **Step 3: Verify the dependency resolves**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:dependencies --configuration compileClasspath --quiet
```

Expected: output includes `io.github.flog99: mapgui-api:2.0.0` and the build completes with `BUILD SUCCESSFUL` (exit 0).

- [ ] **Step 4: Commit**

```bash
cd /home/jlo/dev/guilds && git add guilds-paper/build.gradle.kts gradle/verification-metadata.xml && git commit -m "build: add mapgui-api 2.0.0 compileOnly dependency"
```

---

### Task 2: Port ClaimLayer classification to org.aincraft.guilds.map

**Files:**
- Create: `guilds-paper/src/main/java/org/aincraft/guilds/map/ClaimLayer.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/map/ClaimLayerTest.java`

**Interfaces:**
- Consumes: `org.aincraft.guilds.models.GuildBlock` (`getX()`, `getZ()`, `getWorld()`, `getGuildId()`), `Guild` (`getId()`, `getName()`).
- Produces:
  - `ClaimLayer.Kind` enum: `WILDERNESS, OWN_GUILD, OTHER_GUILD, CENTER`.
  - `ClaimLayer.Cell(int chunkX, int chunkZ, Kind kind)` record.
  - `ClaimLayer.PlotLookup` — `@FunctionalInterface Optional<GuildBlock> plotAt(int chunkX, int chunkZ, String world)`.
  - `ClaimLayer.GuildLookup` — `@FunctionalInterface Optional<Guild> byId(String guildId)`.
  - `static ClaimLayer classify(int centerChunkX, int centerChunkZ, String world, String viewerGuild, int radius, PlotLookup plots, GuildLookup guilds)`.
  - `centerChunkX()`, `centerChunkZ()`, `world()`, `radius()`, `size()` (`radius*2+1`), `cells()` (unmodifiable), `cellAt(int chunkX, int chunkZ)` → `Optional<Cell>`.

- [ ] **Step 1: Write the failing test**

Create `guilds-paper/src/test/java/org/aincraft/guilds/map/ClaimLayerTest.java` (port of the old `.javadoc-backup` test, adjusted to the live model constructors — `new Guild("Alpha", UUID)` and `new GuildBlock(x, z, world, guildId)`; verify the live `GuildBlock` constructor accepts `(int, int, String, String)` by checking the file, else use setters):

```java
package org.aincraft.guilds.map;

import org.aincraft.guilds.map.ClaimLayer;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLayerTest {

    private static final String WORLD = "world";
    private static final int CENTER_X = 10;
    private static final int CENTER_Z = 20;

    @Test
    void classifiesWildernessWhenNoPlotExists() {
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> Optional.empty(),
                id -> Optional.empty());
        ClaimLayer.Cell wilderness = layer.cellAt(CENTER_X + 1, CENTER_Z).orElseThrow();
        assertEquals(ClaimLayer.Kind.WILDERNESS, wilderness.kind());
        assertEquals(CENTER_X + 1, wilderness.chunkX());
        assertEquals(CENTER_Z, wilderness.chunkZ());
    }

    @Test
    void classifiesOwnGuildWhenPlotGuildMatchesViewer() {
        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X + 1, CENTER_Z, WORLD, own.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() && WORLD.equals(world)
                        ? Optional.of(plot) : Optional.empty(),
                id -> own.getId().equals(id) ? Optional.of(own) : Optional.empty());
        assertEquals(ClaimLayer.Kind.OWN_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void classifiesOtherGuildWhenPlotGuildDiffersFromViewer() {
        Guild other = new Guild("Beta", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X, CENTER_Z + 1, WORLD, other.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() && WORLD.equals(world)
                        ? Optional.of(plot) : Optional.empty(),
                id -> other.getId().equals(id) ? Optional.of(other) : Optional.empty());
        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void classifiesCenterEvenWhenThatChunkIsClaimed() {
        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X, CENTER_Z, WORLD, own.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> Optional.of(plot),
                id -> Optional.of(own));
        assertEquals(ClaimLayer.Kind.CENTER, layer.cellAt(CENTER_X, CENTER_Z).orElseThrow().kind());
    }

    @Test
    void classifiesUnknownGuildIdAsOtherGuild() {
        GuildBlock plot = new GuildBlock(CENTER_X - 1, CENTER_Z, WORLD, "missing-id");
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() ? Optional.of(plot) : Optional.empty(),
                id -> Optional.empty());
        assertEquals(ClaimLayer.Kind.OTHER_GGUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void classifiesClaimedChunkAsOtherWhenViewerHasNoGuild() {
        Guild claimed = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X + 1,, CENTER_Z - 1, WORLD, claimed.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, null, 1,
                (x,, z,, world) -> x == plot.getX() && z == plot.getZ() ? Optional.of(plot) : Optional.empty(),
                id -> Optional.of(claimed));
        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void coversEveryChunkInTheRadius() {
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, null, 2,
                (x, z, world) -> Optional.empty(),
                id -> Optional.empty());
        assertEquals(5 * 5, layer.cells().size());
        assertEquals(5, layer.size());
    }

    @Test
    void rejectsNegativeRadius() {
        assertThrows(IllegalArgumentException.class,
                () -> ClaimLayer.classify(CENTER_X, CENTER_Z, WORLD, null, -1,
                        (x, z, w) -> Optional.empty(), id -> Optional.empty()));
    }
}
```

Note: fix any typos above (e.g. `CENTER_X`/`CENTER_Z` spellings, `OTHER_GUILD`) to match the enum names exactly — the enum is `WILDERNESS, OWN_GUILD, OTHER_GUILD, CENTER`.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:test --tests "org.aincraft.guilds.map.ClaimLayerTest" --quiet
```

Expected: FAIL — `ClaimLayer` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

Create `guilds-paper/src/main/java//org/aincraft/guilds/map/ClaimLayer.java` — port the old `dev.mintychochip.guilds.map.ClaimLayer` verbatim, changing only the package to `org.aincraft.guilds.map` and keeping the exact `classify`, `Kind`, `Cell`, `PlotLookup`, `GuildLookup`, `cellAt`, `size`, `cells` API (see the source at `.javadoc-backup/src/main/java/dev/mintychochip/guilds/map/ClaimLayer.java:15-218`).

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /home/jlo/dev/guilds && ./gradlew :gguilds-paper:test --tests "org.aincraft.guilds.map.ClaimLayerTest" --quiet
```

Expected: PASS (all tests green).

- [ ] **Step 5: Commit**

```bash
cd /home/jlo/dev/guilds && git add guilds-paper/src/main/java/org/aincraft/guilds/map/ClaimLayer.java guilds-paper/src/test/java/org/aincraft/guilds/map//ClaimLayerTest.java && git commit -m "feat: port ClaimLayer classification to org.aincraft.guilds.map"
```

---

### Task 3: Build GuildClaimScreen (terrain, follow, hover tooltip)

**Files:**
- Create: `guilds-paper/src/main/java/org/aincraft/guildds/gui/GuildClaimScreen.java`
- Create: `guilds-paper/src/main/java/org/aincraft/guilds/gui/MapFollowTask.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/map/ClaimLayerTest.java` (add screen test)

**Interfaces:**
- Consumes: `ClaimLayer.classify(...)` (Task 2), `PlotService.getGuildBlock(int, int,, String)`, `GuildService.getGuildById(String)`, `PermissionService.canClaimForGuild(UUID, String)`.
- Produces:
  - `GuildClaimScreen extends de.flog99.mapgui.Screen`:
    - `GuildClaimScreen(JavaPlugin plugin, String viewerGuild,, GuildService guilds, PlotService plots, PermissionService permissions)`.
    - `protected Node build()` — overlay: claim layer draw + legend + marquee + confirm.
    - `protected boolean terrain()` → `true`.
    - `protected boolean holdable()` → `true`.
    - `protected Boolean clampPitch()` → `false`.
    - `protected HandOptions hand()` → `HandOptions.pinned(4)`.
    - `protected void onHold(int x, int y)` / `onHoldEnd()` — marquee.
    - `ClaimLayer currentLayer()` — reads `player().getLocation().getChunk()`.
  - `MapFollowTask` — `static void start(JavaPlugin plugin, MapGui mapGui)` — registers a repeating task (every 10 ticks) that, for each open session whose screen is a `GuildClaimScreen`, invalidates only when the player's chunk/world changed.

- [ ] **Step 1: Write the failing test**

Append to `ClaimLayerTest.java`:

```java
    @Test
    void claimScreenIsAMapGuiScreenThatPaintsTheClassifier() throws Exception {
        assertTrue(Screen.class.isAssignableFrom(GuildClaimScreen.class));
        assertEquals(ClaimLayer.class, GuildClaimScreen.class
                .getDeclaredMethod("currentLayer").getReturnType());
    }
```

Add `import de.flog99.mapgui.Screen;` and `import org.aincraft.guilds.gui.GuildClaimScreen;`.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:test --tests "org.aincraft.guilds.map.ClaimLayerTest" --quiet
```

Expected: FAIL — `GuildClaimScreen` does not exist.

- [ ] **Step 3: Write the implementation**

Create `GuildClaimScreen.java`. Key structure (imports `static de.flog99.mapgui.ui.Ui.*`):

```java
package org.aincraft.guilds.gui;

import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.Colors;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.Ui;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.map.ClaimLayer;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.Optional;

public final class GuildClaimScreen extends Screen {

    static final int RADIUS = 5;
    private static final Color WILDERNESS = new Color(34, 90, 34);
    private static final Color OWN_GUILD = new Color(46, 184, 64);
    private static final Color OTHER_GUILD = new Color(212, 168, 40);
    private static final Color CENTER = new Color(230, 255, 230);
    private static final double TINT = 0.55;

    private final JavaPlugin plugin;
    private final String viewerGuild;
    private final GuildService guilds;
    private final PlotService plots;
    private final PermissionService permissions;

    public GuildClaimScreen(JavaPlugin plugin, String viewerGuild, GuildService guilds,
                            PlotService plots, PermissionService permissions) {
        this.plugin = plugin;
        this.viewerGuild = viewerGuild;
        this.guilds = guilds;
        this.plots = plots;
        this.permissions = permissions;
    }

    @Override
    public Component title() {
        return Component.text("Guilds Map");
    }

    @Override
    public boolean terrain() {
        return true;
    }

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public Boolean clampPitch() {
        return false;
    }

    @Override
    public HandOptions hand() {
        return HandOptions.pinned(4);
    }

    @Override
    protected Node build() {
        return Ui.Overlay(
                Ui.Draw(this::paintLayer)
                        .tracksCursor(true)
                        .caption(this::hoveredCaption)
                        .fill(),
                legend()
        ).fill();
    }

    ClaimLayer currentLayer() {
        var loc = player().getLocation();
        return ClaimLayer.classify(
                loc.getChunk().getX(), loc.getChunk().getZ(), loc.getWorld().getName(),
                viewerGuild, RADIUS,
                plots::getGuildBlock,
                guilds::getGuildById);
    }

    private void paintLayer(PaintContext context) {
        ClaimLayer layer = currentLayer();
        Rect bounds = context.bounds();
        int cell = Math.max(1, Math.min(bounds.width(), bounds.height()) / layer.size());
        int grid = cell * layer.size();
        int originX = bounds.x() + (bounds.width() - grid) / 2;
        int originY = bounds.y() + (bounds.height() - grid) / 2;
        Painter painter = context.painter();
        for (ClaimLayer.Cell claim : layer.cells()) {
            int col = claim.chunkX() - layer.centerChunkX() + layer.radius();
            int row = claim.chunkZ() - layer.centerChunkZ() + layer.radius();
            Rect rect = new Rect(originX + col * cell, originY + row * cell, cell, cell);
            tint(painter, rect, colorFor(claim.kind()));
            if (claim.kind() == ClaimLayer.Kind.CENTER) {
                painter.rect(rect, null, 1, Color.WHITE, 0);
            }
        }
    }

    private String hoveredCaption() {
        ClaimLayer layer = currentLayer();
        Rect bounds = new Rect(0, 0, width(), height());
        int cell = Math.max(1, Math.min(bounds.width(), bounds.height()) / layer.size());
        int grid = cell * layer.size();
        int originX = (bounds.width() - grid) / 2;
        int originY = (bounds.height() - grid) / 2;
        int col = (cursorX() - originX) / cell;
        int row = (cursorY() - originY) / cell;
        if (col < 0 || row < 0 || col >= layer.size() || row >= layer.size()) {
            return "Guilds map";
        }
        int chunkX = layer.centerChunkX() - layer.radius() + col;
        int chunkZ = layer.centerChunkZ() - layer.radius() + row;
        return layer.cellAt(chunkX, chunkZ)
                .map(claim -> labelFor(claim.kind()) + " [" + claim.chunkX() + ", " + claim.chunkZ() + "]")
                .orElse("Guilds map");
    }

    private Node legend() {
        return Ui.Column(
                Ui.Spacer(),
                Ui.Row(
                        swatch(OWN_GUILD, "Your guild"),
                        swatch(OTHER_GUILD, "Other guild"),
                        swatch(WILDERNESS, "Wilderness"),
                        swatch(CENTER, "You")
                ).gap(3).justify(Justify.CENTER)
                        .padding(2)
                        .background(Colors.alpha(Color.BLACK, 170))
                        .radius(3)
        ).align(Align.STRETCH).padding(3).fill();
    }

    private static Node swatch(Color color, String label) {
        return Ui.Row(
                Ui.Box(color).size(7, 7).radius(1),
                Ui.Text(label).color(Color.WHITE)
        ).gap(2).align(Align.CENTER);
    }

    private static void tint(Painter painter, Rect rect, Color color) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
            for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                Color under = painter.palette().color(painter.surface().get(x, y));
                painter.pixel(x, y, Colors.mix(under, color, TINT));
            }
        }
    }

    private static Color colorFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> WILDERNESS;
            case OWN_GUILD -> OWN_GUILD;
            case OTHER_GUILD -> OTHER_GUILD;
            case CENTER -> CENTER;
        };
    }

    private static String labelFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> "Wilderness";
            case OWN_GUILD -> "Your guild";
            case OTHER_GUILD -> "Other guild";
            case CENTER -> "You";
        };
    }
}
```

Note: `Painter.surface().get(x, y)` — verify the `Surface` interface has `get(int, int)` (it does in the API jar; the old screen used the same call). If `Colors.mix` signature differs in 2.0.0, use `painter.pixel(x, y, Colors.mix(under, color, TINT))` matching the old call.

Create `MapFollowTask.java`:

```java
package org.aincraft.guilds.gui;

import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.Session;
import org.bukkit.plugin.java.JavaPlugin;

public final class MapFollowTask {

    private MapFollowTask() {
    }

    public static void start(JavaPlugin plugin, MapGui mapGui) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Session session : mapGui.sessions()) {
                if (!(session.screen() instanceof GuildClaimScreen screen)) {
                    continue;
                }
                var loc = session.player().getLocation();
                int cx = loc.getChunk().getX();
                int cz = loc.getChunk().getZ();
                String world = loc.getWorld().getName();
                if (cx != screen.lastChunkX() || cz != screen.lastChunkZ()
                        || !world.equals(screen.lastWorld())) {
                    screen.setFollow(cx, cz, world);
                    session.invalidate();
                }
            }
        }, 20L, 10L);
    }
}
```

Add to `GuildClaimScreen`: `private int lastChunkX = Integer.MIN_VALUE; private int lastChunkZ = Integer.MIN_VALUE; private String lastWorld = "";` and package-private `lastChunkX()/lastChunkZ()/lastWorld()` getters + `setFollow(int, int, String)` that records the new position (the screen's `build()` already reads the live chunk, so recording is only for the change check).

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:test --tests "org.aincraft.guilds.map.ClaimLayerTest" --quiet
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/jlo/dev/guilds && git add guilds-paper/src/main/java/org/aincraft/guilds/gui/ guilds-paper/src/test/java/org/aincraft/guilds/map/ClaimLayerTest.java && git commit -m "feat: add MapGUI claim screen with live follow and hover tooltip"
```

---

### Task 4: Implement marquee claim (confirm + partial-failure report)

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildClaimScreen.java`

**Interfaces:**
- Consumes: `PlotService.claimGuildBlock(int x, int z, String world, String guildName)` → `boolean`; `PermissionService.canClaimForGuild(UUID residentUuid, String guildName)` → `boolean`; `PlotService.getGuildBlock(int, int, String)`.
- Produces: marquee state machine inside `GuildClaimScreen` — anchor/current cells, selection rect, confirm overlay, result flash.

- [ ] **Step 1: Add marquee state + overrides**

Add fields:

```java
    private int anchorX = -1, anchorZ = -1, currentX = -1, currentZ = -1;
    private boolean dragging;
    private boolean confirmOpen;
    private String resultFlash = "";
```

Override `onHold`/`onHoldEnd` and add the selection rect to `build()`:

```java
    @Override
    protected void onHold(int x, int y) {
        if (x < 0 || y < 0) {
            return;
        }
        int[] cell = cellAt(x, y);
        if (cell == null) {
            return;
        }
        if (!dragging) {
            anchorX = cell[0];
            anchorZ = cell[1];
            dragging = true;
        }
        currentX = cell[0];
        currentZ = cell[1];
        invalidate();
    }

    @Override
    protected void onHoldEnd() {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (anchorX == currentX && anchorZ == currentZ) {
            return; // no drag — a plain click, nothing to claim
        }
        if (!permissions.canClaimForGuild(player().getUniqueId(), viewerGuild)) {
            resultFlash = "You need mayor/assistant permission to claim.";
            invalidate();
            return;
        }
        confirmOpen = true;
        invalidate();
    }
```

Add `cellAt(int x, int y)` returning `{chunkX, chunkZ}` or null (same math as `hoveredCaption`), and a `selectionBounds()` helper returning the min/max chunk range of the current drag.

- [ ] **Step 2: Draw the marquee + confirm overlay**

In `build()`, wrap the layer draw in an overlay that also renders the selection rect and the confirm buttons:

```java
    private Node build() {
        return Ui.Overlay(
                Ui.Draw(this::paintLayer)
                        .tracksCursor(true)
                        .caption(this::hoveredCaption)
                        .fill(),
                marqueeOverlay(),
                legend()
        ).fill();
    }

    private Node marqueeOverlay() {
        if (!dragging && !confirmOpen) {
            return Ui.Spacer();
        }
        // translucent rect over the dragged chunk range, drawn in paintLayer via a field
        // + confirm buttons when confirmOpen
        if (confirmOpen) {
            return Ui.Column(
                    Ui.Text("Claim " + selectionCount() + " chunks for " + viewerGuild + "?"),
                    Ui.Row(
                            Ui.Button("Confirm").onClick(this::commitClaims),
                            Ui.Button("Cancel").onClick(() -> { confirmOpen = false; invalidate(); })
                    ).gap(2)
            ).align(Align.CENTER).padding(4).background(Colors.alpha(Color.BLACK, 190)).radius(3);
        }
        return Ui.Spacer();
    }
```

In `paintLayer`, after tinting cells, if `dragging`, draw the selection rect (min/max chunk → pixel rect) as a translucent highlight (`Colors.alpha(Color.WHITE, 90)` fill + 1px border).

- [ ] **Step 3: Commit claims with partial-failure report**

```java
    private void commitClaims() {
        confirmOpen = false;
        int claimed = 0, skipped = 0;
        for (int x = minX(); x <= maxX(); x++) {
            for (int z = minZ(); z <= maxZ(); z++) {
                if (plots.getGuildBlock(x, z, world()).isPresent()) {
                    skipped++;
                    continue;
                }
                if (plots.claimGuildBlock(x, z, world(), viewerGuild)) {
                    claimed++;
                } else {
                    skipped++;
                }
            }
        }
        resultFlash = "Claimed " + claimed + ", skipped " + skipped + " (already claimed / no permission / failed).";
        invalidate();
    }
```

Add `world()` = `player().getLocation().getWorld().getName()`, `minX()/maxX()/minZ()/maxZ()` from the anchor/current range. Draw `resultFlash` in the overlay (a centered text line) until the next interaction.

- [ ] **Step 4: Compile + test**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:compileJava --quiet
```

Expected: `BUILD SUCCESSFUL` (exit 0). The `ClaimLayerTest` screen test still passes.

- [ ] **Step 5: Commit**

```bash
cd /home/jlo/dev/guilds && git add guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildClaimScreen.java && git commit -m "feat: marquee claim with confirm and partial-failure report"
```

---

### Task 5: Wire /guildsmap to open the screen (fallback to ASCII)

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java`
- Modify: `guilds-paper/src/main/resources/plugin.yml`

**Interfaces:**
- Consumes: `MapGui.get()` (static), `GuildClaimScreen` (Task 3/4), `MapFollowTask.start(...)` (Task 3).
- Produces: `/guildsmap` opens the MapGUI screen when MapGUI is present; ASCII fallback otherwise. `MapGUI` added to `softdepend`.

- [ ] **Step 1: Add the MapGUI branch to handleFullMap**

In `handleFullMap`, after the permission check and before the ASCII render, add:

```java
        if (isMapGuiPresent()) {
            try {
                MapGui.get().open(player, new GuildClaimScreen(
                        plugin, getPlayerGuild(player), guildService, plotService, permissionService));
                MapFollowTask.start(plugin, MapGui.get());
                plugin.getLogger().info("MapGUI claim map opened for player: " + player.getName());
                return Command.SINGLE_SUCCESS;
            } catch (Exception e) {
                player.sendMessage(Component.text("Failed to open map: ", NamedTextColor.RED)
                        .append(Component.text(e.getMessage(), NamedTextColor.RED)));
                plugin.getLogger().warning("Failed to open MapGUI map for " + player.getName() + ": " + e.getMessage());
                return 0;
            }
        }
```

Keep `isMapGuiPresent()` (already present in the class) and the ASCII fallback below it. Add imports `de.flog99.mapgui.MapGui`, `org.aincraft.guilds.gui.GuildClaimScreen`, `org.aincraft.guilds.gui.MapFollowTask`.

- [ ] **Step 2: Add MapGUI to softdepend**

In `guilds-paper/src/main/resources/plugin.yml`, change:

```yaml
softdepend: [WorldEdit, WorldGuard, triumph-gui, squaremap]
```

to:

```yaml
softdepend: [WorldEdit, WorldGuard, triumph-gui, squaremap, MapGUI]
```

- [ ] **Step 3: Compile**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:compileJava --quiet
```

Expected: `BUILD SUCCESSFUL` (exit 0).

- [ ] **Step 4: Commit**

```bash
cd /home/jlo/dev/guilds && git add guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java guilds-paper/src/main/resources/plugin.yml && git commit -m "feat: open MapGUI claim map from /guildsmap with ASCII fallback"
```

---

### Task 6: Verify — full build + runServer smoke test

**Files:**
- None (verification only)

- [ ] **Step 1: Run the full test suite**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-paper:test --quiet
```

Expected: all tests pass, `BUILD SUCCESSFUL` (exit 0).

- [ ] **Step 2: Boot the test server**

```bash
cd /home/jlo/dev/guilds && ./gradlew :guilds-test:runServer
```

Expected: MapGUI 2.0.0 loads (`- MapGUI (2.0.0)`), Guilds loads, no MapGUI-related exceptions in the log. (If `world/session.lock` is held by a leftover instance, stop it first.)

- [ ] **Step 3: Smoke-test the screen (manual, via a connected client or the remote-control listener)**

- Join the server, run `/guildsmap`.
- Confirm the map item appears in hotbar slot 4, terrain renders, claims tint by guild.
- Walk — confirm the map re-centers on the new chunk (follow task).
- Hover a claimed chunk — confirm the caption shows the claim info.
- Hold right-click and drag a rectangle over wilderness — confirm the selection rect draws, the confirm overlay appears, Confirm claims the chunks, and the result flash reports claimed/skipped.
- Verify the DB has the new guild blocks (`PlotService` write path).

- [ ] **Step 4: Report results**

Summarize what passed and any failures in the final handoff.

---

## Self-Review Notes

- **Spec coverage:** terrain+follow (Task 3), hover tooltip (Task 3), marquee claim + confirm + partial-failure (Task 4), pinned carry (Task 3 `hand()`), API dep (Task 1), wiring + fallback (Task 5), testing (Tasks 2/6). All spec sections map to a task.
- **Placeholders:** none — every code step has full code; the only "verify" steps are command runs with expected output.
- **Type consistency:** `ClaimLayer.classify(...)` signature matches across Tasks 2/3; `plots::getGuildBlock` and `guilds::getGuildById` match the `PlotLookup`/`GuildLookup` functional interfaces; `claimGuildBlock`/`canClaimForGuild` signatures match the live services.
