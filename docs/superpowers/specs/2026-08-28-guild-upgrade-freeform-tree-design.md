# Guild Upgrade Freeform Radial Tech Web Design

**Date:** 2026-08-28
**Status:** Draft for review
**Related:** `docs/superpowers/specs/2026-08-26-guild-upgrade-mapgui-design.md`, `docs/living-specs/guilds.md`, `docs/living-specs/map.md`, `GuildUpgradeScreen`, `GuildUpgradeGraphLayout`, `GuildUpgradeNodeScreen`, `TechTreeService`, `GuildProjectService`, `GuildLevelService`

---

## 1. Summary

Replace the downward-flowing grid in `/g upgrade` with a **$360^\circ$ Organic Radial Minecraft Ley-Line Web** on a native $128 \times 128$ MapGUI canvas. The entire tech tree is one interconnected constellation expanding outward in all directions from a central **Guild Hearth** lodestone anchor at $(X: 64, Y: 64)$. Sector disciplines are distinguished strictly by **pixel geometric node shapes** (Infrastructure: Hexagon, Defense: Shield, Commerce: Coin, Culture: Diamond) connected by curved stepped splines and live energy sparks. The bottom legend and top-right upgrade button are removed so the full $128 \times 128$ viewport is dedicated entirely to the expansive web.

---

## 2. Intent & Goals

1. **True Radial Web Layout:** Radiate all 16 tech perks outward from the central Guild Hearth $(X: 64, Y: 64)$ across 3 concentric rings ($360^\circ$ layout) instead of a top-to-bottom waterfall.
2. **Dedicated Full-Canvas Surface:** Eliminate external and in-frame bottom status bars and button clutter; use a minimal single-line top header (`VALHALLA • LVL 3 • 4 TP`).
3. **Geometry-Based Sector Identity:** Differentiate disciplines purely through crisp pixel geometries:
   - ⬡ **Hexagon:** `INFRASTRUCTURE`
   - 🛡 **Shield:** `DEFENSE`
   - 🪙 **Coin:** `COMMERCE`
   - ✦ **Diamond:** `CULTURE`
4. **Self-Contained In-Map Modal:** Clicking any node brings up an in-map slate overlay ($X: 10..118, Y: 14..114$) with lore, prerequisites checklist, cost, and action button.

---

## 3. Scope

### In Scope
- **Radial Graph Topology:** 1 Central Hearth Root $(64, 64)$ + 16 Tech Nodes forming a single connected $360^\circ$ DAG.
  - **Inner Ring (Layer 1):** Foundational perks at NW $(42, 44)$, NE $(86, 44)$, SW $(42, 84)$, SE $(86, 84)$.
  - **Mid Ring (Layer 2):** Expanding branch perks along the perimeter.
  - **Outer Apexes (Layer 3):** High-tier masteries at the canvas extremities: W $(10, 64)$, E $(118, 64)$, SW $(50, 114)$, SE $(78, 114)$.
- **Stepped Radial Splines:** Rasterized cubic Bezier curves with organic tangent curvature bending around the center, featuring traveling energy spark particles.
- **In-Map Modal Overlay:** $108 \times 100\text{px}$ in-map slate overlay for inspecting node lore and toggling active research.
- **Click Hitboxes:** $\pm 6\text{px}$ generous hitboxes around each node for effortless map cursor interaction.

### Out of Scope
- Modifying SQL persistence schema or table definitions.
- Changing `techtree.yml` point costs or reward effects.
- Modifying `GuildLevelService` core upgrade calculations.

---

## 4. Radial Coordinates & Topology

```text
+-------------------------------------------------------------+
| VALHALLA • LVL 3 • 4 TP                        [Y: 0..11]   |
+-------------------------------------------------------------+
|               [⬡ Travel]         [🛡 Towers]                |
|                (26, 32)           (102, 32)                 |
|                                                             |
|   [⬡ Farming]    [⬡ Storage]   [🛡 Walls]    [🛡 Healing]    |
|    (22, 52)       (42, 44)       (86, 44)      (106, 52)    |
|            \          \           /          /              |
| [⬡ Sorter]  -------- [ ⚔ HEARTH ] -------- [🛡 Turret]     |
|  (10, 64)             (64, 64)               (118, 64)      |
|            /          /           \          \              |
|   [🪙 Bulk]      [🪙 Market]   [✦ Heritage]  [✦ Library]    |
|    (22, 76)       (42, 84)       (86, 84)      (106, 76)    |
|                                                             |
|               [🪙 Caravan]       [✦ Festival]               |
|                (30, 100)          (98, 100)                 |
|                                                             |
|           [🪙 Trade Empire]     [✦ Nexus]                   |
|              (50, 114)          (78, 114)                   |
+-------------------------------------------------------------+
```

---

## 5. Visual Specifications & Progression States

| State | Fill Color | Border Color | Spline Path Style |
| :--- | :--- | :--- | :--- |
| **Guild Hearth (Core)** | `#451a03` (Deep Amber) | `#fbbf24` (Gold) + White Spark | Radiating Anchor |
| **Mastered / Unlocked** | `#143820` (Dark Emerald) | `#22c55e` (Bright Emerald) | Solid Green Line (`#22c55e`) |
| **Active Research** | `#381f08` (Dark Amber) | `#f59e0b` (Bright Amber) + Pulsing Ring | Dashed Amber Line (`#f59e0b`) + White Sparks |
| **Available to Unlock** | `#0d1829` (Deep Navy) | Branch Color (Cyan/Rose/Amber/Violet) | Dashed Amber Line (`#f59e0b`) |
| **Locked** | `#0d1017` (Dark Slate) | `#3a4254` (Muted Slate) | Dim Slate Line (`#252a38`) |

---

## 6. Testing & Verification

1. **Bounds Verification:** Assert all 17 nodes reside within $[10..118] \times [22..114]$ with no hitbox collisions.
2. **DAG Connectivity:** Assert 100% reachability from `guild_hearth` across all 16 tech nodes.
3. **Modal Hit-Test Verification:** Verify boundary click handling on modal close $[106..115] \times [18..26]$ and action button $[14..114] \times [98..110]$.
4. **Performance:** Ensure pure raster rendering in `Screen.paint()` with zero main-thread database blocking.
