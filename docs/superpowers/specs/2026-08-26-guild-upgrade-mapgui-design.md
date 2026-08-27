# Guild Upgrade MapGUI Design

**Date:** 2026-08-26
**Status:** Draft for review
**Related:** `docs/living-specs/guilds.md`, `docs/living-specs/map.md`, `GuildBrigadierCommand`, `TechTreeBrigadierCommand`, `TechTreeGUI`, `GuildClaimScreen`, `GuildLevelService`, `GuildProjectService`

## Summary
Replace chest-inventory `TechTreeGUI` access via `/g techtree` with a **MapGUI 2.0.0** screen at `/g upgrade` (and `/guild upgrade` via existing `g`→`guild` redirect). The screen is a combined **node-web** showing guild level/XP progress with an Upgrade button on top and the 16 tech-tree projects as a graph below. Top-level `/techtree` and `/tt` registrations are removed; `GuildLevelService.performGuildUpgrade(Guild)` remains separate and unchanged. The implementation reuses existing `TechTreeService.getAllNodes()` grouped by `TechTreeBranch.values()` (`INFRASTRUCTURE`, `DEFENSE`, `COMMERCE`, `CIVIC`) so `techtree.yml` stays authoritative.

## Intent
Give players a single **live MapGUI** for progression: see level, spend XP to level up, and start/clear tech projects without the chest UI. Keep SQL `Guild.balance` wallet and Mint guild-bank (`/guild bank`) separate. Success: `/g upgrade` opens within 1s, shows correct level/tech-points, node colors match `available/unlocked/active/locked`, clicks succeed or show exact service status, and no `WARN Failed to fetch` for guild blocks.

## Scope
- **In:** New `GuildUpgradeScreen extends Screen` (MapGUI), wiring `/g upgrade` to open it, removing `/g techtree` literal under `guild` and top-level `/techtree`/`/tt` registrations, updating user-facing messages from `/techtree` to `/g upgrade`.
- **Out:** Forking MapGUI, changing `techtree.yml` schema, Vault/Mint economy changes, `GuildLevelService` reward logic, chest `TechTreeGUI` deletion (keep for reference but not registered).

## Architecture
- **New:** `guilds-paper/src/main/java/org/aincraft/guilds/gui/GuildUpgradeScreen.java` extends `de.flog99.mapgui.Screen`. Constructor: `JavaPlugin`, `GuildService`, `ResidentService`, `GuildLevelService`, `TechTreeService`, `GuildProjectService`.
- **Existing reuse:** `GuildService`, `ResidentService`, `GuildLevelService`, `TechTreeService`, `GuildProjectService`, `Guild` (`getGuildLevel()`, `getTechPoints()`, `getLevelData().getUpgradeProgress()`), `TechTreeBranch.values()`, `TechTreeNode` (`getPrerequisites()`, `getBranch()`), `MapGui.get().open(Player, Screen)`.
- **Wiring:** `GuildBrigadierCommand.buildCommand()` no longer adds `techTreeCommand.buildCommand()` as `techtree` literal; instead adds `Commands.literal("upgrade").requires(guilds.techtree).redirect(techTreeUpgradeNode)` where `techTreeUpgradeNode` is a private node built to host the MapGUI handler, or directly `executes(this::handleUpgradeMap)` that opens `GuildUpgradeScreen`. `BrigadierCommandRegistry` removes `commands.register(techTreeCommand.buildCommand())` and `tt` alias. `GuildLevelService.performGuildUpgrade(Guild)` stays at `/guildlevel upgrade` unchanged.
- **Soft-depend:** If `MapGUI` absent, `/g upgrade` sends `§cMapGUI not available` and does not throw.

## Components

### Top bar (16px)
- Text: `Guild: <name>  Level: <level> → <next>  XP: <progress>/<cost>  Skill points: <techPoints>` (from `Guild` + `GuildLevelService.getMaxLevel()`). Uses `Guild.getUpgradeProgress()` delegated to `GuildLevelData`.
- Button: `Upgrade` rect at right. Enabled (green) only if `guild.getMayorUuid().equals(player)` or `hasPermission("guilds.admin.guild")` and `guild.getGuildLevel() < getMaxLevel()` and `available` per `GuildLevelService`. Disabled gray otherwise. Tooltip shows cost or reason.

### Node web canvas (below top bar)
- Layout: 4 columns from `TechTreeBranch.values()` in enum order (`INFRASTRUCTURE`, `DEFENSE`, `COMMERCE`, `CIVIC`), 4 rows per column ordered as in `getAllNodes()` filtered by branch. Each node is 18×18 square with icon (fallback colored rect).
- Colors: `unlocked` (already started/cleared in past) = green `0x60FF60`, `available` (prereqs met, points enough) = yellow `0xFFE040`, `locked` (`GuildProjectRules.StartStatus.UNMET_REQUIREMENTS` or insufficient points) = gray `0xA0A0A0`, `active` (`getActiveProjectId(guild).equals(nodeId)`) = gold border `0xFFD700` + yellow fill.
- Prerequisite lines: thin dark lines between prerequisite node center and dependent node center, drawn via `Painter` `drawLine`.
- Interaction: hover → tooltip `Name — Branch — Cost X techpoints — Effect … — Status`. Click:
  - `available` → `guildProjectService.startProject(guild, nodeId)`; on `ProjectStartResult.isSuccessful()` refresh screen, else show `getStatus()` text in footer (e.g., `UNMET_REQUIREMENTS`, `ALREADY_ACTIVE`, `INSUFFICIENT_POINTS`) keeping screen open.
  - `active` → `guildProjectService.clearActiveProject(guild)`; refresh.
  - `locked`/`unlocked` → no-op, show reason.

