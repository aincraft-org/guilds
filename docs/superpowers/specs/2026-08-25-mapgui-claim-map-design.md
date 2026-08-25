# MapGUI Claim Map (`/guildsmap`) Design

Date: 2026-08-25
Status: Approved design (pending user spec review)

## Problem

`MapBrigadierCommand` renders `/guildsmap` as a static ASCII chat dump (`MapRenderer`).
Players cannot see claims while moving, hover a cell for details, or claim from the map.
This replaces the ASCII renderer with a live MapGUI screen on a vanilla map-item canvas.

## Decisions

- **Carry mode: B — pinned, main-hand focus.** `HandOptions.pinned(slot)` gives a fake map
  in one hotbar slot (`Carry.PINNED`, `Focus.MAIN_HAND`), focused while held in the main
  hand. The player walks with it out; the map re-centers on their chunk as they move.
  Hover + marquee work while held. A popup (modal) was rejected because the point is
  "render as I'm walking"; offhand was rejected because an offhand map has no cursor,
  so hover/marquee cannot work there.
- **Marquee scope: claim, with confirm + partial-failure report.** The marquee selects a
  chunk rectangle; committing claims it. No claims happen on a transient drag.

## MapGUI 2.0.0 API (verified from source)

- `MapGui.get()` — static, Bukkit service. `Session open(Player, Screen)`.
- `Screen` hooks: `build()`, `terrain()`, `hand()`, `holdable()`, `clampPitch()`,
  `onHold(int x, int y)` (every tick the right button is down, from the press tick),
  `onHoldEnd()` (release, or when the screen loses the mouse mid-hold), `cursorX()/cursorY()`,
  `width()/height()`, `player()`, `invalidate()`, `state(T)`, `watch(SharedModel)`.
- `HandOptions.pinned(int slot)` → `(Carry.PINNED, Focus.MAIN_HAND, slot, false, false, 0)`.
- `Ui.Overlay(...)`, `Ui.Draw(Consumer<PaintContext>)` with `.tracksCursor(true)`,
  `PaintContext(Painter, Rect bounds, boolean hovered)`, `Painter.pixel/rect/fill`,
  `Ui.Button(...).onClick(...)`, `Ui.Text(...)`, `Ui.Column/Row/Spacer/Box`, `.caption(Supplier<String>)`.
- `Marker` coordinates are surface pixels; the old screen's pixel→chunk math ports directly.

## Components

### `ClaimLayer` (port to `org.aincraft.guilds.map`)

Classifies a radius of chunks around a center as `WILDERNESS`, `OWN_GUILD`, `OTHER_GUILD`,
`CENTER` from `PlotService.getGuildBlock(chunkX, chunkZ, world)` + `GuildService.getGuildById(...)`.
Pure function of (centerChunkX, centerChunkZ, world, viewerGuild, radius, services) — unit-testable.

### `GuildClaimScreen extends Screen` (new, `org.aincraft.guilds.gui`)

- `terrain() = true` (real terrain under the layout), `holdable() = true`, `clampPitch(false)`.
- `hand()` → `HandOptions.pinned(4)` (slot 4; configurable later).
- `build()` → `Ui.Overlay(claimLayer().fill(), legend(), marqueeOverlay(), confirmOverlay())`.
- **Follow:** a plugin-owned repeating task invalidates the session only when the player's
  chunk or world changes (compare `getChunk().getX()/getZ()` + world name; skip if unchanged).
  `build()` reads `player().getLocation().getChunk()` fresh each paint, so the grid re-centers
  as the player walks. No per-tick repaint.
- **Hover tooltip:** the `Draw` node is `.tracksCursor(true)`; paint-time, compute the chunk
  cell under the cursor, resolve via `PlotService` + `GuildService`, and draw a caption box
  (guild name, owner, plot type, or wilderness). Gate the lookup on cursor cell change, not
  pixel change, so a frame is not spent per pixel of movement.
- **Marquee:** `onHold` records anchor + current cell and draws a translucent selection rect
  over the dragged chunk range. `onHoldEnd` finalizes the selection. No writes during drag.

### Marquee claim flow (explicit preview/commit)

1. **Press** right-click on a claimable cell → anchor set, hold starts.
2. **Drag** → translucent selection rect; **pre-validate the whole rectangle**: each cell
   checked for `getGuildBlock(...)` empty + `PermissionService.canClaimForGuild(...)`.
   Unclaimable cells render red-hatched in the preview.
3. **Release** (`onHoldEnd`) → if selection non-trivial, show **confirm overlay**:
   "Claim N chunks for <guild>? [Confirm] [Cancel]" (`Ui.Button` + `.onClick`).
4. **Confirm** → per-chunk `PlotService.claimGuildBlock(x, z, world, guildName)` (best-effort,
   **no bulk/transaction API exists**). Result flash states exactly:
   "Claimed N, skipped M (already claimed / no permission / failed)".
5. Layer rebuilds so new claims color in.

### Wiring

- `guilds-paper/build.gradle.kts`: `compileOnly("io.github.flog99:mapgui-api:2.0.0")` +
  `gradle/verification-metadata.xml` entry.
- `MapBrigadierCommand`: `/guildsmap` opens the screen via `MapGui.get().open(player, screen)`
  when MapGUI is present (`isMapGuiPresent()` guard, kept); falls back to the ASCII renderer
  when absent. Existing subcommands (`compact/small/big/here/help`) keep working as ASCII.
- `plugin.yml`: add `MapGUI` to `softdepend`.

## Error handling

- MapGUI absent → existing "install MapGUI" message; ASCII fallback intact.
- `claimGuildBlock` failure per chunk → counted in the partial-failure report, never thrown.
- Screen closes → follow task stops watching that player (no leak).

## Testing

- Port `ClaimLayerTest` classification test to the new package.
- `GuildClaimScreen` smoke test: `Screen.class.isAssignableFrom(...)` + `currentLayer()`
  returns the classifier (mirrors the old `ClaimLayerTest`).
- Manual: boot `runServer` (MapGUI 2.0.0 now loaded), walk with the map, hover a claim,
  drag-marquee, confirm, verify claims color in and the DB has the new guild blocks.

## Out of scope

- Offhand/glanceable mode, camera/mirror features, wall display, claim cost/economy,
  multi-world UI, admin map editor.
