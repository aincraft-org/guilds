# Guild Upgrade Freeform Tech Web Design

**Date:** 2026-08-28
**Status:** Draft for review
**Related:** `docs/superpowers/specs/2026-08-26-guild-upgrade-mapgui-design.md`, `docs/living-specs/guilds.md`, `docs/living-specs/map.md`, `GuildUpgradeScreen`, `GuildUpgradeGraphLayout`, `GuildUpgradeNodeScreen`, `TechTreeService`, `GuildProjectService`, `GuildLevelService`

---

## 1. Summary

Replace the rigid 4-column lane grid layout in the `/g upgrade` **MapGUI 2.0.0** screen with an organic, freeform **Minecraft Lodestone Ley-Line Tech Web**. The entire progression system is rendered as a single continuous, interconnected graph originating from a central **Guild Hearth** root node. Sector branches are distinguished strictly by **distinct pixel geometric node shapes** (Infrastructure: Hexagon, Defense: Shield, Commerce: Coin, Culture: Diamond) connected by flowy, stepped raster Bezier splines and live traveling energy sparks. All elements, including the top bar, graph canvas, bottom legend, and detail inspection, operate strictly within a native $128 \times 128$ pixel MapGUI viewport.

---

## 2. Intent & Goals

1. **Minecraft Fantasy & Theme:** Ground the upgrade screen in Minecraft lore as an enchanted guild cartography chart showing the ancient ley-line conduits flowing from the guild's central lodestone hearth.
2. **Single Interconnected Graph:** Unify all 16 progression perks into one cohesive web rather than 4 isolated columns or separate sector buckets.
3. **Geometry-Based Sector Identification:** Encode disciplines by distinct geometric pixel shapes rather than artificial sector borders or headers.
4. **Strict 128x128 Native Resolution:** Deliver crisp, pixel-perfect rendering on Minecraft maps with $10\times10\text{px}$ generous click hitboxes and paginated in-map node inspection.

---

## 3. Scope

### In Scope
- **Graph Topology:** Introduce a root `guild_hearth` lodestone anchor node at $(X: 64, Y: 22)$ that branches into the 4 foundational perks (`better_storage`, `reinforced_walls`, `market_stall`, `heritage_monument`), forming a single connected DAG of 17 total nodes.
- **Node Geometry & Renderers:** Implement 4 custom pixel-art shape rasterizers in `GuildUpgradeGraphLayout` / `GuildUpgradeScreen`:
  - ⬡ **Hexagon ($7\times7\text{px}$):** `INFRASTRUCTURE`
  - 🛡 **Shield ($7\times8\text{px}$):** `DEFENSE`
  - 🪙 **Octagonal Coin ($7\times7\text{px}$):** `COMMERCE`
  - ✦ **Diamond ($9\times9\text{px}$):** `CULTURE`
- **Stepped Raster Splines:** Draw organic, curved Bezier splines using stepped Bresenham rasterization with traveling pixel energy sparks along active/frontier research lines.
- **128x128 Layout & Hitboxes:** Position all 17 nodes within bounds $[10..118] \times [20..108]$ with $10\times10\text{px}$ hitboxes for responsive cursor hover and clicks.
- **In-Map Node Inspection:** Sub-screen transition / modal overlay rendering full node details, prerequisite checks, cost, and interactive action buttons (`[START RESEARCH]`, `[CLEAR ACTIVE]`, `[BACK]`).

### Out of Scope
- Modifying underlying SQL database schemas or database tables.
- Changes to `techtree.yml` cost and reward formulas.
- Modifying `GuildLevelService.performGuildUpgrade` logic or level reward progression.

---

## 4. Architecture & Topology

### Graph Nodes Mapping
The tech tree consists of 1 Central Root + 16 Tech Nodes = 17 Nodes mapped across the $128\times128$ pixel grid:

```text
===================================================================
[Y: 0..13]   VALHALLA Lv.3 • 4 TP                         [UPGRADE]
===================================================================
                           [ ⚔ GUILD HEARTH ] (X:64, Y:22)
                           /    /     \    \
                         /     /       \     \
             [⬡ Storage]     /           \     [🛡 Walls]
             (X:42, Y:38)   /             \   (X:86, Y:38)
                  |     [🪙 Market]   [✦ Heritage]    |
                  |     (X:52, Y:54)  (X:76, Y:54)    |
             [⬡ Travel]      |              |     [🛡 Healing]
            (X:30, Y:56)  [🪙 Bulk]    [✦ Library](X:98, Y:56)
             /        \   (X:38, Y:72) (X:90, Y:72)   /        \
   [⬡ Farming]   [⬡ Sorter]  |              |   [🛡 Towers] [🛡 Turret]
   (X:18, Y:46) (X:14, Y:72) [🪙 Caravan] [✦ Festival] (X:110, Y:46)(X:114, Y:72)
                             (X:46, Y:90) (X:82, Y:90)
                                 |              |
                          [🪙 Trade Empire]  [✦ Nexus]
                          (X:40, Y:104)      (X:88, Y:104)
===================================================================
[Y:115..127]  ⬡:INF   🛡:DEF   🪙:COM   ✦:CUL  | Fast Travel [READY]
===================================================================
```