### Reuse and isolation
- No new spatial rules; validation via existing service `ProjectStartResult.getStatus()` and `UpgradeResult.getStatus()`.
- `Screen.paint()` is pure rendering from cached `Guild`+nodes snapshot; no DB in paint thread. Data fetched on open and after each click via `runTask`→`open` refresh.

## Data flow
1. **Open:** Player executes `/g upgrade` (`/guild upgrade` via `g` redirect) → `GuildBrigadierCommand.handleUpgradeMap` resolves `residentService.getResident(player.getUniqueId()) → guildService.getGuild(resident.getGuild()) → Guild`. If empty → `§cYou are not in a guild` and return. Fetches `guild.getGuildLevel()`, `guild.getTechPoints()`, `guild.getLevelData().getUpgradeProgress()`, `techTreeService.getAllNodes()`, `guildProjectService.getActiveProjectId(guild)`. Constructs `GuildUpgradeScreen` and `MapGui.get().open(player, screen)`.
2. **Render:** `paint()` draws top bar + 16 nodes grouped by `TechTreeBranch.values()`, colors from `available` check (`prerequisites` subset of unlocked set, points ≥ cost, no active project). No DB.
3. **Upgrade click:** `guildLevelService.performGuildUpgrade(guild)` — checks mayor/admin and `getMaxLevel()`. Returns `UpgradeResult` with `getNewLevel()`, `getStatus()`, `getMessage()`. On success, re-fetch `Guild` (level/tech-points updated per `GuildLevelData` rewards) and reopen screen; on failure show `getMessage()` and hint `Use '/guildlevel level' to see XP requirements` (preserving existing message).
4. **Node click:** `startProject(guild, nodeId)` or `clearActiveProject(guild)` → `ProjectStartResult`/`boolean` → re-fetch `Guild`+activeId and repaint. Errors (`UNMET_REQUIREMENTS` etc.) shown in footer, screen stays open.
5. **No Mint:** Project start/level upgrade do not touch Mint; keep distinct from `Guild bank` Mint flows. Tech-tree DB failures show `§cService unavailable, try again` (distinct from Mint unavailable).

## Error handling
- **No guild / not member:** close MapGUI, `§cYou are not in a guild`
- **Not mayor for Upgrade:** `§cOnly guild mayor or a guild administrator can upgrade` (button disabled, message on click) — preserves `GuildLevelBrigadierCommand` exact text
- **Max level:** `§aYour guild is already at the maximum level!` — button grayed
- **Project start fails:** Show `ProjectStartResult.getStatus()` text (`UNMET_REQUIREMENTS` etc.) in footer, keep screen open — do not use `PREREQ_NOT_MET` (non-existent enum)
- **Active project exists:** `§7A project is already active. Use /g upgrade clear first.` (updated from `/techtree clear`)
- **DB/service unavailable:** `§cService unavailable, try again` (tech-tree DB) distinct from `§cMint guild bank is unavailable.` (Mint)
- **Permission:** `guilds.techtree` required for `/g upgrade`; if missing → `§cNo permission` and do not open

## Testing
- **Unit:** Pure layout helpers — node positions per `TechTreeBranch.values()` ×4, color mapping tests (green/yellow/gray/gold) without Bukkit.
- **Integration:** `/g upgrade` opens MapGUI with top bar showing real `guild.getGuildLevel()`/`getTechPoints()` and 16 nodes grouped by `INFRASTRUCTURE/DEFENSE/COMMERCE/CIVIC`; verify prerequisite lines match `techtree.yml`. Click yellow node → assert `startProject(guild, nodeId)` returns `isSuccessful()` and `getActiveProjectId(guild)` updates; click active → `clearActiveProject(guild)` clears and `getActiveProjectId` empty; refresh shows updated `Guild` state. Click Upgrade → assert `performGuildUpgrade(guild).isSuccessful()` and `UpgradeResult.getNewLevel()`/`getStatus()` match configured `GuildLevelData` deltas (do not assert fixed +1), and `Guild` row `level`/`techPoints` updated per service.
- **Manual:** Verify `WARN Failed to fetch guild blocks` gone (already fixed via `getTimestamp`), `Guild Claims` squaremap layer still renders, and `/techtree`/`/tt` top-level no longer tab-completes (only `/g upgrade` does). Check 11 territories still `Loaded 11`.

## Decisions
| Date | Decision | Why |
|------|----------|-----|
| 2026-08-26 | MapGUI over chest GUI for upgrade | Live canvas, consistent with `/g map` ClaimLayer, no fork |
| 2026-08-26 | Node-web (4×4 grid by branch) | Matches `techtree.yml` mental model, prerequisite lines visible |
| 2026-08-26 | Keep `GuildLevelService.performGuildUpgrade` separate | Level XP vs project skill points are distinct progressions |
| 2026-08-26 | Remove `/techtree`/`/tt` registrations, keep logic under `/g upgrade` | Requested “remove techtree” while retaining handlers |

## Open questions
- Should top bar also show `Guild.balance` (SQL wallet) beside level/XP, or keep level-only to avoid Mint confusion?
- Keep chest `TechTreeGUI` class for reference or delete after MapGUI ships?

## Implementation notes
- Do not add `UPDATE mint.balances` manual SQL for testing; use `deposit`/`pay` via Mint ledger.
- Use `ResultSet.getTimestamp("claimed_at").toLocalDateTime()` for `GuildBlock` mapping (already fixed) so `GuildUpgradeScreen` does not reintroduce string parse `Failed to fetch`.
- Cancel `GuildBankVillagerListener` immediately for tagged `GUILD_BANK` villagers (already fixed) to prevent vanilla trading; unrelated to upgrade but part of same release train.

