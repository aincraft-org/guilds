# Skill Tree GUI - Visual Guide

## GUI Layout Diagrams

### Main GUI (Chest Inventory - 54 Slots)

```
┌─────────────────────────────────────────────────────────────┐
│                  Unified Skill Tree                         │
│ (Dark Purple Title)                                          │
├─────────────────────────────────────────────────────────────┤
│ Row 0:  [Skill][Skill][Skill][Skill][Skill][Skill][][][]  │ Tier X
│         └─ Centered skills from this tier                   │
├─────────────────────────────────────────────────────────────┤
│ Row 1:  [][Skill][Skill][][][][][][]                       │ Tier X+1
├─────────────────────────────────────────────────────────────┤
│ Row 2:  [Skill][Skill][Skill][Skill][][][][][][]           │ Tier X+2
├─────────────────────────────────────────────────────────────┤
│ Row 3:  [][Skill][][][][][][][][]                          │ Tier X+3
├─────────────────────────────────────────────────────────────┤
│ Row 4:  [Skill][Skill][Skill][Skill][Skill][][][][]        │ Tier X+4
├─────────────────────────────────────────────────────────────┤
│ Row 5:  [][][][Skill][Skill][Skill][][][][]                │ Tier X+5
└─────────────────────────────────────────────────────────────┘
```

### Player Inventory (36 Slots for Controls)

```
┌─────────────────────────────────────────────────────────────┐
│ Hotbar Area                                                 │
├─────────────────────────────────────────────────────────────┤
│ Row 0: [Glass][Glass][Glass][Glass][Glass][Glass][Glass][]│
│        (Slots 0-8)                                          │
├─────────────────────────────────────────────────────────────┤
│ Row 1: [Glass][Glass][Glass][Glass][Glass][Glass][Glass][]│
│        (Slots 9-17)                                         │
├─────────────────────────────────────────────────────────────┤
│ Row 2: [Glass][Glass][Glass][Glass][☆ SP ][Glass][Glass][]│
│        (Slots 18-26)          Info Display (Slot 22)        │
├─────────────────────────────────────────────────────────────┤
│ Row 3: [↑ Up ][↓Down][Glass][Glass][Glass][Glass][Glass][X]│
│        Slot 27 Slot 28                              Close 35 │
└─────────────────────────────────────────────────────────────┘
```

### Slot Layout (Inventory Coordinates)

```
Inventory Row 0: 0   1   2   3   4   5   6   7   8
Inventory Row 1: 9  10  11  12  13  14  15  16  17
Inventory Row 2: 18 19  20  21  22  23  24  25  26
Inventory Row 3: 27 28  29  30  31  32  33  34  35

Key Slots:
- 22: SP Info (Experience Bottle)
- 27: Scroll Up (Arrow or Gray Dye)
- 28: Scroll Down (Arrow or Gray Dye)
- 35: Close (Barrier)
- All others: Gray Glass Pane (filler)
```

---

## Skill Item Visual States

### State 1: Unlocked Skill
```
┌─────────────────────┐
│  ✨ ENCHANTED BOOK  │
│                     │
│ Skill Name          │
│ (GREEN, BOLD)       │
├─────────────────────┤
│ Skill description   │
│                     │
│ Effect: Effect Name │
│ Cost: 5 SP (GREEN)  │
│                     │
│ UNLOCKED            │
│ (GREEN, BOLD)       │
└─────────────────────┘
```
- Material: ENCHANTED_BOOK (with glow)
- Color: GREEN
- Interaction: Click for info, play sound

### State 2: Unlockable Skill
```
┌─────────────────────┐
│  📖 BOOK            │
│                     │
│ Skill Name          │
│ (YELLOW, BOLD)      │
├─────────────────────┤
│ Skill description   │
│                     │
│ Effect: Effect Name │
│ Cost: 5 SP (GREEN)  │
│                     │
│ Prerequisites:      │
│ ✓ Prereq 1          │
│ ✓ Prereq 2          │
│                     │
│ CLICK TO UNLOCK     │
│ (YELLOW, BOLD)      │
└─────────────────────┘
```
- Material: BOOK
- Color: YELLOW
- Interaction: Click to unlock

### State 3: Locked (Missing Prerequisites)
```
┌─────────────────────┐
│  🚫 BARRIER         │
│                     │
│ Skill Name          │
│ (RED, BOLD)         │
├─────────────────────┤
│ Skill description   │
│                     │
│ Effect: Effect Name │
│ Cost: 5 SP (RED)    │
│                     │
│ Prerequisites:      │
│ ✓ Prereq 1          │
│ ✗ Prereq 2 (missing)│
│                     │
│ LOCKED - Missing... │
│ (RED, BOLD)         │
└─────────────────────┘
```
- Material: BARRIER
- Color: RED
- Interaction: Cannot unlock