---

## 5. Visual Specifications

### Colors & States

| State | Fill Color | Border Color | Spline Path Style |
| :--- | :--- | :--- | :--- |
| **Mastered / Unlocked** | `#143820` (Dark Emerald) | `#22c55e` (Bright Emerald) | Solid Green Line (`#22c55e`) |
| **Active Research** | `#381f08` (Dark Amber) | `#f59e0b` (Bright Amber) + Pulsing Ring | Dashed Amber Line (`#f59e0b`) + White Sparks |
| **Available to Unlock** | `#0d1829` (Deep Navy) | Branch Accent Color | Dashed Amber Line (`#f59e0b`) |
| **Locked** | `#0d1017` (Dark Slate) | `#3e4659` (Muted Slate) | Dim Slate Line (`#2c3242`) |

### Branch Accent Colors
- **Infrastructure (⬡):** Cyan `#38bdf8`
- **Defense (🛡):** Rose `#f43f5e`
- **Commerce (🪙):** Amber `#f59e0b`
- **Culture (✦):** Violet `#c084fc`
- **Guild Hearth (⚔):** Gold `#fbbf24`

---

## 6. Interaction Flow & Hitbox Handling

1. **Canvas Coordinate Translation:**
   $$\text{Map } X = \text{round}\left(\frac{\text{screen } x - \text{rect.left}}{\text{rect.width}} \times 128\right)$$
   $$\text{Map } Y = \text{round}\left(\frac{\text{screen } y - \text{rect.top}}{\text{rect.height}} \times 128\right)$$
2. **Generous Hitbox Resolution:**
   Each node has an effective hit region of $\pm 5\text{px}$ ($\text{width} = 11\text{px}, \text{height} = 11\text{px}$), allowing players using Minecraft map cursors to easily select small pixel nodes without precision frustration.
3. **Detail View Modal Sub-Screen:**
   - Clicking a node opens an in-map sub-screen overlay ($X: 10..118, Y: 18..110$).
   - Shows: Node Badge, Discipline Name, Lore (line-wrapped at 20 chars), Effect Bonus, Prerequisite Checklist (`[OK]` / `[NO]`), and Action Button.
   - Action Button dynamically displays: `[MASTERED]` (disabled), `[START (N TP)]` (active amber), `[NEED N TP]` (disabled gray), or `[CLEAR ACTIVE]` (red).
   - Pressing `[X]` or clicking outside returns to the main tech web.

---

## 7. Data Flow & Service Integration

```text
Player executes /g upgrade
       |
       v
GuildUpgradeScreen (MapGUI 2.0.0)
       |
       +---> TechTreeService.getAllNodes() (Load 16 nodes from techtree.yml)
       +---> GuildProjectService.getActiveProjectId(guild)
       +---> GuildLevelService.getGuildLevel() / getMaxLevel()
       |
       +---> Screen.paint() (Pure raster render onto 128x128 map buffer)
       |
       v
Click Node / Upgrade Action
       |
       +---> GuildProjectService.startProject(guild, nodeId) / clearActiveProject()
       +---> GuildLevelService.performGuildUpgrade(guild)
       |
       v
Re-fetch Snapshot & Repaint Canvas
```

---

## 8. Testing & Verification Plan

### Unit Tests
- **Geometry Coordinates & Bounds:** Assert that all 17 nodes reside strictly within $[10..118] \times [20..108]$ and have no overlapping hitboxes.
- **DAG Connectivity:** Test that from `guild_hearth`, traversing effective prerequisites visits all 16 configured nodes (0 disconnected components).
- **Stepped Bezier Rasterizer:** Verify that rasterized spline steps remain within $[0..127]$ without buffer index overflow.

### Integration & Manual Verification
- Verify `/g upgrade` opens the 128x128 MapGUI within $<1\text{s}$.
- Verify clicking yellow available node starts project and triggers active pulsing ring with traveling sparks.
- Verify clicking active node allows clearing.
- Verify green `[UPGRADE]` button performs guild level promotion and updates available TP.

---

## 9. Decisions & Trade-Offs

| Decision | Alternative Considered | Why Chosen |
| :--- | :--- | :--- |
| **Central Guild Hearth Root Node** | 4 Disconnected Chains | Unifies the whole tree into one continuous, connected graph rooted in Minecraft guild identity |
| **Geometric Pixel Shapes** | Text Labels / Sector Boxes | Keeps the $128\times128$ map uncluttered while providing distinct, accessible discipline cues |
| **In-Map Modal Overlay** | External Chest Inventory GUI | Keeps the entire experience within MapGUI without breaking immersion |
| **Stepped Raster Splines** | Straight Orthogonal Lines | Provides an organic, fluid ley-line visual feel matching the freeform layout |
