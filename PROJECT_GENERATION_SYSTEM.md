# Procedural Project Generation System

## Overview

The procedural project generation system provides daily randomized guild projects with unique names, buffs, quests, and material requirements. Each guild receives a consistent set of projects for 24 hours, then a new set is generated the next day.

## Key Components

### ProjectGenerator
**File:** `src/main/java/org/aincraft/project/ProjectGenerator.java`

The core generation engine responsible for creating random `ProjectDefinition` objects.

#### Key Features:
- **Deterministic daily seeding**: Uses `guildId + currentDay` to generate a seed
- **Same guild, same day = same projects**: All guilds receive identical projects for the same calendar day
- **3-5 projects per day**: Randomly selected count between 3 and 5 projects
- **Unique names**: Combines 15 adjectives × 15 nouns = 225 possible combinations
- **No duplicate buffs per day**: Each project in a day's pool has a different buff type
- **Quest variety**: 1-3 quests per project from 6 quest types
- **Material requirements**: 1-3 materials per project from 7 material types

#### Generation Templates:

**Adjectives** (15):
Ancient, Mystic, Savage, Divine, Shadow, Iron, Golden, Eternal, Primal, Arcane, Radiant, Spectral, Infernal, Celestial, Twilight

**Nouns** (15):
Hunt, Harvest, Conquest, Expedition, Crusade, Endeavor, Quest, Trial, Challenge, Venture, Ascension, Dominion, Awakening, Siege, Odyssey

**Buff Types** (5):
- XP_MULTIPLIER: 10%-50% boost (GLOBAL)
- DAMAGE_BOOST: 5%-25% boost (TERRITORY)
- PROTECTION_BOOST: 5%-15% damage reduction (TERRITORY)
- CROP_GROWTH_SPEED: 25%-75% faster (TERRITORY)
- MINING_SPEED: 10%-30% faster (TERRITORY)

**Quest Types** (6):
- KILL_MOB: Zombies, Skeletons, Spiders, Creepers, Endermen (100-1000 kills)
- MINE_BLOCK: Coal, Iron, Gold, Diamond, Stone (200-2000 blocks)
- COLLECT_ITEM: Wheat, Carrot, Potato, Beetroot (500-3000 items)
- CRAFT_ITEM: Iron Ingot, Gold Ingot, Bread, Torch (50-500 items)
- FISH_ITEM: Cod, Salmon, Tropical Fish (50-300 items)
- BREED_MOB: Cow, Pig, Sheep, Chicken (20-150 animals)

**Materials** (7):
- Iron Ingot: 64-512
- Gold Ingot: 32-256
- Diamond: 8-64
- Coal: 128-1024
- Emerald: 16-128
- Redstone: 64-512
- Lapis Lazuli: 64-256

#### Quest Count Rounding:
- Minimum: 50 (enforced)
- Rounding: Multiples of 50

#### Material Amount Rounding:
- Minimum: 32 (one stack)
- Rounding: Multiples of 32 (stack size)

#### Buff Duration:
- Random: 1-7 days

### ProjectPoolService
**File:** `src/main/java/org/aincraft/project/ProjectPoolService.java`

Service responsible for managing the pool of available projects for guilds.

#### Key Methods:
- `getAvailableProjects(guildId, guildLevel)`: Returns 3-5 procedurally generated projects for the guild
- `refreshPool(guildId)`: Triggers daily refresh (once per 24 hours)
- `isProjectAvailable(guildId, guildLevel, projectId)`: Checks if a project is in the daily pool

#### Refresh Behavior:
- Checks `GuildProjectRepository.getLastRefreshTime()`
- Compares against `ProjectRegistry.getRefreshIntervalHours()` (default: 24 hours)
- Automatically refreshes pool when 24 hours have passed
- Updates pool seed to trigger new generation

## Seeding Algorithm

### Deterministic Generation
```
daySeed = System.currentTimeMillis() / 86400000L  // Days since epoch
seed = guildId.hashCode() + daySeed
random = new Random(seed)
```

