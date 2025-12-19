# Skill Tree GUI Architecture - Unified Design

## Architecture Overview

The refactored SkillTreeGUI implements a unified skill tree that displays all skills from all branches in a single cohesive interface. The GUI is split into two areas:

1. **Main GUI (6 rows, 54 slots):** Displays skill items grouped by tier
2. **Player Inventory (36 slots):** Displays navigation and SP info controls

## GUI Layout Details

### Main GUI Area (Chest Inventory - 6 rows)
```
Row 0: [Tier X Skills...]           (slots 0-8)
Row 1: [Tier X+1 Skills...]         (slots 9-17)
Row 2: [Tier X+2 Skills...]         (slots 18-26)
Row 3: [Tier X+3 Skills...]         (slots 27-35)
Row 4: [Tier X+4 Skills...]         (slots 36-44)
Row 5: [Tier X+5 Skills...]         (slots 45-53)
```

Each row displays up to 9 skills. Skills within a tier are **centered** in their row.

### Player Inventory Area (36 slots)
```
Row 0 (Slots 0-8):   [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass]
Row 1 (Slots 9-17):  [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass]
Row 2 (Slots 18-26): [Glass] [Glass] [Glass] [Glass] [SP Info] [Glass] [Glass] [Glass] [Glass]
Row 3 (Slots 27-35): [Up] [Down] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass] [Close]
```

- **Slots 0-17:** Filled with gray glass panes
- **Slot 22:** Skill Points info (EXPERIENCE_BOTTLE item)
  - Shows available SP and total earned SP
  - Non-interactive display
- **Slot 27:** Scroll Up button (ARROW or GRAY_DYE)
  - ARROW: Can scroll up (green text)
  - GRAY_DYE: Cannot scroll up (gray text)
- **Slot 28:** Scroll Down button (ARROW or GRAY_DYE)
  - ARROW: Can scroll down (green text)
  - GRAY_DYE: Cannot scroll down (gray text)
- **Slot 35:** Close button (BARRIER, red text)

## Data Flow

### Opening the GUI
```
Player clicks command or button
    ↓
open() called
    ↓
encodePlayerInventory() - saves original inventory, sets up controls
    ↓
buildGUI() - creates Gui instance with title
    ↓
render() - populates the 54 skill slots
    ↓
gui.open(viewer) - displays to player
```

### Rendering Skills
```
render()
    ↓
renderSkillTree()
    ├─ getAllSkills() from registry
    ├─ Group by tier: Map<Integer, List<SkillDefinition>>
    └─ Loop visible tiers (6 at a time)
        ↓
    renderTier(tierSkills, row)
        ├─ Calculate base slot (row * 9)
        ├─ Center skills in the row
        └─ Loop through skills
            ↓
        createSkillItem(skill) for each skill
            ├─ Check unlock state
            ├─ Check prerequisite state
            ├─ Create ItemStack with appropriate material/color
            ├─ Add lore with details and prerequisites
            └─ Add click handler
```

### Scrolling Mechanism
```
Player clicks scroll button in inventory
    ↓
SkillTreeGUIListener detects click on slot 27 or 28
    ↓
gui.handleInventoryClick(slot)
    ├─ Case 27 (Scroll Up):
    │   ├─ Check if scrollOffset > 0
    │   ├─ Decrease scrollOffset
    │   ├─ Play sound
    │   ├─ encodePlayerInventory() - updates button states
    │   └─ render() - rerenders with new offset
    │
    ├─ Case 28 (Scroll Down):
    │   ├─ Check if scrollOffset + 6 < maxTier
    │   ├─ Increase scrollOffset
    │   ├─ Play sound
    │   ├─ encodePlayerInventory() - updates button states
    │   └─ render() - rerenders with new offset
    │
    └─ Case 35 (Close):
        └─ viewer.closeInventory()
            ↓
        gui.setCloseGuiAction triggers
            ↓
        restorePlayerInventory() - restores original inventory
```

### Skill Unlocking
```
Player clicks skill item in main GUI
    ↓
createSkillItem() click handler triggered
    ↓
handleSkillClick(skill, tree, isUnlocked, canUnlock)
    ├─ If unlocked: Play pling sound
    ├─ If can unlock:
    │   ├─ Call skillTreeService.unlockSkill()
    │   ├─ If success:
    │   │   ├─ Play levelup sound
    │   │   └─ render() - update GUI
    │   └─ If fail: Play no sound, show error message
    └─ If locked: Play no sound
```

## Key Design Decisions

### 1. Unified vs. Branch-Based View
**Decision:** Single unified tree showing all skills

**Rationale:**
- Shows relationships between skills across branches
- Allows natural cross-branch prerequisites
- More efficient use of screen space (6 rows instead of 4)
- Better user experience with larger visible area

**Alternative Rejected:** Branch tabs would fragment view, waste space with info/control bars

### 2. Controls in Player Inventory
**Decision:** Move scroll buttons and SP info to player inventory area

**Rationale:**
- Maximizes GUI area for skills (54 slots vs. 45)
- Buttons are easily accessible without cluttering main GUI
- Clear visual separation of controls from skill display
- Player can see both chest and inventory simultaneously

