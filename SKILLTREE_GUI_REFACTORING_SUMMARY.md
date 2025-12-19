# Skill Tree GUI Refactoring - Unified Design

## Overview
Refactored `SkillTreeGUI` to implement a unified skill tree design that displays ALL skills from all branches in a single, cohesive tree interface instead of separate branch tabs.

## Key Changes

### 1. Removed Branch-Specific Logic
- **Removed field:** `currentBranch` (was `SkillBranch.ECONOMY`)
- **Removed methods:**
  - `renderBranchTabs()` - No longer needed
  - `renderInfoBar()` - Moved to player inventory
  - `renderControls()` - Moved to player inventory

### 2. Full 6-Row Skill Display
- **Updated constant:** `VISIBLE_TIERS` changed from 4 to 6
- **New constants:** `SKILL_AREA_START` (0) and `SKILL_AREA_END` (53)
- **All 54 GUI slots** now dedicated to skill display (6 rows × 9 columns)

### 3. Unified Skill Tree Rendering
- **Modified `renderSkillTree()`:**
  - Now uses `registry.getAllSkills()` instead of `registry.getSkillsForBranch(currentBranch)`
  - Groups skills from ALL branches by tier into a single map
  - Skills from different branches can appear in the same row if they share a tier
  - Enables cross-branch prerequisites naturally

### 4. Player Inventory Controls
- **Modified `encodePlayerInventory()`:**
  - Saves player inventory before opening GUI
  - Fills all 36 slots with gray glass panes
  - **Slot 22 (center of row 2):** SP Info display (EXPERIENCE_BOTTLE)
  - **Slot 27 (left of row 3):** Scroll Up button (ARROW or GRAY_DYE)
  - **Slot 28 (next to scroll up):** Scroll Down button (ARROW or GRAY_DYE)
  - **Slot 35 (far right of row 3):** Close button (BARRIER)

```
Player Inventory Layout:
Row 0 (0-8):   [Glass][Glass][Glass][Glass][Glass][Glass][Glass][Glass][Glass]
Row 1 (9-17):  [Glass][Glass][Glass][Glass][Glass][Glass][Glass][Glass][Glass]
Row 2 (18-26): [Glass][Glass][Glass][Glass][SP Info][Glass][Glass][Glass][Glass]
Row 3 (27-35): [Up][Down][Glass][Glass][Glass][Glass][Glass][Glass][Close]
```

### 5. GUI Title
- Updated title from `"Skill Tree - [BranchName]"` to `"Unified Skill Tree"`
- Single purple-colored title for the entire tree

### 6. Scroll Logic
- **New method:** `getMaxTierAcrossAllSkills()`
  - Calculates max tier across ALL branches
  - Used for scroll bounds checking
- **Updated scroll bounds:** `scrollOffset + VISIBLE_TIERS < maxTier` (instead of using single branch max)

### 7. Inventory Control Handler
- **New method:** `handleInventoryClick(int slot)`
  - Called by `SkillTreeGUIListener` when player clicks inventory items
  - Handles scroll up (slot 27), scroll down (slot 28), and close (slot 35)
  - Updates inventory buttons state after scrolling
  - Plays appropriate sounds

## Retained Features

All existing functionality is preserved:
- `encodePlayerInventory()` - Saves original inventory
- `restorePlayerInventory()` - Restores on close
- Skill click handling - Unlock mechanics unchanged
- Skill item rendering - State indicators (locked/can unlock/unlocked) work with cross-branch prerequisites
- Adventure API text components - No legacy ChatColor
- GuildSkillTree state management - Existing SP tracking works
- Cross-path prerequisites - Already supported, now properly displayed

## Design Improvements

### SOLID Principles Applied
- **Single Responsibility:** GUI only handles display and input, not business logic
- **Open/Closed:** Skills from new branches automatically included in unified view
- **Liskov Substitution:** All SkillDefinitions treated equally regardless of branch
- **Interface Segregation:** Separated GUI display from control handling
- **Dependency Inversion:** Depends on SkillTreeRegistry abstraction, not concrete branch data

### Performance
- Single tier map instead of branch-specific filtering
- Efficient stream operations for max tier calculation
- No additional allocations or complex logic

### User Experience
- Unified view shows relationships between skills across branches
- 6 visible tiers at once (increased from 4)
- Controls in player inventory don't interfere with skill display area
- Navigation buttons clearly indicate when scrolling is possible

## Technical Details

### Class: `SkillTreeGUI`
- **Location:** `src/main/java/org/aincraft/skilltree/gui/SkillTreeGUI.java`
- **Dependencies:**
  - `Guild` - Guild data
  - `Player` - Viewer reference
  - `SkillTreeService` - Business logic
  - `SkillTreeRegistry` - Skill definitions
  - `Gui` (triumph-gui) - GUI framework
  - `ItemBuilder` (triumph-gui) - Item creation

### Public Methods
- `open()` - Opens the GUI for the player
- `handleInventoryClick(int slot)` - Handles control button clicks from player inventory

### Private Methods
- `buildGUI()` - Constructs the Gui instance
- `render()` - Renders all visible skills
- `renderSkillTree()` - Groups and displays skills by tier
- `renderTier()` - Renders a single tier's skills
- `createSkillItem()` - Creates clickable skill item with state
- `handleSkillClick()` - Processes skill unlock attempts
- `encodePlayerInventory()` - Sets up inventory controls
- `restorePlayerInventory()` - Restores original inventory
- `getMaxTierAcrossAllSkills()` - Calculates scrolling bounds

## SkillBranch Retention
- `SkillBranch` enum **NOT removed** from `SkillDefinition`
- Can still be used for color-coding, categorization, or future filtering
- GUI simply doesn't use it for display filtering anymore

## Integration Notes

### SkillTreeGUIListener
The `SkillTreeGUIListener` needs to be updated to:
1. Store open GUI instances by player UUID
2. Call `gui.handleInventoryClick(slot)` when player clicks inventory items
3. Example integration:
```java
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();
    if (!hasOpenGUI(player)) return;

    SkillTreeGUI gui = getOpenGUI(player);
    if (event.getClickedInventory() == player.getInventory()) {
        event.setCancelled(true);
        gui.handleInventoryClick(event.getSlot());
    }
}
```

## Testing Recommendations

1. **Unified Display:** Verify skills from all 3 branches appear in single GUI
2. **Tier Grouping:** Skills with same tier appear in same row
3. **Cross-Branch Prerequisites:** Skills can unlock other branch skills
4. **Scrolling:** Up/Down buttons work correctly with max tier from all branches
5. **Inventory Controls:** Click handlers work for scroll and close buttons
6. **Visual Feedback:** Button states update correctly (enabled/disabled colors)
7. **Sound Effects:** Click sounds play when scrolling/closing
8. **Inventory Restoration:** Original inventory restored after closing

## Build Status
- **Compilation:** SUCCESSFUL
- **No breaking changes** to existing codebase
- **Fully backward compatible** with existing skill definitions