### Implications:
1. **Same Guild, Same Day**: Guild 123 on Dec 17, 2025 always gets same 5 projects
2. **Different Guild, Same Day**: Guild 456 on Dec 17, 2025 gets different projects
3. **Same Guild, Different Day**: Guild 123 on Dec 18, 2025 gets different projects
4. **Timezone Agnostic**: Uses UTC days since epoch, so all servers globally refresh simultaneously

## Data Flow

```
ProjectPoolService.getAvailableProjects()
    ├─ Check if 24 hours since last refresh
    ├─ ProjectGenerator.generateProjects(guildId, level, count)
    │   ├─ Create seeded Random from guildId + day
    │   ├─ For each project (1-5):
    │   │   ├─ Generate unique ID
    │   │   ├─ Generate random name
    │   │   ├─ Select non-duplicate buff
    │   │   ├─ Generate 1-3 quests
    │   │   ├─ Generate 1-3 materials
    │   │   └─ Return ProjectDefinition
    │   └─ Return List<ProjectDefinition>
    └─ Return projects to UI/service
```

## Dependency Injection

**GuildsModule.java** binding:
```java
bind(ProjectGenerator.class).in(Singleton.class);
```

Injected into:
- `ProjectPoolService`: Requires `ProjectGenerator` + `ProjectRegistry` + `GuildProjectRepository`
- `ProjectGenerator`: Requires `Logger` (named "guilds")

## SOLID Principles Applied

### Single Responsibility
- `ProjectGenerator`: Only responsible for generating random projects
- `ProjectPoolService`: Only responsible for managing project availability
- `ProjectRegistry`: Only responsible for static project definitions
- Each template class (BuffTemplate, QuestTemplate, etc.) has single purpose

### Open/Closed
- New buff/quest/material types can be added to template arrays without modifying logic
- Generation algorithms are extensible via new templates
- Seed algorithm remains constant while output varies

### Liskov Substitution
- `ProjectDefinition` is immutable record, always substitutable
- `BuffDefinition`, `QuestRequirement` are records with predictable contracts
- Dependency injection allows testing with mock implementations

### Interface Segregation
- `ProjectPoolService` exposes only necessary public methods
- `ProjectGenerator` is self-contained with no unnecessary public APIs
- Repositories define minimal required interface

### Dependency Inversion
- Both services depend on abstractions (interfaces), not concrete implementations
- `GuildProjectRepository` is injected, not created internally
- Logger injected via dependency injection framework

## Performance Characteristics

### Time Complexity
- `generateProjects()`: O(n) where n = number of projects (typically 3-5)
- No database lookups or blocking I/O
- Pure in-memory random generation

### Memory Complexity
- Single Random instance per call (garbage collected after)
- No caching - generation is deterministic and lightweight
- Safe for high-frequency calls

### Recommendations
- Safe to call multiple times per request
- No caching needed due to deterministic nature
- Consider async calls only for UI responses with many guilds

## Configuration

### Via ProjectRegistry
```yaml
projects:
  pool-size: 3              # Min projects per day (override pool count logic)
  refresh-interval-hours: 24 # How often pool refreshes
  expiration-check-interval: 60 # Background check interval
```

### Programmatically
```java
int projectCount = 3 + (poolSeed % 3);  // 3, 4, or 5 projects
projectPoolService.getAvailableProjects(guildId, guildLevel);
```

## Examples