**Alternative Rejected:** Could keep controls in bottom rows, but loses valuable skill display space

### 3. Tier Grouping Across All Skills
**Decision:** Group ALL skills by tier, not per-branch

**Rationale:**
- Reflects actual progression structure
- Skills at same tier have similar progression expectations
- Enables meaningful skill comparison across branches
- Natural support for cross-branch prerequisites

**Alternative Rejected:** Per-branch tiers would require complex prerequisite resolution

### 4. Scroll Bounds Calculation
**Decision:** Calculate max tier across ALL skills at render time

**Rationale:**
- Accurate scrolling bounds
- Works if skills are added/removed dynamically
- Simple and efficient stream operation

**Alternative Rejected:** Cache max tier would require invalidation logic

### 5. Inventory Item States
**Decision:** Update inventory button items every scroll (encodePlayerInventory())

**Rationale:**
- Buttons always show correct enabled/disabled state
- Color changes reflect scroll possibilities
- Player gets immediate visual feedback

**Alternative Rejected:** Static items wouldn't update when reaching scroll limits

## Skill Item Display States

### Unlocked Skill
- Material: `ENCHANTED_BOOK` (glowing effect)
- Color: `GREEN`
- State Text: "UNLOCKED"
- Click Action: Play sound (pling)

### Unlockable Skill (has prerequisites, has SP)
- Material: `BOOK`
- Color: `YELLOW`
- State Text: "CLICK TO UNLOCK"
- Click Action: Unlock with SP cost

### Locked - Missing Prerequisites
- Material: `BARRIER`
- Color: `RED`
- State Text: "LOCKED - Missing Prerequisites"
- Prerequisite List: Shows which prerequisites are missing
- Click Action: Play error sound

### Locked - Insufficient SP
- Material: `BOOK`
- Color: `GRAY`
- State Text: "LOCKED - Not Enough SP"
- Click Action: Play error sound

### Lore Template
```
[Skill Description]

Effect: [Effect Name]

Cost: [X] SP (green if affordable, red if not)

Prerequisites:
  ✓ [Completed Prerequisite]
  ✗ [Missing Prerequisite]

[STATE TEXT - BOLD AND COLORED]
```

## Thread Safety Considerations

- `scrollOffset` is modified only by GUI click handlers (main thread)
- `originalInventory` saved/restored by single player
- No concurrent modifications to SkillTreeRegistry
- No multithreading issues with current implementation

## Performance Characteristics

### Memory
- Single GUI instance per open player
- One tier map per render cycle (discarded after rendering)
- No caching of skill lists (re-queried from registry each render)

### CPU
- Tier map creation: O(n) where n = total skills
- Skill item creation: O(m) where m = visible skills (max 54)
- Rendering: O(m) GUI updates

### Optimization Notes
- Tier map could be cached in registry (if skills don't change often)
- Skill item creation could be optimized with object pooling (not necessary currently)
- No blocking operations on main thread

## Error Handling

### Null Checks
- Constructor validates all dependencies with `requireNonNull`
- `getMaxTierAcrossAllSkills()` handles empty skill registry gracefully

### Invalid States
- Buttons properly disabled when at scroll bounds
- Skill unlock failures don't crash GUI
- Player inventory restoration is null-safe

### Edge Cases
- Empty skill registry: Shows empty rows
- Single tier: Scroll buttons properly disabled
- Player quits while GUI open: SkillTreeGUIListener cleans up

## Extensibility

### Adding New Branches
1. Add to `SkillBranch` enum
2. Add skills with new branch in config
3. GUI automatically includes them in unified view
4. No code changes needed

### Changing Skill Tiers
1. Update config tier values
2. GUI automatically recalculates max tier
3. Scrolling bounds adjust automatically

### Adding Respec Functionality
1. Respec logic already separated in SkillTreeService
2. Could be moved back to GUI or kept in listener
3. Would show as button in inventory if needed

## Testing Strategy

### Unit Tests Needed
- `getMaxTierAcrossAllSkills()` with various skill sets
- Scroll bounds checking (edge cases: 0 tiers, 1 tier, many tiers)
- Inventory slot calculations

### Integration Tests
- Opening/closing GUI with skill tree
- Unlocking skills across branches
- Scrolling with various max tiers
- Inventory restoration

### Manual Tests
- Visual verification of skill layout
- Cross-branch prerequisite chains
- Scroll button enable/disable states
- Inventory controls responsiveness

## Future Improvements

1. **Search/Filter:** Add inventory item to search by skill name
2. **Respec UI:** Dedicated respec button in inventory (if not using current system)
3. **Branch Highlighting:** Color code skill items by branch for clarity
4. **Hotkeys:** Allow keyboard shortcuts for scrolling (future enhancement)
5. **Skill Preview:** Click skill to see full details without unlocking
6. **Performance:** Cache tier map in registry if skill set is static

## Summary

The refactored SkillTreeGUI successfully achieves the unified skill tree design goal while maintaining all existing functionality. The architecture cleanly separates concerns (display vs. controls), maximizes screen space, and provides an intuitive user experience with smooth scrolling and clear visual feedback.