### State 4: Locked (Insufficient SP)
```
┌─────────────────────┐
│  📖 BOOK            │
│                     │
│ Skill Name          │
│ (GRAY, BOLD)        │
├─────────────────────┤
│ Skill description   │
│                     │
│ Effect: Effect Name │
│ Cost: 5 SP (RED)    │
│                     │
│ Prerequisites:      │
│ ✓ Prereq 1          │
│ ✓ Prereq 2          │
│                     │
│ LOCKED - Not Enough │
│ (GRAY, BOLD)        │
└─────────────────────┘
```
- Material: BOOK
- Color: GRAY
- Interaction: Need more SP

---

## Control Button States

### Scroll Up Button (Slot 27)

**When Can Scroll Up (scrollOffset > 0):**
```
┌──────────────────┐
│  ↑ ARROW         │
│                  │
│ Scroll Up        │
│ (GREEN)          │
└──────────────────┘
```
- Material: ARROW
- Color: GREEN
- Clickable: YES

**When Cannot Scroll Up (scrollOffset == 0):**
```
┌──────────────────┐
│  GRAY DYE        │
│                  │
│ Scroll Up        │
│ (GRAY)           │
└──────────────────┘
```
- Material: GRAY_DYE
- Color: GRAY
- Clickable: NO (but click is caught)

### Scroll Down Button (Slot 28)

**When Can Scroll Down:**
```
┌──────────────────┐
│  ↓ ARROW         │
│                  │
│ Scroll Down      │
│ (GREEN)          │
└──────────────────┘
```
- Material: ARROW
- Color: GREEN
- Clickable: YES

**When Cannot Scroll Down:**
```
┌──────────────────┐
│  GRAY DYE        │
│                  │
│ Scroll Down      │
│ (GRAY)           │
└──────────────────┘
```
- Material: GRAY_DYE
- Color: GRAY
- Clickable: NO

### SP Info Display (Slot 22)

```
┌──────────────────┐
│  ⭐ EXPERIENCE   │
│    BOTTLE        │
│                  │
│ Skill Points     │
│ (GREEN, BOLD)    │
├──────────────────┤
│ Available: 15 SP │
│ (GREEN)          │
│                  │
│ Total Earned:    │
│ 45 SP (GOLD)     │
└──────────────────┘
```
- Material: EXPERIENCE_BOTTLE
- Color: GREEN title, stats in color
- Clickable: NO (display only)

### Close Button (Slot 35)

```
┌──────────────────┐
│  🚫 BARRIER      │
│                  │
│ Close            │
│ (RED, BOLD)      │
└──────────────────┘
```
- Material: BARRIER
- Color: RED
- Clickable: YES

---

## User Flow Diagram

```
┌─────────────┐
│ Player      │
│ Opens GUI   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│ encodePlayerInventory()     │
├─────────────────────────────┤
│ • Save original inventory   │
│ • Fill with glass panes     │
│ • Place control buttons     │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ buildGUI()                  │
├─────────────────────────────┤
│ • Create Gui instance       │
│ • Set title                 │
│ • Setup event handlers      │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ render()                    │
├─────────────────────────────┤
│ • Clear skill slots         │
│ • renderSkillTree()         │
│ • Update display            │
└──────┬──────────────────────┘
       │
       ▼
   ┌───────────────────────────────────┐
   │  GUI DISPLAYED TO PLAYER          │
   │  ✓ 54 skill slots visible         │
   │  ✓ Inventory controls shown       │
   └───────────┬───────────────────────┘
               │
       ┌───────┴────────┬─────────────┐
       │                │             │
       ▼                ▼             ▼
   [Skill Click]  [Scroll Button]  [Close Button]
       │                │             │
       ▼                ▼             ▼
   ┌────────────┐  ┌──────────┐  ┌───────────┐
   │ Unlock     │  │ Scroll   │  │ Close GUI │
   │ Skill      │  │ & Update │  │           │
   │ (if able)  │  │ Display  │  │           │
   └────────────┘  └──────────┘  └───────────┘
       │                │             │
       └────────┬───────┴─────────────┘
                │
                ▼
       ┌──────────────────┐
       │ restorePlayerInv │
       └──────────────────┘
                │
                ▼
       ┌──────────────────┐
       │ Game Continues   │
       └──────────────────┘
```

---

## Tier Layout Example

### With 3 Skills per Tier

```
If max tier is 10 and scrollOffset is 2, showing tiers 3-8:

Row 0: [Skill A][Skill B][Skill C][][][][][][]  ← Tier 3 (3 skills, centered)
Row 1: [Skill D][Skill E][Skill F][][][][][][]  ← Tier 4
Row 2: [Skill G][Skill H][Skill I][][][][][][]  ← Tier 5
Row 3: [Skill J][Skill K][Skill L][][][][][][]  ← Tier 6
Row 4: [Skill M][Skill N][Skill O][][][][][][]  ← Tier 7
Row 5: [Skill P][Skill Q][Skill R][][][][][][]  ← Tier 8
```