### Example 1: Guild Gets Daily Projects
```
Guild "Warriors" (ID: guild_123) on Dec 17, 2025, 10:00 AM UTC

Seed: guild_123.hashCode() + 19747 (days since epoch)
Generates:
  1. Ancient Hunt
     - XP Boost (20% GLOBAL)
     - Quests: Kill 150 Zombies, Mine 500 Coal Ore
     - Materials: 256x Iron Ingot, 64x Diamond
     - Duration: 5 days

  2. Divine Conquest
     - Damage Boost (15% TERRITORY)
     - Quests: Kill 300 Skeletons, Craft 200 Torches
     - Materials: 128x Gold Ingot, 256x Redstone
     - Duration: 3 days

  3. Spectral Venture
     - Crop Growth (50% TERRITORY)
     - Quests: Collect 1500 Wheat, Breed 75 Cows
     - Materials: 512x Coal, 64x Lapis Lazuli
     - Duration: 7 days

Next day (Dec 18), same guild gets different projects.
Other guilds (even same day) get different projects.
```

### Example 2: Reproducible Testing
```java
// Same seed = same projects
Random r1 = new Random(guild123_day_19747);
ProjectDefinition p1 = generateProject(r1, 0);

Random r2 = new Random(guild123_day_19747);
ProjectDefinition p2 = generateProject(r2, 0);

assert p1.id().equals(p2.id());
assert p1.name().equals(p2.name());
// All fields match - completely deterministic
```

## Future Enhancements

1. **Difficulty Scaling**: Adjust quest counts/material amounts based on guild level
2. **Seasonal Themes**: Different adjective/noun pools based on game season
3. **Guild Personality**: Seed modifier based on guild preferences/history
4. **Leaderboard Integration**: Track completion times for speedrun competitions
5. **Event-Based Projects**: Special projects during server events
6. **Difficulty Modes**: Easy/Normal/Hard variants with adjusted rewards
7. **Exotic Materials**: Add rare materials as completion rewards

## Testing

### Unit Test Example
```java
@Test
void testDeterministicGeneration() {
    ProjectGenerator gen = new ProjectGenerator(logger);
    List<ProjectDefinition> projects1 = gen.generateProjects("guild_1", 10, 5);
    List<ProjectDefinition> projects2 = gen.generateProjects("guild_1", 10, 5);

    assertEquals(projects1.size(), projects2.size());
    for (int i = 0; i < projects1.size(); i++) {
        assertEquals(projects1.get(i).id(), projects2.get(i).id());
        assertEquals(projects1.get(i).name(), projects2.get(i).name());
    }
}

@Test
void testNoDuplicateBuffs() {
    ProjectGenerator gen = new ProjectGenerator(logger);
    List<ProjectDefinition> projects = gen.generateProjects("guild_1", 10, 5);

    Set<String> buffs = projects.stream()
        .map(p -> p.buff().categoryId())
        .collect(Collectors.toSet());

    assertEquals(buffs.size(), projects.size()); // No duplicates
}

@Test
void testQuestCounts() {
    ProjectGenerator gen = new ProjectGenerator(logger);
    List<ProjectDefinition> projects = gen.generateProjects("guild_1", 10, 5);

    projects.stream()
        .flatMap(p -> p.quests().stream())
        .forEach(q -> {
            assertTrue(q.targetCount() % 50 == 0);
            assertTrue(q.targetCount() >= 50);
        });
}
```

## Troubleshooting

### "Same guild gets different projects each day"
- **Expected behavior** - Check that `GuildProjectRepository.getLastRefreshTime()` is properly tracking last refresh
- Each day (UTC midnight) triggers new generation

### "Different guilds get same projects"
- **Bug in seeding** - Verify `guildId.hashCode()` is being used
- Check that seed calculation includes both guild ID and day

### "Projects have duplicate buffs in one day"
- **Logic error** - Verify `usedBuffs` set is properly maintained
- Check that `selectBuffTemplate()` is avoiding duplicates

### "Quest counts not rounded"
- **Verify rounding** - Ensure `(count / 50) * 50` is applied
- Check minimum value enforcement `Math.max(50, count)`

## Files Modified

1. **src/main/java/org/aincraft/project/ProjectGenerator.java** - New
2. **src/main/java/org/aincraft/project/ProjectPoolService.java** - Updated
3. **src/main/java/org/aincraft/inject/GuildsModule.java** - Updated