### With Uneven Distribution

```
If skills vary in number per tier:

Row 0: [][][Skill A][Skill B][][][][]  ← Tier 3 (2 skills, centered)
Row 1: [Skill B][Skill C][Skill D][Skill E][][][][][]  ← Tier 4 (4 skills)
Row 2: [Skill F][][][][][][][][]  ← Tier 5 (1 skill, centered)
Row 3: [Skill G][Skill H][][][][][][]  ← Tier 6 (2 skills, centered)
Row 4: [Skill I][Skill J][Skill K][Skill L][Skill M][Skill N][][]  ← Tier 7 (6 skills)
Row 5: [Skill O][Skill P][Skill Q][Skill R][][][][][][]  ← Tier 8 (4 skills)
```

---

## Scroll Mechanics Visualization

### Scenario 1: At Top (scrollOffset = 0)

```
Available Tiers: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 (maxTier = 10)
Visible Window: Tiers 1-6

scrollOffset = 0
canScrollUp = false   ← Button disabled (gray)
canScrollDown = true  ← Button enabled (green)

Current Display:
Row 0: Tier 1
Row 1: Tier 2
Row 2: Tier 3
Row 3: Tier 4
Row 4: Tier 5
Row 5: Tier 6

↑ Click Down Arrow ↓
```

### Scenario 2: In Middle (scrollOffset = 2)

```
scrollOffset = 2
canScrollUp = true   ← Button enabled (green)
canScrollDown = true ← Button enabled (green)

Current Display:
Row 0: Tier 3
Row 1: Tier 4
Row 2: Tier 5
Row 3: Tier 6
Row 4: Tier 7
Row 5: Tier 8

↑ Click Up Arrow ↑
```

### Scenario 3: At Bottom (scrollOffset = 4)

```
scrollOffset = 4
canScrollUp = true   ← Button enabled (green)
canScrollDown = false ← Button disabled (gray) [4 + 6 = 10, at max]

Current Display:
Row 0: Tier 5
Row 1: Tier 6
Row 2: Tier 7
Row 3: Tier 8
Row 4: Tier 9
Row 5: Tier 10

↑ Click Up Arrow ↑
```

---

## Cross-Branch Display Example

### Economy, Territory, Combat Skills Mixed by Tier

```
Tier 1 (No Branches Required):
├─ eco_xp_1 (Economy - XP Insight I)
├─ terr_crop_1 (Territory - Green Thumb I)
└─ combat_prot_1 (Combat - Iron Will I)

Row 0 Display:
[eco_xp_1][terr_crop_1][][combat_prot_1][][][][][]
 (gold)    (green)        (red)
```

### With Prerequisites Across Branches

```
combat_dmg_1 (Combat - Battle Fury I)
├─ Requires: combat_prot_2 (Combat - Iron Will II) ✓ Unlocked
├─ Requires: terr_spawn_1 (Territory - Wild Growth I) ✗ Not Unlocked
└─ State: LOCKED - Missing Prerequisites
```

---

## Color Reference

| Element | Color | Usage |
|---------|-------|-------|
| Title | Dark Purple | GUI title |
| Unlocked Skill | Green | Active/completed |
| Unlockable | Yellow | Ready to unlock |
| Locked (Prereqs) | Red | Missing requirements |
| Locked (SP) | Gray | Need more points |
| Can Scroll | Green | Button enabled |
| Cannot Scroll | Gray | Button disabled |
| SP Available | Green | Current points |
| SP Total | Gold | Lifetime earned |
| Glass Filler | Gray | Spacer items |

---

## Material Reference

| Button | Material | State |
|--------|----------|-------|
| Scroll Up | Arrow | Can scroll |
| Scroll Up | Gray Dye | Cannot scroll |
| Scroll Down | Arrow | Can scroll |
| Scroll Down | Gray Dye | Cannot scroll |
| SP Info | Experience Bottle | Always |
| Close | Barrier | Always |
| Filler | Gray Glass Pane | Always |
| Unlocked Skill | Enchanted Book | Always |
| Unlockable | Book | Always |
| Locked (Prereqs) | Barrier | Always |
| Locked (SP) | Book | Always |

---

## Summary

The unified skill tree GUI provides:
- **54 skill display slots** organized in 6 rows
- **4 control slots** in player inventory
- **Dynamic button states** reflecting scroll availability
- **Clear visual states** for each skill state
- **Intuitive layout** with centered skills and organized tiers
- **Cross-branch support** with mixed-branch tier rows

All visual elements are color-coded for quick understanding and accessibility.
